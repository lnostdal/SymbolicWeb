(in-ns 'symbolicweb.core)

;; MVC core and persistence (DB) abstraction
;; -----------------------------------------
;;
;; TODO: Foreign keys. This should be easy, and fun.
;; TODO: send-off -> with-errors-logged -> with-sw-db is repeated several times.


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
                   (let [view-m (ensure view)
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



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TODO: Persistance stuff comes here; move this to separate file later


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

(defn reset-db-cache [table-name]
  (locking -db-caches-
    (. -db-caches- remove table-name)))


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
                           (if-let [new-obj (db-backend-get db-cache id ((. db-cache constructor-fn) db-cache id))] ;; I/O.
                             (locking db-cache ;; Lock after I/O.
                               (if-let [cache-entry (. (. db-cache cache-data) get id)] ;; Check cache again while within lock.
                                 [cache-entry :hit]
                                 (do
                                   (db-cache-put db-cache id new-obj)
                                   [new-obj :miss])))
                             [nil nil]))))))))


(defn db-cache-remove [^DBCache db-cache ^Long id]
  "Removes object based on ID from DB-CACHE."
  (locking db-cache
    (. (. db-cache cache-data)
       remove id)))



;;;;;;;;;;;;;;;;;;;;;;
;; some quick tests...


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











(defn php-send [message-map]
  "Send message to PHP end."
  ;; TODO: Think about using the Async API here -- or wrap everything in an agent perhaps.
  (let [conn (aleph.http.client/http-request {:auto-transform true
                                              :method :get
                                              :cookies (aleph.http.utils/hash->cookie
                                                        {"x_plugin_api_key" "fod" ;; TODO: Magic value.
                                                         "PHPSESSID" (:value (get (:cookies *request*) "PHPSESSID"))})
                                              :url (str "http://dev.kitch.no/free-or-deal/sw/php/plugin_api/sw_plugin_api.php?data=" ;; TODO: Magic value.
                                                        (json/generate-string message-map))})]
    (dbg-prin1 @conn)))


(defn php-operation-complete-notification [operation-id args]
  ;;(assoc args :operation_id operation-id)
  (php-send args))


(defn php-login! [id access-rights]
  (php-send {:action "login" :id id
             :access_rights access-rights}))


(defn blah []
  (let [conn (aleph.http.client/http-request {:auto-transform true
                                             :method :get
                                             :cookies (aleph.http.utils/hash->cookie
                                                       {"x_plugin_api_key" "fod"
                                                        "session_id" "t89ffkkv1lvkfnbtq8oljlohn1"})
                                             :url (str "http://dev.kitch.no/free-or-deal/sw/php/plugin_api/sw_plugin_api.php?data="
                                                       (json/generate-string {:action "login"
                                                                              :id 291}))}
                                             1000)]
    (dbg-prin1 @conn)
    (lamina.connections/close-connection conn)))





#_(defn blah2 []
  (client/get
   (str "http://dev.kitch.no/free-or-deal/sw/php/plugin_api/sw_plugin_api.php?data="
        (url-encode (str "{\"action\": \"login\","
                         "\"x_plugin_api_key\": \"fod\","
                         "\"session_id\": \"" "t89ffkkv1lvkfnbtq8oljlohn1" "\","
                         "\"id\": 261}")))
   ;;"http://dev.kitch.no/blah.txt"
   ))
