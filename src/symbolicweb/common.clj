(in-ns 'symbolicweb.core)

(declare add-response-chunk ref?)






(set! *print-length* 30)
(set! *print-level* 10)


(defn url-encode-component ^String [^String s]
  ;; TODO: This is retarded and slow.
  (.replace (java.net.URLEncoder/encode s "UTF-8")
            "+"
            "%20"))


(defn url-decode-component ^String [^String s]
  (java.net.URLDecoder/decode s "UTF-8"))


(defn mime-encode-rfc-2047 ^String [^String s]
  (str "=?UTF-8?Q?"
       (-> (url-encode-component s)
           (str/replace "%20" "_")
           (str/replace "%" "="))
       "?="))


(defn expected-response-type [request]
  (let [accept-header (get (:headers request) "accept")]
    (cond
     (re-find #"text/javascript" accept-header) :javascript
     (re-find #"text/html" accept-header) :html
     (re-find #"text/plain" accept-header) :plain
     (re-find #"text/plugin-api" accept-header) :plugin-api
     true accept-header)))


(defn viewport-vm-of [widget]
  (:viewport @widget))


(defn viewport-of [widget]
  @(:viewport @widget))


(defn application-of [widget]
  (when-let [viewport (viewport-of widget)]
    (:application @viewport)))


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
  "Handy when testing things in the REPL.
APPLICATION and VIEWPORT are bound within BODY."
  `(doseq [~'application (vals @-applications-)]
     (doseq [~'viewport (vals (:viewports @~'application))]
       (dosync
        ~@body))))


(defmacro with-app-viewports [app & body]
  "Run BODY in context of all Viewports of APP (Application).
APPLICATION and VIEWPORT are bound within BODY."
  `(let [application# ~app]
     (doseq [~'viewport (vals (:viewports @application#))]
       (dosync
        ~@body))))


(defmacro with-user-viewports [user-model & body]
  "Run BODY in context of all Viewports in all Applications of USER-MODEL (UserModelBase).
APPLICATION and VIEWPORT are bound within BODY."
  `(let [user-model# ~user-model]
     (doseq [application# (dosync @(:applications @user-model#))]
       (with-app-viewports application#
         ~@body))))


(defn get-widget [id viewport]
  (get (:widgets @viewport) (str id)))


(defn children-of [widget]
  (:children @widget))


(defn widget? [obj]
  (when-not (string? obj)
    (isa? (:type @obj) ::WidgetBase)))


(defn ensure-model [obj]
  (assert (ref? obj))
  obj)


(defn url-encode-wrap ^String [^String s]
  (str "decodeURIComponent('" (url-encode-component s) "')"))


(defn agent? [x]
  (= clojure.lang.Agent (type x)))


(defn default-parse-callback-data-handler [request widget callback-data]
  (mapcat (fn [key]
            (list key (get (:params request) (str key))))
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
  (defn generate-uid ^Long []
    "Generates an unique ID; non-universal or only pr. server instance based."
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


(defn set-document-cookie [& {:keys [path domain? name value]
                              :or {domain? true
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


(defn http-js-response [body]
  {:status 200
   :headers {"Content-Type" "text/javascript; charset=UTF-8"
             "Server" -http-server-string-}
   :body body})


(defn http-html-response [body]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Server" -http-server-string-}
   :body body})


(defn http-text-response [body]
  {:status 200
   :headers {"Content-Type" "text/plain; charset=UTF-8"
             "Server" -http-server-string-}
   :body body})


(defn http-replace-response [location]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Server" -http-server-string-}
   :body (str "<script> window.location.replace(" (url-encode-wrap location) "); </script>")})


(defn http-redirect-response [location]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Server" -http-server-string-}
   :body (str "<script> window.location = " (url-encode-wrap location) "; </script>")})


(defn reload-page
  ([viewport rel-url]
     (add-response-chunk (str "window.location = " (url-encode-wrap rel-url) ";")
                         viewport))
  ([viewport]
     ;; TODO: I guess we need three ways of reloading now.
     ;;"window.location.reload(false);"
     ;; http://blog.nostdal.org/2011/12/reloading-or-refreshing-web-page-really.html
     (add-response-chunk "window.location.href = window.location.href;"
                         viewport)))




(defn replace-page [rel-url]
  (add-response-chunk (str "window.location.replace(" (url-encode-wrap rel-url) ");")))


(defn clear-session [application]
  ;; TODO: Do this on the server end instead or also?
  (with-app-viewports application
    (add-response-chunk (set-default-session-cookie nil)
                        viewport)
    (add-response-chunk "window.location.href = window.location.href;"
                        viewport)))



#_(defn clear-all-sessions []
  ;; TODO: Do this on the server end instead or also.
  (doseq [id_application @-applications-]
    (binding [*application* (val id_application)]
      (doseq [id_viewport (:viewports @*application*)]
        (binding [*viewport* (val id_viewport)]
          (clear-session *application*))))))


(defn is-url?
  ([url-path uri]
     (= (subs uri 0 (min (count url-path) (count uri)))
        url-path)))


(defn alert
  ([msg widget]
     (add-response-chunk (str "alert(" (url-encode-wrap msg) ");")
                         widget)))


(defn widget-id-of ^String [widget]
  (:id @widget))


(defn sw-js-base-bootstrap [application viewport]
  (str (set-default-session-cookie (:id @application))
       "_sw_viewport_id = '" (:id @viewport) "';" \newline
       "_sw_comet_timeout_ts = " -comet-timeout- ";" \newline
       (cl-format false "_sw_dynamic_subdomain = '~A';~%"
                  (if-let [it (str "sw-" (generate-uid))]
                    (str it ".")
                    ""))))


(defn sw-css-bootstrap []
  "")



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


#_(defn with-future* [timeout-ms body-fn]
  "Executes BODY-FN in a future with a timeout designated by TIMEOUT-MS for execution; i.e. not only for deref."
  (let [the-future (future (with-errors-logged (body-fn)))]
    (future
      (with-errors-logged
        (let [result (deref the-future timeout-ms ::with-future-timeout-event)]
          (if (not= result ::with-future-timeout-event)
            result
            (future-cancel the-future)))))
    nil))


#_(defmacro with-future [timeout-ms & body]
  "Executes BODY in a future with a timeout designated by TIMEOUT-MS for execution; i.e. not only for deref."
  `(with-future* ~timeout-ms (fn [] ~@body)))



;;; WITH-SW-IO
;;;;;;;;;;;;;;

(def -sw-io-agent-error-handler-
  (fn [the-agent exception]
    (try
      (println "-SW-IO-AGENT-ERROR-HANDLER-, thrown:")
      (clojure.stacktrace/print-stack-trace exception 1000)
      (catch Throwable inner-exception
        (println "-SW-IO-AGENT-ERROR-HANDLER-: Dodge überfail... :(")
        (Thread/sleep 1000))))) ;; Make sure we aren't flooded in case some loop gets stuck.

(defn mk-sw-agent [binding-blacklist]
  {:agent (agent ::initial-state :error-handler #'-sw-io-agent-error-handler-)
   :binding-blacklist (merge {#'clojure.java.jdbc/*db* @#'clojure.java.jdbc/*db*

                              #'*in-sw-db?* *in-sw-db?*
                              #'*pending-prepared-transaction?* *pending-prepared-transaction?*
                              #'*swsync-operations*
                              #'*swsync-db-operations*

                              #'*in-db-cache-get?* *in-db-cache-get?*

                              #'*in-html-container?* *in-html-container?*
                              #'*with-js?* *with-js?*

                              #'*observed-vms-ctx* false
                              #'*observed-vms-active-body-fns* *observed-vms-active-body-fns*}
                             binding-blacklist)})

(defonce -sw-io-agent- (mk-sw-agent nil)) ;; Generic fallback Agent. TODO: Perhaps a bad idea?

(defn with-sw-io* [the-agent body-fn]
  (let [the-agent (if the-agent the-agent -sw-io-agent-)]
    (send-off (:agent the-agent)
              (fn [_]
                (with-bindings (merge (get-thread-bindings) (:binding-blacklist the-agent))
                  (body-fn nil))))))


(defmacro with-sw-io [the-agent & body]
  "Runs BODY in an Agent."
  `(with-sw-io* ~the-agent (fn [_#] ~@body)))



;;; SWSYNC
;;;;;;;;;;

(declare with-sw-db) ;; It's actually a funtion now, so..

(def ^:dynamic *swsync-operations*)
(def ^:dynamic *swsync-db-operations*)

(defn swsync* [db-agent bodyfn]
  (io! "SWSYNC: Nesting of SWSYNC (or SWSYNC inside DOSYNC) contexts not allowed.")
  (when *in-sw-db?*
    (assert *pending-prepared-transaction?*
            "SWSYNC: SWSYNC is meant to be used within the WITH-SW-DB callback context HOLDING-TRANSACTION or outside of WITH-SW-DB context entirely."))
  (let [db-agent (if db-agent db-agent -sw-io-agent-)]
    (dosync
     (binding [*swsync-operations* (atom [])
               *swsync-db-operations* (atom [])]
       (let [return-value (bodyfn)]
         (when-not (and (empty? @*swsync-operations*)
                        (empty? @*swsync-db-operations*))
           (let [swsync-operations *swsync-operations*
                 swsync-db-operations *swsync-db-operations*]
             (with-sw-io db-agent ;; After this point we can be sure DOSYNC won't roll back; we're in an Agent.
               (when-not (empty? @swsync-db-operations)
                 (with-sw-db ;; All pending DB operations execute within a _single_ DB transaction.
                   (fn [_]
                     (binding [*pending-prepared-transaction?* true] ;; TODO: Hm. Why this?
                       (doseq [f @swsync-db-operations]
                         (f))))))
               (when-not (empty? @swsync-operations)
                 (doseq [f @swsync-operations]
                   (f))))))
         return-value)))))

(defmacro swsync [db-agent & body]
  "A DOSYNC where database operations (SWDBOP) are gathered up and executed within a single DB transaction in an Agent
after Clojure side transaction (DOSYNC) is done.
This only blocks until Clojure transaction is done; it will not block waiting for the DB transaction to finish; use AWAIT1
with DB-AGENT as argument to do this."
  `(swsync* ~db-agent (fn [] ~@body)))

(defmacro swop [& body]
  "Wrapper for general I/O operation; runs after (SEND-OFF) Clojure transaction (SWSYNC)."
  `(do
     (assert (thread-bound? #'*swsync-operations*)
             "SWOP (general I/O operation) outside of SWSYNC context.")
     (swap! *swsync-operations* conj (fn [] ~@body))))

(defmacro swdbop [& body]
  "Wrapper for DB type I/O operation; runs after (SEND-OFF) Clojure transaction (SWSYNC)
and all SWDBOPs runs within a single DB transaction."
  `(do
     (assert (thread-bound? #'*swsync-db-operations*)
             "SWDBOP (database operation) outside of SWSYNC context.")
     (swap! *swsync-db-operations* conj (fn [] ~@body))))
