(in-ns 'symbolicweb.core)

;;;; Builds on mk-DBQuery (database_query.clj) and maps the results there to DAOs (database_dao.clj).



(defn mk-DAOQuery [^Ref db-query & args]
  "  DB-QUERY: Return value of mk-DBQuery."
  (ref (apply assoc {}
              :db-query db-query
              args)))



(defn dao-query-transform-db-chunk [^Ref dao-query db-chunk]
  "Converts a DB-CHUNK to a DAO-CHUNK."
  (let [table-name (:query-table-name @(:db-query @dao-query))]
    (mapv #(db-get (:id %) table-name)
          db-chunk)))



(defn dao-query-next-chunk [^Ref dao-query]
  (let [db-chunk (db-query-next-chunk (:db-query @dao-query))]
    (dao-query-transform-db-chunk dao-query db-chunk)))



(defn dao-query-prev-chunk [^Ref dao-query]
  (let [db-chunk (db-query-prev-chunk (:db-query @dao-query))]
    (dao-query-transform-db-chunk dao-query db-chunk)))



;;;;;


(defn testing-model-clj-to-db-transformer [m]
  (db-default-clj-to-db-transformer m))



(defn testing-model-db-to-clj-transformer [m]
  (db-default-db-to-clj-transformer m))



(defn mk-TestingModel [& args]
  (ref (apply assoc {}
              :id (vm nil)
              args)))



(swap! -db-cache-constructors- assoc "testing"
       #(mk-DBCache "testing"
                    (fn [db-cache id] (mk-TestingModel))
                    identity
                    #'testing-model-clj-to-db-transformer
                    #'testing-model-db-to-clj-transformer))
(db-reset-cache "testing")



(defn dao-query-test []
  (swsync
   (let [daoq (mk-DAOQuery (mk-DBQuery))]
     (dbg-prin1 (dao-query-next-chunk daoq))
     (dbg-prin1 (dao-query-next-chunk daoq)))))
