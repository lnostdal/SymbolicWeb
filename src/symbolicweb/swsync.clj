(in-ns 'symbolicweb.core)

;;; http://en.wikipedia.org/wiki/Two-phase_commit_protocol


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
         (when-not (and (empty? @*swsync-operations*) ;; TODO: What about SWSYNC-HT-OPERATIONS?
                        (empty? @*swsync-db-operations*))
           (let [swsync-operations *swsync-operations*
                 swsync-db-operations *swsync-db-operations*
                 swsync-ht-operations *swsync-ht-operations*]
             (binding [*swsync-operations* :n.a.
                       *swsync-db-operations* :n.a.
                       *swsync-ht-operations* :n.a.]
               (with-sw-io db-agent
                 (when-not (empty? @swsync-db-operations) ;; TODO: What about SWSYNC-HT-OPERATIONS?
                   (with-sw-db ;; All pending DB operations execute within a _single_ DB transaction.
                     (fn [^Fn holding-transaction]
                       (binding [*pending-prepared-transaction?* true] ;; TODO: Hm. Why this?
                         (doseq [[^Keyword sql-op-type ^Fn f] @swsync-db-operations]
                           (when (= :insert sql-op-type)
                             (f)))
                         (doseq [[^Keyword sql-op-type ^Fn f] @swsync-db-operations]
                           (when (= :update sql-op-type)
                             (f)))
                         (doseq [[^Keyword sql-op-type ^Fn f] @swsync-db-operations]
                           (when (= :delete sql-op-type)
                             (f)))
                         (doseq [[sql-op-type ^Fn f] @swsync-db-operations]
                           (when-not sql-op-type
                             (f))))
                       (when-not (empty? @swsync-ht-operations)
                         (holding-transaction
                          (fn [_]
                            (doseq [^Fn f @swsync-ht-operations] ;; TODO: DOSYNC or SWSYNC again here?
                              (f))))))))
                 (when-not (empty? @swsync-operations)
                   (doseq [^Fn f @swsync-operations] ;; TODO: DOSYNC or SWSYNC again here?
                     (f)))))))
         return-value)))))



(defmacro swsync [db-agent & body]
  "A DOSYNC (MTX) wrapper where database operations are gathered up via SWDBOP and executed within a single DBTX after said MTX.

This is done in a 2PC fashion (MTX --> DBTX) -- and SWHTOP can be used to add further operations that are to be executed while
holding the prepared DBTX.

The order in which operations are executed is: SWOPs, SWDBOPs then SWHTOPs.

DB-AGENT can be NIL, in which case -SW-IO-AGENT- will be used.

This blocks until the MTX (DOSYNC) is done; it does not block for the DBTX. Use AWAIT1 with DB-AGENT as argument if blocking here
is needed.

Returns what BODY returns."
  `(swsync* ~db-agent (fn [] ~@body)))



(defmacro swop [& body]
  "Wrapper for general I/O operation; runs after (SEND-OFF) MTX and DBTX.
Non-blocking; returns NIL.
See SWSYNC."
  `(do
     (assert (thread-bound? #'*swsync-operations*)
             "SWOP: general I/O operation outside of SWSYNC context.")
     (swap! *swsync-operations* conj (fn [] ~@body))
     nil))



(defmacro swdbop [sql-op-type & body]
  "Wrapper for DB I/O operation; runs after (SEND-OFF) MTX grouped with other DB I/O operations within a single DBTX.
  SQL-OP-TYPE: :INSERT, :UPDATE, :DELETE or logical False. This governs the order in which SWDBOPs are executed.
Non-blocking; returns NIL.
See SWSYNC."

  `(do
     (assert (or (= :insert ~sql-op-type)
                 (= :update ~sql-op-type)
                 (= :delete ~sql-op-type)
                 (not ~sql-op-type)))
     (assert (thread-bound? #'*swsync-db-operations*)
             "SWDBOP: database operation outside of SWSYNC context.")
     (swap! *swsync-db-operations* conj [~sql-op-type (fn [] ~@body)])
     nil))



(defmacro swhtop [& body]
  "Wrapper for 'HOLDING-TRANSACTION' (2PC) type operations; these run while holding the prepared or pending DBTX -- commiting it
afterwards.
Non-blocking; returns NIL.
See SWSYNC."
  `(do
     (assert (thread-bound? #'*swsync-ht-operations*)
             "SWHTOP: \"HOLDING-TRANSACTION\" type operation outside of SWSYNC context.")
     (swap! *swsync-ht-operations* conj (fn [] ~@body))
     nil))