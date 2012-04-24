(in-ns 'symbolicweb.core)


(defn ref? [x]
  (= clojure.lang.Ref (type x)))

(def -http-server-string- "SymbolicWeb (nostdal.org)")

;; Long poll timeout every X / 1000 seconds.
(def -comet-timeout- 25000)

;; X / 1000 seconds since -COMET-TIMEOUT- and still no new request (long poll)?
(def -viewport-timeout- (+ -comet-timeout- 10000))

;; X / 1000 seconds since -VIEWPORT-TIMEOUT- and still no new request (long poll) from any Viewport in the session?
(def -application-timeout- (+ -viewport-timeout- 10000))

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



;; TODO: Hack; def (instead of just forward decl) since -GC-THREAD- is started right away.
(defn ensure-non-visible [widget])

(defn gc-viewport [viewport]
  (dosync
   (let [viewport-m (ensure viewport)]
     (swap! -viewports- #(dissoc % (:id viewport-m))) ;; Remove VIEWPORT from -VIEWPORTS- global.
     ;; This will ensure that Models that hang around "for a long time" (e.g. global vars) doesn't try
     ;; to forward their updates to Widgets in stale Viewports.
     (ensure-non-visible (:root-element viewport-m))
     ;; Application -/-> Viewport.
     (alter (:application viewport-m) update-in [:viewports] dissoc (:id viewport-m)))))

;; This thing iterates through all sessions in -APPLICATIONS- and -VIEWPORTS- and checks their :LAST-ACTIVITY-TIME
;; properties removing unused or timed out sessions / viewports.
(defn do-gc []
  ;;(println "DO-GC")
  (let [now (System/currentTimeMillis)
        checker-fn (fn [cnt timeout]
                     (doseq [obj @cnt]
                       (let [obj-ref (val obj)
                             obj @(val obj)]
                         (when (< timeout (- now @(:last-activity-time obj)))
                           (swap! cnt #(dissoc % (:id obj))) ;; Remove OBJ from -APPLICATIONS- or -VIEWPORTS- global.
                           (case (:type obj)
                             ::Application
                             (dosync
                              (vm-alter -num-applications-model- - 1)
                              ;; UserModel -/-> Application.
                              (when-let [user-model @(:user-model obj)]
                                (vm-alter (:applications user-model) disj obj-ref)))

                             ::Viewport
                             (gc-viewport obj-ref))))))]
    (checker-fn -applications- -application-timeout-)
    (checker-fn -viewports- -viewport-timeout-)))

(def -gc-thread-error-handler-
  (fn [the-agent exception]
    (try
      (println "-GC-THREAD-ERROR-HANDLER-, thrown:")
      (clojure.stacktrace/print-stack-trace exception 50)
      (catch Throwable inner-exception
        (println "-GC-THREAD-ERROR-HANDLER-: Dodge überfail... :(")
        (Thread/sleep 1000))))) ;; Make sure we aren't flooded in case some loop gets stuck.

(defonce -gc-thread-
  (with1 (agent 42 :error-handler #'-gc-thread-error-handler-)
    (send-off it (fn [_]
                   (loop []
                     (do-gc)
                     (Thread/sleep 10000) ;; TODO: Probably too low, and magic value anyway.
                     (recur))))))
