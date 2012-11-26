(in-ns 'symbolicweb.core)


;;;; This is a "row-based" (via ID column) cache or layer for DB tables.


(defprotocol IDBCache
  (set-internal-cache [db-cache new-cache])
  (get-internal-cache [db-cache]))


(deftype DBCache
    [^clojure.lang.Fn db-handle-input-fn
     ^clojure.lang.Fn db-handle-output-fn
     ^String table-name
     ;; Function called on cache miss to construct the initial skeleton for the data from the DB to fill up.
     ^clojure.lang.Fn constructor-fn
     ;; Function called after CONSTRUCTOR-FN and after the DB data has been filled in for the object.
     ^clojure.lang.Fn after-fn
     ;; http://docs.guava-libraries.googlecode.com/git-history/release/javadoc/com/google/common/cache/package-summary.html
     ^:unsynchronized-mutable internal-cache]


  IDBCache
  (set-internal-cache [_ new-cache]
    (set! internal-cache new-cache))

  (get-internal-cache [_]
    internal-cache))



(defn default-db-handle-input [^DBCache db-cache object input-key input-value]
  "SW --> DB.
Swaps - with _ for INPUT-KEY and passes INPUT-VALUE through as is."
  (when input-key
    [(keyword (str/replace (name input-key) \- \_))
     input-value]))



(defn db-handle-input [^DBCache db-cache object input-key input-value]
  "SW --> DB.
Returns two values in form of a vector [TRANSLATED-INPUT-KEY TRANSLATED-INPUT-VALUE] or returns NIL if the field in question,
represented by INPUT-KEY, is not to be stored in the DB."
  (if-let [f (.db-handle-input-fn db-cache)]
    (f db-cache object input-key input-value)
    (default-db-handle-input object input-key input-value)))



(defn default-db-handle-output [^DBCache db-cache object ^clojure.lang.Keyword output-key output-value]
  "DB --> SW.
Swaps _ with - for OUTPUT-KEY and passes OUTPUT-VALUE through as is."
  (when output-key
    [(keyword (str/replace (name output-key) \_ \-))
     output-value]))



(defn db-handle-output [^DBCache db-cache object ^clojure.lang.Keyword output-key output-value]
  "DB --> SW.
Returns two values in form of a vector [TRANSLATED-OUTPUT-KEY TRANSLATED-OUTPUT-VALUE] or returns NIL if the field in question,
represented by OUTPUT-KEY, is not to be fetched from the DB."
  (if-let [f (.db-handle-output-fn db-cache)]
    (f db-cache object output-key output-value)
    (default-db-handle-output db-cache object output-key output-value)))



(defn db-ensure-persistent-field [^DBCache db-cache object ^Long id ^clojure.lang.Keyword key ^ValueModel value-model]
  "SQL `UPDATE ...'.
Setup reactive SQL UPDATEs for VALUE-MODEL."
  (vm-observe value-model nil false
              (fn [lifetime old-value new-value]
                (when-not (= old-value new-value) ;; TODO: Needed?
                  (let [[input-key input-value] (db-handle-input db-cache object key new-value)] ;; is even sent to callbacks.
                    (when input-key
                      (swdbop value-model #(let [[input-key input-value] (db-handle-input db-cache object key @value-model)]
                                             [input-key input-value (.table-name db-cache) id]))))))))



(defn db-backend-get [^DBCache db-cache ^Long id ^clojure.lang.Ref obj]
  "SQL SELECT. This will mutate fields in OBJ or add missing fields to OBJ.
Returns OBJ, or NIL if no entry with id ID was found in (:table-name DB-CACHE).
This does not add the item to the cache."
  (if (not *in-sw-db?*)
    (with-sw-db (fn [_] (db-backend-get db-cache id obj)))
    (with-query-results res [(str "SELECT * FROM " (as-quoted-identifier \" (.table-name db-cache))" WHERE id = ? LIMIT 1;")
                             id]
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
  ([obj ^DBCache db-cache] (db-backend-put obj db-cache true))
  ([obj ^DBCache db-cache ^Boolean update-cache?]
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
               res (insert-record (.table-name db-cache) record-data)] ;; SQL INSERT.
           ;; NOTE: Object added to cache before fields (added below) are there yet? I guess this is ok since only the one
           ;; currently adding the object will "know about it" yet.
           (when update-cache?
             (db-cache-put db-cache (:id res) obj))
           (holding-transaction
            (fn [_]
              ;; Add or update fields in OBJ where needed based on result of SQL INSERT operation.
              (dosync
               (let [obj-m (ensure obj)]
                 (doseq [key_val res]
                   (let [[output-key output-value] (db-handle-output db-cache obj (key key_val) (val key_val))]
                     (when output-key
                       (if (= ::not-found (get obj-m output-key ::not-found))
                         (let [vm-output-value (vm output-value)]
                           (ref-set obj (assoc (ensure obj) output-key vm-output-value)) ;; Add.
                           (db-ensure-persistent-field db-cache obj (:id res)
                                                       output-key vm-output-value))
                         (do
                           (vm-set (output-key obj-m) output-value) ;; Update.
                           (db-ensure-persistent-field db-cache obj (:id res)
                                                       output-key (output-key obj-m)))))))))
              ;; Initialize object further; perhaps add external observers of the objects fields etc..
              (dosync
               ((.after-fn db-cache) obj))
              obj)))))))



