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
               ;; Expire excess connections after 30 minutes of inactivity:
               (.setMaxIdleTimeExcessConnections (* 30 60))
               ;; Expire connections after 3 hours of inactivity:
               (.setMaxIdleTime (* 3 60 60)))]
    {:datasource cpds}))


(defonce -pooled-db-spec- (mk-db-pool -db-spec-))


(def ^:dynamic *with-sw-db-context* [])

(defn finalize-db-transaction [context]
  (dosync
   (doseq [cnt @context]
     (cnt))))

(defn %with-sw-db [body-fn]
  ;; TODO: Find a way (ORLY...) to also print out the SQL query on error. This is why software sucks; APIs designed by fucking
  ;; retards.
  ;; TODO: On transaction conflict (check exception type); retry automatically (..I think..).
  (try
    (with-connection -pooled-db-spec-
      (.setTransactionIsolation (:connection clojure.java.jdbc.internal/*db*)
                                java.sql.Connection/TRANSACTION_SERIALIZABLE)
      (transaction
       (body-fn)))
    (catch java.sql.SQLException e
      (if (= "40001" (. (. e getNextException) ;; Transaction conflict?
                        getSQLState))
        (do
          (println "%WITH-SW-DB: Serialization conflict; retrying!")
          (%with-sw-db body-fn))
        (do
          (clojure.stacktrace/print-stack-trace e)
          (print-sql-exception-chain e))))))


;; To test out serialization conflict:
#_(defn test-serialization []
  (let [local (ref [])]
    (do
      (dosync
       (with-sw-io []
         (with-sw-db
           (println "updating to 2")
           (update-values :test ["id = ?" 78] {:value 2})
           (Thread/sleep 250)))
       (alter local conj 2)))
    (do
      (with-sw-db
        (println "updating to 1")
        (update-values :test ["id = ?" 78] {:value 1}))
      (dosync (alter local conj 1)))
    (Thread/sleep 1000)
    @local))


(defmacro with-sw-db [& body]
  `(%with-sw-db (fn [] ~@body)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Persistance stuff comes here.


(defrecord DBCache
    [db-handle-input-fn
     db-handle-output-fn
     agent
     ^String table-name
     constructor-fn ;; Function called on cache miss to construct the initial skeleton for the data from the DB to fill up.
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
  "Setup reactive SQL UPDATEs for VALUE-MODEL.
The DB updates are done in the background; asynchronously. This should be safe because the Clojure STM should have resolved
any conflicts before we get to this point."
  (mk-view value-model nil
           (fn [value-model old-value new-value]
             (when-not (= old-value new-value) ;; TODO: This should probably be generalized and handled before the notification
               (let [[input-key input-value] (db-handle-input db-cache object key new-value)] ;; is even sent to callbacks.
                 (when input-key
                   (send-off (. db-cache agent)
                             (fn [_]
                               (with-errors-logged
                                 (with-sw-db
                                   (update-values (. db-cache table-name) ["id=?" id]
                                                  {(as-quoted-identifier \" input-key) input-value})))))))))
           :trigger-initial-update? false))


(defn db-backend-get [db-cache ^Long id ^clojure.lang.Ref obj]
  "SQL SELECT. This will mutate fields in OBJ or add missing fields to OBJ.
Returns OBJ, or NIL if no entry with id ID was found in (:table-name DB-CACHE)."
  (with-query-results res [(str "SELECT * FROM " (as-quoted-identifier \" (. db-cache table-name)) " WHERE id = ?;") id]
    (when-let [res (first res)]
      (dosync
       (let [obj-m (ensure obj)]
         (doseq [key_val res]
           (let [[output-key output-value] (db-handle-output db-cache obj (key key_val) (val key_val))]
             (when output-key
               (if (output-key obj-m)
                 (do
                   (vm-set (output-key obj-m) output-value)
                   (db-ensure-persistent-field db-cache obj (:id res) output-key (output-key obj-m)))
                 (let [vm-output-value (vm output-value)]
                   (ref-set obj (assoc (ensure obj) output-key vm-output-value))
                   (db-ensure-persistent-field db-cache obj (:id res) output-key vm-output-value)))))))
       obj))))


(declare db-cache-put)
(defn db-backend-put
  "SQL INSERT of OBJ whos keys and values are translated via DB-HANDLE-INPUT. This will also add OBJ to DB-CACHE unless
UPDATE-CACHE? is given a FALSE value."
  ([obj db-cache] (db-backend-put obj db-cache true))
  ([obj db-cache update-cache?]
     (let [record-data
           (dosync
            (assert (or (not (dosync (:id (ensure obj))))
                        (not (dosync @(:id (ensure obj))))))
            (with-local-vars [record-data {}]
              (doseq [key_val (ensure obj)]
                (when (isa? (type (val key_val)) ValueModel) ;; TODO: Possible magic check. TODO: Foreign keys; ContainerModel.
                  (let [[input-key input-value] (db-handle-input db-cache obj (key key_val) @(val key_val))]
                    (when input-key
                      (var-set record-data (assoc (var-get record-data)
                                             input-key input-value))))))
              (var-get record-data)))
           res (with-sw-db (insert-record (. db-cache table-name) record-data))] ;; SQL INSERT.
       (when update-cache?
         (db-cache-put db-cache (:id res) obj))
       (dosync
        (let [obj-m (ensure obj)]
          (doseq [key_val res]
            (let [[output-key output-value] (db-handle-output db-cache obj (key key_val) (val key_val))]
              (when output-key
                ;; Update and add fields in OBJ where needed based on result of SQL INSERT operation.
                (if (= ::not-found (get obj-m output-key ::not-found))
                  (let [vm-output-value (vm output-value)]
                    (ref-set obj (assoc (ensure obj) output-key vm-output-value)) ;; Add.
                    (db-ensure-persistent-field db-cache obj (:id res)
                                                output-key vm-output-value))
                  (do
                    (vm-set (output-key obj-m) output-value) ;; Update.
                    (db-ensure-persistent-field db-cache obj (:id res)
                                                output-key (output-key obj-m))))))))))
     obj))



(defn mk-db-cache [table-name constructor-fn db-handle-input-fn db-handle-output-fn]
  (DBCache.
   (if db-handle-input-fn db-handle-input-fn default-db-handle-input)
   (if db-handle-output-fn db-handle-output-fn default-db-handle-output)
   (agent :db-cache-agent)
   table-name
   constructor-fn
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
  "If ID is NIL, store OBJ in DB then store association between the resulting id and OBJ in DB-CACHE.
If ID is non-NIL, store association between ID and OBJ in DB-CACHE.
Fails (via assert) if an object with the same id already exists in DB-CACHE."
  (let [id (Long. id)] ;; Because (. (int 261) equals 261) => false
    (locking db-cache
      (let [cache-data (. db-cache cache-data)]
        (assert (not (. cache-data containsKey id)) "DB-CACHE-PUT: Ups. This shouldn't happen.")
        (. cache-data put id obj)))))


(defn db-cache-get [db-cache ^Long id]
  "Get object based on ID from DB-CACHE or backend (via CONSTRUCTOR-FN in DB-CACHE).

Assuming DB-CACHE-GET is the only function used to fetch objects from the back-end (DB), this will do the needed locking to ensure
that only one object with id ID exists in the cache and the system at any point in time. It'll fetch from the DB using
:CONSTRUCTOR-FN from DB-CACHE."
  (let [id (Long. id)] ;; Because (. (int 261) equals 261) => false
    (if-let [cache-entry (. (. db-cache cache-data) get id)]
      cache-entry
      (if-let [cache-entry (locking db-cache (. (. db-cache cache-data) get id))] ;; Check cache again while within lock.
        cache-entry
        (if-let [new-obj (with-sw-db (db-backend-get db-cache id ((. db-cache constructor-fn) db-cache id)))]
          ;; Check cache yet again while within lock; also possibly adding NEW-OBJ to it still within lock.
          (locking db-cache
            (if-let [cache-entry (. (. db-cache cache-data) get id)]
              cache-entry
              (do
                (db-cache-put db-cache id new-obj)
                new-obj)))
          nil)))))


(defn db-cache-remove [db-cache ^Long id]
  "Removes object based on ID from DB-CACHE."
  (let [id (Long. id)] ;; Because (. (Int. 261) equals 261) => false
    (locking db-cache
      (. (. db-cache cache-data)
         remove id))))

(defn db-put
  "Async; non-blocking. Result is passod to CONT-FN."
  ([object table-name]
     (db-put object table-name true))
  ([object table-name update-cache?]
     (db-backend-put object
                     (db-get-cache table-name)
                     update-cache?)))


(defn db-get [id table-name]
  "Sync; blocking."
  (db-cache-get (db-get-cache table-name) id))



;; TODO:
(defn db-remove [id table-name]
  #_(db-backend-remove id table-name))



;;;;;;;;;;;;;;;;;;;;;;
;; Some quick tests...


(defn test-cache-perf [num-iterations object-id]
  (def -db-cache- (mk-db-cache "test"
                               (fn [db-cache id]
                                 (ref {:value (vm "default")}))
                               nil
                               nil))
  (let [first-done? (promise)]
    (db-cache-get -db-cache- object-id
                  (fn [obj cache-state]
                    (dbg-prin1 [:db-cache-get-cb obj cache-state])
                    (deliver first-done? :yup)))
    (deref first-done?)
    (println "Cache is now hot; request object with ID" object-id "from it" num-iterations "times and print total time taken..")
    (time
     (dotimes [i num-iterations]
       (db-cache-get -db-cache- object-id
                     (fn [obj cache-state]
                       (dbg-prin1 [obj cache-state])))))))


(defn test-cache-insert []
  (def -db-cache- (mk-db-cache "test"
                               (fn [db-cache id]
                                 (println "hum")
                                 (ref {:value (vm "default value")}))
                               nil
                               nil))
  (let [new-obj (ref {:value (vm "non-random initial value")})]
    ;; SQL INSERT.
    (dosync
     (db-backend-put new-obj -db-cache- (fn [new-obj]
                                          (dosync (dbg-prin1 @new-obj)))))
    (Thread/sleep 1000)
    (dosync
     (dbg-prin1 @new-obj)
     (db-cache-get -db-cache- @(:id @new-obj)
                   (fn [obj cache-state]
                     (dbg-prin1 [obj cache-state]))))
    ;; SQL UPDATE.
    (dosync
     (vm-set (:value @new-obj) (str "rand-int: " (rand-int 9999)))
     (dbg-prin1 @(:value @new-obj)))))
