(in-ns 'symbolicweb.core)

(declare add-response-chunk ref?)


(set! *print-length* 30)
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
  (when-let [viewport (viewport-of widget)]
    (:application @viewport)))


#_(defn assoc-param ;; Stolen from Ring.
  "Associate a key with a value. If the key already exists in the map, create a vector of values."
  [map key val]
  (assoc map key
    (if-let [cur (map key)]
      (if (vector? cur)
        (conj cur val)
        [cur val])
      val)))


(defn parse-params ;; Stolen from Ring.
  "Parse parameters from a string into a map."
  [^String param-string encoding]
  (reduce
   (fn [param-map encoded-param]
     (if-let [[_ key val] (re-matches #"([^=]+)=(.*)" encoded-param)]
       (assoc-param param-map
                    (url-decode key encoding)
                    (url-decode (or val "") encoding))
        param-map))
    {}
    (str/split param-string #"&")))


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
  "Run BODY in context of all Viewports of APP (Application)."
  `(binding [*application* (or ~app *application*)]
     (doseq [~'viewport (vals (:viewports @*application*))]
       (binding [*viewport* ~'viewport]
         (dosync
          ~@body)))))


(defmacro with-user-viewports [user-model & body]
  "Run BODY in context of all Viewports in all Applications of USER-MODEL (UserModelBase)."
  `(doseq [application# @(:applications @~user-model)]
     (with-app-viewports application#
       ~@body)))


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


(let [id-generator-value (atom 0)]
  (defn generate-uid []
    "Generates an unique ID; non-universal or pr. server instance based."
    (swap! id-generator-value inc)))


(defn generate-uuid []
  "Generates an universal unique ID (UUID).
Returns a string."
  ;; TODO: This is only pseudo random; we can do better.
  (str (java.util.UUID/randomUUID)))


(defn time-since-last-activity [obj]
  (- (System/currentTimeMillis)
     (:last-activity-time @obj)))


(defn touch [obj]
  (reset! (:last-activity-time @obj)
          (System/currentTimeMillis)))


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
     ;; TODO: I guess we need three ways of reloading now.
     ;;"window.location.reload(false);"
     (add-response-chunk
      ;; http://blog.nostdal.org/2011/12/reloading-or-refreshing-web-page-really.html
      "window.location.href = window.location.href;")))


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
  (assert (thread-bound? #'*viewport*))
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
       "_sw_viewport_id = '" (:id @*viewport*) "';" \newline
       "_sw_comet_timeout_ts = " -comet-timeout- ";" \newline
       (cl-format false "_sw_dynamic_subdomain = '~A';~%"
                  (if-let [it (str "sw-" (generate-uid))]
                    (str it ".")
                    ""))))


(defn sw-css-bootstrap []
  "")


(def ^:dynamic *with-js?* false)


(defmacro with-js [& body]
  `(binding [*with-js?* true]
     ~@body))



(defn remove-limited [vec item limit]
  (with-local-vars [lim limit]
    (doall ;; Clojure being lazy by-default for these kinds of things is retarded...
     (mapcat (fn [elt]
               (if (= elt item)
                 (if (zero? (var-get lim))
                   [elt]
                   (do
                     (var-set lim (- (var-get lim) 1))
                     []))
                 [elt]))
             vec))))



;;; WITH-SW-IO
;;;;;;;;;;;;;;

(def -sw-io-agent-error-handler-
  (fn [the-agent exception]
    (try
      (println "-SW-IO-AGENT-ERROR-HANDLER-, thrown:")
      (clojure.stacktrace/print-stack-trace exception 50)
      (catch Throwable inner-exception
        (println "-SW-IO-AGENT-ERROR-HANDLER-: Dodge überfail... :(")
        (Thread/sleep 1000))))) ;; Make sure we aren't flooded in case some loop gets stuck.

(defonce -sw-io-agent- (agent 42 :error-handler #'-sw-io-agent-error-handler-))

(defn %with-sw-io [options body-fn]
  (send-off (if (not-empty options)
              options
              -sw-io-agent-)
            (fn [old-res]
              (binding [clojure.java.jdbc.internal/*db* nil
                        *in-sw-db?* false
                        *pending-prepared-transaction?* false]
                (body-fn old-res)))))


(defmacro with-sw-io [options & body]
  "Runs BODY in an Agent."
  `(%with-sw-io ~options (fn [_#] ~@body)))



;;; SWSYNC
;;;;;;;;;;

(declare with-sw-db) ;; It's actually a funtion now, so..

(def ^:dynamic *swsync-operations*)
(def ^:dynamic *swsync-db-operations*)

(defn %swsync [bodyfn]
  (io! "SWSYNC: Nesting of SWSYNC forms not allowed. Nor can SWSYNC be placed inside a DOSYNC form.")
  (when *in-sw-db?*
    (assert *pending-prepared-transaction?*
            "SWSYNC: SWSYNC is meant be used within WITH-SW-DB callback context; HOLDING-TRANSACTION or outside of WITH-SW-DB context entirely."))
  (dosync
   (binding [*swsync-operations* (atom [])
             *swsync-db-operations* (atom [])]
     (let [return-value (bodyfn)]
       (when-not (and (empty? @*swsync-operations*)
                      (empty? @*swsync-db-operations*))
         (with-sw-io [] ;; After this point we can be sure DOSYNC won't roll back; we're in an Agent.
           (when-not (empty? @*swsync-db-operations*)
             (binding [*in-sw-db?* false ;; TODO: Kind of silly; needed to make WITH-SW-CONNECTION work by force.
                       *pending-prepared-transaction?* false]
               (with-sw-connection ;; All pending DB operations execute within a _single_ DB transaction.
                 (binding [*pending-prepared-transaction?* true]
                   (doseq [f @*swsync-db-operations*]
                     (f))))))
           (when-not (empty? @*swsync-operations*)
             (doseq [f @*swsync-operations*]
               (f)))))
       return-value))))

(defmacro swsync [& body]
  "A DOSYNC where database operations (SWDBOP) are gathered up and executed within a single DB transaction in an Agent
after Clojure side transaction (DOSYNC) is done."
  `(%swsync (fn [] ~@body)))

(defmacro swop [& body]
  "Wrapper for general IO operation; runs after (SEND-OFF) Clojure transaction (SWSYNC)."
  `(swap! *swsync-operations* conj (fn [] ~@body)))

(defmacro swdbop [& body]
  "Wrapper for DB type IO operation; runs after (SEND-OFF) Clojure transaction (SWSYNC)."
  `(swap! *swsync-db-operations* conj (fn [] ~@body)))
