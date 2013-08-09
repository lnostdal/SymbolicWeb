(ns symbolicweb.core
  (:require symbolicweb.init)

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

  (:require [cheshire.core :as json])

  (:require ring.util.codec)
  (:require ring.middleware.params)
  (:require ring.middleware.cookies)

  (:require [clojure.java.jdbc :as jdbc])
  (:require [clojure.java.jdbc.sql :as sql])

  (:require [clj-time.core :as time])
  (:require [clj-time.coerce :as time.coerce])

  (:require symbolicweb.macros)
  (:require symbolicweb.lifetime)
  (:require symbolicweb.reactive-paradigm)
  (:require symbolicweb.value_model)
  (:require symbolicweb.globals)
  (:require symbolicweb.common)
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

  (:require symbolicweb.examples.nostdal-org))



(let [bnds (get-thread-bindings)]
  (defn handler [request]
    (let [http-request-id (swap! -request-counter- inc')]
      ;; TODO: Clojure printing isn't very solid; pretty printing with circular checks is needed!
      (with-bindings bnds
        (binding [*print-level* 2]
          (try
            (swsync
             (let [^Ref session (find-or-create-session request)]
               (touch session)
               ;; TODO: Session level try/catch here: ((:exception-handler-fn @session) e).
               ;; TODO: Production / development modes needed here too. Logging, etc. etc...
               (with1 ((:request-handler @session) request session)
                 (when (:one-shot? @session)
                   (gc-session session)))))

            (catch Throwable e
              ;; Log first..
              (log "Top Level Exception:"
                   (with-out-str
                     (println "\n\nHTTP-REQUEST-ID:" http-request-id)
                     (println e)
                     (println request)
                     (clojure.stacktrace/print-stack-trace e 50)))

              ;; ..then send to HTTP client.
              ;; TODO: This doesn't check what sort of response the client expects; the "Accept" header.
              {:status 500
               :headers {"Content-Type" "text/html; charset=UTF-8"
                         "Cache-Control" "no-cache"}

               :body
               (html
                [:html
                 [:head [:title "SymbolicWeb: Top Level Server Exception: HTTP 500"]]
                 [:body {:style "font-family: sans-serif;"}
                  [:p {:style "color: red;"} [:b (escape-html (str e))]]
                  [:p "This incident has been logged (ID: " http-request-id ")."]
                  [:p [:pre (escape-html (with-out-str (clojure.stacktrace/print-stack-trace e 1000)))]]
                  [:p "HTTP 500: Top level server exception caught by " [:a {:href "https://github.com/lnostdal/SymbolicWeb"}
                                                                         "SymbolicWeb"] "."]]])})))))))



(defonce -server- nil)



(defn stop-server []
  (when -server-
    (-server-)))



(defn start-server [^Long port]
  (stop-server)
  (def -server-
    (http.server/run-server (ring.middleware.cookies/wrap-cookies
                             (ring.middleware.params/wrap-params
                              handler))
                            {:port port
                             :thread (. (Runtime/getRuntime) availableProcessors)
                             :worker-name-prefix "http-worker-"
                             :max-body 1048576}))) ;; Max 1M request body (e.g. POST).
