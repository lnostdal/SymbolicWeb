(in-ns 'symbolicweb.core)


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
               (.setMaxPoolSize 1))]
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


(defn %with-sw-connection [body-fn]
  (io! "WITH-SW-CONNECTION: Cannot be used directly while within Clojure transaction (DOSYNC or SWSYNC).")
  (assert (not *in-sw-db?*)
          "WITH-SW-CONNECTION: Nesting of WITH-SW-CONNECTION forms not allowed.")
  (assert (not *pending-prepared-transaction?*)
          "WITH-SW-CONNECTION: This is not meant to be used within WITH-SW-DB's HOLDING-TRANSACTION callback. Use SWSYNC and SWDBOP instead?")
  (assert (not (find-connection))
          "WITH-SW-CONNECTION: Cannot be nested.")
  (try
    (with-connection @@-pooled-db-spec-
      (body-fn))
    (catch java.sql.SQLException e
      (if (= "40001" (.getSQLState e))
        (do
          (println "WITH-SW-CONNECTION: Serialization conflict; retrying!")
          (%with-sw-connection body-fn))
        (do
          (clojure.java.jdbc/print-sql-exception-chain e)
          (throw e))))))


(defn abort-transaction [return-value]
  (throw (ex-info "WITH-SW-DB: ABORT-TRANSACTION"
                  {:with-sw-db :abort-transaction
                   :return-value return-value})))


(defn with-sw-db [body-fn]
  "BODY-FN is passed one argument; HOLDING-TRANSACTION (callback fn). BODY-FN is executed in a DB transaction which is fully
finalized after HOLDING-TRANSACTION has finished executing.
HOLDING-TRANSACTION is called when the transaction is pending finalization, and it takes one argument; ABORT-TRANSACTION (fn).
If either BODY-FN or HOLDING-TRANSACTION throws an exception, the transaction, in either pre-pending or pending state, is rolled
back.
Note that actually calling HOLDING-TRANSACTION is optional, and that further, direct i.e. non-Agent, DB operations within
HOLDING-TRANSACTION are not allowed.
If HOLDING-TRANSACTION is called, its return value will be the return value of WITH-SW-DB.
If HOLDING-TRANSACTION isn't called, the return value of BODY-FN will be the return value of WITH-SW-DB.
If ABORT-TRANSACTION is called, its argument will be the return value of WITH-SW-DB."
  (assert (fn? body-fn))
  (with-sw-connection
    ;; The BINDING here is sort of a hack to ensure that java.jdbc's UPDATE-VALUES etc. type functions doesn't create
    ;; inner transactions which will commit even though we'd want them to roll back here in WITH-SW-DB.
    (binding [clojure.java.jdbc/*db* (update-in @#'clojure.java.jdbc/*db* [:level] inc)
              *in-sw-db?* true]
      (let [id-str (str (generate-uid))
            conn (find-connection)
            stmt (.createStatement conn)]
        (with-local-vars [result nil
                          holding-transaction-fn nil
                          commit-inner-transaction? false
                          commit-prepared-transaction? false]
          (try
            (.setTransactionIsolation conn java.sql.Connection/TRANSACTION_SERIALIZABLE)
            (.setAutoCommit conn false) ;; Start transaction.
            (var-set result (body-fn (fn [holding-transaction]
                                       (assert (not (var-get holding-transaction-fn))
                                               "WITH-SW-DB: HOLDING-TRANSACTION callback should only be called once.")
                                       (assert (fn? holding-transaction))
                                       (var-set holding-transaction-fn holding-transaction))))
            (when (var-get holding-transaction-fn)
              (.execute stmt (str "PREPARE TRANSACTION '" id-str "';")))
            (.commit conn) (.setAutoCommit conn true) ;; Commit or prepare commit; "hold" the transaction.
            (var-set commit-inner-transaction? true)
            (when-let [holding-transaction (var-get holding-transaction-fn)]
              (binding [clojure.java.jdbc/*db* nil ;; Cancel out current low-level DB connection while we do this..
                        *pending-prepared-transaction?* true] ;; ..and make sure no further connections can be made here.
                (var-set result (holding-transaction (fn [return-value]
                                                       (throw (ex-info "WITH-SW-DB: ABORT-TRANSACTION"
                                                                       {:with-sw-db :abort-transaction
                                                                        :return-value return-value})))))))
            (var-set commit-prepared-transaction? true)
            (var-get result)
            (catch clojure.lang.ExceptionInfo e
              (if (= :abort-transaction (:with-sw-db (ex-data e)))
                (do
                  (println "WITH-SW-DB: Manual abort of both DB and Clojure transactions!")
                  (:return-value (ex-data e)))
                (throw e)))
            (finally
             (if (var-get commit-inner-transaction?)
               (if (var-get commit-prepared-transaction?)
                 (when (var-get holding-transaction-fn)
                   (.execute stmt (str "COMMIT PREPARED '" id-str "';")))
                 (.execute stmt (str "ROLLBACK PREPARED '" id-str "';")))
               (do (.rollback conn) (.setAutoCommit conn true)))
             (.close stmt))))))))


(defn db-stmt [sql-str]
  (let [stmt (.createStatement (find-connection))]
    (.execute stmt sql-str)
    (.close stmt)))












(defn db-delete-prepared-transactions []
  (with-sw-connection
    (with-query-results res ["SELECT gid FROM pg_prepared_xacts;"]
      (doseq [res res]
        (println "deleting prepared transaction:" (:gid res))
        (db-stmt (str "ROLLBACK PREPARED '" (:gid res) "';"))))))


#_(swap! -db-cache-constructors- assoc "test"
       #(mk-db-cache "test"
                     (fn [db-cache id] (ref {}))
                     (fn [obj] obj)
                     #'default-db-handle-input
                     #'default-db-handle-output))

;; To test out serialization conflict:
(defn test-serialization [do-after]
  (db-delete-prepared-transactions) ;; NOTE: While developing.
  (let [local (ref [])
        f (future
            (with-sw-db
              (fn [holding-transaction]
                (with-query-results res ["SELECT * FROM test WHERE id = ?;" 78]
                  (println "inner-transaction #1 before:" res)
                  (update-values :test ["id = ?" 78] {:value (inc (Integer/parseInt (:value (first res))))}))
                (with-query-results res ["SELECT * FROM test WHERE id = ?;" 78]
                  (println "inner-transaction #1 after:" res))

                (dosync (alter local conj "inner-transaction #1"))

                (when do-after
                  (holding-transaction (fn [tid]
                                       (println "holding-transaction #1: begin")
                                       (dosync (alter local conj "holding-transaction #1"))
                                       (Thread/sleep 1000)
                                       (println "holding-transaction #1: end")))))))]
    (with-sw-db
      (fn [holding-transaction]
        (with-query-results res ["SELECT * FROM test WHERE id = ?;" 78]
          (println "inner-transaction #2 before:" res)
          (update-values :test ["id = ?" 78] {:value (inc (Integer/parseInt (:value (first res))))}))
        (with-query-results res ["SELECT * FROM test WHERE id = ?;" 78]
          (println "inner-transaction #2 after:" res))
        (dosync (alter local conj "inner-transaction #2"))
        (when do-after
          (holding-transaction (fn [tid]
                               (dosync
                                (println "holding-transaction #2: begin")
                                (alter local conj "holding-transaction #2")
                                (Thread/sleep 500) ;; To make sure the first ts has gotten to its call to Thread/sleep.
                                (println "holding-transaction #2: end")))))))
    @f
    @local))
