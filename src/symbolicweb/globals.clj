(in-ns 'symbolicweb.core)

(defn ref? [x]
  (= clojure.lang.Ref (type x)))

(def -http-server-string- "nostdal.org")

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
  "SESSION-COOKIE-VALUE -> APPLICATION"
  (atom {}))

(def -viewports-
  "ID -> VIEWPORT"
  (atom {}))


;; TODO: Hack; forward decl and def since -GC-THREAD- is started right away.
(defn ensure-non-visible [widget])
(defn do-gc []
  ;;(println "DO-GC")
  (let [now (System/currentTimeMillis)
        checker-fn (fn [cnt timeout]
                     (doseq [obj @cnt]
                       (let [obj-ref (val obj)
                             obj @(val obj)]
                         (when (< timeout (- now (:last-activity-time obj)))
                           (swap! cnt #(dissoc % (:id obj))) ;; Remove from -APPLICATIONS- or -VIEWPORTS-.
                           (case (:type obj)
                             ::Application
                             (dosync
                              ;;(println "GC: Application")
                              (vm-set -num-applications-model- (count @-applications-))
                              ;; UserModel -/-> Application.
                              (when-let [user-model @(:user-model obj)]
                                (vm-alter (:applications user-model) disj obj-ref)))

                             ::Viewport
                             (dosync
                              ;;(println "DO-GC: Viewport")
                              ;; This will ensure that Models that hang around "for a long time" (e.g. global vars) doesn't try
                              ;; to forward their updates to Widgets in stale Viewports.
                              (ensure-non-visible (:root-element obj))
                              ;; Application -/-> Viewport.
                              (alter (:application obj) update-in [:viewports] dissoc (:id obj))))))))]
    (checker-fn -applications- -application-timeout-)
    (checker-fn -viewports- -viewport-timeout-)))


;; This thing iterates through all sessions in -APPLICATIONS- and -VIEWPORTS- and checks their :LAST-ACTIVITY-TIME
;; properties removing unused or timed out sessions.
(defonce -gc-thread-
  (let [it (agent 42)] ;; TODO: Error handler!
    (send-off it (fn [_]
                   (loop []
                     (do-gc)
                     (Thread/sleep 10000) ;; TODO: Probably too low, and magic value anyway.
                     (recur))))
    it))
