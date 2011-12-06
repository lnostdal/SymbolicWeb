(in-ns 'symbolicweb.core)


(let [db-host  "localhost"
      db-port  5432
      db-name  "temp"
      user     "temp"
      password "temp"]
  (def -db-spec- {:classname "org.postgresql.Driver"
                  :subprotocol "postgresql"
                  :subname (str "//" db-host ":" db-port "/" db-name)
                  :user user
                  :password password}))

(defn mk-db-pool [spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname spec))
               (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
               (.setUser (:user spec))
               (.setPassword (:password spec))
               ;; expire excess connections after 30 minutes of inactivity:
               (.setMaxIdleTimeExcessConnections (* 30 60))
               ;; expire connections after 3 hours of inactivity:
               (.setMaxIdleTime (* 3 60 60)))]
    {:datasource cpds}))

(defonce -pooled-db-spec- (mk-db-pool -db-spec-))


(defn %with-sw-db [body-fn]
  (with-connection -pooled-db-spec-
    (.setTransactionIsolation (:connection clojure.java.jdbc.internal/*db*)
                              java.sql.Connection/TRANSACTION_SERIALIZABLE)
    (transaction
     (try
       (body-fn)
       (catch java.lang.Exception exception
         (letfn ([do-it [e]
                  (if (= java.sql.BatchUpdateException (type e))
                    (do
                      (println e)
                      (println (. e (getNextException))))
                    (if e
                      (do-it (. e (getCause)))
                      (throw exception)))])
           (do-it exception)))))))

(defmacro with-sw-db [& body]
  `(%with-sw-db (fn [] ~@body)))
