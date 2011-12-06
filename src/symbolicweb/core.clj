(ns symbolicweb.core
  (:require clojure.stacktrace)
  (:require [clojure.string :as str])
  (:use [clojure.pprint :only (cl-format)])

  (:import java.util.Calendar)
  (:import (java.util.concurrent TimeoutException TimeUnit FutureTask))

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
  (:require symbolicweb.postgresql-test)
  (:require symbolicweb.model)
  (:require symbolicweb.globals)
  (:require symbolicweb.common)

  (:require symbolicweb.container-model)
  (:require symbolicweb.container-model-node)

  (:require symbolicweb.viewport)
  (:require symbolicweb.jquery)
  (:require symbolicweb.container)
  (:require symbolicweb.widget-base)
  (:require symbolicweb.text-input)
  (:require symbolicweb.html-elements)
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


(def my-pool (mk-pool 5))


(let [*out* *out*
      *err* *err*]
  (defn handler [request]
    (swap! -request-counter- inc')
    (binding [*out* *out*
              *err* *err*
              *request* request]
      (case (get (:query-params *request*) "do")
        "concurrency-test" ((wrap-aleph-handler (fn [channel request]
                                                  (at (+ (now) 100)
                                                      #(binding [*out* *out*
                                                                 *err* *err*
                                                                 *request* request]
                                                         (enqueue channel
                                                                  {:status 200
                                                                   :headers {"Content-Type" "text/html; charset=UTF-8"}
                                                                   :body "concurrency-test: pong!"}))
                                                      my-pool)))
                            *request*)
        "sync-handler-test" (do
                              (Thread/sleep 5000)
                              {:status 200
                               :headers {"Content-Type" "text/html; charset=UTF-8"}
                               :body "sync-handler-test: pong!"})
        (try
          (binding [*application* (find-or-create-application-instance)]
            (dosync
             (touch *application*))
            ;; TODO: Application level try/catch here: ((:exception-handler-fn @application) e).
            ;; Production / development modes needed here too. Logging, etc. etc..
            ((:request-handler @*application*)))
          (catch Throwable e
            (clojure.stacktrace/print-stack-trace e 10)
            ;; TODO: Production / development modes? Send to both browser and development environment (Slime)? Etc.
            (if (= :javascript (dbg-prin1 (expected-response-type)))
              {:status 200
               :headers {"Content-Type" "text/javascript; charset=UTF-8"
                         "Connection" "keep-alive"}
               :body
               ;; TODO: Figure out why this crap doesn't work.
               (str
                ;;(with-js (show-Notification (url-encode-wrap (with-out-str "blah"))))
                ;;(with-js (show-Notification (url-encode-wrap (with-out-str "blah"))))
                ;;(str "alert(decodeURIComponent(\"" (url-encode (with-out-str (clojure.stacktrace/print-stack-trace e 100))) "\"));")
                ;;(str "alert(decodeURIComponent(\"" "blah" #_(url-encode (with-out-str (clojure.stacktrace/print-stack-trace e 100))) "\"));")
                ;;"alert(" (url-encode-wrap (with-out-str (clojure.stacktrace/print-stack-trace e 100))) ");"
                ;;"alert('cunt');"
                ;;"alert(" (url-encode-wrap (with-out-str (clojure.stacktrace/print-stack-trace e 10))) ");"
                "$.sticky(" (url-encode-wrap (with-out-str (clojure.stacktrace/print-stack-trace e 10))) ");"
                )}
              {:status 500
               :headers {"Content-Type" "text/html; charset=UTF-8"
                         "Connection" "keep-alive"}
               :body
               (with-out-str
                 (println "<html><body><font face='sans-serif'>")
                 (println "<h3><a href='https://github.com/lnostdal/SymbolicWeb'>SymbolicWeb</a>: Top Level Server Exception (HTTP 500)</h3>")
                 (println "<pre>")
                 (clojure.stacktrace/print-stack-trace e 100)
                 (println "</pre>")
                 (println "</font></body></html>"))})))))))


(defn main [& args]
  ;; TODO: STOP-SW-SERVER doesn't actually work. Hm.
  (defonce stop-sw-server (start-http-server (wrap-ring-handler (wrap-cookies (wrap-params #'handler)))
                                             {:port 8080})))
