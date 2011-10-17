(in-ns 'symbolicweb.core)

(declare make-Application)


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


;; TODO: In general all this is silly, but it'll go away as soon as I switch to a sane backend (Netty?) and decouple the HTTP
;; request and response; i.e. I go event-based.
(defn handle-out-channel-request []
  "Output channel."
  {:status 200
   :headers {"Content-Type" "text/javascript; charset=UTF-8"
             "Connection"   "keep-alive"}
   :body
   (with-local-vars [our-response-str ""]
     (deref (:response-str-promise @*viewport*) -comet-timeout- nil) ;; Hanging HTTP request.
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
    (dosync
     (binding [*in-channel-request?* ""]
       (apply callback-fn ((:parse-callback-data-handler @widget) widget callback-data))
       {:status 200
        :headers {"Content-Type" "text/javascript; charset=UTF-8"
                  "Connection"   "keep-alive"}
        :body (str *in-channel-request?* "_sw_comet_response = true;")}))))


(defn default-ajax-handler []
  (if-let [sw-request-type-str (get (:query-params *request*) "_sw_request_type")]
    (case sw-request-type-str
      "comet" (handle-out-channel-request)
      "ajax"  (handle-in-channel-request)
      (throw (Exception. (str "SymbolicWeb: Unknown _sw_request_type \"" sw-request-type-str "\" given."))))
    (dosync
     ((:aux-handler @*application*)))))


(declare clear-session-page-handler)
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
      ;; TODO: #sw-root doesn't exist anymore; check ID of :ROOT-ELEMENT!
      [:style {:type "text/css"} "
* {
  -webkit-box-sizing: border-box; /* Safari/Chrome, other WebKit */
  -moz-box-sizing: border-box;    /* Firefox, other Gecko */
  box-sizing: border-box;         /* Opera/IE 8+ */
}
html, body, #sw-root {
  font-family: sans-serif;
  position: absolute;
  width: 100%; height: 100%;
  margin: 0; padding: 0; border: 0;
}
.sw-hide {
  display: none !important;
}"]
      (sw-css-bootstrap)
      (script-src "/js/common/jquery-1.6.4.min.js")
      (with-out-str
        (doseq [head-element (:head-elements @*application*)]
          (println head-element)))
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


(defn simple-aux-handler [fn-to-wrap]
  (fn []
    (binding [*in-channel-request?* ""]
      (fn-to-wrap)
      {:status 200
       :headers {"Content-Type" "text/javascript; charset=UTF-8"
                 "Connection"   "keep-alive"}
       :body (str *in-channel-request?* "_sw_comet_response = true;")})))



(defapp empty-page
  (fn [] (is-url? "/empty-page/sw"))
  (fn [] (make-Application [])))
