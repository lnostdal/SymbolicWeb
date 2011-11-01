(ns symbolicweb.core
  (:import java.lang.ref.WeakReference)
  (:import java.util.Calendar)
  (:require clojure.stacktrace)
  (:use hiccup.core)
  (:use hiccup.page-helpers)
  (:use ring.adapter.jetty)
  (:use ring.util.codec)
  (:use ring.middleware.params)
  (:use ring.middleware.cookies)
  (:use net.cgrand.enlive-html)
  (:use [clojure.java.jdbc :exclude (resultset-seq)])
  (:require [clojure.string :as str])
  (:require symbolicweb.model)
  (:require symbolicweb.globals)
  (:require symbolicweb.common)
  (:require symbolicweb.viewport)
  (:require symbolicweb.jquery)
  (:require symbolicweb.container)
  (:require symbolicweb.widget-base)
  (:require symbolicweb.text-input)
  (:require symbolicweb.html-elements)
  (:require symbolicweb.container-model)
  (:require symbolicweb.container-model-node)
  (:require symbolicweb.container-view)
  (:require symbolicweb.html-container)
  (:require symbolicweb.checkbox)
  (:require symbolicweb.img)
  (:require symbolicweb.dialog)
  (:require symbolicweb.tooltip)
  (:require symbolicweb.sticky)
  (:require symbolicweb.handy-handlers)
  (:require symbolicweb.application)
  (:require symbolicweb.date-and-time))

(in-ns 'symbolicweb.core)


(let [*out* *out*
      *err* *err*]
  (defn handler [request]
    (swap! -request-counter- inc')
    (binding [*out* *out*
              *err* *err*
              *request* request]
      (try
        (binding [*application* (find-or-create-application-instance)]
          (dosync
           (touch *application*))
          ;; TODO: Application level try/catch here: ((:exception-handler-fn @application) e).
          ;; Production / development modes needed here too. Logging, etc. etc..
          ((:request-handler @*application*)))
        (catch Throwable e
          ;; TODO: Production / development modes? Send to both browser and development environment (Slime)? Etc.
          {:status 500
           :headers {"Content-Type" "text/html; charset=UTF-8"
                     "Connection"   "keep-alive"}
           :body
           (with-out-str
             (println "<html><body><font face='sans-serif'>")
             (println "<h3><a href='https://github.com/lnostdal/SymbolicWeb'>SymbolicWeb</a>: Top Level Server Exception (HTTP 500)</h3>")
             (println "<pre>")
             (clojure.stacktrace/print-stack-trace e 100)
             (println "</pre>")
             (println "</font></body></html>"))})))))


(defn main [& args]
  (defonce symbolicweb-server
    (run-jetty (wrap-cookies (wrap-params #'handler))
               {:port 8080 :join? false})))
