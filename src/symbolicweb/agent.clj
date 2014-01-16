(in-ns 'symbolicweb.core)



(def -sw-io-agent-error-handler-
  (fn [the-agent exception]
    (try
      (flush)
      (println "-SW-IO-AGENT-ERROR-HANDLER- (" the-agent "), thrown:") (flush)
      (clojure.stacktrace/print-stack-trace exception 1000) (flush)
      (catch Throwable inner-exception
        (println "-SW-IO-AGENT-ERROR-HANDLER-: Dodge überfail... :(") (flush)
        (Thread/sleep 1000))))) ;; Make sure we aren't flooded in case some loop gets stuck.



(defn mk-sw-agent [m binding-whitelist binding-blacklist]
  "  BINDING-WHITELIST: A Set of keys representing bindings to forward from current or existing (outer) context to WITH-SW-AGENT
context. If NIL, a default one will be generated based on all existing keys in return value of GET-THREAD-BINDINGS in context
of this function (call to MK-SW-AGENT).

  BINDING-BLACKLIST: A Set of keys that are to be filtered away from the whitelisted keys at hand."
  (merge {:clj-agent (agent :initial-state)
          :binding-whitelist
          (clojure.set/difference (or binding-whitelist
                                      (into #{} (keys (get-thread-bindings))))
                                  binding-blacklist)
          :agent-bindings (get-thread-bindings)
          :executor clojure.lang.Agent/soloExecutor} ;; This is SEND-OFF, and pooledExecutor is SEND.
         m))



(def -sw-io-agent- (mk-sw-agent {} nil nil))



(defn with-sw-agent* [m ^Fn body-fn]
  (let [sw-agent (if m (merge -sw-io-agent- m) -sw-io-agent-)
        ^clojure.lang.Agent clj-agent (:clj-agent sw-agent)]
    (.dispatch clj-agent
               (let [outer-bindings (get-thread-bindings)] ;; The ones setup by WITH-BINDINGS will be the inner ones.
                 (fn [& _]
                   (with-bindings (merge (:agent-bindings sw-agent)
                                         (select-keys outer-bindings (:binding-whitelist sw-agent))
                                         {#'clojure.core/*agent* clj-agent})
                     (try
                       (body-fn) (flush)
                       (catch Throwable e
                         (try
                           (if-let [^Fn f (:exception-handler-fn sw-agent)]
                             (f sw-agent e)
                             (-sw-io-agent-error-handler- sw-agent e))
                           (catch Throwable ne
                             (println "WITH-SW-IO*: Exception while handling exception!") (flush)
                             (println "\nOriginal exception:") (flush)
                             (clojure.stacktrace/print-stack-trace e 1000)
                             (println "\nNested exception:") (flush)
                             (clojure.stacktrace/print-stack-trace ne 1000) (flush)
                             (Thread/sleep 2000)))))))) ;; Dodge potential flood from a loop or similar.
               nil
               (:executor sw-agent))))



(defmacro with-sw-agent [m & body]
  `(with-sw-agent* ~m (fn [] ~@body)))
