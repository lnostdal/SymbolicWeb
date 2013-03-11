(in-ns 'symbolicweb.core)



(defn gc-viewport [^Ref viewport]
  (dosync
   (let [viewport-m (ensure viewport)]
     ;; This will call the DO-LIFETIME-DEACTIVATION CBs which will disconnect the widgets from their models (Observables).
     (detach-lifetime (.lifetime (:root-element viewport-m)))
     ;; Session -/-> Viewport.
     (alter (:viewports (ensure (:session viewport-m)))
            dissoc (:id viewport-m)))))



(defn gc-session [^Ref session]
  (dosync
   (let [session-m (ensure session)]
     (alter -sessions- dissoc (:uuid session-m))
     (vm-alter -num-sessions-model- - 1)

     ;; GC all Viewports in SESSION.
     (doseq [[viewport-id viewport] (ensure (:viewports session-m))]
       (gc-viewport viewport))

     ;; UserModel -/-> Session
     (when-let [user-model @(:user-model session-m)]
       (vm-alter (:sessions user-model) disj session)))))



(defn do-gc []
  (let [now (System/currentTimeMillis)]
    (doseq [[session-id session] @-sessions-]
      (if (< -session-timeout- (- now @(:last-activity-time @session)))
        (gc-session session)
        ;; The Session hasn't timed out, but perhaps some of the Viewports in the Session has?
        (dosync
         (doseq [[viewport-id viewport] (ensure (:viewports @session))]
           (when (< -viewport-timeout- (- now @(:last-activity-time @viewport)))
             (gc-viewport viewport))))))))



(defonce -gc-thread-
  (future
    (loop []
      (try
        (do-gc)
        (Thread/sleep 10000)
        (catch Throwable e
          (clojure.stacktrace/print-stack-trace e 50)
          (Thread/sleep 1000)))
      (recur))))
