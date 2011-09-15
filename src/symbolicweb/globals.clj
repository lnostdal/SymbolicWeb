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


(def -num-applications-model-
  (vm 0))

(def -applications-
  "ID -> APPLICATION"
  (atom {}))

(def -viewports-
  "ID -> VIEWPORT"
  (atom {}))


;; TODO: yeah, this stuff; remove-branch needs to be called to disconnect Widgets from any long-living Models.
(declare jqRemove)
#_(when (and (= ::Viewport (:type obj))
             (:root-element obj))
    (dosync
     (jqRemove (:root-element obj))))


(defn do-gc []
  ;;(println "..DO-GC.!")
  (let [now (System/currentTimeMillis)
        checker-fn (fn [cnt timeout]
                     (doseq [obj @cnt]
                       (let [obj @(val obj)]
                         (when (< timeout (- now (:last-activity-time obj)))
                           ;;(println "DO-GC: Timeout found!")
                           (swap! cnt #(dissoc % (:id obj)))
                           (when (= (:type obj) ::Application)
                             ;;(println "DO-GC: Updating Application counter..")
                             (dosync
                              (set-value -num-applications-model- (count @-applications-))))))))]
    (checker-fn -applications- -application-timeout-)
    (checker-fn -viewports- -viewport-timeout-)))


;; This thing iterates through all sessions in -APPLICATIONS- and -VIEWPORTS- and checks their :LAST-ACTIVITY-TIME
;; properties removing unused or timed out sessions.
(defonce -gc-thread-
  (let [it (agent 42)]
    (send-off it (fn [_]
                   (loop []
                     (do-gc)
                     (Thread/sleep 5000)
                     (recur))))
    it))


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
