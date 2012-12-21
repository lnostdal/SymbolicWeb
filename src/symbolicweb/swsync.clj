(in-ns 'symbolicweb.core)

;;; A DOSYNC (MTX; Clojure) and database (DBTX; PostgreSQL) 2PC wrapper:
;;; http://en.wikipedia.org/wiki/Two-phase_commit_protocol

;; TODO: There isn't much difference between SWHTOPs and SWOPs at the moment except SWHTOPs execute before SWOPs. Combine them?
;; ..or have something like:
;;
;;
;; Multi-pass:
;;;;;;;;;;;;;;
;;
;; (swop
;;  (println "1")
;;  (swop
;;   (println "3")
;;   (swop
;;    (println "5"))
;;   (println "4"))
;;  (println "2"))
;; => 1, 2, 3, 4, 5
;;
;;
;; Single-pass:
;;;;;;;;;;;;;;;
;;
;; (swop
;;  (println "1")
;;  (swop
;;   (println "2")
;;   (swop
;;    (println "3"))
;;   (println "4"))
;;  (println "5"))
;; => 1, 2, 3, 4, 5
;;
;;
;; ...could perhaps have SWSPOP and SWMPOP here. ..or SW This Pass OP and SW Next Pass OP. There's a chance this would be a
;; better mechanism than the current :INSERT, :UPDATE, etc. stuff for SWDBOP too -- and it can be generalized to not related
;; to this code or component at all.
;;
;; TODO: I suppose SWSYNC* would be simpler if *SWSYNC-OPERATIONS* was a map with :INSERT, :UPDATE, :DELETE etc. for keys?


(defn swsync* [db-agent ^Fn bodyfn]
  (io! "SWSYNC: Nesting of SWSYNC (or SWSYNC inside DOSYNC) contexts not allowed.")
  (when *in-sw-db?*
    (assert *pending-prepared-transaction?*
            "SWSYNC: SWSYNC is meant to be used within the WITH-SW-DB callback context HOLDING-TRANSACTION or outside of WITH-SW-DB context entirely."))
  (let [db-agent (if db-agent db-agent -sw-io-agent-)]
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
                               (doseq [the-sql-op-type [:insert :update :delete :select nil]]
                                 (doseq [[^Keyword sql-op-type ^Fn f] swsync-db-operations]
                                   (when (= the-sql-op-type sql-op-type)
                                     (f)
                                     (when-not (empty? @*swsync-db-operations*)
                                       (handle-swdbops @*swsync-db-operations*))))
                                 (when-not (empty? @*swsync-db-operations*)
                                   (handle-swdbops @*swsync-db-operations*)))))]
                     (when-not (empty? @*swsync-db-operations*)
                       (handle-swdbops @*swsync-db-operations*)))
                   (when-not (and (empty? @*swsync-ht-operations*)
                                  (empty? @*swsync-operations*))
                     (holding-transaction
                      (fn [_]
                        (dosync
                         (handle-swops-and-swhtops))))))))))
         return-value)))))



(defmacro swsync [db-agent & body]
  "A DOSYNC (MTX; Clojure) and database (DBTX; PostgreSQL) 2PC wrapper:
  http://en.wikipedia.org/wiki/Two-phase_commit_protocol

Things are done in a 2PC fashion after MTX1:

    (MTX1 <body>) --SEND-OFF--> (DBTX <swdbop>* (MTX2 <swhtop>* <swop>*))

  * MTX1: Is BODY which will be wrapped in a DOSYNC.
  * DBTX: Is operations added via SWDBOP within the dynamic scope of BODY.
  * MTX2: Is operations added via SWOP and SWHTOP within the dynamic scopes of BODY and DBTX.
          These are executed while DBTX is held or pending.


The order in which operations are executed is: SWDBOPs, SWHTOPs then SWOPs. The SWHTOPs and SWOPs execute within the same MTX;
MTX2. SWDBOPs are executed in this order:

  :INSERT, :UPDATE, :DELETE, :SELECT then logical False; given by the SQL-OP-TYPE argument to SWDBOP.


* If the :INSERT operations trigger new :INSERT operations, those will be executed in turn before any :UPDATE operations.
* If the :UPDATE operations trigger new :INSERT or :UPDATE operations, those will be executed before any :DELETE operations.
* ..and so on..

DB-AGENT can be NIL, in which case -SW-IO-AGENT- will be used.

This blocks until the MTX1 is done; it does not block for DBTX and MTX2. Use (await1 (:agent DB-AGENT) if blocking is needed.

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
                 (= :select ~sql-op-type)
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
