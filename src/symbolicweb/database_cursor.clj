(in-ns 'symbolicweb.core)


(defn mk-DBCursor [^Lifetime lifetime ^String sql-query]
  ;; TODO: Escaping for SQL-QUERY?
  ;;(assert lifetime)
  (let [cursor-id (str "sw_cursor_" (generate-uid))
        sql (str "DECLARE " cursor-id " SCROLL CURSOR WITH HOLD FOR " sql-query ";")] ;; TODO: ; needed here?
    (with-db-conn
      (jdbc/do-commands sql))
    cursor-id))



(defn db-cursor-fetch [db-cursor ^Long range-start ^Long range-end]
  (assert (or (> range-end range-start)
              (= range-start range-end)))
  (with-db-conn
    (jdbc/do-commands (str "MOVE ABSOLUTE " range-start " IN " db-cursor ";"))
    (jdbc/with-query-results res [(str "FETCH FORWARD " (- range-end range-start) " IN " db-cursor ";")]
      (doall res))))



(defn db-cursor-close [db-cursor]
  (with-db-conn
    (jdbc/do-commands (str "CLOSE " db-cursor ";"))))



(defn db-cursor-test []
  (let [db-cursor (mk-DBCursor nil "SELECT * FROM testing ORDER BY id")]
    (dbg-prin1 (db-cursor-fetch db-cursor 0 5))
    (dbg-prin1 (db-cursor-fetch db-cursor 5 10))
    (db-cursor-close db-cursor)))





#_(do
  (with-db-conn
    (jdbc/do-commands "DECLARE blah SCROLL CURSOR WITH HOLD FOR SELECT * FROM testing ORDER BY id;"))
  (with-db-conn
    (jdbc/with-query-results res ["FETCH FORWARD 5 IN blah;"]
      (dbg-prin1 res))
    (jdbc/with-query-results res ["FETCH FORWARD 5 IN blah;"]
      (dbg-prin1 res))
    (jdbc/do-commands "CLOSE blah;")))


#_(with-db-conn
  (jdbc/with-query-results res ["SELECT pg_cursors;"]
    (dbg-prin1 res)))
