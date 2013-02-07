(in-ns 'symbolicweb.core)

;;; PostgreSQL specific "JDBC stuff".


(def ^:dynamic *db* nil)



(defn mk-db-pool ^com.jolbox.bonecp.BoneCP [spec]
  (let [config (com.jolbox.bonecp.BoneCPDataSource.)]
    (.setDriverClass config (:classname spec)) ;; Would work with BoneCPDataSource class (subclass of BoneCPConfig).
    (.setJdbcUrl config (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
    (.setUsername config (:user spec))
    (.setPassword config (:password spec))
    (.setDefaultAutoCommit config true)
    ;;(.setDefaultTransactionIsolation config "SERIALIZABLE")
    (com.jolbox.bonecp.BoneCP. config)))



(defonce -pooled-db-spec- nil)
#_(mk-db-pool (let [db-host  "localhost"
                    db-port  5432
                    db-name  "temp"
                    user     "temp"
                    password "temp"]
                {:classname "org.postgresql.Driver"
                 :subprotocol "postgresql"
                 :subname (str "//" db-host ":" db-port "/" db-name)
                 :user user
                 :password password}))



(defn %with-jdbc-conn [^com.jolbox.bonecp.BoneCP db-spec ^Fn on-serialization-failure-fn ^Fn body-fn]
  (if *db*
    (do
      (println "WARNING: Nesting of WITH-JDBC-CONN forms might cause unintended behavior.")
      (body-fn))
    (let [done? (atom false)
          retval (atom nil)]
      (while (not @done?)
        (let [db-conn (delay (with1 (.getConnection db-spec)
                               (.setTransactionIsolation it java.sql.Connection/TRANSACTION_SERIALIZABLE) ;; TODO: Magic value.
                               (.setAutoCommit ^com.jolbox.bonecp.ConnectionHandle it false)))] ;; Begin.
          (try
            (binding [*db* db-conn]
              (reset! retval (body-fn))
              (when (and (realized? db-conn)
                         (not (.getAutoCommit ^com.jolbox.bonecp.ConnectionHandle @db-conn)))
                (.commit ^com.jolbox.bonecp.ConnectionHandle @db-conn)) ;; Commit.
              (reset! done? true))

            (catch Throwable e
              (when (and (realized? db-conn)
                         (not (.getAutoCommit ^com.jolbox.bonecp.ConnectionHandle @db-conn)))
                (.rollback ^com.jolbox.bonecp.ConnectionHandle @db-conn)) ;; Rollback.
              (if (isa? (class e) java.sql.SQLException)
                (if (= "40001" (.getSQLState ^java.sql.SQLException e))
                  (when on-serialization-failure-fn
                    (on-serialization-failure-fn))
                  (do
                    (jdbc/print-sql-exception-chain e) ;; TODO: Improve; consider using jdbc/throw-non-rte.
                    (throw e)))
                (throw e)))

            (finally
              (when (.isRealized db-conn)
                (.setAutoCommit ^com.jolbox.bonecp.ConnectionHandle @db-conn true) ;; End after Commit or Rollback.
                (.close ^com.jolbox.bonecp.ConnectionHandle @db-conn))))))
      @retval)))

(defmacro with-jdbc-conn [db-spec on-serialization-failure-fn & body]
  "Runs BODY within a DBTX.
  ON-SERIALIZATION-FN: Fn or NIL. When NIL the transaction will restart silently on conflict."
  `(%with-jdbc-conn ~db-spec ~on-serialization-failure-fn (fn [] ~@body)))



(defn- jdbc-pstmt [^com.jolbox.bonecp.ConnectionHandle db-conn ^String sql params]
  "Create (or fetch from cache) and execute PreparedStatement."
  (with-open [stmt (.prepareStatement db-conn sql)] ;; TODO: I'm assuming the Pg JDBC driver caches based on SQL here.
    (#'jdbc/set-parameters stmt params)
    (if (.execute stmt)
      (with-open [rs (.getResultSet stmt)]
        ;; Being lazy is worse than pointless when "non-lazy design" (WITH-OPEN) is enforced from the underlying source anyway.
        ;; TODO: Could probably write an improved RESULTSET-SEQ because of this.
        (doall (resultset-seq rs)))
      (.getUpdateCount stmt))))



(defn- jdbc-stmt [^com.jolbox.bonecp.ConnectionHandle db-conn ^String sql]
  "Create and execute Statement."
  (with-open [stmt (.createStatement db-conn)]
    (if (.execute stmt sql)
      (with-open [rs (.getResultSet stmt)]
        ;; Being lazy is worse than pointless when "non-lazy design" (WITH-OPEN) is enforced from the underlying source anyway.
        ;; TODO: Could probably write an improved RESULTSET-SEQ because of this.
        (doall (resultset-seq rs)))
      (.getUpdateCount stmt))))
