(in-ns 'symbolicweb.core)


;;; TODO:
;;
;; * PostgreSQL 9.2+ supports a native JSON type; might be useful here.
;;
;; * Related perhaps: https://bitbucket.org/kotarak/lazymap ..seems interesting; haven't thought about this closely though.



(defn db-json-update [json-store]
  "Sync JSON-STORE to the DB."
  (db-pstmt (str "UPDATE " (:table-name json-store) " SET " (name (:colname json-store)) " = ? WHERE id = ?;")
            (json-generate (zipmap (keys @(:data json-store))
                                   (map #(deref %) (vals @(:data json-store)))))
            (:id json-store)))



;; TODO: The cache here is probably not the best idea, but it does avoid problems related to TX retries.
(let [cache (-> (CacheBuilder/newBuilder)
                (.softValues)
                (.concurrencyLevel (.availableProcessors (Runtime/getRuntime)))
                (.build))]
  (defn db-json-store-get [^String table-name ^Long id ^Keyword colname]
    (.get cache [table-name id colname]
          (fn []
            ;;(println "DB-JSON-STORE-GET: Creating new JSON store and putting it in the cache.")
            (let [js-str (colname (first (db-pstmt (str "SELECT " (name colname) " FROM " table-name " WHERE id = ? LIMIT 1;")
                                                   id)))
                  js-m (json-parse js-str)
                  js {:table-name table-name
                      :id id
                      :colname colname
                      :data (vm (zipmap (keys js-m)
                                        (map #(vm %) (vals js-m))))}]
              ;; Observe the store itself; see the VM-ALTER call in DB-JSON-GET.
              (vm-observe (:data js) nil false
                          (fn [_ _ _] (db-json-update js)))
              ;; Observe each field in the store.
              (doseq [[k v] @(:data js)]
                (vm-observe v nil false
                            (fn [_ _ _] (db-json-update js))))
              js)))))



(defn db-json-store-new [^String table-name ^Keyword colname]
  "Returns ID."
  (:id (first (db-insert table-name {colname "{}"}))))



(defn db-json-get
  ([json-store ^Keyword k]
     (db-json-get json-store k
                  (with1 (vm nil)
                    (let [done? (atom false)] ;; TODO: Too bad a Lifetime isn't passed to the callback in these cases..hm.
                      (vm-observe it nil false
                                  (fn [_ _ new-value]
                                    (when-not @done?
                                      (reset! done? true)
                                      (let [new-value-vm (vm new-value)]
                                        ;; Add the new field to store.
                                        (vm-alter (:data json-store) assoc
                                                  k new-value-vm)
                                        ;; Observe the new field.
                                        (vm-observe new-value-vm nil false
                                                    (fn [_ _ _] (db-json-update json-store)))))))))))

  ([json-store ^Keyword k not-found]
     (get @(:data json-store) k not-found)))
