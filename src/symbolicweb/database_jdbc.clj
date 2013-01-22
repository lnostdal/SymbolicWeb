(in-ns 'symbolicweb.core)


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



(defn jdbc-prepare-statement ^java.sql.PreparedStatement [^java.sql.Connection db-conn ^String sql ^Boolean return-keys?]
  (if return-keys?
    (.prepareStatement db-conn sql java.sql.Statement/RETURN_GENERATED_KEYS)
    (.prepareStatement db-conn sql)))



(defn jdbc-command [^java.sql.Connection db-conn ^String sql params]
  (with-open [stmt (jdbc-prepare-statement db-conn sql true)]
    (#'jdbc/set-parameters stmt params)
    (let [count (.executeUpdate stmt)]
      (with-open [rs (.getGeneratedKeys stmt)]
        (first (resultset-seq rs))))))



(defn jdbc-query [^java.sql.Connection db-conn ^String sql params]
  (with-open [stmt (jdbc-prepare-statement db-conn sql false)]
    (#'jdbc/set-parameters stmt params)
    (with-open [rs (.executeQuery stmt)]
      ;; Being lazy is worse than pointless when "non-lazy" design (WITH-OPEN) is enforced on the underlying source anyway.
      ;; TODO: Could probably write an improved RESULTSET-SEQ because of this.
      (doall (resultset-seq rs)))))



(defn db-query [^String sql & params]
  (jdbc-query *db* sql params))



(defn db-command [args]
  (let [^String sql (first args)
        params (rest args)]
    (jdbc-command *db* sql params)))



(defn db-insert [table-name m]
  (db-command (first (sql/insert table-name m))))



(defn db-update [table-name m where]
  (db-command (sql/update table-name m where)))



(defn db-delete [table-name where]
  (db-command (sql/delete table-name where)))
