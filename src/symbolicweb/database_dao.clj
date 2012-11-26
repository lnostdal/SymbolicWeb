(in-ns 'symbolicweb.core)


;;;; This is a "row-based" (via ID column) cache or layer for DB tables
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; This maps a DB row to a Clojure Ref holding a map where the values might be ValueModels or ContainerModels (SQL array refs).
;;
;; It further sets up reactive connections between the Clojure side ValueModels or ContainerModels and their associated
;; DB entries; meaning any change to the content of these objects will be synced to the DB.
;;
;;
;; * DB-PUT: SQL INSERT.
;; * DB-GET: SQL SELECT.
;; * DB-ENSURE-PERSISTENT-VM-FIELD: SQL UPDATE.
;; * DB-ENSURE-PERSISTENT-CM-FIELD: SQL UPDATE.   (SQL array)
;;
;;
;; TODO: ContainerModel based abstraction for SQL queries? This probably belongs in a different file; it's a different concept.
;; TODO: It should perhaps be possible to call DB-PUT many times on the same object, but only have it added to the DB once.
;;       I.e., it'll change the :ID field from NIL (only) to some integer in a synchronized fashion.



(defn db-db-array-to-clj-vector [^java.sql.Array db-array]
  (vec (.getArray db-array)))


(defn ^String db-clj-coll-to-db-array [clj-coll ^String db-type]
  (cl-format false "ARRAY[~{~S~^, ~}]::~A[]" clj-coll db-type))


(defn ^String db-clj-cm-to-db-array [^ContainerModel container-model ^String db-type]
  (with-local-vars [elts []]
    (cm-iterate container-model _ obj
      (var-alter elts conj @(:id @obj))
      false)
    (cl-format false "ARRAY[~{~S~^, ~}]::~A[]" (var-get elts) db-type)))


(defn ^Keyword db-default-db-to-clj-key-transformer [^Keyword k]
  ":some_field --> :some-field"
  (keyword (str/replace (name k) \_ \-)))


;; TODO: Consider returning a String instead?
(defn ^Keyword db-default-clj-to-db-key-transformer [^Keyword k]
  ":some-field --> :some_field"
  (keyword (str/replace (name k) \- \_)))



(defn db-default-handle-input [^DBCache db-cache ^Ref object ^Keyword clj-key clj-value]
  "SW --> DB."
  [(if (= :id clj-key)
     nil
     (db-default-clj-to-db-key-transformer clj-key))
   clj-value])



(defn db-handle-input [^DBCache db-cache ^Ref object ^Keyword clj-key clj-value]
  "SW --> DB.
Returns two values in form of a vector [TRANSLATED-INPUT-KEY TRANSLATED-INPUT-VALUE] or returns NIL if the field in question,
represented by INPUT-KEY, is not to be stored in the DB."
  (if-let [^Fn f (.db-handle-input-fn db-cache)]
    (f db-cache object clj-key clj-value)
    (db-default-handle-input object clj-key clj-value)))



(defn db-ensure-persistent-vm-field [^DBCache db-cache ^Ref object ^Long id ^Keyword clj-key
                                     ^ValueModel value-model]

  "Sets up reactive SQL UPDATEs for VALUE-MODEL."
  (vm-observe value-model nil false
              (fn [inner-lifetime old-value new-value]
                ;; NOTE: Passing VALUE-MODEL here instead of NEW-VALUE should be OK since we're within the same MTX.
                (let [[db-key db-value] (db-handle-input db-cache object clj-key value-model)]
                  (swdbop
                    (update-values (.table-name db-cache) ["id = ?" id]
                                   {(as-quoted-identifier \" db-key) db-value})))
                #_(when-not (= old-value new-value) ;; TODO: Needed?
                  ;; TODO: Why is DB-HANDLE-INPUT called twice here?
                  (let [[db-key db-value] (db-handle-input db-cache object clj-key value-model)]
                    (when db-key
                      (swdbop value-model #(let [[db-key db-value] (db-handle-input db-cache object clj-key value-model)]
                                             [db-key db-value (.table-name db-cache) id]))))))))



(defn db-ensure-persistent-cm-field [^DBCache db-cache ^Ref object ^Long id ^Keyword clj-key
                                     ^ContainerModel container-model]
  "Sets up reactive SQL UPDATEs for CONTAINER-MODEL."
  (observe (.observable container-model) nil
           (fn [inner-lifetime & args]
             (let [[db-key db-value] (db-handle-input db-cache object clj-key container-model)
                   db-value (db-clj-cm-to-db-array db-value "bigint")]
               (swdbop
                 (jdbc/do-prepared (str "UPDATE " (.table-name db-cache) " SET " (name db-key) " = " db-value
                                        " WHERE id = ?;")
                                   [id]))))))



(defn ^Ref db-value-to-vm-handler [^DBCache db-cache db-row ^Ref object ^Keyword clj-key clj-value]
  "Updates ValueModel associated with CLJ-KEY in OBJECT to CLJ-VALUE, or adds a new ValueModel to OBJECT if no entry was found.
Returns OBJECT."
  ;; TODO: I think DB-ENSURE-PERSISTENT-VM-FIELD can be called for the same VM twice in some cases...
  (dosync
   (if-let [^ValueModel existing-vm (clj-key (ensure object))] ;; Does field already exist in OBJECT?
     (do ;; Yes; mutate it.
       (vm-set existing-vm clj-value)
       (db-ensure-persistent-vm-field db-cache object (:id db-row) clj-key existing-vm))
     (let [^ValueModel new-vm (vm clj-value)] ;; No; add it.
       (ref-set object (assoc (ensure object) clj-key new-vm))
       (db-ensure-persistent-vm-field db-cache object (:id db-row) clj-key new-vm)))
   object))



(declare db-get)
(defn ^Ref db-value-to-cm-handler [^DBCache db-cache db-row ^Ref object ^Keyword clj-key ^java.sql.Array clj-value]
  (let [^clojure.lang.PersistentVector clj-value (db-db-array-to-clj-vector clj-value)]
    (dosync
     (if-let [^ContainerModel existing-cm (clj-key (ensure object))] ;; Does field already exist in OBJECT?
       (do ;; Yes; mutate it.
         ;; TOOD: Sync existing data with data from DBs somehow? Not sure how, so we just clear out the stuff on the Clj end.
         (cm-clear existing-cm)
         (doseq [^Long id clj-value]
           (cm-append existing-cm (cmn (db-get id (.table-name db-cache)))))
         (db-ensure-persistent-cm-field db-cache object (:id db-row) clj-key existing-cm))
       ;; TODO: DB-GET within DOSYNC won't do.
       (let [^ContainerModel new-cm (with1 (cmn) ;; No; add it.
                                      (doseq [^Long id clj-value]
                                        (cm-append it (cmn (db-get id (.table-name db-cache))))))]
         (db-ensure-persistent-cm-field db-cache object (:id db-row) clj-key new-cm)))
     object)))



(defn db-default-handle-output [^DBCache db-cache db-row ^Ref object]
  "DB --> SW."
  (doseq [^MapEntry entry db-row]
    (let [^Keyword db-key (key entry)
          db-value (val entry)]
      (if (isa? (class db-value) java.sql.Array) ;; TODO: Also check if DB-KEY ends in "_refs" here?
        (db-value-to-cm-handler db-cache db-row object
                                (db-default-db-to-clj-key-transformer db-key)
                                db-value)
        (db-value-to-vm-handler db-cache db-row object
                                (db-default-db-to-clj-key-transformer db-key)
                                db-value)))))



(defn db-handle-output [^DBCache db-cache ^Ref object db-row]
  "DB --> SW."
  (if-let [^Fn f (.db-handle-output-fn db-cache)]
    (f db-cache db-row object)
    (dosync (db-default-handle-output db-cache db-row object))))



(defn db-backend-get [^DBCache db-cache ^Long id ^Ref obj]
  "SQL SELECT. This will mutate fields in OBJ or add missing fields to OBJ.
Returns OBJ, or NIL if no entry with ID was found in (:table-name DB-CACHE).
This does not add the item to the cache."
  (if (not *in-sw-db?*)
    (with-sw-db (fn [_] (db-backend-get db-cache id obj)))
    (with-query-results res [(str "SELECT * FROM " (as-quoted-identifier \" (.table-name db-cache))" WHERE id = ? LIMIT 1;")
                             id]
      (when-let [db-row (first res)]
        (db-handle-output db-cache obj db-row)
        obj))))



(declare db-cache-put)
(defn db-backend-put
  "SQL INSERT of OBJ whos keys and values are translated via DB-HANDLE-INPUT. This will also add OBJ to DB-CACHE unless
UPDATE-CACHE? is given a FALSE value.
Blocking.
Returns OBJ when object was added or logical False when object was not added; e.g. if it has been added before."
  ([^Ref obj ^DBCache db-cache] (db-backend-put obj db-cache true))
  ([^Ref obj ^DBCache db-cache ^Boolean update-cache?]
     (with-sw-db
       (fn [^Fn holding-transaction]
         (let [[abort-transaction? sql values-to-escape]
               ;; Grab snapshot of all data and use it to generate SQL statement.
               (dosync
                (if (and (:id (ensure obj))
                         @(:id (ensure obj)))
                  [true] ;; ABORT-TRANSACTION?
                  (with-local-vars [record-data {}
                                    values-to-escape []]
                    (doseq [^MapEntry key_val (ensure obj)]
                      ;; TODO: Is this test needed? Why can't DB-HANDLE-INPUT do it?
                      (when (or (= ValueModel (class (val key_val)))
                                (= ContainerModel (class (val key_val))))
                        (let [[db-key db-value] (db-handle-input db-cache obj (key key_val) (val key_val))]
                          (when db-key
                            (var-alter record-data assoc db-key db-value)))))
                    [false ;; ABORT-TRANSACTION? ...
                     (cl-format false "INSERT INTO ~A (~{~A~^, ~}) VALUES (~{~A~^, ~});"
                                (.table-name db-cache)
                                (mapv name (keys (var-get record-data)))
                                (mapv (fn [v]
                                        (cond
                                          (isa? (class v) ValueModel)
                                          (do1 "?"
                                            (var-alter values-to-escape conj @v))

                                          (isa? (class v) ContainerModel)
                                          (dosync (db-clj-cm-to-db-array v "bigint"))

                                          true
                                          (do1 "?"
                                            (var-alter values-to-escape conj v))))
                                      (vals (var-get record-data))))
                     (var-get values-to-escape)])))]
           (when-not abort-transaction?
             (let [res (jdbc/do-prepared-return-keys sql values-to-escape)]
               ;; NOTE: Object added to cache before fields (added below) are there yet? I guess this is ok since only the one
               ;; currently adding the object (this code) will "know about it" until HOLDING-TRANSACTION returns.
               #_(when update-cache?
                 (db-cache-put db-cache (:id res) obj))
               (holding-transaction
                (fn [^Fn abort-transaction]
                  (dosync
                   (if (and (:id (ensure obj))
                            @(:id (ensure obj)))
                     (abort-transaction false)
                     (when update-cache?
                       (db-cache-put db-cache (:id res) obj)))
                   ;; Add or update fields in OBJ where needed based on result of SQL INSERT operation.
                   ;; TODO: No need to trigger updates via observers here.
                   (db-handle-output db-cache obj res)
                   ;; Initialize object further; perhaps add external observers of the objects fields etc..
                   ((.after-fn db-cache) obj)
                   obj))))))))))



(defn ^DBCache mk-DBCache [^String table-name
                           ^Fn constructor-fn
                           ^Fn after-fn
                           db-handle-input-fn
                           db-handle-output-fn]
  (let [^DBCache db-cache (DBCache.
                           (if db-handle-input-fn db-handle-input-fn db-default-handle-input)
                           (if db-handle-output-fn db-handle-output-fn db-default-handle-output)
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
