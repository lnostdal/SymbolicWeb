(in-ns 'symbolicweb.core)


;;; http://en.wikipedia.org/wiki/Two-phase_commit_protocol


(defn mk-db-pool [spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname spec))
               (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
               (.setUser (:user spec))
               (.setPassword (:password spec))
               ;; Expire excess connections after 30 minutes of inactivity:
               (.setMaxIdleTimeExcessConnections (* 30 60))
               ;; Expire connections after 3 hours of inactivity:
               (.setMaxIdleTime (* 3 60 60))
               (.setMaxPoolSize 10))] ;; TODO: Configurable (from higher level; users of SW).
    (delay {:datasource cpds})))



(defonce -pooled-db-spec-
  (atom (mk-db-pool (let [db-host  "localhost"
                          db-port  5432
                          db-name  "temp"
                          user     "temp"
                          password "temp"]
                      {:classname "org.postgresql.Driver"
                       :subprotocol "postgresql"
                       :subname (str "//" db-host ":" db-port "/" db-name)
                       :user user
                       :password password}))))



(defmacro with-db-conn [& body]
  `(with-jdbc-conn @@-pooled-db-spec-
     ~@body))



(defn db-stmt [^String sql]
  (jdbc-stmt *db* sql))



(defn db-pstmt [^String sql & params]
  (jdbc-pstmt *db* sql params))



(defn db-insert
  ([table-name m]
     (db-insert table-name m true))

  ([table-name m ^Boolean return-result?]
     (let [res (first (sql/insert table-name m))
           sql (if return-result?
                 (str (first res) " RETURNING *")
                 (first res))
           params (rest res)]
       (apply db-pstmt sql params))))



(defn db-update [table-name m where]
  (let [res (sql/update table-name m where)
        ^String sql (first res)
        params (rest res)]
    (apply db-pstmt sql params)))



(defn db-delete [table-name where]
  (let [res (sql/delete table-name where)
        ^String sql (first res)
        params (rest res)]
    (apply db-pstmt sql params)))




(defn abort-2pctx [return-value]
  (throw (ex-info "ABORT-2PCTX" {:2pctx-state :abort, :return-value return-value})))



(defn retry-2pctx [^String msg]
  (throw (ex-info msg {:2pctx-state :retry})))



;; TODO: Switch to WITH-JDBC-DBTX.
(defn %with-dbtx-ctx [^Fn body-fn]
  (try
    (body-fn)
    (catch java.sql.SQLException e
      (if (= "40001" (.getSQLState e))
        (do
          (println "%WITH-DBTX-CTX: Serialization conflict (PostgreSQL state 40001); retrying!")
          (retry-2pctx "%WITH-DBTX-CTX: Retry."))
        (throw e)))))



;; TODO: Switch to WITH-JDBC-DBTX.
(defmacro with-dbtx-ctx [& body]
  `(%with-dbtx-ctx (fn [] ~@body)))



(defn do-dbtx [^Fn body-fn]
  (let [^java.sql.Connection db-conn *db*
        db-stmt (.createStatement db-conn)
        dbtx-id (.toString (generate-uid))
        dbtx-phase (atom 0)]
    (try
      (with-dbtx-ctx ;; TODO: Switch to WITH-JDBC-DBTX.
        ;; (println "DO-DBTX: Start transaction.. (1st phase)")
        (.setTransactionIsolation db-conn java.sql.Connection/TRANSACTION_SERIALIZABLE)
        (.setAutoCommit db-conn false)
        (reset! dbtx-phase 1)

        (body-fn (fn []
                   ;; (println "DO-DBTX: ..hold-transaction.. (2nd phase)")
                   (.execute db-stmt (str "PREPARE TRANSACTION '" dbtx-id "';"))
                   (.commit db-conn)
                   (.setAutoCommit db-conn true)
                   (reset! dbtx-phase 2))
                 (fn []
                   (assert (= 2 @dbtx-phase))
                   ;; (println "DO-DBTX: ..commit!")
                   (.execute db-stmt (str "COMMIT PREPARED '" dbtx-id "';"))
                   (reset! dbtx-phase 3))))

      (catch Throwable e
        ;; (println "DO-DBTX: Rolling back phase" @dbtx-phase "DBTX.")
        (case @dbtx-phase
          0 nil
          1 (do (.rollback db-conn)
                (.setAutoCommit db-conn true))
          2 (.execute db-stmt (str "ROLLBACK PREPARED '" dbtx-id "';"))
          3 (assert false "DO-DBTX: This shouldn't happen."))
        (throw e))
      (finally
        (.close db-stmt)))))



(defn do-mtx [^Fn body-fn ^Fn prepare-fn ^Fn commit-fn]
  "  PREPARE-FN is called just before the MTX is held; we might still roll back.
  COMMIT-FN is called while the MTX is held; we will not roll back here."
  (with-local-vars [mtx-phase 0]
    (let [dummy (ref nil :validator (fn [x]
                                      (when (= x 42)
                                        (assert (= 1 (var-get mtx-phase)))
                                        (var-set mtx-phase 2) ;; ..hold transaction.. (2nd phase)
                                        (commit-fn)
                                        (var-set mtx-phase 3))
                                      true))] ;; ..and here the transaction will be commited in full (return value).
      ;; Start transaction.. (1st phase)
      (dosync
       (if-not (zero? (var-get mtx-phase))
         (retry-2pctx "DO-MTX: Retry.")
         (do
           (var-set mtx-phase 1)
           (ref-set dummy 42)
           (do1 (body-fn)
             (prepare-fn))))))))



(defn do-2pctx [^Fn body-fn]
  (if (clojure.lang.LockingTransaction/isRunning) ;; Handle nesting..
    (do
      (println "WARN: DO-2PCTX nested MTX assumed to be part of 2PCTX.")
      (body-fn)) ;; ..by assuming we're already inside a DO-2PCTX BODY-FN.
    (with-local-vars [^Boolean done? false
                      retval nil]
      (while (not (var-get done?))
        (try
          (var-set retval (do-dbtx (fn [^Fn dbtx-prepare-fn ^Fn dbtx-commit-fn]
                                     (do-mtx body-fn dbtx-prepare-fn dbtx-commit-fn))))
          (var-set done? true)
          (catch clojure.lang.ExceptionInfo e
            (case (:2pctx-state (ex-data e))
              :retry
              (do #_(println "DO-2PCTX: :RETRY"))

              :abort
              (let [ex-retval (:return-value (ex-data e))]
                #_(println "DO-2PCTX: :ABORT, :RETURN-VALUE:" ex-retval)
                (var-set done? true)
                (var-set retval ex-retval))

              (throw e)))))
      (var-get retval))))



(defmacro with-2pctx [& body]
  `(do-2pctx (fn [] ~@body)))



(defmacro swsync [& body]
  "BODY executes within a 2PCTX; MTX and DBTX."
  `(if *in-swsync?*
     (do ~@body) ;; Handle nesting.
     (binding [*in-swsync?* true]
       (with-db-conn
         (with-2pctx
           ~@body)))))



(defn swsync-abort
  "Abort SWSYNC transaction in progress; rolls back all transactions in progress; MTX and DBTX."
  ([]
     (swsync-abort nil))

  ([retval]
     (abort-2pctx retval)))









;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;




(defn db-delete-prepared-transactions []
  (with-db-conn
    (with-jdbc-dbtx nil
      (doseq [res (db-stmt "SELECT gid FROM pg_prepared_xacts;")]
        (println "deleting prepared transaction:" (:gid res))
        (db-stmt "ROLLBACK PREPARED ?;" (:gid res))))) )
