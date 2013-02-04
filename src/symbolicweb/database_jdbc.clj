(in-ns 'symbolicweb.core)

;;; PostgreSQL specific "JDBC stuff".


(def ^:dynamic *db* nil)



(defn %with-jdbc-conn [db-spec ^Fn body-fn]
  (try
    (with-open [^java.sql.Connection db-conn (jdbc/get-connection db-spec)]
      (binding [*db* db-conn]
        (body-fn)))
    ;; TODO: Improve; consider using jdbc/throw-non-rte.
    (catch java.sql.SQLException e
      (jdbc/print-sql-exception-chain e)
      (throw e))))



(defmacro with-jdbc-conn [db-spec & body]
  `(%with-jdbc-conn ~db-spec (fn [] ~@body)))



(defn %with-jdbc-dbtx [^java.sql.Connection db-conn ^Fn on-conflict-fn ^Fn body-fn]
  (with-local-vars [done? false
                    retval nil]
    (while (not (var-get done?))
      (try

        (.setAutoCommit db-conn false)
        (let [the-retval (body-fn)]
          (.commit db-conn)
          (var-set done? true)
          (var-set retval the-retval))

        (catch java.sql.SQLException e
          (.rollback db-conn)
          (if (= "40001" (.getSQLState e)) ;; NOTE: http://www.postgresql.org/docs/current/static/transaction-iso.html
            (do
              (println "%WITH-JDBC-DBTX: Transaction conflict detected; handling it..")
              (when on-conflict-fn
                (on-conflict-fn)))
            (throw e)))
        (catch Throwable e
          (.rollback db-conn)
          (throw e))

        (finally
          (.setAutoCommit db-conn true))))
    (var-get retval)))



(defmacro with-jdbc-dbtx [on-conflict-fn & body]
  `(%with-jdbc-dbtx *db* ~on-conflict-fn (fn [] ~@body)))



(defn- jdbc-pstmt [^java.sql.Connection db-conn ^String sql params]
  "Create (or fetch from cache) and execute PreparedStatement."
  (with-open [stmt (.prepareStatement db-conn sql)] ;; TODO: I'm assuming the Pg JDBC driver caches based on SQL here.
    (#'jdbc/set-parameters stmt params)
    (if (.execute stmt)
      (with-open [rs (.getResultSet stmt)]
        ;; Being lazy is worse than pointless when "non-lazy design" (WITH-OPEN) is enforced from the underlying source anyway.
        ;; TODO: Could probably write an improved RESULTSET-SEQ because of this.
        (doall (resultset-seq rs)))
      (.getUpdateCount stmt))))



(defn- jdbc-stmt [^java.sql.Connection db-conn ^String sql]
  "Create and execute Statement."
  (with-open [stmt (.createStatement db-conn)]
    (if (.execute stmt sql)
      (with-open [rs (.getResultSet stmt)]
        ;; Being lazy is worse than pointless when "non-lazy design" (WITH-OPEN) is enforced from the underlying source anyway.
        ;; TODO: Could probably write an improved RESULTSET-SEQ because of this.
        (doall (resultset-seq rs)))
      (.getUpdateCount stmt))))
