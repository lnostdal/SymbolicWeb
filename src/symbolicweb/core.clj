(set! *warn-on-reflection* true)
(set! *print-length* 30)
(set! *print-level* 3)
(set! *read-eval* false)



(ns symbolicweb.core
  ;;(:require symbolicweb.init)

  (:import clojure.lang.Ref
           clojure.lang.Keyword
           clojure.lang.Fn
           clojure.lang.MapEntry
           clojure.lang.Atom
           clojure.lang.Delay)
  (:require [clojure.math.numeric-tower :refer (round)])
  (:require clojure.stacktrace)
  (:require [clojure.string :as str])
  (:require [clojure.pprint :refer (cl-format pprint print-table)])

  (:import (com.google.common.cache CacheBuilder CacheLoader))

  (:require [hiccup.core :refer (html)])
  (:require [hiccup.util :refer (escape-html)])
  (:require [hiccup.page :refer (doctype xhtml-tag)])

  (:import [org.jsoup Jsoup])

  (:require [garden.core :refer [css style]])

  (:require [cheshire.core :as json])

  (:require ring.util.codec)
  (:require ring.middleware.params)
  (:require ring.middleware.cookies)

  (:require [sqlingvo.core :as sql])

  (:require [clj-time.core :as time])
  (:require [clj-time.coerce :as time.coerce])

  (:require symbolicweb.macros)
  (:require symbolicweb.lifetime)
  (:require symbolicweb.reactive-paradigm-types)
  (:require symbolicweb.reactive-paradigm)
  (:require symbolicweb.value_model)
  (:require symbolicweb.globals)
  (:require symbolicweb.common)
  (:require symbolicweb.agent)
  (:require symbolicweb.logging)

  (:require symbolicweb.container-model)
  (:require symbolicweb.container-model-node)

  (:require symbolicweb.util)
  (:require symbolicweb.database-types)
  (:require symbolicweb.database-jdbc)
  (:require symbolicweb.database-common)
  (:require symbolicweb.database-dao)
  (:require symbolicweb.database-query)
  (:require symbolicweb.database-json)

  (:require symbolicweb.widget-base-class)
  (:require symbolicweb.jquery)
  (:require symbolicweb.widget-base)
  (:require symbolicweb.viewport)

  (:require symbolicweb.user)

  (:require symbolicweb.text-input)
  (:require symbolicweb.html-elements)
  (:require symbolicweb.container-view)
  (:require symbolicweb.container-model-filtered)
  (:require symbolicweb.html-container)
  (:require symbolicweb.html-template)
  (:require symbolicweb.link)
  (:require symbolicweb.checkbox)
  (:require symbolicweb.img)
  (:require symbolicweb.dialog)
  (:require symbolicweb.combo-box)
  (:require symbolicweb.tooltip)
  (:require symbolicweb.sticky)
  (:require symbolicweb.sortable)
  (:require symbolicweb.date-and-time)
  (:require symbolicweb.garbage-collection)

  (:require [org.httpkit.server :as http.server])
  (:require org.httpkit.timer)

  (:require symbolicweb.history)
  (:require symbolicweb.handy-handlers)
  (:require symbolicweb.session)

  (:require symbolicweb.bs-alert)
  (:require symbolicweb.bs-dialog)

  (:require symbolicweb.bitcoin)

  (:require symbolicweb.examples.nostdal-org)
  (:require symbolicweb.examples.history)
  (:require symbolicweb.examples.bitcoin)
  (:require symbolicweb.examples.bitcoin-units)
  )



(defn handler [request]
  (swap! -request-counter- inc')
  (try
    (swsync
     (let [^Ref session (find-or-create-session request)]
       (touch session)
       ;; TODO: Session level try/catch here: ((:exception-handler-fn @session) e).
       ;; TODO: Production / development modes needed here too. Logging, etc. etc...
       (with1 ((:request-handler @session) request session)
         (when (:one-shot? @session)
           (gc-session (:uuid @session) session))
         (when-let [^Fn f (::url-alter-query-params @*dyn-ctx*)]
           (f))
         (when-let [^Fn f (::add-response-chunk-agent-fn @*dyn-ctx*)]
           (f)))))

    (catch Throwable e
      (let [ex-id (generate-uuid)]
        (log "Top Level Exception:"
             (with-out-str
               (println "\n\nREQUEST-ID:" ex-id)
               (println e)
               (println request)
               (clojure.stacktrace/print-stack-trace e 50)))

        ;; TODO: This doesn't check what sort of response the client expects; the "Accept" header, but the client
        ;; (sw-ajax.js) currently handles this by checking for 500 status.
        {:status 500
         :headers {"Content-Type" "text/html; charset=UTF-8"
                   "Cache-Control" "no-cache"}
         :body
         (html
          [:html
           [:head [:title "Top Level Server Exception: HTTP 500"]]
           [:body {:style "font-family: sans-serif;"}
            [:p {:style "color: red;"} [:b (escape-html (str e))]]
            [:p "This incident has been logged (ID: " ex-id ")."]
            [:p [:pre (escape-html (with-out-str (clojure.stacktrace/print-stack-trace e 1000)))]]]])}))))



(defonce -server- nil)



(defn stop-server []
  (when -server-
    (-server-)))



(defn start-server [^Long port]
  (stop-server)
  ;; Back-end Java code from HTTP-KIT spawning threads has no idea what Clojure bindings are, so we forward them here.
  (let [bnds (get-thread-bindings)]
    (def -server-
      (http.server/run-server (ring.middleware.cookies/wrap-cookies
                               (ring.middleware.params/wrap-params
                                #(with-bindings bnds
                                   (handler %1))))
                              {:port port
                               :thread (. (Runtime/getRuntime) availableProcessors)
                               :worker-name-prefix "http-worker-"
                               :max-body 1048576})))) ;; Max 1M request body (e.g. POST).
