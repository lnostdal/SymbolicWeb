(in-ns 'symbolicweb.core)

;;; Chunked DB query results by using LIMIT and ORDER BY on indexed (for performance) ID fields.
;;; Used for e.g. pagination or "infinite scroll".
;;
;;
;; Features and limitations:
;;
;;   * No global sorting by user defined criteria i.e. via ORDER BY.
;;   * Global ordering by newest or oldest entries first is supported though.
;;
;;
;; TODO: It'd be great to change to a less strict isolation level, locally, here, if that's possible.



(defn mk-DBQuery [& args]
  (let [dbq (ref (apply assoc {}
                        :global-direction :oldest-first

                        :chunk-start-id (vm nil) ;; >=
                        :chunk-size 3 ;; SQL LIMIT.
                        :chunk-end-id (vm nil) ;; <

                        :query-table-name "testing" ;; TODO: Set to NIL.
                        :query-where (vm nil)
                        :query-other (vm nil)
                        :query-params (vm nil)
                        args))]
    dbq))



;; TODO: Private?
(defn db-query-get-chunk [^Ref db-query ^Keyword direction]
  (let [global-direction (:global-direction @db-query)]
    ;; TODO: What about future INSERTs? Need to resync `max(id)' somehow.
    (when-not @(:chunk-start-id @db-query) ;; First call?
      (case global-direction
        :oldest-first (vm-set (:chunk-start-id @db-query) 0)
        :newest-first (vm-set (:chunk-start-id @db-query)
                              (:max (first (db-stmt (str "SELECT max(id) FROM " (:query-table-name @db-query) ";")))))))
    (let [res (apply db-pstmt (str "SELECT * FROM " (:query-table-name @db-query)

                                   " WHERE "
                                   (case [direction global-direction]
                                     [:forward :oldest-first] "id >= ?"
                                     [:backward :oldest-first] "id < ?"
                                     [:forward :newest-first] "id <= ?"
                                     [:backward :newest-first] "id > ?")
                                   (when-let [where @(:query-where @db-query)]
                                     (str " AND " where))

                                   " ORDER BY " (case [direction global-direction]
                                                  [:forward :oldest-first] "id ASC"
                                                  [:backward :oldest-first] "id DESC"
                                                  [:forward :newest-first] "id DESC"
                                                  [:backward :newest-first] "id ASC")

                                   (when-let [other @(:query-other @db-query)]
                                     (str " " other))

                                   " LIMIT ?;")
                     (conj (into [(if-let [end-id (and (= :forward direction) @(:chunk-end-id @db-query))]
                                    (case global-direction
                                      :oldest-first (inc end-id)
                                      :newest-first (dec end-id))
                                    @(:chunk-start-id @db-query))]
                                 @(:query-params @db-query))
                           (:chunk-size @db-query)))]

      (when-not (empty? res)
        (vm-set (:chunk-start-id @db-query)
                (case direction
                  :forward (:id (first res))
                  :backward (:id (last res))))
        (vm-set (:chunk-end-id @db-query)
                (case direction
                  :forward (:id (last res))
                  :backward (:id (first res)))))

      (case direction
        :forward res
        :backward (reverse res)))))



(defn db-query-next-chunk [^Ref db-query]
  "Returns the 'next' chunk (Coll of result sets) in series of chunks and moves the internal cursor -- or returns an EMPTY? Coll
if no next chunk was available."
  (db-query-get-chunk db-query :forward))



(defn db-query-prev-chunk [^Ref db-query]
  "Returns the 'previous' chunk (Coll of result sets) in series of chunks and moves the curser -- or returns an an EMPTY? Coll
if no previous chunk was available."
  (db-query-get-chunk db-query :backward))





;;;;;;;

(defn db-query-test []
  (swsync
   (let [dbq (mk-DBQuery :query-where (vm "value > 100 AND value < 200")
                         ;:global-direction (vm :newest-first)
                         )]
     (println (db-query-next-chunk dbq))
     (println "--")
     (println (db-query-next-chunk dbq))
     (println (db-query-next-chunk dbq))
     (println (db-query-next-chunk dbq))
     (println (db-query-next-chunk dbq))
     (println "---")
     (println (db-query-prev-chunk dbq))
     (println (db-query-prev-chunk dbq))
     (println (db-query-prev-chunk dbq))
     (println (db-query-prev-chunk dbq)))))
