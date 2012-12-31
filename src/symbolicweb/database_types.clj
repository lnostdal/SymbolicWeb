(in-ns 'symbolicweb.core)


(defprotocol IDBCache
  (set-internal-cache [db-cache new-cache])
  (get-internal-cache [db-cache]))



(deftype DBCache
    [^Fn db-clj-to-db-transformer-fn
     ^Fn db-db-to-clj-transformer-fn

     ^String table-name

     ;; Function called on cache miss to construct the initial skeleton for the data from the DB to fill up.
     ^Fn constructor-fn

     ;; Function called after CONSTRUCTOR-FN and after the DB data has been filled in for the object.
     ^Fn after-fn

     ;; http://docs.guava-libraries.googlecode.com/git-history/release/javadoc/com/google/common/cache/package-summary.html
     ^:unsynchronized-mutable internal-cache]


  IDBCache
  (set-internal-cache [_ new-cache]
    (set! internal-cache new-cache))

  (get-internal-cache [_]
    internal-cache))
