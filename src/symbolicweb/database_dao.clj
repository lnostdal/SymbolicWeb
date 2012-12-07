(in-ns 'symbolicweb.core)


;;;; This is a "row-based" (via ID column) cache or layer for DB tables; it is quite horrible
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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



(defn db-db-array-to-clj-vector [^java.sql.Array db-array]
  "DB --> SW."
  (vec (.getArray db-array)))


(defn ^String db-clj-coll-to-db-array [clj-coll ^String db-type]
  "SW --> DB."
  (cl-format false "ARRAY[~{~S~^, ~}]::~A[]" clj-coll db-type))


(defn ^String db-clj-cm-to-db-array [^ContainerModel container-model ^String db-type]
  "SW --> DB."
  (with-local-vars [elts []]
    (cm-iterate container-model _ obj
      (var-alter elts conj (with1 @(:id @obj)
                             (assert it "DB-CLJ-CM-TO-DB-ARRAY: Object not in DB yet? It has no :ID.")))
      false)
    (cl-format false "ARRAY[~{~S~^, ~}]::~A[]" (var-get elts) db-type)))


(defn ^Keyword db-default-db-to-clj-key-transformer [^Keyword k]
  "DB --> SW.
:some_field --> :some-field"
  (keyword (str/replace (name k) \_ \-)))


;; TODO: Consider returning a String instead?
(defn ^Keyword db-default-clj-to-db-key-transformer [^Keyword k]
  "SW --> DB.
:some-field --> :some_field"
  (keyword (str/replace (name k) \- \_)))



(defn db-default-handle-input [^DBCache db-cache ^Ref object ^Keyword clj-key clj-value]
  "SW --> DB."
  [(if (or (not clj-key) (= :id clj-key))
     nil
     (db-default-clj-to-db-key-transformer clj-key))
   (cond
     (isa? (class clj-value) ValueModel)
     @clj-value

     true
     clj-value)])



(defn db-handle-input [^DBCache db-cache ^Ref object ^Keyword clj-key clj-value]
  "SW --> DB.
Returns two values in form of a vector [TRANSLATED-INPUT-KEY TRANSLATED-INPUT-VALUE] or returns NIL if the field in question,
represented by INPUT-KEY, is not to be stored in the DB."
  (if-let [^Fn f (.db-handle-input-fn db-cache)]
    (f db-cache object clj-key clj-value)
    (db-default-handle-input object clj-key clj-value)))



(defn db-ensure-persistent-vm-field [^DBCache db-cache ^Ref object ^Keyword clj-key
                                     ^ValueModel value-model]

  "Sets up reactive SQL UPDATEs for VALUE-MODEL."
  (vm-observe value-model nil false
              (fn [inner-lifetime old-value new-value]
                (swdbop :update
                  ;; TODO: Two DOSYNCs in a row here sucks.
                  (let [[db-key db-value] (dosync (db-handle-input db-cache object clj-key value-model))]
                    (update-values (.table-name db-cache) ["id = ?" (dosync @(:id @object))]
                                   {(as-quoted-identifier \" db-key) db-value}))))))



(declare db-put)
(declare db-remove)
(defn db-ensure-persistent-cm-field [^DBCache db-cache ^Ref object ^Keyword clj-key
                                     ^ContainerModel container-model]
  "Sets up reactive SQL UPDATEs for CONTAINER-MODEL."
  (observe (.observable container-model) nil
           (fn [inner-lifetime args]
             (let [[event-sym & event-args] args
                   ;; TODO: OP-TYPE stuff here can be simplified.
                   [op-type op-object]
                   (case event-sym
                     cm-prepend
                     [:add (cmn-data (nth event-args 0))]

                     (cmn-after cmn-before)
                     [:add (cmn-data (nth event-args 1))]

                     cmn-remove
                     [:remove (cmn-data (nth event-args 0))])
                   db-table-name (:db-table-name @op-object)]
               (case op-type
                 :add (db-put op-object db-table-name)
                 :remove (db-remove @(:id @op-object) db-table-name))
               (swdbop :update
                 (let [[db-key db-value] (db-handle-input db-cache object clj-key container-model)
                       ;; TODO: Think about the DOSYNC here, and two DOSYNCs in a row sucks.
                       db-value (dosync (db-clj-cm-to-db-array db-value "bigint"))]
                   (jdbc/do-prepared (str "UPDATE " (.table-name db-cache) " SET " (name db-key) " = " db-value
                                          " WHERE id = ?;")
                                     [(dosync @(:id @object))])))))))



(defn ^Ref db-value-to-vm-handler [^DBCache db-cache db-row ^Ref object ^Keyword clj-key clj-value]
  "DB --> SW."
  ;; TODO: I think DB-ENSURE-PERSISTENT-VM-FIELD can be called for the same VM twice in some cases...
  (swhtop
    (if-let [^ValueModel existing-vm (clj-key (ensure object))] ;; Does field already exist in OBJECT?
      (do ;; Yes; mutate it.
        (vm-set existing-vm clj-value)
        (db-ensure-persistent-vm-field db-cache object clj-key existing-vm))
      (let [^ValueModel new-vm (vm clj-value)] ;; No; add it.
        (ref-set object (assoc (ensure object) clj-key new-vm))
        (db-ensure-persistent-vm-field db-cache object clj-key new-vm))))
  object)



(declare db-get)
(defn ^Ref db-value-to-cm-handler [^DBCache db-cache db-row ^Ref object ^Keyword clj-key ^java.sql.Array clj-value]
  "DB --> SW."
  (let [^clojure.lang.PersistentVector clj-value (db-db-array-to-clj-vector clj-value)]

    (if-let [^ContainerModel existing-cm (clj-key (ensure object))] ;; Does field already exist in OBJECT?
      (do ;; Yes; mutate it.
        ;; TOOD: Sync existing data with data from DBs somehow? Not sure how, so we just clear out the stuff on the Clj end.
        (swhtop
          (cm-clear existing-cm))
        (swdbop false
          (doseq [obj (mapv #(db-get % (.table-name db-cache)) clj-value)]
            (swhtop
              (cm-append existing-cm (cmn obj))))
          (swhtop
            (db-ensure-persistent-cm-field db-cache object clj-key existing-cm))))
      (let [^ContainerModel new-cm (with1 (cmn) ;; No; add it.
                                     (doseq [^Long id clj-value]
                                       ;; TODO: DB-GET within DOSYNC/SWSYNC won't do.
                                       (cm-append it (cmn (db-get id (.table-name db-cache))))))]
        (db-ensure-persistent-cm-field db-cache object clj-key new-cm)))
    object))



(defn db-default-handle-output [^DBCache db-cache db-row ^Ref object]
  "DB --> SW."
  (doseq [^MapEntry entry db-row]
    (let [^Keyword db-key (db-default-db-to-clj-key-transformer (key entry))
          db-value (val entry)]
      (if (isa? (class db-value) java.sql.Array)
        (db-value-to-cm-handler db-cache db-row object
                                db-key db-value)
        (db-value-to-vm-handler db-cache db-row object
                                db-key db-value)))))



(defn db-handle-output [^DBCache db-cache ^Ref object db-row]
  "DB --> SW."
  (if-let [^Fn f (.db-handle-output-fn db-cache)]
    (f db-cache db-row object)
    (db-default-handle-output db-cache db-row object)))




(defn db-backend-get [^DBCache db-cache ^Long id ^Ref obj]
  "SQL SELECT. This will mutate fields in OBJ or add missing fields to OBJ.
This does not add the item to the cache.

Non-blocking; instantly returns OBJ and later fills in its :ID field with either:
  * A number, meaning success.
  * A Throwable, meaning something went wrong.
  * :NOT-FOUND, meaning an object with ID was not found in the DB."
  (swdbop nil
    (try
      (with-query-results res [(str "SELECT * FROM " (as-quoted-identifier \" (.table-name db-cache))" WHERE id = ? LIMIT 1;")
                               id]
        (if-let [db-row (first res)]
          (db-handle-output db-cache obj db-row) ;; Not a SWHTOP since this might lead to further SWDBOPs.
          (swhtop
            (vm-set (:id @obj) :not-found))))
      (catch Throwable e
        ;; TODO: Think about this. What about ABORT-TRANSACTION?
        (dosync (vm-set (:id @obj) e))
        (throw e))))
  obj)



(declare db-cache-put)
(defn db-backend-put
  "SQL INSERT of OBJ whos keys and values are translated via DB-HANDLE-INPUT. This will also add OBJ to DB-CACHE unless
UPDATE-CACHE? is given a FALSE value.
Blocking.
Returns OBJ when object was added or logical False when object was not added; e.g. if it has been added before."
  ([^Ref obj ^DBCache db-cache] (db-backend-put obj db-cache true))
  ([^Ref obj ^DBCache db-cache ^Boolean update-cache?]
     (let [[abort-put? sql values-to-escape]
           ;; Grab snapshot of all data and use it to generate SQL statement.
           (if (and (:id (ensure obj))
                    @(:id (ensure obj)))
             [true] ;; ABORT-PUT?
             (with-local-vars [record-data {}
                               values-to-escape []]
               (doseq [^MapEntry key_val (ensure obj)]
                 ;; TODO: Is this test needed? Why can't DB-HANDLE-INPUT do it?
                 (when (or (= ValueModel (class (val key_val)))
                           (= ContainerModel (class (val key_val))))
                   (let [[db-key db-value] (db-handle-input db-cache obj (key key_val) (val key_val))]
                     (when db-key
                       (cond
                         (isa? (class (val key_val)) ValueModel)
                         (db-ensure-persistent-vm-field db-cache obj (key key_val) (val key_val))

                         (isa? (class (val key_val)) ContainerModel)
                         (db-ensure-persistent-cm-field db-cache obj (key key_val) (val key_val)))
                       (var-alter record-data assoc db-key db-value)))))
               [false ;; ABORT-PUT? SQL VALUES-TO-ESCAPE
                (cl-format false "INSERT INTO ~A (~{~A~^, ~}) VALUES (~{~A~^, ~});"
                           (.table-name db-cache)
                           (mapv name (keys (var-get record-data)))
                           (mapv (fn [v]
                                   (cond
                                     (isa? (class v) ValueModel)
                                     (do1 "?"
                                       (var-alter values-to-escape conj @v))

                                     (isa? (class v) ContainerModel)
                                     (db-clj-cm-to-db-array v "bigint")

                                     true
                                     (do1 "?"
                                       (var-alter values-to-escape conj v))))
                                 (vals (var-get record-data))))
                (var-get values-to-escape)]))]
       (when-not abort-put?
         (swdbop :insert
           (let [res (jdbc/do-prepared-return-keys sql values-to-escape)]
             (dosync
              (if (and (:id (ensure obj))
                       @(:id (ensure obj)))
                (abort-transaction false)
                (when update-cache?
                  (db-cache-put db-cache (:id res) obj)))
              (vm-set (:id @obj) (:id res)) ;; TODO: ..or add.
              ;; Initialize object further; perhaps add further (e.g. non-DB related) observers of the objects fields etc..
              ((.after-fn db-cache) obj)
              obj)))))))



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
  "SQL `SELECT ...'."
  ([^Long id ^String table-name]
     (db-get id table-name identity))


  ([^Long id ^String table-name ^Fn after-construction-fn]
     (let [id (long id) ;; Because (.equals (int 261) 261) => false
           ^DBCache db-cache (db-get-cache table-name)]
       (try
         (.get ^com.google.common.cache.LocalCache$LocalLoadingCache (get-internal-cache db-cache) id
               (fn []
                 (let [new-obj (db-backend-get db-cache id ((.constructor-fn db-cache) db-cache id))]
                   (vm-observe (:id @new-obj) nil true
                               (fn [_ old-id new-id]
                                 (cond
                                   (not new-id)
                                   (when-not (= :symbolicweb.core/initial-update old-id)
                                     (db-cache-remove db-cache id)
                                     (assert false))

                                   (number? new-id)
                                   (when-not (not old-id)
                                     (db-cache-remove db-cache id)
                                     (assert false))

                                   (or (= :not-found)
                                       (isa? (class new-id) Throwable))
                                   (db-cache-remove db-cache id))))
                   (swop
                     (when (number? @(:id @new-obj))
                       (after-construction-fn ((.after-fn db-cache) new-obj))))
                   new-obj)))
         ;; TODO: This seems wrong..
         (catch com.google.common.cache.CacheLoader$InvalidCacheLoadException e
           (println "DB-GET: Object with ID" id "not found in" (.table-name db-cache))
           nil)))))



;; TODO:
(defn db-remove [^Long id ^String table-name]
  "SQL `DELETE FROM ...'."
  (assert false "DB-REMOVE: TODO!")
  #_(db-backend-remove id table-name))
