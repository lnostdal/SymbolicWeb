(in-ns 'symbolicweb.core)



(defn user-model-base-clj-to-db-transformer [m]
  "SW --> DB"
  (-> m
      ((fn [m]
         (case (:key m)
           :sessions
           (assoc m
             :key nil)

           m)))))



(defn mk-UserModelBase [& args]
  (ref (apply assoc {}
              :id (vm nil)
              ;; A single user can have several sessions running on several computers/browsers at the same time.
              :sessions (vm #{}) ;; NOTE: GC-SESSION depends on this field.
              :uuid (generate-uuid)
              args)))
