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
               (.setMaxIdleTime (* 3 60 60)))]
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
  (assert (not (:connection clojure.java.jdbc.internal/*db*))
          "WITH-SW-CONNECTION: Cannot be nested.")
  (try
    (with-connection @@-pooled-db-spec-
      (body-fn))
    (catch java.sql.SQLException e
      (if (= "40001" (. e getSQLState))
        (do
          (println "WITH-SW-CONNECTION: Serialization conflict; retrying!")
          (%with-sw-connection body-fn))
        (do
          (print-sql-exception-chain e)
          (throw e))))))


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
    (binding [clojure.java.jdbc.internal/*db* (update-in clojure.java.jdbc.internal/*db* [:level] inc)
              *in-sw-db?* true]
      (let [id-str (str (generate-uid))
            conn (:connection clojure.java.jdbc.internal/*db*)
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
              (binding [clojure.java.jdbc.internal/*db* nil ;; Cancel out current low-level DB connection while we do this..
                        *pending-prepared-transaction?* true] ;; ..and make sure no further connections can be made here.
                (var-set result (holding-transaction (fn [return-value]
                                                       (var-set result return-value)
                                                       (throw (Exception. "%with-sw-db-abort")))))))
            (var-set commit-prepared-transaction? true)
            (var-get result)
            (catch Exception e
              (if (= "%with-sw-db-abort" (.getMessage e))
                (do
                  (println "WITH-SW-DB: Manual abort of both DB and Clojure transactions!")
                  (var-get result))
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
  (let [stmt (.createStatement (:connection clojure.java.jdbc.internal/*db*))]
    (.execute stmt sql-str)
    (.close stmt)))


(defn db-delete-prepared-transactions []
  (with-sw-connection
    (with-query-results res ["SELECT gid FROM pg_prepared_xacts;"]
      (doseq [res res]
        (println "deleting prepared transaction:" (:gid res))
        (db-stmt (str "ROLLBACK PREPARED '" (:gid res) "';"))))))


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



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Persistance stuff comes here.


(defrecord DBCache
    [db-handle-input-fn
     db-handle-output-fn
     agent
     ^String table-name
     constructor-fn ;; Function called on cache miss to construct the initial skeleton for the data from the DB to fill up.
     after-fn ;; Function called after CONSTRUCTOR-FN and after the DB data has been filled in for the object.
     ^ReferenceMap cache-data]) ;; http://commons.apache.org/collections/api/org/apache/commons/collections/ReferenceMap.html


(defn default-db-handle-input [db-cache object input-key input-value]
  "SW --> DB.
Swaps - with _ for INPUT-KEY and passes INPUT-VALUE through as is."
  (when input-key
    [(keyword (str/replace (name input-key) #"-" "_"))
     input-value]))

(defn db-handle-input [db-cache object input-key input-value]
  "SW --> DB.
Returns two values in form of a vector [TRANSLATED-INPUT-KEY TRANSLATED-INPUT-VALUE] or returns NIL if the field in question,
represented by INPUT-KEY, is not to be stored in the DB."
  (if-let [f (. db-cache db-handle-input-fn)]
    (f db-cache object input-key input-value)
    (default-db-handle-input object input-key input-value)))

(defn default-db-handle-output [db-cache object output-key output-value]
  "DB --> SW.
Swaps _ with - for OUTPUT-KEY and passes OUTPUT-VALUE through as is."
  (when output-key
    [(keyword (str/replace (name output-key) #"_" "-"))
     output-value]))

(defn db-handle-output [db-cache object output-key output-value]
  "DB --> SW.
Returns two values in form of a vector [TRANSLATED-OUTPUT-KEY TRANSLATED-OUTPUT-VALUE] or returns NIL if the field in question,
represented by OUTPUT-KEY, is not to be fetched from the DB."
  (if-let [f (. db-cache db-handle-output-fn)]
    (f db-cache object output-key output-value)
    (default-db-handle-output db-cache object output-key output-value)))


(defn db-ensure-persistent-field [db-cache object ^Long id ^clojure.lang.Keyword key ^ValueModel value-model]
  "SQL `UPDATE ...'.
Setup reactive SQL UPDATEs for VALUE-MODEL."
  (observe value-model nil false
           (fn [old-value new-value]
             (when-not (= old-value new-value) ;; TODO: Needed?
               (let [[input-key input-value] (db-handle-input db-cache object key new-value)] ;; is even sent to callbacks.
                 (when input-key
                   (swdbop
                    (update-values (. db-cache table-name) ["id=?" id]
                                   {(as-quoted-identifier \" input-key) input-value}))))))))


(defn db-backend-get [db-cache ^Long id ^clojure.lang.Ref obj]
  "SQL SELECT. This will mutate fields in OBJ or add missing fields to OBJ.
Returns OBJ, or NIL if no entry with id ID was found in (:table-name DB-CACHE).
This does not add the item to the cache."
  (if (not *in-sw-db?*)
    (with-sw-db (fn [_] (db-backend-get db-cache id obj)))
    (with-query-results res [(str "SELECT * FROM " (as-quoted-identifier \" (. db-cache table-name))
                                  " WHERE id = ? LIMIT 1;") id]
      (when-let [res (first res)]
        (dosync
         (let [obj-m (ensure obj)]
           (doseq [key_val res]
             (let [[output-key output-value] (db-handle-output db-cache obj (key key_val) (val key_val))]
               (when output-key
                 (if-let [output-vm (output-key obj-m)] ;; Does field OUTPUT-KEY already exist in OBJ?
                   (do
                     (vm-set output-vm output-value) ;; If so, mutate it.
                     (db-ensure-persistent-field db-cache obj (:id res) output-key output-vm))
                   (let [vm-output-value (vm output-value)]
                     (ref-set obj (assoc (ensure obj) output-key vm-output-value)) ;; If not, add it.
                     (db-ensure-persistent-field db-cache obj (:id res) output-key vm-output-value))))))))
        obj))))


(declare db-cache-put)
(defn db-backend-put
  "SQL INSERT of OBJ whos keys and values are translated via DB-HANDLE-INPUT. This will also add OBJ to DB-CACHE unless
UPDATE-CACHE? is given a FALSE value."
  ([obj db-cache] (db-backend-put obj db-cache true))
  ([obj db-cache update-cache?]
     (with-sw-db
       (fn [holding-transaction]
         (let [record-data
               (dosync
                (assert (or (not (:id (ensure obj)))
                            (not @(:id (ensure obj)))))
                (with-local-vars [record-data {}]
                  (doseq [key_val (ensure obj)]
                    (when (isa? (class (val key_val)) ValueModel)
                      (let [[input-key input-value] (db-handle-input db-cache obj (key key_val) @(val key_val))]
                        (when input-key
                          (var-set record-data (assoc (var-get record-data)
                                                 input-key input-value))))))
                  (var-get record-data)))
               res (insert-record (. db-cache table-name) record-data)] ;; SQL INSERT.
           (when update-cache?
             (db-cache-put db-cache (:id res) obj))
           (holding-transaction
            (fn [_]
              (swsync
               (let [obj-m (ensure obj)]
                 (doseq [key_val res]
                   (let [[output-key output-value] (db-handle-output db-cache obj (key key_val) (val key_val))]
                     (when output-key
                       ;; Add or update fields in OBJ where needed based on result of SQL INSERT operation.
                       (if (= ::not-found (get obj-m output-key ::not-found))
                         (let [vm-output-value (vm output-value)]
                           (ref-set obj (assoc (ensure obj) output-key vm-output-value)) ;; Add.
                           (db-ensure-persistent-field db-cache obj (:id res)
                                                       output-key vm-output-value))
                         (do
                           (vm-set (output-key obj-m) output-value) ;; Update.
                           (db-ensure-persistent-field db-cache obj (:id res)
                                                       output-key (output-key obj-m))))))))
               ((. db-cache after-fn) obj))
              obj)))))))



(defn mk-db-cache [table-name constructor-fn after-fn db-handle-input-fn db-handle-output-fn]
  (DBCache.
   (if db-handle-input-fn db-handle-input-fn default-db-handle-input)
   (if db-handle-output-fn db-handle-output-fn default-db-handle-output)
   (agent :db-cache-agent)
   table-name
   constructor-fn
   after-fn
   (ReferenceMap. ReferenceMap/HARD ReferenceMap/SOFT)))

(defonce -db-cache-constructors- (atom {})) ;; table-name -> fn
(defonce -db-caches- ;; table-name -> ReferenceMap
  (ReferenceMap. ReferenceMap/HARD ReferenceMap/SOFT))

(defn db-get-cache [table-name]
  ;; A cache for TABLE-NAME must be found.
  {:post [(if % true (do (println "DB-GET-CACHE: No cache found for" table-name) false))]}
  (if-let [db-cache (. -db-caches- get table-name)]
    db-cache
    (locking -db-caches-
      (if-let [db-cache (. -db-caches- get table-name)]
        db-cache
        (when-let [db-cache (get @-db-cache-constructors- table-name)]
          (let [db-cache (db-cache)]
            (. -db-caches- put table-name db-cache)
            db-cache))))))

(defn db-reset-cache [table-name]
  (locking -db-caches-
    (. -db-caches- remove table-name)))

(defn reset-db-cache [table-name]
  (db-reset-cache table-name))


(defn db-cache-put [db-cache ^Long id obj]
  "Store association between ID and OBJ in DB-CACHE.
Fails (via assert) if an object with the same id already exists in DB-CACHE."
  (let [id (Long. id)] ;; Because (. (int 261) equals 261) => false
    (locking db-cache
      (let [cache-data (. db-cache cache-data)]
        (assert (not (. cache-data containsKey id)) "DB-CACHE-PUT: Ups. This shouldn't happen.")
        (. cache-data put id obj)))))


(defn db-cache-get [db-cache ^Long id after-construction-fn]
  "Get object based on ID from DB-CACHE or backend (via CONSTRUCTOR-FN in DB-CACHE).

Assuming DB-CACHE-GET is the only function used to fetch objects from the back-end (DB), this will do the needed locking to ensure
that only one object with id ID exists in the cache and the system at any point in time. It'll fetch from the DB using
:CONSTRUCTOR-FN from DB-CACHE."
  (io! "DB-CACHE-GET: This (I/O) cannot be done within DOSYNC or SWSYNC.")
  (let [id (Long. id)] ;; Because (. (int 261) equals 261) => false
    (if-let [cache-entry (. (. db-cache cache-data) get id)]
      cache-entry
      (if-let [cache-entry (locking db-cache (. (. db-cache cache-data) get id))] ;; Check cache again while within lock.
        cache-entry
        ;; We're OK with this stuff manipulating ValueModels within a WITH-SW-DB context;
        ;; see the implementation of VM-SET in model.clj.
        (binding [*in-db-cache-get?* true]
          (if-let [new-obj (db-backend-get db-cache id ((. db-cache constructor-fn) db-cache id))]
            ;; Check cache yet again while within lock; also possibly adding NEW-OBJ to it still within lock.
            (locking db-cache
              (if-let [cache-entry (. (. db-cache cache-data) get id)]
                cache-entry
                (with1 (dosync (after-construction-fn ((. db-cache after-fn) new-obj)))
                  (db-cache-put db-cache id new-obj))))
            nil))))))


(defn db-cache-remove [db-cache ^Long id]
  "Removes object based on ID from DB-CACHE."
  (let [id (Long. id)] ;; Because (. (Int. 261) equals 261) => false
    (locking db-cache
      (. (. db-cache cache-data)
         remove id))))


(defn db-put
  "SQL `INSERT ...'."
  ([object table-name]
     (db-put object table-name true))
  ([object table-name update-cache?]
     (db-backend-put object
                     (db-get-cache table-name)
                     update-cache?)))


(defn db-get
  "SQL `SELECT ...'.
CONSTRUCTION-FN is called with the resulting (returning) object as argument on cache miss."
  ([id table-name]
     (db-get id table-name (fn [obj] obj)))
  ([id table-name after-construction-fn]
     (db-cache-get (db-get-cache table-name) id after-construction-fn)))


;; TODO:
(defn db-remove [id table-name]
  "SQL `DELETE FROM ...'."
  #_(db-backend-remove id table-name))
