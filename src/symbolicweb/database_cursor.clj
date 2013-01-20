(in-ns 'symbolicweb.core)



(do
  (with-db-conn
    (jdbc/do-commands "DECLARE blah SCROLL CURSOR WITH HOLD FOR SELECT * FROM testing ORDER BY id;"))
  (with-db-conn
    (jdbc/with-query-results res ["FETCH FORWARD 5 IN blah;"]
      (dbg-prin1 res))
    (jdbc/with-query-results res ["FETCH FORWARD 5 IN blah;"]
      (dbg-prin1 res))
    (jdbc/do-commands "CLOSE blah;")))
