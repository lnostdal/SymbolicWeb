(in-ns 'symbolicweb.core)



(defn gc-viewport [^Ref viewport]
  (let [viewport-m (ensure viewport)]
    ;; This will call the DO-LIFETIME-DEACTIVATION CBs which will disconnect the widgets from their models (Observables).
    (detach-lifetime (.lifetime ^WidgetBase (:root-element viewport-m)))
    ;; Session -/-> Viewport.
    (alter (:viewports (ensure (:session viewport-m)))
           dissoc (:id viewport-m))))



(defn gc-session [^String session-uuid ^Ref session]
  (let [session-m (ensure session)]
    (alter -sessions- dissoc session-uuid)
    (vm-alter -num-sessions-model- dec)
    (when-not (:one-shot? @session)
      (if (= "permanent" @(spget session :session-type))
        (dao-cache-remove (dao-get-cache "sessions") @(:id session-m))
        (dao-remove session "sessions")))

    ;; GC all Viewports in SESSION.
    (doseq [[viewport-id viewport] (ensure (:viewports session-m))]
      (gc-viewport viewport))

    ;; UserModel -/-> Session
    (when-let [user-model @(:user-model session-m)]
      (vm-alter (:sessions @user-model) disj session))))



(defn do-gc []
  (swsync
   (let [now (System/currentTimeMillis)]
     (doseq [[^String session-uuid ^Ref session] (ensure -sessions-)]
       (if (< -session-timeout- (- now @(:last-activity-time @session)))
         (gc-session session-uuid session)
         ;; The Session hasn't timed out, but perhaps some of the Viewports in the Session has?
         (doseq [[viewport-id viewport] (ensure (:viewports @session))]
           (when (< -viewport-timeout- (- now @(:last-activity-time @viewport)))
             (gc-viewport viewport))))))))



(defonce -gc-thread-
  (future
    (loop []
      (try
        (Thread/sleep 10000)
        (do-gc)
        (catch Throwable e
          (println "## -GC-THREAD- ##")
          (clojure.stacktrace/print-stack-trace e 50)))
      (recur))))
