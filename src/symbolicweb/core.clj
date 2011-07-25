(ns symbolicweb.core
  (:use hiccup.core)
  (:use hiccup.page-helpers)
  (:use ring.adapter.jetty)
  (:use ring.util.codec)
  (:use ring.middleware.params)
  (:use ring.middleware.cookies)
  (:use net.cgrand.enlive-html)
  ;;(:use [clojure.java.jdbc :exclude (resultset-seq)])
  (:require [clojure.string :as str])
  (:require symbolicweb.globals)
  (:require symbolicweb.common)
  (:require symbolicweb.viewport)
  (:require symbolicweb.handy-handlers)
  (:require symbolicweb.application)
  (:require symbolicweb.widget-base)
  (:gen-class))

(in-ns 'symbolicweb.core)


(let [*out* *out*
      *err* *err*]
  (defn handler [request]
    (binding [*out* *out*
              *err* *err*
              *request* request]
      (let [[application viewport] (find-or-create-application-instance)]
        (if (not (and application viewport))
          (clear-session-page-handler)
          (do
            (def ^:dynamic *application* application) ;; Set root bindings for easy REPL-inspection.
            (def ^:dynamic *viewport* viewport)
            (touch application)
            (touch viewport)
            (binding [*application* application ;; Set thread-local bindings which will be used from now on.
                      *viewport* viewport]
              ((:request-handler @application)))))))))


(defn -main [& args]
  (def symbolicweb-server
    (run-jetty (wrap-cookies (wrap-params #'handler))
               {:port 8080 :join? false})))
