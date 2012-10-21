(ns symbolicweb.core
  (:require [clojure.math.numeric-tower :refer (round)])
  (:require clojure.stacktrace)
  (:require [clojure.string :as str])
  (:require [clojure.pprint :refer (cl-format)])

  (:import (com.google.common.cache CacheBuilder CacheLoader))

  (:require [hiccup.core :refer (html)])
  (:require [hiccup.util :refer (escape-html)])
  (:require [hiccup.page :refer (doctype xhtml-tag)])

  (:import [org.jsoup Jsoup])

  (:use [cheshire.core :as json]) ;; JSON.

  (:require [clojure.java.jdbc :refer (with-connection
                                       with-query-results
                                       find-connection
                                       update-values
                                       as-quoted-identifier
                                       insert-record
                                       delete-rows)])
  (:import com.mchange.v2.c3p0.ComboPooledDataSource)

  (:require ring.util.codec)
  (:require ring.middleware.params)
  (:require ring.middleware.cookies)

  ;; Netty (EPOLL).
  (:require lamina.core)
  (:require aleph.http)
  (:require overtone.at-at)

  (:require symbolicweb.macros)
  (:require symbolicweb.lifetime)
  (:require symbolicweb.reactive-paradigm)
  (:require symbolicweb.value_model)
  (:require symbolicweb.globals)
  (:require symbolicweb.common)
  (:require symbolicweb.database-common)
  (:require symbolicweb.database-dao)

  (:require symbolicweb.container-model)
  (:require symbolicweb.container-model-node)

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
  (:require symbolicweb.handy-handlers)
  (:require symbolicweb.application))



(set! *warn-on-reflection* true)


(defn handler [request]
  (swap! -request-counter- inc')
  (binding [*print-level* 2] ;; Clojure printing isn't very solid; pretty printing with circular checks is needed!
    (try
      (let [application (find-or-create-application-instance request)]
        (touch application)
        ;; TODO: Application level try/catch here: ((:exception-handler-fn @application) e).
        ;; TODO: Production / development modes needed here too. Logging, etc. etc...
        ((:request-handler @application) request application))
      (catch Throwable e
        ;; Send to REPL first..
        ;; TODO: Let an agent handle this; or the logging system mentioned above will probably handle it
        (println) (println)
        (println "SymbolicWeb.core/handler (core.clj); Top Level Exception")
        (println "--------------------------------------------------------")
        (clojure.stacktrace/print-stack-trace e 50)

        ;; ..then send to HTTP client.
        {:status 500
         :headers {"Content-Type" "text/html; charset=UTF-8"
                   "Server" "http://nostdal.org/"}
         :body
         (html
          [:html
           [:body {:style "font-family: sans-serif;"}
            [:h3 [:a {:href "https://github.com/lnostdal/SymbolicWeb"} "SymbolicWeb"] ": Top Level Server Exception (HTTP 500)"]
            [:pre
             \newline
             (with-out-str (clojure.stacktrace/print-stack-trace e 1000))
             \newline]
            [:img {:src "/gfx/common/sw/stack_trace_or_gtfo.jpg"}]]])}))))



;; TODO: STOP-SW-SERVER doesn't actually work. Hm.
(defn main
  ([] (main 8080))
  ([port]
     (defonce stop-sw-server (aleph.http/start-http-server
                              (aleph.http/wrap-ring-handler
                               (ring.middleware.cookies/wrap-cookies
                                (ring.middleware.params/wrap-params handler)))
                              {:port port}))))
