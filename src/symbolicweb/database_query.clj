(in-ns 'symbolicweb.core)

;;; Chunked DB query results by using LIMIT and ORDER BY without OFFSET on indexed (for performance) ID fields.
;;; Used for e.g. pagination or "infinite scroll".
;;
;;
;; Features and limitations:
;;
;;   * No global sorting by user defined criteria i.e. via ORDER BY.
;;   * Global ordering by newest or oldest entries first is supported though. I.e. by ID column.
;;   * These limitations are tradeoffs for (I hope) scalability and performance.
;;
;;
;; TODO:
;;
;;   * It'd perhaps be great to change to a less strict isolation level, locally, here, if that's possible.
;;   * Since things are always ordered by ID, writes (via e.g. DAOs) know what chunks might need updating.



(defn db-query-get-chunk [table-name ^Keyword global-direction ^Long from-id ^Keyword direction ^Long size
                          & {:keys [where other params]}]
  "Returns a LazySeq of IDs representing a chunk of a complete result. See DB-QUERY-SEQ for a way to get hold of the complete
result.

  GLOBAL-DIRECTION: :OLDEST-FIRST or :NEWEST-FIRST

  FROM-ID: Relative ID of chunk start.
           If -1 and GLOBAL-DIRECTION is :NEWEST-FIRST, the last DB entry is implied.

  DIRECTION: :RIGHT or :LEFT

  SIZE: Size of chunk. E.g. LIMIT in SQL query.

  :WHERE: E.g. \"mod(value, 2) = 1\"

  :PARAMS: Params (prepared DB statement) for :WHERE and :OTHER (in that order)."
  (let [table (name table-name)
        res (apply db-pstmt (str "SELECT id FROM " table

                                 " WHERE "
                                 (case [direction global-direction]
                                   [:right :oldest-first] "id >= ?"
                                   [:left :oldest-first] " id <= ?"
                                   [:right :newest-first] (if (neg? from-id)
                                                            (str "id <= (SELECT max(id) FROM " table ")")
                                                            "id <= ?")
                                   [:left :newest-first] (if (neg? from-id)
                                                           (str "id >= (SELECT max(id) FROM " table ")")
                                                           "id >= ?"))
                                 (when where
                                   (str " AND " where))

                                 " ORDER BY " (case [direction global-direction]
                                                [:right :oldest-first] "id ASC"
                                                [:left :oldest-first] "id DESC"
                                                [:right :newest-first] "id DESC"
                                                [:left :newest-first] "id ASC")

                                 (when other
                                   (str " " other))

                                 " LIMIT ?;")

                   (concat (when-not (and (= :newest-first global-direction)
                                          (neg? from-id))
                             [from-id])
                           params
                           [size]))]
    (map :id res)))



(defn db-query-seq [table-name ^Keyword global-direction ^Long from-id ^Keyword direction ^Long size
                    & {:keys [where other params]}]
  "Returns a LazySeq of IDs representing the complete result set (which can be very big).

  SIZE: Size of internal chunks; how much to fetch at a time from the DB when consuming data from the Seq.


A DB table with 10 entries, IDs 1 to 10, would give results like:

  (swsync (doall (take 3 (db-query-seq :testing :oldest-first 5 :right 2))))  => (5 6 7)
  (swsync (doall (take 3 (db-query-seq :testing :oldest-first 5 :left 2))))   => (5 4 3)
  (swsync (doall (take 3 (db-query-seq :testing :newest-first 5 :right 2))))  => (5 4 3)
  (swsync (doall (take 3 (db-query-seq :testing :newest-first 5 :left 2))))   => (5 6 7)
  (swsync (doall (take 3 (db-query-seq :testing :newest-first -1 :right 2)))) => (10 9 8)


Mapping to DAOs goes like this:

  (swsync (doall (take 3 (map #(db-get % \"testing\")
                              (db-query-seq :testing :oldest-first 5 :right 2)))))"
  (let [chunk (db-query-get-chunk table-name global-direction from-id direction size
                                  :where where :other other :params params)]
    (when-not (empty? chunk)
      (concat chunk
              (lazy-seq (db-query-seq table-name global-direction
                                      (case [direction global-direction]
                                        [:right :oldest-first] (inc (last chunk))
                                        [:left :oldest-first] (dec (last chunk))
                                        [:right :newest-first] (dec (last chunk))
                                        [:left :newest-first] (inc (last chunk)))
                                      direction size
                                      :where where :other other :params params))))))
