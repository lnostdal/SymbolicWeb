(in-ns 'symbolicweb.core)

(declare mk-view ref?)


(defprotocol IModel
  (add-view [vm view])
  (remove-view [vm view]))

(defprotocol IValueModel
  (%vm-inner-ref [vm]))


(deftype ValueModel [^clojure.lang.Ref value-ref
                     views
                     notify-views-fn]
  clojure.lang.IDeref
  (deref [_]
    (ensure value-ref))

  IValueModel
  (%vm-inner-ref [_]
    value-ref)

  IModel
  (add-view [_ view]
    (alter views conj view))

  (remove-view [_ view]
    (alter views disj view)))

(defmethod print-method ValueModel [value-model stream]
  (print-method (. value-model %vm-inner-ref) stream))

(defn make-ValueModel [^clojure.lang.Ref value-ref]
  "Creates a ValueModel wrapping VALUE-REF."
  (assert (ref? value-ref))
  (ValueModel. value-ref
               (ref #{})
               (fn [value-model old-value new-value]
                 (doseq [view (ensure (. value-model views))]
                   (let [view-m @view
                         new-value ((:output-parsing-fn view-m) new-value)]
                     ((:handle-model-event-fn view-m) view old-value new-value))))))

(defn vm [initial-value]
  "Creates a ValueModel. INITIAL-VALUE will be wrapped in a Ref."
  (assert (not (ref? initial-value))) ;; TODO: User might want to nest?
  (make-ValueModel (ref initial-value)))

(defn get-value [^ValueModel value-model]
  "[DEPRECATED] Returns the value held in VALUE-MODEL. It will be safe from write-skew (STM)."
  (assert (isa? (type value-model) ValueModel))
  @value-model)

(defn set-value [^ValueModel value-model new-value]
  "Sets VALUE-MODEL to NEW-VALUE and notifies Views of VALUE-MODEL of the change.
Note that the Views are only notified when NEW-VALUE isn't = to the old value of VALUE-MODEL. This (read of the old value) is safe with regards to
write-skew (STM)."
  (assert (isa? (type value-model) ValueModel))
  (let [old-value @value-model]
    (when-not (= old-value new-value)
      (ref-set (%vm-inner-ref value-model) new-value)
      ((. value-model notify-views-fn) value-model old-value new-value)))
  new-value)

(defn vm-set [^ValueModel value-model new-value]
  (set-value value-model new-value))

(defn alter-value [^ValueModel value-model fn & args]
  "Alters (calls clojure.core/alter on) VALUE-MODEL using FN and ARGS and notifies Views of VALUE-MODEL of the change.
Note that the Views are only notified when the resulting value of FN and ARGS wasn't = to the old value of VALUE-MODEL."
  (assert (isa? (type value-model) ValueModel))
  (let [old-value @value-model]
    (apply alter (%vm-inner-ref value-model) fn args)
    (let [new-value @value-model]
      (when-not (= old-value new-value)
        ((. value-model notify-views-fn) value-model old-value new-value)))))

(defn vm-alter [^ValueModel value-model fn & args]
  (apply alter-value value-model fn args))

(defn vm-copy [^ValueModel value-model]
  "Creates a ValueModel. The initial value of it will be extracted from SOURCE-VM. Further changes (mutation of) to SOURCE-VM will not affect the
ValueModel created and returned here."
  (assert (isa? (type value-model) ValueModel))
  (vm @value-model))


;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;


;; http://commons.apache.org/collections/api/org/apache/commons/collections/ReferenceMap.html


(defrecord DBCache
    [db-handle-input-fn
     db-handle-output-fn
     agent
     ^String table-name
     constructor-fn ;; Function called on cache miss to construct the initial skeleton for the data from the DB to fill up.
     ^ReferenceMap cache-data])


(defn default-db-handle-input [input-key input-value]
  "SW --> DB.
DEREFs INPUT-VALUE if it is a ValueModel, so needs to be run within DOSYNC.
If INPUT-VALUE is not a ValueModel, returns [NIL NIL] resulting in the entry not being stored in the DB."
  #_(if (isa? (type input-value) ValueModel)
      [input-key @input-value]
      [nil nil])
  [input-key input-value])

(defn db-handle-input [^DBCache db-cache ^clojure.lang.Keyword input-key input-value]
  "SW --> DB.
Returns two values in form of a vector [TRANSLATED-INPUT-KEY TRANSLATED-INPUT-VALUE] or returns NIL if the field in question,
represented by INPUT-KEY, is not to be stored in the DB."
  (if-let [f (. db-cache db-handle-input-fn)]
    (f input-key input-value)
    (default-db-handle-input input-key input-value)))

(defn default-db-handle-output [output-key output-value]
  "DB --> SW."
  [output-key output-value])

(defn db-handle-output [^DBCache db-cache ^clojure.lang.Keyword output-key output-value]
  "DB --> SW.
Returns two values in form of a vector [TRANSLATED-OUTPUT-KEY TRANSLATED-OUTPUT-VALUE] or returns NIL if the field in question,
represented by OUTPUT-KEY, is not to be fetched from the DB."
  (if-let [f (. db-cache db-handle-output-fn)]
    (f output-key output-value)
    (default-db-handle-output output-key output-value)))


(defn db-ensure-persistent-field [^DBCache db-cache ^Long id ^clojure.lang.Keyword key ^ValueModel value-model]
  "Setup reactive SQL UPDATEs for VALUE-MODEL."
  (mk-view value-model nil
           (fn [value-model old-value new-value]
             (when-not (= old-value new-value) ;; TODO: Hum, is this needed? And anyways, comparison using = as a magic value is bad.
               (let [[input-key input-value] (db-handle-input db-cache key new-value)]
                 (when input-key
                   (send-off (. db-cache agent)
                             (fn [_]
                               (with-errors-logged
                                 (with-sw-db
                                   (update-values (. db-cache table-name) ["id=?" id]
                                                  {(as-quoted-identifier \" key)input-value})))))))))
           :trigger-initial-update? false))


(defn db-backend-get [^String table-name ^Long id ^DBCache db-cache]
  "SQL SELECT. Returns a map with translated, via :DB-HANDLE-OUTPUT-FN of DB-CACHE, keys and values."
  (with-query-results res [(str "SELECT * FROM " (as-quoted-identifier \" table-name) " WHERE id = ?;") id]
    (with-local-vars [object-map {}]
      (doseq [key_val (first res)]
        (let [[output-key output-value] (db-handle-output db-cache (key key_val) (val key_val))]
          (when output-key
            (let [vm-output-value (vm output-value)]
              (var-set object-map (assoc (var-get object-map)
                                    output-key vm-output-value))
              (db-ensure-persistent-field db-cache (:id res) output-key vm-output-value)))))
      (var-get object-map))))


(declare db-cache-put)
(defn db-backend-put [object ^DBCache db-cache cont-fn]
  "SQL INSERT of OBJECT whos keys and values are translated via DB-HANDLE-INPUT. This will also add OBJECT to DB-CACHE after the
:ID field has been set -- which might happen some time after this function has returned."
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
                      (db-cache-put db-cache (:id res) object) ;; We have our object ID; add object to cache.
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
                                 (db-ensure-persistent-field db-cache (:id res) output-key (output-key (ensure object)))))))))))
                  (cont-fn object))))))


(defn mk-db-cache [table-name constructor-fn db-handle-input-fn db-handle-output-fn]
  (DBCache.
   (if db-handle-input-fn db-handle-input-fn default-db-handle-input)
   (if db-handle-output-fn db-handle-output-fn default-db-handle-output)
   (agent :db-cache-agent)
   table-name
   constructor-fn
   (ReferenceMap. ReferenceMap/HARD ReferenceMap/SOFT)))


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

Assuming DB-CACHE-GET is the only function used to fetch objects from the back-end (DB), this will do the needed locking to ensure
that only one object with id ID exists in the cache and the system at any point in time. It'll fetch from the DB using
:CONSTRUCTOR-FN from DB-CACHE."
  (if-let [cache-entry (. (. db-cache cache-data) get id)]
    (cont-fn cache-entry :hit)
    (send-off (. db-cache agent)
              (fn [_]
                (with-errors-logged
                  (with-sw-db
                    (apply cont-fn
                           (if-let [new-obj ((. db-cache constructor-fn) db-cache id)] ;; I/O.
                             (locking db-cache ;; Lock after I/O.
                               (if-let [cache-entry (. (. db-cache cache-data) get id)] ;; Check cache again while within lock.
                                 [cache-entry :hit]
                                 (do
                                   (db-cache-put db-cache id new-obj)
                                   ;; TODO: Do we need to do this while within the lock? Or at all here? I guess we can do it pretty quickly if we
                                   ;; make sure no SQL UPDATE is done; :TRIGGER-INITIAL-UPDATE? should be false for MK-VIEW in this case.
                                   ;;(ensure-persistent db-cache new-obj)
                                   #_(doseq [key_val new-obj]
                                     (db-ensure-persistent-field db-cache @(:id (ensure new-obj)) (key key_val) (val key_val)))

                                   [new-obj :miss])))
                             [nil nil]))))))))


(defn db-cache-remove [^DBCache db-cache ^Long id]
  "Removes object based on ID from DB-CACHE."
  (locking db-cache
    (. (. db-cache cache-data)
       remove id)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn test-cache-perf [num-iterations object-id]
  (def -db-cache- (mk-db-cache "test"
                               (fn [db-cache id]
                                 (dosync ;; TODO: Needed since mk-view calls add-view which calls alter (STM mutation).
                                  (ref (db-backend-get "test" id db-cache))))
                               nil
                               nil))
  (let [first-done? (promise)]
    (db-cache-get -db-cache- object-id
                  (fn [obj cache-state]
                    (dbg-prin1 [obj cache-state])
                    (deliver first-done? :yup)))
    (deref first-done?)
    (println "Cache is now hot; request object with ID" object-id "from it" num-iterations "times and print total time taken..")
    (time
     (dotimes [i num-iterations]
       (db-cache-get -db-cache- object-id
                     (fn [obj cache-state]))))))



(defn test-cache-insert []
  (def -db-cache- (mk-db-cache "test"
                               (fn [db-cache id]
                                 (dosync
                                  (ref (db-backend-get "test" id db-cache))))
                               nil
                               nil))
  (let [new-obj (ref {:value (vm "non-random initial value")})]
    (dosync
     (db-backend-put new-obj -db-cache- (fn [new-obj]
                                          (dosync (dbg-prin1 @new-obj)))))
    (Thread/sleep 1000)
    (dosync
     (dbg-prin1 @new-obj)
     (db-cache-get -db-cache- @(:id @new-obj)
                   (fn [obj cache-state]
                     (dbg-prin1 [obj cache-state]))))
    (dosync
     (vm-set (:value @new-obj) (str "rand-int: " (rand-int 9999)))
     (dbg-prin1 @(:value @new-obj)))))
















#_(defn ensure-persistent [db-cache object-or-id & aux-data]
  "If an object with an :ID field containing NIL is given for OBJECT-OR-ID, an SQL INSERT operation is done based on the fields
in the object (default values) combined with AUX-DATA. Note that both AUX-DATA and the SQL INSERT operation might add additional
fields to the object -- also note that the :ID field might not be set yet on returning from this function.

If an integer or an object with a non-NIL :ID field is given for OBJECT-OR-ID, fetches the associated object from cache or DB
and does an SQL UPDATE operation on it based on the data given by AUX-DATA if any.

In both cases, an observer is created for each field in the object in question which will continiously sync to the related columns
in the DB.

AUX-DATA is parsed or handled the same way data from the DB is parsed or handled. I.e., via :DB-HANDLE-OUTPUT-FN of the cache.

Returns the object."
  (let [[id object] (if (number? object-or-id)
                      [object-or-id nil]
                      [@(:id @object-or-id) object-or-id])]
    (letfn ([handle-aux-data [f]
             (doseq [key_val aux-data]
               (let [[input-key input-value] (db-handle-input db-cache object (key key_val) (val key_val))]
                 (when input-key
                   (f input-key input-value))))]

            [do-observe-value-model [object key value-model]
             ;; SQL UPDATE; observed and synced/updated in real-time.
             (mk-view value-model nil
                      (fn [value-model old-value new-value]
                        (let [id @(:id @object)]
                          (assert id)
                          (let [[input-key input-value] (db-handle-input db-cache object key new-value)]
                            (when input-key
                              (update-values table-name ["id=?" id]
                                             {(as-quoted-identifier \" input-key) input-value}))))))]

              [do-init [object]
               ;; SQL INSERT.
               (when (not @(:id @object))
                 (with-local-vars [record-data {}]
                   ;; TODO: We only handle AUX-DATA here; what about already existing fields in OBJECT? AUX-DATA should take
                   ;; AUX-DATA should take precedence; already existing fields in OBJECT being regarded as default values to be overridden.
                   (doseq [key_val insert-data] ;; Build data structure which then will be converted to a single SQL INSERT statement.
                     (let [key_val (first key_val)] ;; TODO: This seems dumb; {:key val} --> [:key val]
                       (let [[input-key input-value] (db-handle-input db-cache object (key key_val) (val key_val))]
                         (when input-key
                           (if-let [field (input-key @object)]
                             (vm-set field input-value)
                             (ref-set object (assoc (ensure object) :input-key (vm input-value))))
                           (var-set record-data (assoc (var-get record-data) (as-quoted-identifier \" input-key) input-value))))))
                   ;; TODO: Use an agent here to make sure things don't change under our feet before we make a snapshot to the DB.
                   (let [id (:id (insert-record table-name (var-get record-data)))]
                     (vm-set (:id @object) id)
                     (db-cache-put db-cache id object))))
               ;; Setup observers which will do continious SQL UPDATEs.
               (dorun (map (fn [elt]
                             (let [k (key elt)
                                   v (val elt)]
                               (when (and (= ValueModel (type v))
                                          (not= :id k))
                                 (do-observe-value-model object k v))))
                           (ensure object)))])
      (if object
        ;; INSERT / UPDATE (SW --> DB).
        (do-init object)
        ;; SELECT (DB --> SW).
        (let [[object (let [[cache-entry cache-entry-found?] (db-cache-get db-cache id)]
                        (if cache-entry-found?
                          cache-entry
                          (let [object #_(ref {:id (vm nil)})
                                (mk-new-object)]
                            (with-query-results res [(str "SELECT * FROM " (as-quoted-identifier \" table-name) " WHERE id = ?;") id]
                              (assert (not (empty? res)) (str "ENSURE-PERSISTENT: Object with ID " id " not found in table \"" table-name "\"."))
                              (let [res (first res)]
                                (doseq [key_val res]
                                  (let [[output-key output-value] (db-handle-output db-cache object (key key_val) (val key_val))]
                                    (when output-key
                                      (ref-set object (assoc (ensure object)
                                                        output-key (vm output-value))))))))
                            (db-cache-put db-cache id object)
                            (do-init object)
                            object)))]]
          ;; Update object (CACHE-ENTRY) fields.
          (doseq [key_val insert-data]
            (let [key_val (first key_val)]
              (let [[input-key input-value] (db-handle-input db-cache cache-entry (first key_val) (second key_val))]
                (when input-key
                  (vm-set (input-key @cache-entry) input-value)))))
          cache-entry)))))
