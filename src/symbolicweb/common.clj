(in-ns 'symbolicweb.core)

(def -comet-timeout- 29000)
(def -viewport-timeout- (+ -comet-timeout- 30000))
(def -application-timeout- (+ -viewport-timeout- 30000))


(def -application-types-
  "name -> {:fit-fn fit-fn
            :application-constructor-fn application-constructor-fn]"
  (atom {}))

(def -applications-
  "ID -> APPLICATION"
  (atom {}))

(def -viewports-
  "ID -> VIEWPORT"
  (atom {}))

(def -gc-thread-
  "This thing iterates through all sessions in -APPLICATIONS- and -VIEWPORTS- end checks their :LAST-ACTIVITY-TIME properties
removing unused or timed out sessions."
  (agent 42))

(send-off -gc-thread-
          (fn [_]
            (loop  []
              (let [now (System/currentTimeMillis)
                    checker-fn (fn [cnt timeout]
                                 (doseq [obj @cnt]
                                   (let [obj @(val obj)]
                                     (when (< timeout (- now (:last-activity-time obj)))
                                       (swap! cnt #(dissoc % (:id obj)))))))]
                (checker-fn -applications- -application-timeout-)
                (checker-fn -viewports- -viewport-timeout-)
                (when (= *agent* -gc-thread-)
                  (Thread/sleep 5000)
                  (recur))))))

(def -widgets-
  "ID -> WIDGET"
  (atom {}))


(def ^:dynamic *request*
  "The current request data (map)."
  nil)

(def ^:dynamic *viewport*
  "The current VIEWPORT."
  nil)

(def ^:dynamic *application*
  "The current APPLICATION (session)."
  nil)


(let [id-generator-value (atom 0)]
  (defn generate-uid []
    "Generates an unique ID; non-universal.
Returns a string."
    (str (swap! id-generator-value inc'))))


(defn generate-uuid []
  "Generates an universal unique ID (UUID).
Returns a string."
  ;; TODO: This is only pseudo random; we can do better.
  (str (java.util.UUID/randomUUID)))


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


(defn set-document-cookie [& {:keys [application viewport name value]
                              :or {application *application*
                                   viewport *viewport*
                                   name "name" ;;(cookie-name-of (server-of application))
                                   value "value"}}] ;;(cookie-value-of app)
  (str "document.cookie = \""
       name "=" (if value value "") "; "
       "domain=.\" + window.location.hostname + \"; "
       (if value
         (str "expires=\""
              "+ (function(){ var date = new Date(); date.setFullYear(date.getFullYear()+1); return date.toUTCString(); })()"
              "+ \"; ")
         ;; MDC states that this could simply be 0, but I have not tested this.
         "expires=Fri, 27 Jul 2001 02:47:11 UTC; ")
       "path=\" + window.location.pathname + \"; "
       "\";"))


(defn is-url?
  ([url-path]
     (assert (thread-bound? #'*request*))
     (is-url? url-path (:uri *request*)))
  ([url-path uri]
     (= (subs uri 0 (min (count url-path) (count uri)))
        url-path)))
