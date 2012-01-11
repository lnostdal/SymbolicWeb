(ns symbolicweb.core
  (:use clojure.math.numeric-tower)
  (:require clojure.stacktrace)
  (:require [clojure.string :as str])

  (:use [clojure.pprint :only (cl-format)])

  ;; TODO: Needed for WIP in model.clj
  ;;(:import java.util.WeakHashMap)
  ;;(:import java.lang.ref.SoftReference)
  (:import org.apache.commons.collections.map.ReferenceMap)

  (:use hiccup.core)
  (:use [hiccup.page-helpers :exclude (encode)])

  (:use [net.cgrand.enlive-html :exclude (at)])

  (:use [cheshire.core :as json]) ;; JSON.

  (:use [clojure.java.jdbc :exclude (resultset-seq)])
  (:import com.mchange.v2.c3p0.ComboPooledDataSource)

  ;; TODO: I think a lot of these things might be available via aleph?
  (:use ring.util.codec)
  (:use ring.middleware.params)
  (:use ring.middleware.cookies)

  ;; Netty (EPOLL).
  (:use lamina.core)
  (:use aleph.http)
  (:use overtone.at-at)

  (:require symbolicweb.macros)
  (:require symbolicweb.model)
  (:require symbolicweb.database-common)
  (:require symbolicweb.globals)
  (:require symbolicweb.common)

  (:require symbolicweb.container-model)
  (:require symbolicweb.container-model-node)

  (:require symbolicweb.viewport)
  (:require symbolicweb.jquery)
  (:require symbolicweb.container)

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
  (:require symbolicweb.date-and-time))
(in-ns 'symbolicweb.core)



(let [bnds (get-thread-bindings)] ;; TODO: Think about this some more..
  (defn handler [request]
    (with-bindings bnds
      (swap! -request-counter- inc')
      (binding [*request* request]
        (try
          (binding [*application* (find-or-create-application-instance)]
            (dosync
             (touch *application*))
            ;; TODO: Application level try/catch here: ((:exception-handler-fn @application) e).
            ;; TODO: Production / development modes needed here too. Logging, etc. etc...
            ((:request-handler @*application*)))
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
                [:img {:src "/gfx/common/sw/stack_trace_or_gtfo.jpg"}]]])}))))))


(defn main [& args]
  ;; TODO: STOP-SW-SERVER doesn't actually work. Hm.
  (defonce stop-sw-server (start-http-server (wrap-ring-handler (wrap-cookies (wrap-params #'handler)))
                                             {:port 8080})))
