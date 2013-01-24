(in-ns 'symbolicweb.core)

;; TODO: Think about isolation level here.


(defonce -cursor-lifetime-root- (dosync (mk-LifetimeRoot)))



(defn mk-DBCursor ^String [^Lifetime lifetime ^String sql & params]
  #_(assert (not *in-swsync?*)
            "mk-DBCursor: Can't do this within SWSYNC dynamic context; SQL cursors and prepared transactions (2pct) don't mix.")
  (let [cursor-id (str "sw_cursor_" (generate-uid))]
    ;;(apply db-pstmt (str "DECLARE " cursor-id " SCROLL CURSOR WITH HOLD FOR " sql ";") params)
    (add-lifetime-activation-fn lifetime
                                (fn [_]
                                  (println "activating..")
                                  (with-sw-io nil
                                    (with-db-conn
                                      (apply db-stmt (str "DECLARE " cursor-id " SCROLL CURSOR WITH HOLD FOR " sql ";")
                                             params)))))
    (add-lifetime-deactivation-fn lifetime
                                  (fn [_]
                                    (println "deactivating..")
                                    (with-sw-io nil
                                      (with-db-conn
                                        (db-cursor-close cursor-id)))))
    cursor-id))



(defn db-cursor-fetch [^String db-cursor ^Long range-start ^Long range-end]
  (assert (or (> range-end range-start)
              (= range-start range-end)))
  (db-stmt (str "MOVE ABSOLUTE " range-start " IN " db-cursor  ";"))
  (db-stmt (str "FETCH FORWARD " (- range-end range-start) " IN " db-cursor ";")))



(defn db-cursor-close [db-cursor]
  (db-stmt (str "CLOSE " db-cursor ";")))




(defn db-cursor-test2 []
  (swsync
   (let [lifetime (mk-LifetimeRoot)
         db-cursor (mk-DBCursor lifetime "SELECT * FROM testing ORDER BY id")]
     (do-lifetime-activation lifetime)
     (with-sw-io nil
       (Thread/sleep 1000)
       (swsync
         (dbg-prin1 (db-cursor-fetch db-cursor 0 5))
         (println "a")
         (dosync (do-lifetime-deactivation lifetime))
         (println "b"))))))


(defn db-cursor-test []
  (with-db-conn
    (let [db-cursor (mk-DBCursor nil "SELECT * FROM testing ORDER BY id")]
      (dbg-prin1 (db-cursor-fetch db-cursor 0 5))
      (dbg-prin1 (db-cursor-fetch db-cursor 5 10))
      (dbg-prin1 (db-cursor-fetch db-cursor 10000 10010))
      (dbg-prin1 (db-cursor-fetch db-cursor 149995 150000))
      (db-cursor-close db-cursor)
      db-cursor)))
