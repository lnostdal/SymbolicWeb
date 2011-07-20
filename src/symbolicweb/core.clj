(ns symbolicweb.core
  (:use hiccup.core)
  (:use hiccup.page-helpers)
  (:use ring.adapter.jetty)
  (:use ring.util.codec)
  (:use ring.middleware.params)
  (:use ring.middleware.cookies)
  (:use net.cgrand.enlive-html)
  (:use [clojure.java.jdbc :exclude (resultset-seq)])
  (:require [clojure.string :as str])
  (:require symbolicweb.common)
  (:require symbolicweb.viewport)
  (:require symbolicweb.handy-handlers)
  (:require symbolicweb.application)
  (:require symbolicweb.widget-base)
  (:gen-class))

(in-ns 'symbolicweb.core)


(defn handle-comet-request []
  {:status 200
   :headers {"Content-Type" "text/javascript; charset=UTF-8"
             "Connection" "keep-alive"}
   :body
   ;; TODO: In general all this is silly, but it'll go away as soon as I switch to a sane backend (Netty?) and decouple the HTTP
   ;; request and response; i.e. I go event-based.
   (let [our-response-chunks (promise)]
     (deref (:response-chunks-promise @*viewport*) -comet-timeout- nil)
     ;; Fetch current chunks and reset to empty set of chunks.
     (send *viewport* (fn [m]
                        (deliver our-response-chunks (:response-chunks m))
                        (assoc m
                          :response-chunks []
                          :response-chunks-promise (promise))))
     (apply str (conj @our-response-chunks
                      "sw_comet_response = true; console.log('SW WAS HERE!! :D');")))})


(defn handle-ajax-request []
  (println "TODO: (HANDLE-AJAX-REQUEST ..)")
  ;; TODO: I've just mirrored what I did in old-SW, but it'd be nice to return JS in the body here.
  {:status 200
   :headers {"Content-Type" "text/plain; charset=UTF-8"
             "Connection" "keep-alive"}
   :body ""})


(defn handle-sw-request-type [request-type-str]
  "The request has a '_sw_request-type' entry in its query-params."
  (case request-type-str
    "comet" (handle-comet-request)
    "ajax"  (handle-ajax-request)
    (throw (Exception. (str "SymbolicWeb: Unknown _sw_request-type \"" request-type-str "\" given.")))))


(let [*out* *out*
      *err* *err*]
  (defn handler [req]
    (binding [*out* *out*
              *err* *err*
              *request* req]
      (let [[application viewport] (find-or-create-application-instance)]
        (if (not (and application viewport))
          (reload-page-handler)
          (do
            ;; Set root bindings for easy REPL-inspection.
            (def ^:dynamic *application* application)
            (def ^:dynamic *viewport* viewport)
            (touch application)
            (touch viewport)
            (binding [*application* application ;; Set thread-local bindings which will be used from now on.
                      *viewport* viewport]
              (if-let [sw-request-type (get (:query-params req) "_sw_request-type")]
                (handle-sw-request-type sw-request-type) ;; It's an AJAX or Comet request.
                ((:handler-fn @*application*))))))))))


(defn -main [& args]
  (def symbolicweb-server
    (run-jetty (wrap-cookies (wrap-params #'handler))
               {:port 8080 :join? false})))
