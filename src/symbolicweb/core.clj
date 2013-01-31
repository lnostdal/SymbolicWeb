(ns symbolicweb.core
  (:require symbolicweb.init)

  (:import clojure.lang.Ref
           clojure.lang.Keyword
           clojure.lang.Fn
           clojure.lang.MapEntry)
  (:require [clojure.math.numeric-tower :refer (round)])
  (:require clojure.stacktrace)
  (:require [clojure.string :as str])
  (:require [clojure.pprint :refer (cl-format)])

  (:import (com.google.common.cache CacheBuilder CacheLoader))

  (:require [hiccup.core :refer (html)])
  (:require [hiccup.util :refer (escape-html)])
  (:require [hiccup.page :refer (doctype xhtml-tag)])

  (:import [org.jsoup Jsoup])

  (:require [cheshire.core :as json])

  (:import com.mchange.v2.c3p0.ComboPooledDataSource)

  (:require ring.util.codec)
  (:require ring.middleware.params)
  (:require ring.middleware.cookies)

  (:require [clojure.java.jdbc :as jdbc])
  (:require [clojure.java.jdbc.sql :as sql])

  (:require [clj-time.core :as time])
  (:require [clj-time.coerce :as time.coerce])

  (:require overtone.at-at)

  (:require symbolicweb.macros)
  (:require symbolicweb.lifetime)
  (:require symbolicweb.reactive-paradigm)
  (:require symbolicweb.value_model)
  (:require symbolicweb.globals)
  (:require symbolicweb.common)

  (:require symbolicweb.container-model)
  (:require symbolicweb.container-model-node)

  (:require symbolicweb.database-types)
  (:require symbolicweb.database-jdbc)
  (:require symbolicweb.database-common)
  (:require symbolicweb.database-dao)

  (:require symbolicweb.widget-base-class)
  (:require symbolicweb.jquery)
  (:require symbolicweb.widget-base)
  (:require symbolicweb.viewport)
  (:require symbolicweb.util)

  (:require symbolicweb.user)

  (:require symbolicweb.text-input)
  (:require symbolicweb.html-elements)
  (:require symbolicweb.container-view)
  (:require symbolicweb.container-model-filtered)
  (:require symbolicweb.html-container)
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
  (:require symbolicweb.handy-handlers)
  (:require symbolicweb.session))



(set! *warn-on-reflection* true)

(let [bnds (get-thread-bindings)]
  (defn handler [request]
    (swap! -request-counter- inc')
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
            ;; Send to REPL first..
            ;; TODO: Let an agent handle this; or the logging system mentioned above will probably handle it
            (println) (println)
            (println "SymbolicWeb.core/handler (core.clj); Top Level Exception")
            (println "--------------------------------------------------------")
            (clojure.stacktrace/print-stack-trace e 50)

            ;; ..then send to HTTP client.
            {:status 500
             :headers {"Content-Type" "text/html; charset=UTF-8"}
             :body
             (html
              [:html
               [:body {:style "font-family: sans-serif;"}
                [:h3 [:a {:href "https://github.com/lnostdal/SymbolicWeb"} "SymbolicWeb"]
                 ": Top Level Server Exception (HTTP 500)"]
                [:pre
                 \newline
                 (with-out-str (clojure.stacktrace/print-stack-trace e 1000))
                 \newline]
                [:img {:src "/gfx/common/sw/stack_trace_or_gtfo.jpg"}]]])}))))))



(defonce -server- nil)



(defn stop-server []
  (when -server-
    (-server-)))



(defn start-server [^Long port]
  (stop-server)
  (def -server- (http.server/run-server (ring.middleware.cookies/wrap-cookies
                                         (ring.middleware.params/wrap-params
                                          handler))
                                        {:port port
                                         :thread 8
                                         :worker-name-prefix "http-worker-"
                                         :max-body 1048576}))) ;; Max 1M request body (e.g. POST).
