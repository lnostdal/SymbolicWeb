(in-ns 'symbolicweb.core)


(defrecord DBCache
    [^clojure.lang.Fn db-handle-input-fn
     ^clojure.lang.Fn db-handle-output-fn
     ^clojure.lang.Agent agent
     ^String table-name
     ;; Function called on cache miss to construct the initial skeleton for the data from the DB to fill up.
     ^clojure.lang.Fn constructor-fn
     ;; Function called after CONSTRUCTOR-FN and after the DB data has been filled in for the object.
     ^clojure.lang.Fn after-fn
     ;; http://commons.apache.org/collections/api/org/apache/commons/collections/ReferenceMap.html
     ^ReferenceMap cache-data])


(defn default-db-handle-input [db-cache object input-key input-value]
  "SW --> DB.
Swaps - with _ for INPUT-KEY and passes INPUT-VALUE through as is."
  (when input-key
    [(keyword (str/replace (name input-key) \- \_))
     input-value]))

(defn db-handle-input [db-cache object input-key input-value]
  "SW --> DB.
Returns two values in form of a vector [TRANSLATED-INPUT-KEY TRANSLATED-INPUT-VALUE] or returns NIL if the field in question,
represented by INPUT-KEY, is not to be stored in the DB."
  (if-let [f (.db-handle-input-fn db-cache)]
    (f db-cache object input-key input-value)
    (default-db-handle-input object input-key input-value)))

(defn default-db-handle-output [db-cache object ^clojure.lang.Keyword output-key output-value]
  "DB --> SW.
Swaps _ with - for OUTPUT-KEY and passes OUTPUT-VALUE through as is."
  (when output-key
    [(keyword (str/replace (name output-key) \_ \-))
     output-value]))

(defn db-handle-output [db-cache object ^clojure.lang.Keyword output-key output-value]
  "DB --> SW.
Returns two values in form of a vector [TRANSLATED-OUTPUT-KEY TRANSLATED-OUTPUT-VALUE] or returns NIL if the field in question,
represented by OUTPUT-KEY, is not to be fetched from the DB."
  (if-let [f (.db-handle-output-fn db-cache)]
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
                   (swdbop value-model #(let [[input-key input-value] (db-handle-input db-cache object key @value-model)]
                                          [input-key input-value (.table-name db-cache) id]))))))))


(defn db-backend-get [db-cache ^Long id ^clojure.lang.Ref obj]
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
  (if-let [db-cache (.get -db-caches- table-name)]
    db-cache
    (locking -db-caches-
      (if-let [db-cache (.get -db-caches- table-name)]
        db-cache
        (when-let [db-cache (get @-db-cache-constructors- table-name)]
          (let [db-cache (db-cache)]
            (.put -db-caches- table-name db-cache)
            db-cache))))))

(defn db-reset-cache [table-name]
  (locking -db-caches-
    (.remove -db-caches- table-name)))

(defn reset-db-cache [table-name]
  (db-reset-cache table-name))


(defn db-cache-put [db-cache ^Long id obj]
  "Store association between ID and OBJ in DB-CACHE.
Fails (via assert) if an object with the same id already exists in DB-CACHE."
  (let [id (Long. id)] ;; Because (.equals (int 261) 261) => false
    (locking db-cache
      (let [cache-data (.cache-data db-cache)]
        (assert (not (.containsKey cache-data id)) "DB-CACHE-PUT: Ups. This shouldn't happen.")
        (.put cache-data id obj)))))


(defn db-cache-get [db-cache ^Long id after-construction-fn]
  "Get object based on ID from DB-CACHE or backend (via CONSTRUCTOR-FN in DB-CACHE).

Assuming DB-CACHE-GET is the only function used to fetch objects from the back-end (DB), this will do the needed locking to ensure
that only one object with id ID exists in the cache and the system at any point in time. It'll fetch from the DB using
:CONSTRUCTOR-FN from DB-CACHE."
  (io! "DB-CACHE-GET: This (I/O) cannot be done within DOSYNC or SWSYNC.")
  (let [id (Long. id)] ;; Because (.equals (int 261) 261) => false
    (if-let [cache-entry (.get (.cache-data db-cache) id)]
      cache-entry
      (if-let [cache-entry (locking db-cache (.get (.cache-data db-cache) id))] ;; Check cache again while within lock.
        cache-entry
        ;; We're OK with this stuff manipulating ValueModels within a WITH-SW-DB context;
        ;; see the implementation of VM-SET in model.clj.
        (binding [*in-db-cache-get?* true]
          (if-let [new-obj (db-backend-get db-cache id ((.constructor-fn db-cache) db-cache id))]
            ;; Check cache yet again while within lock; also possibly adding NEW-OBJ to it then fully initializing NEW-OBJ, still
            ;; within lock.
            (locking db-cache
              (if-let [cache-entry (.get (.cache-data db-cache) id)]
                cache-entry
                (with1 (dosync (after-construction-fn ((.after-fn db-cache) new-obj)))
                  (db-cache-put db-cache id new-obj))))
            nil))))))


(defn db-cache-remove [db-cache ^Long id]
  "Removes object based on ID from DB-CACHE."
  (let [id (Long. id)] ;; Because (. (Int. 261) equals 261) => false
    (locking db-cache
      (.remove (.cache-data db-cache)
               id))))


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
