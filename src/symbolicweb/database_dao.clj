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
;; TODO: Prefixing everything here with DB- is retarded; use a namespace.

;; NOTE: :ID fields should only be read within SWDBOPs. This means a small DOSYNC is needed for this.



(defn db-db-array-to-clj-vector [^java.sql.Array db-array]
  "DB (SQL Array) --> SW (Clj Vector)"
  (vec (.getArray db-array)))


(defn ^String db-clj-coll-to-db-array [clj-coll ^String db-type]
  "SW (Clj Coll) --> DB (SQL Array)"
  (cl-format false "ARRAY[~{~S~^, ~}]::~A[]" clj-coll db-type))


(defn ^String db-clj-cm-to-db-array [^ContainerModel container-model ^String db-type]
  "SW (ContainerModel) --> DB (SQL Array)"
  (with-local-vars [elts []]
    (cm-iterate container-model _ obj
      (var-alter elts conj (with1 @(:id @obj)
                             (assert (integer? it)
                                     (str "DB-CLJ-CM-TO-DB-ARRAY: Object not in DB yet? :ID is not an integer: " it))))

      false)
    (cl-format false "ARRAY[~{~S~^, ~}]::~A[]" (var-get elts) db-type)))



(defn db-default-clj-to-db-transformer [m]
  "SW --> DB"
  (assoc m
    :key (cond
          (not (:key m))
          nil

          (= :id (:key m))
          nil

          true
          (keyword (str/replace (name (:key m)) \- \_)))

    :value (cond
            (isa? (class (:value m)) ValueModel)
            @(:value m)

            ;; TODO: Perhaps :VALUE should be a FN?
            ;; NOTE: DB-PUT'ing objects with CM fields not possible if we do this here; it's too early with regards to :ID fields.
            ;; (isa? (class clj-value) ContainerModel)
            ;; (db-clj-cm-to-db-array clj-value "bigint") ;; TODO: Magic value "bigint".

            true
            (:value m))))


(defn db-clj-to-db-transformer [^DBCache db-cache ^Ref obj ^Keyword clj-key clj-value]
  "SW --> DB"
  (let [m {:db-cache db-cache :obj obj :key clj-key :value clj-value}]
    (if-let [^Fn f (.db-clj-to-db-transformer-fn db-cache)]
      (f m)
      (db-default-clj-to-db-transformer m))))



(declare db-value-to-vm-handler)
(defn db-default-db-to-clj-transformer [m]
  "DB --> SW"
  (assoc m
    :key (keyword (str/replace (name (:key m)) \_ \-)) ;; :some_field --> :some-field
    :handler (fn [clj-key db-value]
               (cond
                (isa? (class db-value) java.sql.Array)
                (assert false
                        "DB-DEFAULT-DB-TO-CLJ-HANDLER: Can't call DB-VALUE-TO-CM-HANDLER here; REF-DB-TABLE-NAME arg missing.")

                true
                (db-value-to-vm-handler (:db-cache m) (:obj m) clj-key db-value)))))



(defn db-db-to-clj-transformer [^DBCache db-cache ^Ref obj ^Keyword db-key db-value]
  "DB --> SW"
  (let [m {:db-cache db-cache :obj obj :key db-key :value db-value}]
    (if-let [^Fn f (.db-db-to-clj-transformer-fn db-cache)]
      (f m)
      (db-default-db-to-clj-transformer m))))



(defn db-db-to-clj-handler [^DBCache db-cache ^Ref obj ^Keyword db-key db-value]
  "DB --> SW"
  (let [res (db-db-to-clj-transformer db-cache obj db-key db-value)]
    (when (:key res)
      ((:handler res) (:key res) (:value res)))))



(defn db-db-to-clj-entry-handler [^DBCache db-cache ^Ref obj entry-data]
  "DB --> SW
  ENTRY-DATA: Map representing a DB Row; DB-KEY and DB-VALUEs."
  (doseq [[^Keyword db-key db-value] entry-data]
    (db-db-to-clj-handler db-cache obj db-key db-value)))



