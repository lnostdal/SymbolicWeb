(in-ns 'symbolicweb.core)

(declare add-response-chunk)

(set! *print-length* 10)
(set! *print-level* 10)


(defn expected-response-type []
  (let [accept-header (get (:headers *request*) "accept")]
    (cond
     (re-find #"text/javascript" accept-header) :javascript
     (re-find #"text/html" accept-header) :html
     (re-find #"text/plain" accept-header) :plain
     (re-find #"text/plugin-api" accept-header) :plugin-api
     true accept-header)))


(defn viewport-of [widget]
  (:viewport @widget))


(defn application-of [widget]
  (:application (viewport-of widget)))


(defn alter-options [options fn & args]
  "> (alter-options (list :js-options {:close (with-js (alert \"closed!\"))}
                          :on-close (with-js (alert \"yep, really closed!\")))
                    update-in [:js-options] assoc :modal :true)
   (:js-options {:modal :true, :close \"alert(decodeURIComponent('closed%21'));\"}
    :on-close \"alert(decodeURIComponent('yep%2C%20really%20closed%21'));\")"
  (with-local-vars [lst (list)]
    (doseq [option (apply fn (apply hash-map options) args)]
      (var-set lst (conj (var-get lst) (val option) (key option))))
    (var-get lst)))


(defmacro typecase [e & clauses]
  ;; (str 'clojure.lang.PersistentVector)
  ;; (case (.getName (type []))
  ;;   "clojure.lang.PersistentVector" (println "test"))
  ;;
  ;; <clgv> lnostdal_: another way is to use cond and instance? see ##(doc instance?)
  ;; <lazybot> ⇒ "([c x]); Evaluates x and tests if it is an instance of the class c. Returns true or false"
  )


(defn sha [input-str]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")]
    (. md update (.getBytes input-str))
    (let [digest (.digest md)]
      (apply str (map #(format "%02x" (bit-and % 0xff)) digest)))))


(defn ensure-vector [x]
  (if (vector? x)
    x
    (vector x)))


(defmacro with-all-viewports [& body]
  "Handy when testing things in the REPL."
  `(doseq [~'application (vals @-applications-)]
     (doseq [~'viewport (vals (:viewports @~'application))]
       (binding [*application* ~'application
                 *viewport* ~'viewport]
         (dosync
          ~@body)))))


(defmacro with-app-viewports [app & body]
  "Run BODY in context of all Viewports of APP (session)."
  `(binding [*application* (or ~app *application*)]
     (doseq [~'viewport (vals (:viewports @*application*))]
       (binding [*viewport* ~'viewport]
         (dosync
          ~@body)))))


(defn get-widget [id]
  (get (:widgets @*viewport*) (str id)))


(defn children-of [widget]
  (:children @widget))


(defn widget? [obj]
  (when-not (string? obj)
    (isa? (:type @obj) ::WidgetBase)))


(defn ensure-model [obj]
  (assert (ref? obj))
  obj)


(defn url-encode-wrap [text]
  (str "decodeURIComponent('" (str/replace (url-encode text) "+" "%20") "')"))


(defn agent? [x]
  (= clojure.lang.Agent (type x)))


(defn default-parse-callback-data-handler [widget callback-data]
  (mapcat (fn [key]
            (list key (get (:params *request*) (str key))))
          (keys callback-data)))


(defn map-to-js-options [opts]
  (with-out-str
    (doseq [opt opts]
      (print (str (name (key opt)) ": "
                  (with (val opt)
                    (if (keyword? it)
                      (name it)
                      it))
                  ", ")))))


(defn defapp [[name fit-fn cookie-path & args] application-constructor-fn]
  (let [app-type-data (atom nil)]
    (reset! app-type-data (apply assoc {}
                                 :name name
                                 :fit-fn fit-fn
                                 :cookie-name "_sw_application_id"
                                 :cookie-path cookie-path
                                 :application-constructor-fn (fn [] (application-constructor-fn app-type-data))
                                 :agent (agent name)
                                 args))
    (swap! -application-types- #(assoc % name @app-type-data))))


;; TODO: Support for timeout; with default value, and adjustable.
(declare show-Dialog mk-pre)
(defmacro with-ctx [[& send-off?] & body]
  "This will run BODY in the context of an Agent for the current session, i.e. whatever *APPLICATION* is bound to."
  `(~(if send-off? 'send-off 'send)
    (:agent @*application*)
    (fn [_#]
      (binding [*in-channel-request?* false]
        (try
          (do ~@body)
          (catch Throwable e# ;; TODO: Throwable is "wrong", but it also actually works.
            (clojure.stacktrace/print-stack-trace e# 10)
            (dosync
             (binding [*in-channel-request?* false]
               (show-Dialog (mk-pre (vm (with-out-str (clojure.stacktrace/print-stack-trace e# 10))))
                            :js-options {:modal :true :width 1500 :height 800
                                         :buttons "{ 'Ok': function() { $(this).dialog('close'); }}"})))))))))


(let [id-generator-value (atom 0)]
  (defn generate-uid []
    "Generates an unique ID; non-universal or pr. server instance based."
    (swap! id-generator-value inc)))


(defn generate-uuid []
  "Generates an universal unique ID (UUID).
Returns a string."
  ;; TODO: This is only pseudo random; we can do better.
  (str (java.util.UUID/randomUUID)))


;; TODO: I messed up once; shared IDs where they shouldn't be.
#_(defn generate-aid
  "Generate Application/session scoped unique ID."
  ([]
     (if *application*
       (generate-aid @*application*)
       (generate-uid)))
  ([application]
     ((:id-generator application))))


(defn time-since-last-activity [obj]
  (- (System/currentTimeMillis)
     (:last-activity-time @obj)))


(defn touch [obj]
  (alter obj assoc :last-activity-time (System/currentTimeMillis)))


 (defn script-src [src]
  [:script {:type "text/javascript" :src src}])


(defn link-css [href]
  [:link {:rel "stylesheet" :type "text/css"
          :href href}])


(defn set-document-cookie [& {:keys [application viewport path domain? name value]
                              :or {application *application*
                                   viewport *viewport*
                                   domain? true
                                   path "\" + window.location.pathname + \""
                                   name "name"
                                   value "value"}}]
  (str "document.cookie = \""
       name "=" (if value value "") "; "
       (when domain?
         (if (= domain? true)
           "domain=\" + window.location.hostname + \"; "
           (str "domain=" domain? "; ")))
       (if value
         (str "expires=\""
              " + (function(){ var date = new Date(); date.setFullYear(date.getFullYear()+1); return date.toUTCString(); })()"
              " + \"; ")
         "expires=Fri, 27 Jul 2001 02:47:11 UTC; ")
       "path=" path "; "
       "\";" \newline))


(defn set-default-session-cookie [value]
  "If VALUE is NIL the cookie will be cleared."
  (set-document-cookie :name "_sw_application_id" :value value :path "/" :domain? false))


(defn set-default-login-cookie [value]
  (str (set-document-cookie :name "_sw_login_id" :value value :path "/" :domain? false)
       (when-not value
         (set-document-cookie :name "PHPSESSID" :value value :path "/" :domain? false))))


(defn remove-session [application]
  (let [application @application]
    (swap! -applications- #(dissoc % (:id application)))))


(defn reload-page
  ([rel-url]
     (add-response-chunk (str "window.location = " (url-encode-wrap rel-url) ";")))
  ([]
     (add-response-chunk "window.location.reload();")))


(defn replace-page [rel-url]
  (add-response-chunk (str "window.location.replace(" (url-encode-wrap rel-url) ");")))


(defn clear-session
  ([] (clear-session *application*))
  ([application]
     ;; TODO: Do this on the server end instead or also?
     (add-response-chunk (set-default-session-cookie nil))
     (with-app-viewports application
       (add-response-chunk "window.location.reload();"))))


(defn clear-all-sessions []
  ;; TODO: Do this on the server end instead or also.
  (doseq [id_application @-applications-]
    (binding [*application*(val id_application)]
      (doseq [id_viewport (:viewports @*application*)]
        (binding [*viewport* (val id_viewport)]
          (clear-session *application*))))))


(defn is-url?
  ([url-path]
     (assert (thread-bound? #'*request*))
     (is-url? url-path (:uri *request*)))
  ([url-path uri]
     (= (subs uri 0 (min (count url-path) (count uri)))
        url-path)))


(defn root-element []
  (:root-element @*viewport*))

(defn root-model []
  (:model @(root-element)))


(defn alert
  ([msg] (alert msg (root-element)))
  ([msg widget] (add-response-chunk (str "alert(" (url-encode-wrap msg) ");")
                                    widget)))


(defn widget-id-of [widget]
  (if (string? widget)
    widget
    (:id (if (ref? widget)
           @widget
           widget))))


(defn sw-js-base-bootstrap []
  (str (set-default-session-cookie (:id @*application*))
       "_sw_viewport_id = '" (:id @*viewport*) "'; " \newline
       "_sw_dynamic_subdomain = '" (if-let [it (str "sw-" (generate-uid))]
                                     (str it ".")
                                     "") "'; " \newline))


(defn sw-js-bootstrap
  ([] (sw-js-bootstrap "/"))
  ([path]
     (html
      [:script {:type "text/javascript"} (sw-js-base-bootstrap path)]
      [:script {:type "text/javascript" :defer "defer" :src "/js/common/sw/sw-ajax.js"}])))


(defn sw-css-bootstrap []
  "")


(def ^:dynamic *with-js?* false)


(defmacro with-js [& body]
  `(binding [*with-js?* true]
     ~@body))
