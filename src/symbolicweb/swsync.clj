(in-ns 'symbolicweb.core)

;;; http://en.wikipedia.org/wiki/Two-phase_commit_protocol

;; TODO: There isn't much difference between SWHTOPs and SWOPs at the moment except SWHTOPs execute before SWOPs. Combine them?


(defn swsync* [db-agent ^Fn bodyfn]
  (io! "SWSYNC: Nesting of SWSYNC (or SWSYNC inside DOSYNC) contexts not allowed.")
  (when *in-sw-db?*
    (assert *pending-prepared-transaction?*
            "SWSYNC: SWSYNC is meant to be used within the WITH-SW-DB callback context HOLDING-TRANSACTION or outside of WITH-SW-DB context entirely."))
  (let [^clojure.lang.Agent db-agent (if db-agent db-agent -sw-io-agent-)]
    (dosync ;; DOSYNC placed here ensures that new BINDINGs are created on MTX retry.
     (binding [^Atom *swsync-operations* (atom [])
               ^Atom *swsync-db-operations* (atom [])
               ^Atom *swsync-ht-operations* (atom [])]
       (let [return-value (bodyfn)]
         (letfn [(handle-swops-and-swhtops []
                   (let [swsync-ht-operations *swsync-ht-operations*
                         swsync-operations *swsync-operations*]
                     (binding [^Keyword *swsync-db-operations* :n.a.
                               ^Keyword *swsync-ht-operations* :n.a.
                               ^Keyword *swsync-operations* :n.a.]
                       (doseq [^Fn f @swsync-ht-operations]
                         (f))
                       (doseq [^Fn f @swsync-operations]
                         (f)))))]
           (if (empty? @*swsync-db-operations*)
             (handle-swops-and-swhtops)
             (with-sw-io db-agent
               (with-sw-db ;; All pending DB operations execute within a _single_ DB transaction.
                 (fn [^Fn holding-transaction]
                   (letfn [(handle-swdbops [swsync-db-operations]
                             (binding [^Atom *swsync-db-operations* (atom [])
                                       *pending-prepared-transaction?* true] ;; TODO: Why this?
                               (doseq [[^Keyword sql-op-type ^Fn f] swsync-db-operations]
                                 (when (= :insert sql-op-type)
                                   (f)))
                               (doseq [[^Keyword sql-op-type ^Fn f] swsync-db-operations]
                                 (when (= :update sql-op-type)
                                   (f)))
                               (doseq [[^Keyword sql-op-type ^Fn f] swsync-db-operations]
                                 (when (= :delete sql-op-type)
                                   (f)))
                               (doseq [[sql-op-type ^Fn f] swsync-db-operations]
                                 (when-not sql-op-type
                                   (f)))
                               ;; SWDBOPs might lead to (call) further SWDBOPs.
                               (when-not (empty? @*swsync-db-operations*)
                                 (handle-swdbops @*swsync-db-operations*))))]
                     (when-not (empty? @*swsync-db-operations*)
                       (handle-swdbops @*swsync-db-operations*)))
                   (when-not (empty? @*swsync-ht-operations*)
                     (holding-transaction
                      (fn [_]
                        (dosync
                         (handle-swops-and-swhtops))))))))))
         return-value)))))



(defmacro swsync [db-agent & body]
  "A DOSYNC (MTX) wrapper where database operations are gathered up via SWDBOP and executed within a single DBTX after said MTX.

This is done in a 2PC fashion (MTX --> DBTX) -- and SWHTOP can be used to add further operations that are to be executed while
holding the prepared DBTX.

The order in which operations are executed is: SWDBOPs, SWHTOPs then SWOPs. The SWHTOPs and SWOPs execute within the same MTX.
SWDBOPs executed in order :INSERT, :UPDATE, :DELETE then logical False; given by the SQL-OP-TYPE argument to SWDBOP.

DB-AGENT can be NIL, in which case -SW-IO-AGENT- will be used.

This blocks until the MTX (DOSYNC) is done; it does not block for the DBTX. Use AWAIT1 with DB-AGENT as argument if blocking here
is needed.

Returns what BODY returns."
  `(swsync* ~db-agent (fn [] ~@body)))



(defmacro swdbop [sql-op-type & body]
  "Wrapper for DB I/O operation; runs after (SEND-OFF) MTX grouped with other DB I/O operations within a single DBTX.
  SQL-OP-TYPE: :INSERT, :UPDATE, :DELETE or logical False. These govern the order in which SWDBOPs are executed.
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
     (if (= :n.a. *swsync-ht-operations*)
       (do ~@body)
       (swap! *swsync-ht-operations* conj (fn [] ~@body)))
     nil))



(defmacro swop [& body]
  "Wrapper for general I/O operation; runs after (SEND-OFF) MTX and DBTX.
Non-blocking; returns NIL.
See SWSYNC."
  `(do
     (assert (thread-bound? #'*swsync-operations*)
             "SWOP: general I/O operation outside of SWSYNC context.")
     (if (= :n.a. *swsync-operations*)
       (do ~@body)
       (swap! *swsync-operations* conj (fn [] ~@body)))
     nil))
