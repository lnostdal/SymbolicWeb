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


(defn %with-sw-db [body-fn]
  ;; TODO: Perhaps the TRY/CATCH block here should be within the transaction?
  ;; TODO: Find a way (ORLY...) to also print out the SQL query on error.
  (try
    (with-connection -pooled-db-spec-
      (.setTransactionIsolation (:connection clojure.java.jdbc.internal/*db*)
                                java.sql.Connection/TRANSACTION_SERIALIZABLE)
      (transaction
       (body-fn)))
    (catch java.sql.SQLException exception
      (print-sql-exception-chain exception))))


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


(defn default-db-handle-input [^DBCache db-cache input-key input-value]
  "SW --> DB.
Swaps - with _ for INPUT-KEY and passes INPUT-VALUE through as is."
  (when input-key
    [(keyword (str/replace (name input-key) #"-" "_"))
     input-value]))

(defn db-handle-input [^DBCache db-cache ^clojure.lang.Keyword input-key input-value]
  "SW --> DB.
Returns two values in form of a vector [TRANSLATED-INPUT-KEY TRANSLATED-INPUT-VALUE] or returns NIL if the field in question,
represented by INPUT-KEY, is not to be stored in the DB."
  (if-let [f (. db-cache db-handle-input-fn)]
    (f db-cache input-key input-value)
    (default-db-handle-input input-key input-value)))

(defn default-db-handle-output [^DBCache db-cache output-key output-value]
  "DB --> SW.
Swaps _ with - for OUTPUT-KEY and passes OUTPUT-VALUE through as is."
  (when output-key
    [(keyword (str/replace (name output-key) #"_" "-"))
     output-value]))

(defn db-handle-output [^DBCache db-cache ^clojure.lang.Keyword output-key output-value]
  "DB --> SW.
Returns two values in form of a vector [TRANSLATED-OUTPUT-KEY TRANSLATED-OUTPUT-VALUE] or returns NIL if the field in question,
represented by OUTPUT-KEY, is not to be fetched from the DB."
  (if-let [f (. db-cache db-handle-output-fn)]
    (f db-cache output-key output-value)
    (default-db-handle-output output-key output-value)))


(defn db-ensure-persistent-field [^DBCache db-cache ^Long id ^clojure.lang.Keyword key ^ValueModel value-model]
  "Setup reactive SQL UPDATEs for VALUE-MODEL."
  (mk-view value-model nil
           (fn [value-model old-value new-value]
             (when-not (= old-value new-value) ;; TODO: This should probably be generalized and handled before the notification
               (let [[input-key input-value] (db-handle-input db-cache key new-value)] ;; is even sent to callbacks.
                 (when input-key
                   (send-off (. db-cache agent)
                             (fn [_]
                               (with-errors-logged
                                 (with-sw-db
                                   (update-values (. db-cache table-name) ["id=?" id]
                                                  {(as-quoted-identifier \" input-key) input-value})))))))))
           :trigger-initial-update? false))


(defn db-backend-get [^DBCache db-cache ^Long id ^clojure.lang.Ref obj]
  "SQL SELECT. This will mutate fields in OBJ or add missing fields to OBJ.
Returns OBJ, or NIL if no entry with id ID was found in (:table-name DB-CACHE)."
  (with-query-results res [(str "SELECT * FROM " (as-quoted-identifier \" (. db-cache table-name)) " WHERE id = ?;") id]
    (when-let [res (first res)]
      (dosync
       (doseq [key_val res]
         (let [[output-key output-value] (db-handle-output db-cache (key key_val) (val key_val))]
           (when output-key
             (if (output-key (ensure obj))
               (do
                 (vm-set (output-key (ensure obj)) output-value)
                 (db-ensure-persistent-field db-cache (:id res) output-key (output-key (ensure obj))))
               (let [vm-output-value (vm output-value)]
                 (ref-set obj (assoc (ensure obj)
                                output-key vm-output-value))
                 (db-ensure-persistent-field db-cache (:id res) output-key vm-output-value))))))
       obj))))


(declare db-cache-put)
(defn db-backend-put
  "SQL INSERT of OBJECT whos keys and values are translated via DB-HANDLE-INPUT. This will also add OBJECT to DB-CACHE after the
:ID field has been set -- which might happen some time after this function has returned."
  ([object ^DBCache db-cache cont-fn] (db-backend-put object db-cache cont-fn true))
  ([object ^DBCache db-cache cont-fn update-cache?]
     (assert (or (not (:id (ensure object)))
                 (not @(:id (ensure object)))))
     (with-local-vars [record-data {}]
       (doseq [key_val (ensure object)]
         (when (isa? (type (val key_val)) ValueModel) ;; TODO: Possible magic check. TODO: Foreign keys; ContainerModel.
           (let [[input-key input-value] (db-handle-input db-cache (key key_val) @(val key_val))]
             (when input-key
               (var-set record-data (assoc (var-get record-data)
                                      input-key input-value))))))
       (let [record-data (var-get record-data)]
         (send-off (. db-cache agent)
                   (fn [_]
                     (with-errors-logged
                       (let [res (with-sw-db (insert-record (. db-cache table-name) record-data))] ;; SQL INSERT.
                         (when update-cache?
                           (db-cache-put db-cache (:id res) object)) ;; We have our object ID; add object to cache.
                         ;; Update and/or add fields in OBJECT where needed based on result of SQL INSERT operation.
                         (dosync
                          (doseq [key_val res]
                            (let [[output-key output-value] (db-handle-output db-cache (key key_val) (val key_val))]
                              (when output-key
                                (if (= ::not-found (get (ensure object) output-key ::not-found))
                                  (let [vm-output-value (vm output-value)]
                                    (ref-set object (assoc (ensure object)
                                                      output-key vm-output-value))
                                    (db-ensure-persistent-field db-cache (:id res) output-key vm-output-value))
                                  (do
                                    (vm-set (output-key (ensure object)) output-value)
                                    (db-ensure-persistent-field db-cache (:id res) output-key (output-key (ensure object))))))))))
                       (cont-fn object))))))))


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

(defn get-db-cache [table-name]
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


(defn db-cache-put [^DBCache db-cache ^Long id obj]
  "If ID is NIL, store OBJ in DB then store association between the resulting id and OBJ in DB-CACHE.
If ID is non-NIL, store association between ID and OBJ in DB-CACHE.
Fails (via assert) if an object with the same id already exists in DB-CACHE."
  (locking db-cache
    (let [cache-data (. db-cache cache-data)]
      (assert (not (. cache-data containsKey id)) "DB-CACHE-PUT: Ups. This shouldn't happen.")
      (. cache-data put id obj))))


(defn db-cache-get [^DBCache db-cache ^Long id cont-fn]
  "Get object based on ID from DB-CACHE or backend (via CONSTRUCTOR-FN in DB-CACHE). Passes two arguments to CONT-FN:
  OBJ :HIT  -- Cache hit.
  OBJ :MISS -- Cache miss, but object found in DB.
  NIL NIL   -- Cache miss, and object not found in DB.

Returns a prototype of the object on cache miss -- to be filled in at a later time. If no object with id ID exists in the cache or
at the back-end, the prototype object (a REF) will be set to :DB-NOT-FOUND.

Assuming DB-CACHE-GET is the only function used to fetch objects from the back-end (DB), this will do the needed locking to ensure
that only one object with id ID exists in the cache and the system at any point in time. It'll fetch from the DB using
:CONSTRUCTOR-FN from DB-CACHE."
  (if-let [cache-entry (. (. db-cache cache-data) get id)]
    (do
      (cont-fn cache-entry :hit)
      [cache-entry :hit])
    (locking db-cache
      (if-let [cache-entry (. (. db-cache cache-data) get id)] ;; Check cache again while within lock.
        (do
          (cont-fn cache-entry :hit)
          [cache-entry :hit])
        (let [new-obj-prototype ((. db-cache constructor-fn) db-cache id)]
          (db-cache-put db-cache id new-obj-prototype)
          (send-off (. db-cache agent)
                    (fn [_]
                      (with-errors-logged
                        (apply cont-fn
                               (with-sw-db
                                 (if-let [new-obj (db-backend-get db-cache id new-obj-prototype)]
                                   [new-obj :miss]
                                   (do
                                     (dosync (ref-set new-obj-prototype :db-not-found))
                                     [nil nil])))))))
          [new-obj-prototype :pending])))))


(defn db-cache-remove [^DBCache db-cache ^Long id]
  "Removes object based on ID from DB-CACHE."
  (locking db-cache
    (. (. db-cache cache-data)
       remove id)))


(defn db-put
  ([object table-name cont-fn] (db-put object table-name cont-fn true))
  ([object table-name cont-fn update-cache?]
     (db-backend-put object (get-db-cache table-name) cont-fn update-cache?)))


(defn db-get [id table-name cont-fn]
  (db-cache-get (get-db-cache table-name) id cont-fn))


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
