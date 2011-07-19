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
  (:require symbolicweb.application)
  (:require symbolicweb.widget-base)

  (:gen-class))

(in-ns 'symbolicweb.core)


(defn sw-js-bootstrap []
  (html
   [:script {:type "text/javascript"}
    (set-document-cookie :name "sw" :value (:id @*application*))
    "sw_viewport_id = '" (:id @*viewport*) "'; "
    "sw_dynamic_subdomain = '" (if-let [it (str "sw-" (generate-uid))]
                                 (str it ".")
                                 "") "'; "]
   [:script {:type "text/javascript" :defer "defer" :src "../kitch/js/sw/sw-ajax.js"}]))


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


(defn handle-normal-request-type []
  "\"Normal\" as opposed to HANDLE-SW-REQUEST-TYPE or non-AJAX/Comet."
  )


(let [*out* *out*
      *err* *err*]
  (defn handler [req]
    (binding [*out* *out*
              *err* *err*
              *request* req]
      (let [[application viewport] (find-or-create-application-instance)]
        (touch application)
        (touch viewport)
        ;; Set the global or outer *application* and *viewport* for easy REPL-inspection.
        (swap! *application* (fn [_] application))
        (swap! *viewport* (fn [_] viewport))
        (binding [*application* application
                  *viewport* viewport]
          (if-let [sw-request-type (get (:query-params req) "_sw_request-type")]
            (handle-sw-request-type sw-request-type) ;; It's an AJAX or Comet request.
            {:status  200
             :headers {"Content-Type" "text/html; charset=UTF-8"
                       "Connection" "keep-alive"
                       "Expires" "Mon, 26 Jul 1997 05:00:00 GMT"
                       "Cache-Control" "no-store, no-cache, must-revalidate, post-check=0, pre-check=0"
                       "Pragma" "no-cache"}
             :body
             (html
              (doctype :xhtml-strict)
              (xhtml-tag
               "en"
               [:head
                [:title "SymbolicWeb"]
                [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
                (script-src "https://ajax.googleapis.com/ajax/libs/jquery/1.6.1/jquery.min.js")
                (sw-js-bootstrap)]

               [:body
                [:h1 "SymbolicWeb - bringing life to the web!"]
                [:p "Hello, this is SymbolicWeb running on Clojure " (clojure-version)]
                [:p "Norwegian characters: æøå."]
                [:ul (for [i (range 10)]
                       [:li [:b "This is nr. " i "."]])]

                [:p "Here is the Clojure source code for this page:"
                 [:pre (slurp "src/symbolicweb/core.clj")]]

                [:p (link-to "http://validator.w3.org/check?uri=referer"
                             [:img {:src "http://www.w3.org/Icons/valid-xhtml10"
                                    :alt "Valid XHTML 1.0 Strict"
                                    :height 31
                                    :width  88}])]]))}))))))


(defn -main [& args]
  (def symbolicweb-server
    (run-jetty (wrap-cookies (wrap-params #'handler))
               {:port 8080 :join? false})))
