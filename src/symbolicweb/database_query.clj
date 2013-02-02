(in-ns 'symbolicweb.core)

;;; Chunked DB query results by using LIMIT and ORDER BY on indexed (for performance) ID fields.
;;; Used for e.g. pagination or "infinite scroll".



(defn mk-DBQuery [& args]
  (let [model (ref (apply assoc {}
                          :chunk-start-id (vm 0) ;; >=
                          :chunk-size 3 ;; SQL LIMIT.
                          :chunk-end-id (vm nil) ;; <

                          :query-table-name (vm "testing") ;; TODO: Set to NIL.
                          :query-where (vm nil)
                          :query-order-by (vm nil)
                          :query-other (vm nil)
                          :query-params (vm nil)
                          args))]
    model))


;; TODO: Private?
(defn db-query-get-chunk [^Ref db-query ^Keyword direction]
  ;; TODO: Should probably only grab ID here, then return a Coll of IDs instead of RES directly.
  (let [res (apply db-pstmt (str "SELECT * FROM " @(:query-table-name @db-query)
                                 " WHERE id " (case direction
                                                :forward ">= ?"
                                                :backward "< ?")
                                 (when-let [where @(:query-where @db-query)]
                                   (str " AND " where))
                                 (if-let [order-by @(:query-order-by @db-query)]
                                   (str " ORDER BY " order-by ", ")
                                   (str " ORDER BY "))
                                 (case direction
                                   :forward "id ASC"
                                   :backward "id DESC")
                                 (when-let [other @(:query-other @db-query)]
                                   (str " " other))
                                 " LIMIT ?;")
                   (conj (into [@(:chunk-start-id @db-query)]
                               @(:query-params @db-query))
                         (:chunk-size @db-query)))]
    (vm-set (:chunk-start-id @db-query)
            (case direction
              :forward (:id (first res))
              :backward (:id (last res))))
    (vm-set (:chunk-end-id @db-query)
            (case direction
              :forward (:id (last res))
              :backward (:id (first res))))
    (case direction
      :forward res
      :backward (reverse res))))



(defn db-query-next-chunk [^Ref db-query]
  (when @(:chunk-end-id @db-query) ;; First call?
    (vm-set (:chunk-start-id @db-query)
            (+ 1 @(:chunk-end-id @db-query))))
  (db-query-get-chunk db-query :forward)) ;; Sets :CHUNK-END-ID.



(defn db-query-prev-chunk [^Ref db-query]
  (db-query-get-chunk db-query :backward)) ;; Sets :CHUNK-END-ID.
