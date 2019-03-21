(in-ns 'symbolicweb.core)


;;; Custom Ordered: LIMIT + OFFSET
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn db-query-get-chunk "RELATION: SQL or [SQL PARAMS]"
  [relation ^Long offset ^Long limit
   & {:keys [where order-by other params]}]
  (let [res (apply db-pstmt (str "SELECT * FROM " (if (string? relation)
                                                    relation
                                                    (str "(" (first relation) ") AS db_query_get_chunk_rel"))

                                 (when where
                                   (str " WHERE " where))

                                 (when order-by
                                   (str " ORDER BY " order-by))

                                 (when other
                                   (str " " other))

                                 " LIMIT ? OFFSET ?;")
                   (concat (when-not (string? relation)
                             (second relation))
                           params
                           [limit offset]))]
    res))



(defn db-query-seq "Returns a LazySeq of IDs."
  [relation ^Long offset ^Long limit
   & {:keys [where order-by other params]}]
  (let [chunk (db-query-get-chunk relation offset limit
                                  :where where :order-by order-by :other other :params params)]
    (when-not (empty? chunk)
      (concat chunk
              (lazy-seq (db-query-seq relation (+ offset limit) limit
                                      :where where :order-by order-by :other other :params params))))))



;;; Globally Ordered: ORDER BY + index without OFFSET for performance
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; http://www.postgresql.org/docs/current/static/indexes-ordering.html
;;
;; Fast chunked DB query results by using LIMIT and ORDER BY (without OFFSET) on indexed field.
;; Used for fast pagination or similar over huge amounts of data.
;;
;; TODO:
;;
;;   * It'd perhaps be great to change to a less strict isolation level, locally, here, if that's possible.
;;   * Writes (via e.g. DAOs) know what chunks might need updating. Might be able to sync this on MTX side?
;;   * Rethink the use of >= and <=. It is perhaps more common to not include the tail;
;;     "from and including, up to but not including".
;;   * This used to support subqueries (via RELATION arg), but does not anymore. Fix this.

(defn db-ordered-query-get-chunk "Returns a chunk of a complete result. See DB-QUERY-SEQ for a way to get hold of the complete (e.g. big) result without running out of memory.

  RELATION: DB table or view name (String).

  GLOBAL-DIRECTION: :LESSER-FIRST or :GREATER-FIRST

  FROM: Relative id, timestamp or similar of chunk start (inclusive).
        If :LAST is given and GLOBAL-DIRECTION is :GREATER-FIRST, the last DB entry is implied.

  DIRECTION: :DOWN or :UP. I.e. based on order of rows in result.

  SIZE: Size of chunk. I.e. LIMIT in SQL query.

  :WHERE: E.g. \"mod(value, 2) = 1\"

  :ORDER-BY: Name of field that can be ordered (String).

  :PARAMS: Params (prepared DB statement) for :WHERE and :OTHER (in that order)."
  [relation ^Keyword global-direction from ^Keyword direction ^Long size
   & {:keys [order-by where other params]
      :or {order-by "id"}}]
  (apply db-pstmt (str "SELECT * FROM " (if (string? relation)
                                          relation
                                          (str "(" (first relation) ") AS db_ordered_query_get_chunk_rel"))

                       " WHERE "
                       (case [direction global-direction]
                         [:down :lesser-first] (str order-by " >= ?")
                         [:up   :lesser-first] (str order-by " <= ?")
                         [:down :greater-first] (if (= :last from)
                                                  (str order-by " <= (SELECT max(" order-by ") FROM " relation ")")
                                                  (str order-by " <= ?"))
                         [:up   :greater-first] (if (= :last from)
                                                  (str order-by " >= (SELECT max(" order-by ") FROM " relation ")")
                                                  (str order-by " >= ?")))
                       (when where
                         (str " AND " where))

                       " ORDER BY " order-by " "
                       (case [direction global-direction]
                         [:down :lesser-first] "ASC"
                         [:up   :lesser-first] "DESC"
                         [:down :greater-first] "DESC"
                         [:up   :greater-first] "ASC")

                       (when other
                         (str " " other))

                       " LIMIT ?;")

         (concat (when-not (string? relation)
                   (rest relation))
                 (when-not (and (= :greater-first global-direction)
                                (= :last from))

                   [from])
                 params
                 [size])))



;; TODO: Check this out: https://github.com/timescale/timescaledb
(defn db-ordered-query-seq "Wraps chunks from DB-ORDERED-QUERY-GET-CHUNK in a LazySeq.

  SIZE: Size of internal chunks; how much to fetch at a time from the DB when consuming data from the LazySeq.

  See DB-ORDERED-QUERY-GET-CHUNK docstring for description of other parameters. A DB table with 10 entries, IDs 1 to 10, would
  give results like:

  (swsync (doall (take 3 (db-ordered-query-seq \"testing\" :lesser-first 5   :down 2)))) => (5 6 7)
  (swsync (doall (take 3 (db-ordered-query-seq \"testing\" :lesser-first 5   :up 2))))   => (5 4 3)
  (swsync (doall (take 3 (db-ordered-query-seq \"testing\" :greater-first 5  :down 2)))) => (5 4 3)
  (swsync (doall (take 3 (db-ordered-query-seq \"testing\" :greater-first 5  :up 2))))   => (5 6 7)
  (swsync (doall (take 3 (db-ordered-query-seq \"testing\" :greater-first -1 :down 2)))) => (10 9 8)


  Mapping to DAOs goes like this:

  (swsync (doall (take 3 (map #(db-get % \"testing\")
                              (db-ordered-query-seq :testing :lesser-first 5 :down 2)))))"
  [relation ^Keyword global-direction from ^Keyword direction ^Long size
   & {:keys [step-forward step-backward order-by where other params]
      :or {step-forward inc
           step-backward dec
           order-by "id"}}]
  ;; TODO: Args are repeated over and over again here.
  (let [chunk (db-ordered-query-get-chunk relation global-direction from direction size
                                          :order-by order-by :where where :other other :params params)]
    (when-not (empty? chunk)
      (concat chunk
              (lazy-seq (db-ordered-query-seq relation global-direction
                                              (case [direction global-direction]
                                                [:down :lesser-first] (step-forward (last chunk))
                                                [:up :lesser-first] (step-backward (last chunk))
                                                [:down :greater-first] (step-backward (last chunk))
                                                [:up :greater-first] (step-forward (last chunk)))
                                              direction size
                                              :step-forward step-forward :step-backward step-backward
                                              :order-by order-by :where where :other other :params params))))))
