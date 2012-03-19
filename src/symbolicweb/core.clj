(ns symbolicweb.core
  (:use clojure.math.numeric-tower)
  (:require clojure.stacktrace)
  (:require [clojure.string :as str])

  (:use [clojure.pprint :only (cl-format)])

  (:import org.apache.commons.collections.map.ReferenceMap)

  (:use hiccup.core)
  (:use [hiccup.util :exclude (url-encode)])
  (:use [hiccup.page])

  (:use [net.cgrand.enlive-html :exclude (at)])

  (:use [cheshire.core :as json]) ;; JSON.

  (:use [clojure.java.jdbc :exclude (resultset-seq)])
  (:import com.mchange.v2.c3p0.ComboPooledDataSource)

  (:use ring.util.codec)
  (:use ring.middleware.params)
  (:use ring.middleware.cookies)

  ;; Netty (EPOLL).
  (:use lamina.core)
  (:use aleph.http)
  (:use overtone.at-at)

  (:require symbolicweb.macros)
  (:require symbolicweb.model)
  (:require symbolicweb.globals)
  (:require symbolicweb.common)
  (:require symbolicweb.database-common)

  (:require symbolicweb.container-model)
  (:require symbolicweb.container-model-node)

  (:require symbolicweb.jquery)
  (:require symbolicweb.container)
  (:require symbolicweb.viewport)

  (:require symbolicweb.user)

  (:require symbolicweb.widget-base)
  (:require symbolicweb.text-input)
  (:require symbolicweb.html-elements)
  (:require symbolicweb.container-view)
  (:require symbolicweb.html-container)
  (:require symbolicweb.checkbox)
  (:require symbolicweb.img)
  (:require symbolicweb.dialog)
  (:require symbolicweb.combo-box)
  (:require symbolicweb.tooltip)
  (:require symbolicweb.sticky)

  (:require symbolicweb.handy-handlers)
  (:require symbolicweb.application)
  (:require symbolicweb.date-and-time)
  (:require symbolicweb.sortable))



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
             (with-out-str (clojure.stacktrace/print-stack-trace e 1000)) ;; TODO: Magic value.
             \newline]
            [:img {:src "/gfx/common/sw/stack_trace_or_gtfo.jpg"}]]])}))))


;; TODO: STOP-SW-SERVER doesn't actually work. Hm.
(defn main
  ([] (main 8080))
  ([port]
     (defonce stop-sw-server (start-http-server (wrap-ring-handler (wrap-cookies (wrap-params handler)))
                                                {:port port}))))
