(in-ns 'symbolicweb.core)

(def -comet-timeout- 29000)
(def -viewport-timeout- (+ -comet-timeout- 30000))
(def -application-timeout- (+ -viewport-timeout- 30000))

(def -request-counter-
  "Number of HTTP requests since server started."
  (atom 0))


(def -application-types-
  "name -> {:fit-fn fit-fn
            :application-constructor-fn application-constructor-fn]"
  (atom {}))

(def -applications-
  "ID -> APPLICATION"
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


(def ^:dynamic *request*
  "The current request data (map)."
  nil)

(def ^:dynamic *viewport*
  "The current VIEWPORT."
  nil)

(def ^:dynamic *application*
  "The current APPLICATION (session)."
  nil)


(def ^{:dynamic true
       :doc "This is used by ADD-RESPONSE-CHUNK to determine whether we're currently handling an AJAX-request;
probably based on some DOM/client side event."}
  *in-channel-request?*
  false)


(def ^:dynamic *in-html-container?* false)
(def ^:dynamic *html-container-accu-children* [])
