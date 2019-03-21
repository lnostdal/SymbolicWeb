(in-ns 'symbolicweb.core)

;;; PostgreSQL specific "JDBC stuff".


(def ^:dynamic *db* nil)



(defn mk-db-pool ^com.zaxxer.hikari.HikariDataSource [spec]
  (with (com.zaxxer.hikari.HikariConfig.)
    (.setMaximumPoolSize it (* 2 (. (Runtime/getRuntime) availableProcessors)))
    (.setDataSourceClassName it "com.impossibl.postgres.jdbc.PGDataSource")
    (.addDataSourceProperty it "host" "127.0.0.1")
    (.addDataSourceProperty it "database" (:database spec))
    (.addDataSourceProperty it "user" (:user spec))
    (.addDataSourceProperty it "password" (:password spec))

    (.setTransactionIsolation it "TRANSACTION_SERIALIZABLE")
    (.setAutoCommit it false)

    (.setLeakDetectionThreshold it 5)

    (com.zaxxer.hikari.HikariDataSource. it)))


(defonce -pooled-db-spec- nil)
(defn start-database []
  (when -pooled-db-spec- (.close ^com.zaxxer.hikari.HikariDataSource -pooled-db-spec-))
  (def -pooled-db-spec-
    (mk-db-pool
     {:database "test"
      :user "test"
      :password "test"})))



(defn %with-jdbc-conn [^com.zaxxer.hikari.HikariDataSource db-spec ^Fn on-serialization-failure-fn ^Fn body-fn]
  (let [done? (atom false)
        retval (atom nil)]
    (while (not @done?)
      (let [db-conn (delay (.getConnection db-spec))]
        (try
          (binding [*db* db-conn]
            (reset! retval (body-fn))
            (reset! done? true))

          (catch Throwable e
            (if (isa? (class e) java.sql.SQLException)
              (if (= "40001" (.getSQLState ^java.sql.SQLException e))
                (when on-serialization-failure-fn
                  (on-serialization-failure-fn))
                (do
                  (println "\n%WITH-JDBC-CONN: getSQLState:" (.getSQLState ^java.sql.SQLException e))
                  (throw e)))
              (throw e)))

          (finally
            (when (.isRealized db-conn)
              (.close ^com.zaxxer.hikari.pool.HikariProxyConnection @db-conn))))))
    @retval))

(defmacro with-jdbc-conn "Runs BODY within a DBTX.
  ON-SERIALIZATION-FN: Fn or NIL. When NIL the transaction will restart silently on conflict."
  [db-spec on-serialization-failure-fn & body]
  `(%with-jdbc-conn ~db-spec ~on-serialization-failure-fn (fn [] ~@body)))



(defn- jdbc-pstmt "Create (or fetch from cache) and execute PreparedStatement."
  [^com.zaxxer.hikari.pool.HikariProxyConnection db-conn ^String sql params]
  (try
    (with-open [stmt (.prepareStatement db-conn sql)]
      (dorun (map-indexed (fn [^long ix value]
                            (.setObject stmt (inc ix) value))
                          params))
      (if (.execute stmt)
        (with-open [rs (.getResultSet stmt)]
          ;; Being lazy is worse than pointless when "non-lazy design" (WITH-OPEN) is enforced from the underlying source anyway.
          ;; TODO: Could probably write an improved RESULTSET-SEQ because of this.
          (doall (resultset-seq rs)))
        (.getUpdateCount stmt)))
    (catch Throwable e
      (when (and (isa? (class e) java.sql.SQLException)
                 (not= "40001" (.getSQLState ^java.sql.SQLException e)))
        (println "\nJDBC-PSTMT\nSQL:" sql "\nPARAMS:" params)
        (println "getSQLState:" (.getSQLState ^java.sql.SQLException e)))
      (throw e))))



(defn- jdbc-stmt "Create and execute Statement."
  [^com.zaxxer.hikari.pool.HikariProxyConnection db-conn ^String sql]
  (try
    (with-open [stmt (.createStatement db-conn)]
      (if (.execute stmt sql)
        (with-open [rs (.getResultSet stmt)]
          ;; Being lazy is worse than pointless when "non-lazy design" (WITH-OPEN) is enforced from the underlying source anyway.
          ;; TODO: Could probably write an improved RESULTSET-SEQ because of this.
          (doall (resultset-seq rs)))
        (.getUpdateCount stmt)))
    (catch Throwable e
      (when (and (isa? (class e) java.sql.SQLException)
                 (not= "40001" (.getSQLState ^java.sql.SQLException e)))
        (println "\nJDBC-STMT\nSQL:" sql)
        (println "getSQLState:" (.getSQLState ^java.sql.SQLException e)))
      (throw e))))
