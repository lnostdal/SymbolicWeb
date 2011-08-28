(in-ns 'symbolicweb.core)

(declare make-Application)

;; TODO: In general all this is silly, but it'll go away as soon as I switch to a sane backend (Netty?) and decouple the HTTP
;; request and response; i.e. I go event-based.
(defn handle-out-channel-request []
  "Output channel."
  {:status 200
   :headers {"Content-Type" "text/javascript; charset=UTF-8"
             "Connection"   "keep-alive"}
   :body
   (with-local-vars [our-response-str ""]
     (deref (:response-str-promise @*viewport*) -comet-timeout- nil)
     (dosync
      (alter *viewport* (fn [m]
                          (var-set our-response-str (:response-str m))
                          (assoc m
                            :response-str ""
                            :response-str-promise (promise)))))
     (str (var-get our-response-str) "_sw_comet_response = true;"))})


(defn handle-in-channel-request []
  "Input channel."
  (let [query-params (:query-params *request*)
        widget-id (get query-params "_sw_widget-id")
        callback-id (get query-params "_sw_callback-id")
        widget (get (:widgets @*viewport*) widget-id)
        callback (get (:callbacks @widget) callback-id)
        [callback-fn callback-data] callback]
    (binding [*in-channel-request?* ""]
      (dosync
       (apply callback-fn ((:parse-callback-data-handler @widget) widget callback-data)))
      {:status 200
       :headers {"Content-Type" "text/javascript; charset=UTF-8"
                 "Connection"   "keep-alive"}
       :body (str *in-channel-request?* "_sw_comet_response = true;")})))


(defn default-aux-handler []
  "Attempt to handle auxiliary callbacks or events; AJAX requests that do not follow the SW protocol. These might come from 3rd
party code or plugins or similar running on the browser end.
Returns TRUE if the event was handled or FALSE if no callback was found for the event."
  (assert false "TODO: DEFAULT-AUX-HANDLER")
  (with-local-vars [handled? false]
    (loop [aux-callbacks (:aux-callbacks @*viewport*)]
      (when-first [aux-callback aux-callbacks]
        (let [aux-callback (val aux-callback)]
          (if ((:fit-fn aux-callback))
            (do
              ((:handler-fn aux-callback))
              (var-set handled? true))
            (recur (next aux-callbacks))))))
    (var-get handled?)))


(defn default-ajax-handler []
  (if-let [sw-request-type-str (get (:query-params *request*) "_sw_request_type")]
    (case sw-request-type-str
      "comet" (handle-out-channel-request)
      "ajax"  (handle-in-channel-request)
      (throw (Exception. (str "SymbolicWeb: Unknown _sw_request_type \"" sw-request-type-str "\" given."))))
    (dosync
     ((:aux-handler @*application*)))))


(defn default-request-handler []
  (if (or (= (get (:headers *request*) "x-requested-with")
             "XMLHttpRequest")
          (get (:query-params *request*) "_sw_request_type")) ;; jQuery doesn't use XHR for cross-domain background requests.
    (if (= clear-session-page-handler (:rest-handler @*application*))
      ;; Send an "AJAX style" (text/javascript content-type) clear-session response.
      {:status 200
       :headers {"Content-Type" "text/javascript; charset=UTF-8"
                 "Connection"   "keep-alive"}
       :body (with-js (clear-session))}
      ((:ajax-handler @*application*)))
    (dosync
     (with1 ((:rest-handler @*application*))
       ((:reload-handler @*application*))))))


(defn default-rest-handler []
  {:status  200
   :headers {"Content-Type"  "text/html; charset=UTF-8"
             "Connection"    "keep-alive"
             "Expires"       "Mon, 26 Jul 1997 05:00:00 GMT"
             "Cache-Control" "no-store, no-cache, must-revalidate, post-check=0, pre-check=0"
             "Pragma"        "no-cache"}
   :body
   (html
    (doctype :xhtml-strict)
    (xhtml-tag
     "en"
     [:head
      [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
      ;; Do want; http://blog.chromium.org/2009/09/introducing-google-chrome-frame.html
      [:meta {:http-equiv "X-UA-Compatible" :content "chrome=1"}]
      [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
      [:title (:html-title @*application*)]
      [:style {:type "text/css"} "
* {
  -webkit-box-sizing: border-box; /* Safari/Chrome, other WebKit */
  -moz-box-sizing: border-box;    /* Firefox, other Gecko */
  box-sizing: border-box;         /* Opera/IE 8+ */
}
html, body, #sw-root {
  position: absolute;
  width: 100%; height: 100%;
  margin: 0; padding: 0; border: 0;
}
.sw-hide {
  display: none !important;
}"]
      (sw-css-bootstrap)
      (script-src "../js/common/jquery-1.6.2.min.js")
      (sw-js-bootstrap)]

     [:body
      (render-html (:root-element @*viewport*))]))})


(defn clear-session-page-handler []
  "Clears the session; removes client side cookies and reloads the page."
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Connection"   "keep-alive"}
   :body
   (html
    (doctype :xhtml-strict)
    (xhtml-tag
     "en"
     [:head
      [:title "[SymbolicWeb] Reloading page..."]
      [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
      [:script {:type "text/javascript"}
       ;; Clear session cookie and reload page.
       (set-document-cookie :name "_sw_application_id" :value nil)
       (set-document-cookie :name "_sw_application_id" :value nil :domain? false)]]

     [:body {:onload "window.location.reload();"}
      [:p "Reloading page..."]]))})


(defn not-found-page-handler []
  "This doesn't set a cookie on the client end."
  {:status 404
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Connection"   "keep-alive"}
   :body
   (html
    (doctype :xhtml-strict)
    (xhtml-tag
     "en"
     [:head
      [:title "[SymbolicWeb] HTTP 404: Not Found"]
      [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]]

     [:body
      [:h1 "HTTP 404: Not Found"]
      [:p "Going " [:a {:href "javascript:history.go(-1);"} "back"] " might help."]]))})


(defn hello-world-page-handler []
  {:status  200
   :headers {"Content-Type"  "text/html; charset=UTF-8"
             "Connection"    "keep-alive"
             "Expires"       "Mon, 26 Jul 1997 05:00:00 GMT"
             "Cache-Control" "no-store, no-cache, must-revalidate, post-check=0, pre-check=0"
             "Pragma"        "no-cache"}
   :body
   (render-html
    (with-html-container
      (doctype :xhtml-strict)
      (xhtml-tag
       "en"
       [:head
        [:title "[SymbolicWeb] Hello World"]
        [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
        (script-src "../js/common/jquery-1.6.2.min.js")
        (sw-js-bootstrap)]

       [:body
        [:h1 "SymbolicWeb - bringing life to the web!"]
        [:p "Hello, this is SymbolicWeb running on Clojure " (clojure-version)]
        [:p "Norwegian characters: æøå"]

        [:button {:onclick "$.getScript(swURL('&something=some-value'));"}
         "Test aux event handler"]

        (render-html (set-event-handler "click" (make-Button "Test widget event handler")
                                        (fn [& {:keys [page-x page-y]}]
                                          (alert (str "Widget handler called: "
                                                      " page-x => " page-x
                                                      ", page-y => " page-y)))
                                        :callback-data {:page-x "' + event.pageX + '"
                                                        :page-y "' + event.pageY + '"}))

      [:ul (for [i (range 10)]
             [:li [:b "This is nr. " i "."]])]

        [:p (link-to "http://validator.w3.org/check?uri=referer"
                     [:img {:src "http://www.w3.org/Icons/valid-xhtml10"
                            :alt "Valid XHTML 1.0 Strict"
                            :height 31
                            :width  88}])]

        [:div [:img {:id "sw-loading-spinner" :alt "" :style "position: absolute; z-index: 1000; right: 0px; top: 0px;"
                     :src "gfx/sw-loader.gif"}]]
        [:div {:id "sw-recycler" :class "sw-hide"}]])))})


(defn simple-aux-handler [fn-to-wrap]
  (fn []
    (binding [*in-channel-request?* ""]
      (fn-to-wrap)
      {:status 200
       :headers {"Content-Type" "text/javascript; charset=UTF-8"
                 "Connection"   "keep-alive"}
       :body (str *in-channel-request?* "_sw_comet_response = true;")})))


(defapp hello-world
  (fn [] (is-url? "/sw/hello-world"))
  (fn [] (make-Application [:aux-handler (simple-aux-handler #(alert "Aux handler called!"))])))


(defapp empty-page
  (fn [] (is-url? "/sw/empty-page"))
  (fn [] (make-Application [])))