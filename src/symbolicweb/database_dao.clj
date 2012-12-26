(in-ns 'symbolicweb.core)

;;;; This is a "row-based" (via ID column) reactive cache or DAO layer for DB tables
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

;; NOTE: :ID fields should only be read within SWDBOPs. This means a small DOSYNC is needed for this.


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
                             (assert (integer? it)
                                     (str "DB-CLJ-CM-TO-DB-ARRAY: Object not in DB yet? :ID is not an integer: " it))))

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



(defn db-db-to-clj-key-transformer [^DBCache db-cache ^Ref object k]
  "DB --> SW."
  (if-let [^Fn f (.db-db-to-clj-key-transformer-fn db-cache)]
    (f db-cache object k)
    (db-default-db-to-clj-key-transformer k)))


(defn db-clj-to-db-key-transformer [^DBCache db-cache ^Ref object k]
  "SW --> DB."
  (if-let [^Fn f (.db-clj-to-db-key-transformer-fn db-cache)]
    (f db-cache object k)
    (db-default-clj-to-db-key-transformer k)))



(defn db-default-handle-input [^DBCache db-cache ^Ref object ^Keyword clj-key clj-value]
  "SW --> DB."
  [(if (or (not clj-key)
           (= :id clj-key))
     nil
     (db-clj-to-db-key-transformer db-cache object clj-key))

   (cond
     (isa? (class clj-value) ValueModel)
     @clj-value

     ;; NOTE: DB-PUT'ing objects with CM fields not possible if we do this here; it's too early with regards to :ID fields.
     ;; (isa? (class clj-value) ContainerModel)
     ;; (db-clj-cm-to-db-array clj-value "bigint") ;; TODO: Magic value "bigint".

     true
     clj-value)])



(defn db-handle-input [^DBCache db-cache ^Ref object ^Keyword clj-key clj-value]
  "SW --> DB.
Returns two values in form of a vector [TRANSLATED-INPUT-KEY TRANSLATED-INPUT-VALUE] or returns NIL if the field in question,
represented by INPUT-KEY, is not to be stored in the DB."
  (if-let [^Fn f (.db-handle-input-fn db-cache)]
    (f db-cache object clj-key clj-value)
    (db-default-handle-input object clj-key clj-value)))



