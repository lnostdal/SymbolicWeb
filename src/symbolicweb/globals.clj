(in-ns 'symbolicweb.core)

(defn send
  "Dispatch an action to an agent. Returns the agent immediately.
  Subsequently, in a thread from a thread pool, the state of the agent
  will be set to the value of:

  (apply action-fn state-of-agent args)"
  {:added "1.0"
   :static true}
  [^clojure.lang.Agent a f & args]
  (.dispatch a (binding [*agent* a] f) args false))

(defn send-off
  "Dispatch a potentially blocking action to an agent. Returns the
  agent immediately. Subsequently, in a separate thread, the state of
  the agent will be set to the value of:

  (apply action-fn state-of-agent args)"
  {:added "1.0"
   :static true}
  [^clojure.lang.Agent a f & args]
  (.dispatch a (binding [*agent* a] f) args true))


(defn future-call
  "Takes a function of no args and yields a future object that will
  invoke the function in another thread, and will cache the result and
  return it on all subsequent calls to deref/@. If the computation has
  not yet finished, calls to deref/@ will block, unless the variant
  of deref with timeout is used. See also - realized?."
  {:added "1.1"
   :static true}
  [f]
  (let [fut (.submit clojure.lang.Agent/soloExecutor ^Callable f)]
    (reify
     clojure.lang.IDeref
     (deref [_] (.get fut))
     clojure.lang.IBlockingDeref
     (deref
      [_ timeout-ms timeout-val]
      (try (.get fut timeout-ms java.util.concurrent.TimeUnit/MILLISECONDS)
           (catch java.util.concurrent.TimeoutException e
             timeout-val)))
     clojure.lang.IPending
     (isRealized [_] (.isDone fut))
     java.util.concurrent.Future
      (get [_] (.get fut))
      (get [_ timeout unit] (.get fut timeout unit))
      (isCancelled [_] (.isCancelled fut))
      (isDone [_] (.isDone fut))
      (cancel [_ interrupt?] (.cancel fut interrupt?)))))


(defmacro future
  "Takes a body of expressions and yields a future object that will
  invoke the body in another thread, and will cache the result and
  return it on all subsequent calls to deref/@. If the computation has
  not yet finished, calls to deref/@ will block, unless the variant of
  deref with timeout is used. See also - realized?."
  {:added "1.1"}
  [& body] `(future-call (^{:once true} fn* [] ~@body)))



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
