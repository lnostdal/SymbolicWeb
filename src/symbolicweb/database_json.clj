(in-ns 'symbolicweb.core)


;;; TODO:
;;
;; * PostgreSQL 9.2+ supports a native JSON type; might be useful here.
;;
;; * Related perhaps: https://bitbucket.org/kotarak/lazymap ..seems interesting; haven't thought about this closely though.



(defn db-db-to-json-transformer [m]
  "DB --> SW"
  (let [js (into {} (map (fn [[k v]]
                           [k (vm v)])
                         (json-parse (:value m))))]
    ;; Observe each existing field in store.
    (doseq [[k v] js]
      (vm-observe v nil false (fn [_ _ _]
                                ;;(db-json-update js)
                                (vm-alter ((:key m) @(:obj m)) assoc k v))))
    (assoc m
      :value js)))



(defn db-json-to-db-transformer [m]
  "SW --> DB"
  (let [json-str (json-generate (into {} (map (fn [[k v]]
                                                [k @v])
                                              @(:value m))))]
    (assoc m
      :value json-str)))



(defn db-json-get [^ValueModel json-store ^Keyword k & not-found]
  (let [res (get @json-store k ::not-found)]
    (if (= res ::not-found)
      (with1 (vm (first not-found))
        ;; Observe field itself.
        (vm-observe it nil false (fn [_ _ _]
                                   ;; Readding the field to the store will trigger an update.
                                   (vm-alter json-store assoc k it)))
        ;; Add field to store.
        (vm-alter json-store assoc k it))
      res)))