(defn db-ensure-persistent-vm-field [^DBCache db-cache ^Ref obj ^Keyword clj-key ^ValueModel value-model]
  "Sets up reactive SQL UPDATEs for VALUE-MODEL."
  ;; TODO: I think this gets called many times for the same VALUE-MODELs in some cases -- which of course is a bad.
  (vm-observe value-model nil false
              (fn [inner-lifetime old-value new-value]
                (let [res (db-clj-to-db-transformer db-cache obj clj-key value-model)
                      ^Keyword db-key (:key res)
                      db-value (:value res)]
                  (when db-key ;; TODO: Remove this check later..
                    (swdbop :update
                            ;; The :ID field is extracted here, inside SWDBOP instead of in the BODY context of SWSYNC --
                            ;; since in some cases it might (still) be NIL in that context.
                            (update-values (.table-name db-cache) ["id = ?" (dosync @(:id @obj))]
                                           {(as-quoted-identifier \" db-key) db-value})))))))



(declare db-put)
(declare db-remove)
(defn db-ensure-persistent-cm-field
  "Sets up reactive SQL UPDATEs for CONTAINER-MODEL."
  ([^DBCache db-cache ^Ref obj ^Keyword clj-key ^ContainerModel container-model]
     (db-ensure-persistent-cm-field db-cache obj clj-key container-model false))

  ([^DBCache db-cache ^Ref obj ^Keyword clj-key ^ContainerModel container-model ^Boolean initial-sync?]
     (letfn [(do-it []
               ;; Extract objects while in the scope of the BODY of SWSYNC. Note how the :IDs aren't extracted here, as those
               ;; fields might still be NIL while pending :INSERTs.
               (let [cm-objs (with-local-vars [cm-objs []]
                                  (cm-iterate container-model _ cm-obj
                                    (var-alter cm-objs cm-obj)
                                    false)
                                  (var-get cm-objs))]
                 (swdbop :update
                   ;; The :ID field is extracted here, inside SWDBOP instead of in the BODY context of SWSYNC -- since in some
                   ;; cases it might (still) be NIL in that context. The same ID-related concern applies to the CM.
                   (let [[db-key db-value id]
                         (dosync
                          (let [res ((.db-clj-to-db-transformer-fn db-cache) db-cache obj clj-key container-model)]
                            [(:key res)
                             (db-clj-cm-to-db-array (:value res) "bigint") ;; TODO: Magic value.
                             @(:id @obj)]))]
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
                        [op-type op-obj] (case event-sym
                                              cm-prepend
                                              [:add (cmn-data (nth event-args 0))]

                                              (cmn-after cmn-before)
                                              [:add (cmn-data (nth event-args 1))]

                                              cmn-remove
                                              [:remove (cmn-data (nth event-args 0))])]
                    (case op-type
                      :add (db-put op-obj (:db-table-name @op-obj))
                      :remove (db-remove @(:id @op-obj) (:db-table-name @op-obj)))
                    (do-it)))))))



(defn db-value-to-vm-handler [^DBCache db-cache ^Ref obj ^Keyword clj-key db-value]
  "DB --> SW"
  ;; TODO: I think DB-ENSURE-PERSISTENT-VM-FIELD can be called for the same VM twice in some cases; there's currently no check
  ;;; for this.
  (dosync
   (if-let [^ValueModel existing-vm (clj-key (ensure obj))] ;; Does field already exist in OBJ?
     (do
       (when-not (isa? (class existing-vm) ValueModel)
         (println "DB-VALUE-TO-VM-HANDLER:"
                  (class existing-vm)
                  "," (.table-name db-cache)
                  "," clj-key
                  "," db-value))
       (vm-set existing-vm db-value)
       (db-ensure-persistent-vm-field db-cache obj clj-key existing-vm))
     (let [^ValueModel new-vm (vm db-value)]
       (ref-set obj (assoc (ensure obj) clj-key new-vm))
       (db-ensure-persistent-vm-field db-cache obj clj-key new-vm))))
  true)



(declare db-get)
(defn db-value-to-cm-handler [^DBCache db-cache ^Ref obj ^Keyword clj-key ^java.sql.Array clj-value
                              ^String ref-db-table-name]
  "DB --> SW"
  (let [^clojure.lang.PersistentVector cm-objs (mapv #(db-get % ref-db-table-name)
                                                        (db-db-array-to-clj-vector clj-value))]
    (dosync
     (if-let [^ContainerModel existing-cm (clj-key (ensure obj))] ;; Does field already exist in OBJ?
       (do
         (assert (zero? (count existing-cm)))
         (doseq [cm-obj cm-objs]
           (cm-append existing-cm (cmn cm-obj)))
         (db-ensure-persistent-cm-field db-cache obj clj-key existing-cm))
       (let [^ContainerModel new-cm (with1 (cm)
                                      (doseq [cm-obj cm-objs]
                                        (cm-append it (cmn cm-obj))))]
         (db-ensure-persistent-cm-field db-cache obj clj-key new-cm))))))



(defn ^Ref db-backend-get [^DBCache db-cache ^Long id ^Ref obj]
  "Used by DB-GET; see DB-GET.
Returns OBJ or NIL"
  (if (not *in-sw-db?*)
    ;; TODO: WITH-SW-CONNECTION doesn't work, but it'd be all we need here.
    (with-sw-db (fn [_] (db-backend-get db-cache id obj)))
    (with-query-results res [(str "SELECT * FROM " (as-quoted-identifier \" (.table-name db-cache)) " WHERE id = ? LIMIT 1;") id]
      (when-let [db-row (first res)]
        (db-db-to-clj-entry-handler db-cache obj db-row)
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
                  ;; CLJ-VALUE; Too early to DEREF stuff here; see the note about "dummy value" below.
                  (doseq [[^Keyword clj-key clj-value] (ensure obj)]
                    (let [res (db-clj-to-db-transformer db-cache obj clj-key clj-value)
                          ^Keyword db-key (:key res)]
                      (when (and db-key
                                 (or (= ValueModel (class clj-value))
                                     (= ContainerModel (class clj-value))))
                        (var-alter record-data assoc db-key clj-value)
                        (cond
                         (isa? (class clj-value) ValueModel)
                         (db-ensure-persistent-vm-field db-cache obj clj-key clj-value)

                         ;; The True for INITIAL-SYNC? queues up an :UPDATE that will execute after the current :INSERT OP.
                         (isa? (class clj-value) ContainerModel)
                         (db-ensure-persistent-cm-field db-cache obj clj-key clj-value true)))))
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
                                        "ARRAY[]::bigint[]" ;; TODO: Magic value (bigint).

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
                  (vm-set (:id @obj) (:id res)) ;; TODO: ..or add? Though, always assuming OBJ contains :ID makes things easier.
                  (when update-cache?
                    (db-cache-put db-cache (:id res) obj)))))
             (swhtop
               ;; TODO: This certainly adds multiple observers for each field (DB-ENSURE-PERSISTENT...).
               (db-db-to-clj-entry-handler db-cache obj res)
               ;; Initialize object further; perhaps add further (e.g. non-DB related) observers of the objects fields etc..
               ((.after-fn db-cache) obj)
               obj)))))))



(defn ^DBCache mk-DBCache [^String table-name
                           ^Fn constructor-fn
                           ^Fn after-fn

                           db-clj-to-db-transformer-fn
                           db-db-to-clj-transformer-fn]
  (let [^DBCache db-cache (DBCache.
                           db-clj-to-db-transformer-fn
                           db-db-to-clj-transformer-fn

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
  ([^Ref obj ^String table-name]
     (db-put obj table-name true))

  ([^Ref obj ^String table-name ^Boolean update-cache?]
     (db-backend-put obj (db-get-cache table-name) update-cache?)))



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