(defn ^DBCache mk-DBCache [^String table-name
                           ^clojure.lang.Fn constructor-fn
                           ^clojure.lang.Fn after-fn
                           db-handle-input-fn
                           db-handle-output-fn]
  (let [^DBCache db-cache (DBCache.
                           (if db-handle-input-fn db-handle-input-fn default-db-handle-input)
                           (if db-handle-output-fn db-handle-output-fn default-db-handle-output)
                           table-name
                           constructor-fn
                           after-fn
                           nil)]
    (set-internal-cache db-cache
                        (-> (CacheBuilder/newBuilder)
                            (.softValues)
                            (.concurrencyLevel (.availableProcessors (Runtime/getRuntime))) ;; TODO: Configurable?
                            (.build)))
    db-cache))



;; TODO: All this seems to suck a bit too much.
(defonce -db-cache-constructors- (atom {})) ;; table-name -> fn
(defonce -db-caches- ;; table-name -> DBCache
  (-> (CacheBuilder/newBuilder)
      (.concurrencyLevel (.availableProcessors (Runtime/getRuntime))) ;; TODO: Configurable?
      (.build (proxy [CacheLoader] []
                (load [table-name]
                  ((get @-db-cache-constructors- table-name)))))))



(defn ^DBCache db-get-cache [^String table-name]
  (.get ^com.google.common.cache.LocalCache$LocalLoadingCache -db-caches- table-name))



(defn ^DBCache db-reset-cache [^String table-name]
  (.invalidate -db-caches- table-name))



(defn db-cache-put [^DBCache db-cache ^Long id ^Ref obj]
  "Store association between ID and OBJ in DB-CACHE.
If ID already exists, the entry will be overwritten."
  (let [id (long id)] ;; Because (.equals (int 261) 261) => false
    (.put (get-internal-cache db-cache) id obj)))



(defn db-cache-remove [^DBCache db-cache ^Long id]
  "Removes object based on ID from DB-CACHE."
  (let [id (long id)] ;; Because (. (Int. 261) equals 261) => false
    (.invalidate (get-internal-cache db-cache) id)))



(defn db-put
  "SQL `INSERT ...'.
Blocking."
  ([^Ref object ^String table-name]
     (db-put object table-name true))

  ([^Ref object ^String table-name ^Boolean update-cache?]
     (db-backend-put object (db-get-cache table-name) update-cache?)))



(defn db-get
  "SQL `SELECT ...'.
CONSTRUCTION-FN is called with the resulting (returning) object as argument on cache miss."
  ([^Long id ^String table-name]
     (db-get id table-name identity))


  ([^Long id ^String table-name ^Fn after-construction-fn]
     (io! "DB-CACHE-GET: This (I/O) cannot be done within DOSYNC or SWSYNC.")
     (let [id (long id) ;; Because (.equals (int 261) 261) => false
           ^DBCache db-cache (db-get-cache table-name)]
       (try
         (.get ^com.google.common.cache.LocalCache$LocalLoadingCache (get-internal-cache db-cache) id
               (fn []
                 ;; Binding this makes VMs settable within the currently active WITH-SW-DB context.
                 ;; See the implementation of VM-SET in value_model.clj.
                 (binding [*in-db-cache-get?* true]
                   (let [new-obj (db-backend-get db-cache id ((.constructor-fn db-cache) db-cache id))]
                     (dosync (after-construction-fn ((.after-fn db-cache) new-obj)))))))
         (catch com.google.common.cache.CacheLoader$InvalidCacheLoadException e
           (println "DB-CACHE-GET: Object with ID" id "not found in" (.table-name db-cache))
           nil)))))



;; TODO:
(defn db-remove [^Long id ^String table-name]
  "SQL `DELETE FROM ...'."
  #_(db-backend-remove id table-name))
