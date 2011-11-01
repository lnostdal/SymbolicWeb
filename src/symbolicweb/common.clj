(in-ns 'symbolicweb.core)


(set! *print-length* 10)
(set! *print-level* 10)


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


(defmacro dbg-prin1 [form]
  `(let [res# ~form]
     (println (str '~form " => " res#))
     res#))


(defmacro with [form & body]
  `(let [~'it ~form]
     ~@body))


(defmacro with1 [form & body]
  `(with ~form
     ~@body
     ~'it))


(defmacro with-object [object & body]
  "Used by OBJ."
  `(let [~'%with-object ~object]
     ~@body))

(defmacro obj [signature]
  `(~signature ~'%with-object))


(defn map-to-js-options [opts]
  (with-out-str
    (doseq [opt opts]
      (print (str (name (key opt)) ": "
                  (with (val opt)
                    (if (keyword? it)
                      (name it)
                      it))
                  ", ")))))


(defmacro defapp [name fit-fn application-constructor-fn]
  `(swap! -application-types-
          #(assoc % '~name {:fit-fn ~fit-fn
                            :application-constructor-fn ~application-constructor-fn})))


(defmacro with-ctx [[& send-off?] & body]
  "This will run BODY in the context of an Agent for the current session, i.e. whatever *APPLICATION* is bound to."
  `(~(if send-off? 'send-off 'send)
    (:agent @*application*)
    (fn [_#]
      (binding [*in-channel-request?* false]
        (try
          (do ~@body)
          (catch Throwable e# ;; TODO: Throwable is "wrong", but it also actually works.
            (dosync
             (show-Dialog (mk-pre (vm (with-out-str (clojure.stacktrace/print-stack-trace e# 10))))
                          :js-options {:modal :true :width 1500 :height 800
                                       :buttons "{ 'Ok': function() { $(this).dialog('close'); }}"}))))))))

(let [id-generator-value (atom 0)]
  (defn generate-uid []
    "Generates an unique ID; non-universal or pr. server instance based.
Returns a string."
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


(defn remove-session [application]
  (let [application @application]
    (swap! -applications- #(dissoc % (:id application)))))


(declare add-response-chunk)
(defn clear-session []
  (add-response-chunk (str (set-document-cookie :name "_sw_application_id" :value nil)
                           " window.location.reload();")))


(defn reload-page
  ([rel-url]
     (add-response-chunk (str "window.location. = " (url-encode-wrap rel-url) ";")))
  ([]
     (add-response-chunk "window.location.reload();")))


(defn replace-page [rel-url]
  (add-response-chunk (str "window.location.replace(" (url-encode-wrap rel-url) ");")))


(defn clear-all-sessions []
  (doseq [id_application @-applications-]
    (let [application (val id_application)]
      (doseq [id_viewport (:viewports @application)]
        (binding [*viewport* (val id_viewport)]
          (clear-session))))))


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


(defn sw-js-bootstrap []
  (html
   [:script {:type "text/javascript"}
    (set-document-cookie :name "_sw_application_id" :value (:id @*application*))
    "_sw_viewport_id = '" (:id @*viewport*) "'; "
    "_sw_dynamic_subdomain = '" (if-let [it (str "sw-" (generate-uid))]
                                  (str it ".")
                                  "") "'; "]
   [:script {:type "text/javascript" :defer "defer" :src "/js/common/sw/sw-ajax.js"}]))


(defn sw-css-bootstrap []
  "")


(def ^:dynamic *with-js?* false)


(defmacro with-js [& body]
  `(binding [*with-js?* true]
     ~@body))
