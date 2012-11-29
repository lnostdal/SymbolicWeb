(in-ns 'symbolicweb.core)


;;; http://en.wikipedia.org/wiki/Two-phase_commit_protocol

;;(declare with-sw-db) ;; It's actually a funtion now, so..


(defn swsync* [db-agent ^Fn bodyfn]
  (io! "SWSYNC: Nesting of SWSYNC (or SWSYNC inside DOSYNC) contexts not allowed.")
  (when *in-sw-db?*
    (assert *pending-prepared-transaction?*
            "SWSYNC: SWSYNC is meant to be used within the WITH-SW-DB callback context HOLDING-TRANSACTION or outside of WITH-SW-DB context entirely."))
  (let [^clojure.lang.Agent db-agent (if db-agent db-agent -sw-io-agent-)]
    (dosync
     (binding [^Atom *swsync-operations* (atom [])
               ^Atom *swsync-db-operations* (atom [])
               ^Atom *swsync-ht-operations* (atom [])]
       (let [return-value (bodyfn)]
         (when-not (and (empty? @*swsync-operations*)
                        (empty? @*swsync-db-operations*))
           (let [swsync-operations *swsync-operations*
                 swsync-db-operations *swsync-db-operations*
                 swsync-ht-operations *swsync-ht-operations*]
             (with-sw-io db-agent
               (when-not (empty? @swsync-db-operations) ;; TODO: What if SWSYNC-HT-OPERATIONS isn't empty?
                 (with-sw-db ;; All pending DB operations execute within a _single_ DB transaction.
                   (fn [^Fn holding-transaction]
                     (binding [*pending-prepared-transaction?* true] ;; TODO: Hm. Why this?
                       (doseq [^Fn f @swsync-db-operations]
                         (f)))
                     (when-not (empty? @swsync-ht-operations)
                       (holding-transaction
                        (fn [_]
                          (doseq [^Fn f @swsync-ht-operations]
                            (f))))))))
               (when-not (empty? @swsync-operations)
                 (doseq [^Fn f @swsync-operations]
                   (f))))))
         return-value)))))



(defmacro swsync [db-agent & body]
  "A DOSYNC (MTX) wrapper where database operations are gathered up via SWDBOP and executed within a single DBTX after said MTX.

This is done in a 2PC fashion (MTX --> DBTX) -- and SWHTOP can be used to add further operations that are to be executed while
holding the prepared DBTX.

DB-AGENT can be NIL, in which case -SW-IO-AGENT- will be used.

This blocks until the MTX (DOSYNC) is done; it does not block for the DBTX. Use AWAIT1 with DB-AGENT as argument if blocking here
is needed."
  `(swsync* ~db-agent (fn [] ~@body)))



(defmacro swop [& body]
  "Wrapper for general I/O operation; runs after (SEND-OFF) MTX.
See SWSYNC."
  `(do
     (assert (thread-bound? #'*swsync-operations*)
             "SWOP: general I/O operation outside of SWSYNC context.")
     (swap! *swsync-operations* conj (fn [] ~@body))))



(defmacro swdbop [& body]
  "Wrapper for DB I/O operation; runs after (SEND-OFF) MTX grouped with other DB I/O operations within a single DBTX.
See SWSYNC."
  `(do
     (assert (thread-bound? #'*swsync-db-operations*)
             "SWDBOP: database operation outside of SWSYNC context.")
     (swap! *swsync-db-operations* conj (fn [] ~@body))))



(defmacro swhtop [& body]
  "Wrapper for 'HOLDING-TRANSACTION' (2PC) type operations; these run while holding the prepared or pending DBTX -- commiting it
afterwards.
See SWSYNC."
  `(do
     (assert (thread-bound? #'swsync-ht-operations*)
             "SWHTOP: \"HOLDING-TRANSACTION\" type operation outside of SWSYNC context.")
     (swap! *swsync-ht-operations* conj (fn [] ~@body))))