(defn db-ensure-persistent-vm-field [^DBCache db-cache ^Ref object ^Keyword clj-key ^ValueModel value-model]
  "Sets up reactive SQL UPDATEs for VALUE-MODEL."
  (vm-observe value-model nil false
              (fn [inner-lifetime old-value new-value]
                (let [[db-key db-value] (db-handle-input db-cache object clj-key value-model)]
                  (swdbop :update
                    ;; The :ID field is extracted here, inside SWDBOP instead of in the BODY context of SWSYNC -- since in some
                    ;; cases it might (still) be NIL in that context.
                    (update-values (.table-name db-cache) ["id = ?" (dosync @(:id @object))]
                                   {(as-quoted-identifier \" db-key) db-value}))))))



(declare db-put)
(declare db-remove)
(defn db-ensure-persistent-cm-field
  "Sets up reactive SQL UPDATEs for CONTAINER-MODEL."
  ([^DBCache db-cache ^Ref object ^Keyword clj-key ^ContainerModel container-model]
     (db-ensure-persistent-cm-field db-cache object clj-key container-model false))

  ([^DBCache db-cache ^Ref object ^Keyword clj-key ^ContainerModel container-model ^Boolean initial-sync?]
     (letfn [(do-it []
               ;; Extract objects while in the scope of the BODY of SWSYNC. Note how the :IDs aren't extracted here, as those
               ;; fields might still be NIL while pending :INSERTs.
               (let [cm-objects (with-local-vars [cm-objects []]
                                  (cm-iterate container-model _ cm-obj
                                    (var-alter cm-objects cm-obj)
                                    false)
                                  (var-get cm-objects))]
                 (swdbop :update
                   ;; The :ID field is extracted here, inside SWDBOP instead of in the BODY context of SWSYNC -- since in some
                   ;; cases it might (still) be NIL in that context. The same ID-related concern applies to the CM.
                   (let [[db-key db-value id]
                         ;; TODO: It seems silly having to go through DB-HANDLE-INPUT here just for key translation.
                         (dosync (let [[db-key db-value] (db-handle-input db-cache object clj-key container-model)]
                                   [db-key
                                    (db-clj-cm-to-db-array db-value "bigint") ;; TODO: Magic value.
                                    @(:id @object)]))]
                     (jdbc/do-prepared (str "UPDATE " (.table-name db-cache) " SET " (name db-key) " = " db-value " WHERE id = ?;")
                                       [id])))))]
       (when initial-sync?
         (cm-iterate container-model _ obj
           (db-put obj (:db-table-name @obj))
           false)
         (do-it))
       (observe (.observable container-model) nil
                (fn [inner-lifetime args]
                  (let [[event-sym & event-args] args
                        [op-type op-object] (case event-sym
                                              cm-prepend
                                              [:add (cmn-data (nth event-args 0))]

                                              (cmn-after cmn-before)
                                              [:add (cmn-data (nth event-args 1))]

                                              cmn-remove
                                              [:remove (cmn-data (nth event-args 0))])]
                    (case op-type
                      :add (db-put op-object (:db-table-name @op-object))
                      :remove (db-remove @(:id @op-object) (:db-table-name @op-object)))
                    (do-it)))))))



(defn db-value-to-vm-handler [^DBCache db-cache db-row ^Ref object ^Keyword clj-key clj-value]
  "DB --> SW."
  ;; TODO: I think DB-ENSURE-PERSISTENT-VM-FIELD can be called for the same VM twice in some cases; there's currently no check
  ;;; for this.
  (dosync
   (if-let [^ValueModel existing-vm (clj-key (ensure object))] ;; Does field already exist in OBJECT?
     (do
       (when-not (isa? (class existing-vm) ValueModel)
         (println "DB-VALUE-TO-VM-HANDLER:"
                  (class existing-vm)
                  "," (.table-name db-cache)
                  "," clj-key
                  "," clj-value))
       (vm-set existing-vm clj-value)
       (db-ensure-persistent-vm-field db-cache object clj-key existing-vm))
     (let [^ValueModel new-vm (vm clj-value)]
       (ref-set object (assoc (ensure object) clj-key new-vm))
       (db-ensure-persistent-vm-field db-cache object clj-key new-vm)))))



(declare db-get)
(defn db-value-to-cm-handler [^DBCache db-cache db-row ^Ref object ^Keyword clj-key ^java.sql.Array clj-value
                              ^String ref-db-table-name]
  "DB --> SW."
  (let [^clojure.lang.PersistentVector cm-objects (mapv #(db-get % ref-db-table-name)
                                                        (db-db-array-to-clj-vector clj-value))]
    (dosync
     (if-let [^ContainerModel existing-cm (clj-key (ensure object))] ;; Does field already exist in OBJECT?
       (do
         (assert (zero? (count existing-cm)))
         (doseq [obj cm-objects]
           (cm-append existing-cm (cmn obj)))
         (db-ensure-persistent-cm-field db-cache object clj-key existing-cm))
       (let [^ContainerModel new-cm (with1 (cm)
                                      (doseq [obj cm-objects]
                                        (cm-append it (cmn obj))))]
         (db-ensure-persistent-cm-field db-cache object clj-key new-cm))))))



(defn db-default-handle-output [^DBCache db-cache db-row ^Ref object]
  "DB --> SW."
  (doseq [^MapEntry entry db-row]
    (let [^Keyword clj-key (db-db-to-clj-key-transformer db-cache object (key entry))
          db-value (val entry)]
      (if (isa? (class db-value) java.sql.Array)
        (db-value-to-cm-handler db-cache db-row object clj-key db-value
                                false)
        (db-value-to-vm-handler db-cache db-row object clj-key db-value)))))



(defn db-handle-output [^DBCache db-cache ^Ref object db-row]
  "DB --> SW."
  (if-let [^Fn f (.db-handle-output-fn db-cache)]
    (f db-cache db-row object)
    (db-default-handle-output db-cache db-row object)))



(defn ^Ref db-backend-get [^DBCache db-cache ^Long id ^Ref obj]
  "Used by DB-GET; see DB-GET.
Returns OBJ."
  (if (not *in-sw-db?*)
    ;; TODO: WITH-SW-CONNECTION doesn't work, but it'd be all we need here.
    (with-sw-db (fn [_] (db-backend-get db-cache id obj)))
    (with-query-results res [(str "SELECT * FROM " (as-quoted-identifier \" (.table-name db-cache)) " WHERE id = ? LIMIT 1;")
                             id]
      (when-let [db-row (first res)]
        (db-handle-output db-cache obj db-row)
        obj))))



(declare db-cache-put)
(defn db-backend-put
  "SQL INSERT of OBJ whos keys and values are translated via DB-HANDLE-INPUT. This will also add OBJ to DB-CACHE unless
UPDATE-CACHE? is given a FALSE value.
Sets the :ID field of OBJ to an integer.
Non-blocking."
  ([^Ref obj ^DBCache db-cache] (db-backend-put obj db-cache true))
  ([^Ref obj ^DBCache db-cache ^Boolean update-cache?]
     (swdbop :insert
       (let [[abort-put? sql values-to-escape]
             (dosync
              ;; Grab snapshot of all data and use it to generate SQL statement. Note how this is done inside the :INSERT; this is
              ;; to ensure that any :UPDATES from the DB-ENSURE-PERSISTENT-* calls below won't happen before this :INSERT.
              (if (and (:id (ensure obj))
                       @(:id (ensure obj)))
                [true] ;; ABORT-PUT?
                (with-local-vars [record-data {}
                                  values-to-escape []
                                  after-put-fns []]
                  (doseq [^MapEntry key_val (ensure obj)]
                    ;; TODO: This test kind of sucks, but DB-HANDLE-INPUT can't do it because it will DEREF some ValueModels in
                    ;; order to convert them to a format suitable for the DB.
                    (when (or (= ValueModel (class (val key_val)))
                              (= ContainerModel (class (val key_val))))
                      (let [[db-key db-value] (db-handle-input db-cache obj (key key_val) (val key_val))]
                        (when db-key
                          (cond
                            (isa? (class (val key_val)) ValueModel)
                            (db-ensure-persistent-vm-field db-cache obj (key key_val) (val key_val))

                            (isa? (class (val key_val)) ContainerModel)
                            (db-ensure-persistent-cm-field db-cache obj (key key_val) (val key_val) true))
                          (var-alter record-data assoc db-key db-value)))))
                  [false ;; ABORT-PUT? SQL VALUES-TO-ESCAPE
                   (cl-format false "INSERT INTO ~A (~{~A~^, ~}) VALUES (~{~A~^, ~});"
                              (.table-name db-cache) ;; TODO: Escape.
                              (mapv name (keys (var-get record-data))) ;; TODO: Escape?
                              (mapv (fn [v]
                                      (cond
                                        (isa? (class v) ValueModel)
                                        (do1 "?"
                                          (var-alter values-to-escape conj @v))

                                        ;; Dummy value here so we can do our :INSERT without having to :INSERT other objects first.
                                        ;; Doing that would be tricky since those objects might rely on this object having been
                                        ;; :INSERTed first (:ID field).
                                        (isa? (class v) ContainerModel)
                                        "ARRAY[]::bigint[]"

                                        true
                                        (do1 "?"
                                          (var-alter values-to-escape conj v))))
                                    (vals (var-get record-data))))
                   (var-get values-to-escape)])))]
         (when-not abort-put?
           (let [res (jdbc/do-prepared-return-keys sql values-to-escape)]
             ;; Doing this here (instead of in the SWHTOP that followss) so other SWDBOPs can refer to the :ID field e.g. while
             ;; generating SQL.
             (dosync
              (if (and (:id (ensure obj))
                       @(:id (ensure obj)))
                (abort-transaction false)
                (do
                  (vm-set (:id @obj) (:id res)) ;; TODO: ..or add?
                  (when update-cache?
                    (db-cache-put db-cache (:id res) obj)))))
             (swhtop
               (db-handle-output db-cache obj res)
               ;; Initialize object further; perhaps add further (e.g. non-DB related) observers of the objects fields etc..
               ((.after-fn db-cache) obj)
               obj)))))))



(defn ^DBCache mk-DBCache [^String table-name
                           ^Fn constructor-fn
                           ^Fn after-fn
                           ;; SW -> DB.
                           db-handle-input-fn
                           db-clj-to-db-key-transformer-fn

                           ;; DB --> SW.
                           db-handle-output-fn
                           db-db-to-clj-key-transformer-fn]
  (let [^DBCache db-cache (DBCache.
                           ;; SW -> DB.
                           db-handle-input-fn
                           db-clj-to-db-key-transformer-fn

                           ;; DB --> SW.
                           db-handle-output-fn
                           db-db-to-clj-key-transformer-fn

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



(defn ^Ref db-get
  ([^Long id ^String table-name]
     (db-get id table-name identity))

  ([^Long id ^String table-name ^Fn after-construction-fn]
     (io! "DB-GET: Cannot be used directly while within Clojure transaction (DOSYNC or SWSYNC).")
     (let [id (long id) ;; Because (.equals (int 261) 261) => false
           ^DBCache db-cache (db-get-cache table-name)]
       (try
         (.get ^com.google.common.cache.LocalCache$LocalLoadingCache (get-internal-cache db-cache) id
               (fn []
                 (when-let [^Ref new-obj (db-backend-get db-cache id ((.constructor-fn db-cache) db-cache id))]
                   (dosync (after-construction-fn ((.after-fn db-cache) new-obj)))
                   new-obj)))
         (catch com.google.common.cache.CacheLoader$InvalidCacheLoadException e
           (println "DB-GET: Object with ID" id "not found in" (.table-name db-cache))
           false)
         (catch com.google.common.util.concurrent.UncheckedExecutionException e
           (println (str "DB-GET [" id " " table-name "]: Re-throwing cause " (.getCause e) " of " e))
           (throw (.getCause e)))))))



;; TODO:
(defn db-remove [^Long id ^String table-name]
  "SQL `DELETE FROM ...'."
  (assert false "DB-REMOVE: TODO!")
  #_(db-backend-remove id table-name))
