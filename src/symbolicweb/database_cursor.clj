(in-ns 'symbolicweb.core)


(defn mk-DBCursor ^String [^Lifetime lifetime ^String sql & params]
  ;;(assert lifetime)
  (assert (not *in-swsync?*)
          "mk-DBCursor: Can't do this within SWSYNC dynamic context; SQL cursors and prepared transactions (2pct) don't mix.")
  (let [cursor-id (str "sw_cursor_" (generate-uid))]
    (apply db-pstmt (str "DECLARE " cursor-id " SCROLL CURSOR WITH HOLD FOR " sql ";") params)
    cursor-id))



(defn db-cursor-fetch [^String db-cursor ^Long range-start ^Long range-end]
  (assert (or (> range-end range-start)
              (= range-start range-end)))
  (db-stmt (str "MOVE ABSOLUTE " range-start " IN " db-cursor  "; "
                "FETCH FORWARD " (- range-end range-start) " IN " db-cursor ";")))



(defn db-cursor-close [db-cursor]
  (db-stmt (str "CLOSE " db-cursor ";")))



(defn db-cursor-test []
  (let [db-cursor (mk-DBCursor nil "SELECT * FROM testing ORDER BY id")]
    (dbg-prin1 (db-cursor-fetch db-cursor 0 5))
    (dbg-prin1 (db-cursor-fetch db-cursor 5 10))
    (dbg-prin1 (db-cursor-fetch db-cursor 10000 10010))
    (dbg-prin1 (db-cursor-fetch db-cursor 149995 150000))
    (db-cursor-close db-cursor)
    db-cursor))





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
