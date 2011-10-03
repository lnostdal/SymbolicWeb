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
  ;;(:use [clojure.java.jdbc :exclude (resultset-seq)])
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
  (:require symbolicweb.handy-handlers)
  (:require symbolicweb.application)
  (:require symbolicweb.date-and-time)
  (:gen-class))

(in-ns 'symbolicweb.core)


(let [*out* *out*
      *err* *err*]
  (defn handler [request]
    (swap! -request-counter- inc')
    (binding [*out* *out*
              *err* *err*
              *request* request]
      (let [[application viewport] (find-or-create-application-instance)]
        (def ^:dynamic *application* application) ;; Set root bindings for easy REPL-inspection.
        (def ^:dynamic *viewport* viewport)
        (binding [*application* application ;; Set thread-local bindings which will be used from now on.
                  *viewport* viewport]
          (dosync
           (touch application)
           (touch viewport))
          ((:request-handler @application)))))))


(defn -main [& args]
  (defonce symbolicweb-server
    (run-jetty (wrap-cookies (wrap-params #'handler))
               {:port 8080 :join? false})))
