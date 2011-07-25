(in-ns 'symbolicweb.core)

(defmacro defapp [name fit-fn application-constructor-fn]
  `(swap! -application-types-
          #(assoc % '~name {:fit-fn ~fit-fn
                            :application-constructor-fn ~application-constructor-fn})))


(let [id-generator-value (atom 0)]
  (defn generate-uid []
    "Generates an unique ID; non-universal or pr. server instance based.
Returns a string."
    (str (swap! id-generator-value inc'))))


(defn generate-uuid []
  "Generates an universal unique ID (UUID).
Returns a string."
  ;; TODO: This is only pseudo random; we can do better.
  (str (java.util.UUID/randomUUID)))


(defn generate-aid
  "Generate Application/session scoped unique ID."
  ([] (generate-aid @*application*))
  ([application]
     ((:id-generator application))))


(defn time-since-last-activity [obj]
  (- (System/currentTimeMillis)
     (:last-activity-time @obj)))


(defn touch [obj]
  (send obj (fn [m] (assoc m :last-activity-time (System/currentTimeMillis)))))


 (defn script-src [src]
  [:script {:type "text/javascript" :src src}])


(defn link-css [href]
  [:link {:rel "stylesheet" :type "text/css"
          :href href}])


(defn set-document-cookie [& {:keys [application viewport domain? name value]
                              :or {application *application*
                                   viewport *viewport*
                                   domain? true
                                   name "name" ;;(cookie-name-of (server-of application))
                                   value "value"}}] ;;(cookie-value-of app)
  (str "document.cookie = \""
       name "=" (if value value "") "; "
       (when domain?
         "domain=.\" + window.location.hostname + \"; ")
       (if value
         (str "expires=\""
              " + (function(){ var date = new Date(); date.setFullYear(date.getFullYear()+1); return date.toUTCString(); })()"
              " + \"; ")
         ;; MDC states that this could simply be 0, but I have not tested this.
         "expires=Fri, 27 Jul 2001 02:47:11 UTC; ")
       "path=\" + window.location.pathname + \"; "
       "\";"))


(declare add-response-chunk)
(defn clear-session []
  (add-response-chunk (str (set-document-cookie :name "_sw_application_id" :value nil)
                           " window.location.reload();")))


(defn is-url?
  ([url-path]
     (assert (thread-bound? #'*request*))
     (is-url? url-path (:uri *request*)))
  ([url-path uri]
     (= (subs uri 0 (min (count url-path) (count uri)))
        url-path)))


(defn alert
  ([msg] (alert msg *viewport*))
  ([msg viewport] (add-response-chunk (str "alert('" msg "');") viewport)))


(defn sw-js-bootstrap []
  (html
   [:script {:type "text/javascript"}
    (set-document-cookie :name "_sw_application_id" :value (:id @*application*))
    "_sw_viewport_id = '" (:id @*viewport*) "'; "
    "_sw_dynamic_subdomain = '" (if-let [it (str "sw-" (generate-uid))]
                                  (str it ".")
                                  "") "'; "]
   [:script {:type "text/javascript" :defer "defer" :src "../js/common/sw/sw-ajax.js"}]))
