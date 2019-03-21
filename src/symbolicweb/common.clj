(in-ns 'symbolicweb.core)

(declare add-response-chunk ref?)


(definline ref? [x]
  `(instance? Ref ~x))

(definline agent? [x]
  `(instance? clojure.lang.Agent ~x))



(let [jom (json/object-mapper {:encode-key-fn true,
                               :decode-key-fn true})]

  (defn json-generate ^String [o]
    (json/write-value-as-string o jom))

  (defn json-parse [^String json-str]
    (json/read-value json-str jom)))



(defn var-alter [^clojure.lang.Var var ^Fn fun & args]
  (var-set var (apply fun (var-get var) args)))



(defn expected-response-type [request]
  (let [accept-header (get (:headers request) "accept")]
    (cond
      (re-find #"text/javascript" accept-header) :javascript
      (re-find #"text/html" accept-header) :html
      (re-find #"text/plain" accept-header) :plain
      (re-find #"text/plugin-api" accept-header) :plugin-api
      true accept-header)))


(declare viewport-of)
(defn session-of [widget]
  (when-let [viewport (viewport-of widget)]
    (:session @viewport)))


(defn alter-options "> (alter-options (list :js-options {:close (with-js (alert \"closed!\"))}
                          :on-close (with-js (alert \"yep, really closed!\")))
                    update-in [:js-options] assoc :modal :true)
   (:js-options {:modal :true, :close \"alert(decodeURIComponent('closed%21'));\"}
    :on-close \"alert(decodeURIComponent('yep%2C%20really%20closed%21'));\")"
  [options fn & args]
  (with-local-vars [lst (list)]
    (doseq [option (apply fn (apply hash-map options) args)]
      (var-set lst (conj (var-get lst) (val option) (key option))))
    (var-get lst)))



(defmacro with-all-viewports "Handy when testing things in the REPL.
  SESSION and VIEWPORT are bound within BODY."
  [& body]
  `(doseq [~'session (vals @-sessions-)]
     (doseq [~'viewport (vals @(:viewports @~'session))]
       ~@body)))


(defmacro with-session-viewports "Run BODY in context of all Viewports of SESSION..
  SESSION and VIEWPORT are bound within BODY."
  [session & body]
  `(let [~'session ~session]
     (doseq [~'viewport (vals @(:viewports @~'session))]
       ~@body)))


(defmacro with-user-viewports "Run BODY in context of all Viewports in all Sessions of USER-MODEL (UserModelBase).
  SESSION and VIEWPORT are bound within BODY."
  [user-model & body]
  `(let [user-model# ~user-model]
     (doseq [session# (dosync @(:sessions @user-model#))]
       (with-session-viewports session#
         ~@body))))


(defn get-widget [^String id ^Ref viewport]
  (get (:widgets @viewport) id))



(defn url-encode-wrap ^String [^String s]
  (str "decodeURIComponent('" (url-encode-component s) "')"))



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


(defn defapp [[name fit-fn & args] session-constructor-fn]
  (swap! -session-types-
         #(assoc % name (apply hash-map
                               :name name
                               :fit-fn fit-fn
                               :cookie-name "_sw_s"
                               :session-constructor-fn session-constructor-fn
                               args))))



(defn touch [^Ref obj]
  (reset! ^Atom (:last-activity-time @obj)
          (System/currentTimeMillis)))


(defn script-src [^String src]
  [:script {:type "text/javascript" :src src}])


(defn link-css [^String href]
  [:link {:rel "stylesheet" :type "text/css"
          :href href}])



(defn set-document-cookie ^String [& {:keys [path domain? name value permanent?]
                                      :or {domain? true
                                           path "\" + window.location.pathname + \""
                                           name "name"
                                           value "value"
                                           permanent? true}}]
  (str "document.cookie = \""
       name "=" (if value value "") "; "
       (when domain?
         (if (= domain? true)
           "domain=\" + window.location.hostname + \"; "
           (str "domain=" domain? "; ")))
       (if value
         (if-not permanent?
           ""
           (str "expires=\""
                " + (function(){ var date = new Date(); date.setFullYear(date.getFullYear() + 42); return date.toUTCString(); })()"
                " + \"; "))
         "expires=Fri, 27 Jul 2001 02:47:11 UTC; ")
       "path=" path "; "
       "\" + (function(){ if(window.location.protocol == 'https:') return('secure=true; '); else return(''); })();"
       "\n"))



(defn set-session-cookie ^String [value permanent?]
  (set-document-cookie :name -session-cookie-name- :value value :domain? false :permanent? permanent?))



(defn remove-session [^Ref session]
  (let [session @session]
    (swap! -sessions- #(dissoc % (:id session)))))


(defn http-js-response [body]
  {:status 200
   :headers {"Content-Type" "text/javascript; charset=UTF-8"
             "Cache-Control" "no-cache, no-store, must-revalidate"
             "Expires" "0"}
   :body body})


(defn http-html-response [body]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Cache-Control" "no-cache, no-store, must-revalidate"
             "Expires" "0"}
   :body body})


(defn http-text-response [body]
  {:status 200
   :headers {"Content-Type" "text/plain; charset=UTF-8"
             "Cache-Control" "no-cache, no-store, must-revalidate"
             "Expires" "0"}
   :body body})


(defn http-replace-response [^String location]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Cache-Control" "no-cache, no-store, must-revalidate"
             "Expires" "0"}
   :body (str "<script> window.location.replace(" (url-encode-wrap location) "); </script>")})


(defn http-redirect-response [^String location]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Cache-Control" "no-cache, no-store, must-revalidate"
             "Expires" "0"}
   :body (str "<script> window.location = " (url-encode-wrap location) "; </script>")})


(defn reload-page
  ([viewport ^String rel-url]
   (js-run viewport "window.location = " (url-encode-wrap rel-url) ";"))

  ([viewport]
   ;; TODO: I guess we need three ways of reloading now.
   ;;"window.location.reload(false);"
   ;; http://blog.nostdal.org/2011/12/reloading-or-refreshing-web-page-really.html
   (js-run viewport "window.location.href = window.location.href;")))


(defn replace-page [viewport ^String rel-url]
  (js-run viewport "window.location.replace(" (url-encode-wrap rel-url) ");"))


(defn clear-session [session]
  ;; TODO: Do this on the server end instead or also?
  (with-session-viewports session
    (js-run viewport
      (set-session-cookie nil)
      "window.location.href = window.location.href;")))


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
   (js-run widget "alert(" (url-encode-wrap msg) ");")))


(declare spget)
(defn sw-js-base-bootstrap ^String [^Ref session ^Ref viewport]
  (str "var sw_cookie_name = '" -session-cookie-name- "';\n"
       (set-document-cookie :name -session-cookie-name- :value nil :domain? false :path "/")
       (set-session-cookie (:uuid @session) (= "permanent" @(spget session :session-type)))
       "var _sw_viewport_id = '" (:id @viewport) "';\n"))



(defn pred-with-limit "Returns a new predicate based on PRED which only \"lasts\" LIMIT number of times."
  [pred limit]
  (let [limit (atom limit)]
    (fn [elt]
      (if-not (pred elt)
        true
        (if (zero? @limit)
          true
          (do
            (reset! limit (dec @limit))
            false))))))


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
