(in-ns 'symbolicweb.core)


(defn gc-viewport [viewport]
  (dosync
   (let [viewport-m (ensure viewport)]
     (swap! -viewports- #(dissoc % (:id viewport-m))) ;; Remove VIEWPORT from -VIEWPORTS- global.
     ;; This will call the DO-LIFETIME-DEACTIVATION CBs which will disconnect the widgets from their models (Observables).
     (detach-lifetime (.lifetime (:root-element viewport-m)))
     ;; Session -/-> Viewport.
     (alter (:session viewport-m) update-in [:viewports] dissoc (:id viewport-m)))))



;; This thing iterates through all sessions in -SESSIONS- and -VIEWPORTS- and checks their :LAST-ACTIVITY-TIME
;; properties removing unused or timed out sessions / viewports.
(defn do-gc []
  (let [now (System/currentTimeMillis)
        checker-fn (fn [cnt timeout]
                     (doseq [obj @cnt]
                       (let [obj-ref (val obj)
                             obj @(val obj)]
                         (when (< timeout (- now @(:last-activity-time obj)))
                           (swap! cnt #(dissoc % (:id obj))) ;; Remove OBJ from -SESSIONS- or -VIEWPORTS- global.
                           (case (:type obj)
                             ::Session
                             (dosync
                              (vm-alter -num-sessions-model- - 1)
                              ;; UserModel -/-> Session
                              (when-let [user-model @(:user-model obj)]
                                (vm-alter (:sessionss user-model) disj obj-ref)))

                             ::Viewport
                             (gc-viewport obj-ref))))))]
    (checker-fn -sessions- -session-timeout-)
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
