(in-ns 'symbolicweb.core)


;;; http://en.wikipedia.org/wiki/Two-phase_commit_protocol
;;
;; Builds on stuff in database_jdbc.clj.


(def -sqlingvo-db- (sqlingvo.db/db :postgresql))



(defmacro with-db-conn [on-serialization-failure-fn & body]
  `(with-jdbc-conn -pooled-db-spec- ~on-serialization-failure-fn
     ~@body))



(defn db-stmt [^String sql]
  (jdbc-stmt (.deref ^Delay *db*) sql))



(defn db-pstmt [^String sql & params]
  (jdbc-pstmt (.deref ^Delay *db*) sql params))



;; TODO: Add support for [m] or just m here.
(defn db-insert
  "E.g. (db-insert :testing {:value 42})"
  ([table-name m]
   (db-insert table-name m true))

  ([table-name m ^Boolean return-result?]
   (let [res (sql/sql (sql/insert -sqlingvo-db- table-name [] (sql/values [m])
                                  (when return-result? (sql/returning :*))))
         ^String sql (first res)
         params (rest res)]
     (apply db-pstmt sql params))))



(defn db-update [table-name m where]
  "E.g. (db-update :testing {:value 42} '(= :id 100))"
  (let [res (sql/sql (sql/update -sqlingvo-db- table-name m (sql/where where)))
        ^String sql (first res)
        params (rest res)]
    (apply db-pstmt sql params)))



(defn db-delete [table-name where]
  "E.g. (db-delete :testing '(= :id 100))"
  (let [res (sql/sql (sql/delete -sqlingvo-db- table-name (sql/where where)))
        ^String sql (first res)
        params (rest res)]
    (apply db-pstmt sql params)))



(defn abort-2pctx [return-value]
  (throw (ex-info "ABORT-2PCTX" {:2pctx-state :abort, :return-value return-value})))



(defn retry-2pctx [^String msg]
  (throw (ex-info msg {:2pctx-state :retry})))



(declare stop-server)
(defn do-dbtx [^Fn dbtx-body-fn]
  "  2PCTX-BODY-FN: (fn [DBTX-COMMIT-FN] ..)"
  (let [dbtx-phase (atom 0)]
    (try
      (dbtx-body-fn
       (fn []
         (assert (= 0 @dbtx-phase))
         (when (.isRealized ^Delay *db*)
           (.commit ^com.zaxxer.hikari.pool.HikariProxyConnection (.deref ^Delay *db*)))
         (reset! dbtx-phase 1)))

      (catch Throwable e
        #_(println "DO-DBTX: Rolling back phase" @dbtx-phase "DBTX:" e)
        (case (int @dbtx-phase)
          0 (when (.isRealized ^Delay *db*)
              (.rollback ^com.zaxxer.hikari.pool.HikariProxyConnection (.deref ^Delay *db*)))
          1 (do
              (println "DO-DBTX: This shouldn't happen; MTX can rollback while DBTX can't. :(")
              (println "DO-DBTX: Stopping server to avoid data corruption.")
              (clojure.stacktrace/print-stack-trace e 50)
              (stop-server)))
        (throw (or (:dyn-ctx-validator-throwable (ex-data e))
                   e))))))



;; TODO: Unrelated *DYN-CTX* stuff being mixed in here sucks.
;; Perhaps a simple fix would be to name the key in *DYN-CTX* ::POST-COMMIT or similar, but order of ::URL-ALTER-QUERY-PARAMS
;; vs. :VIEWPORT / ::COMET-STRING-BUILDER etc. still matter.
;; TODO: Using a :VALIDATOR like this has significant risks. See issue #48.
(defn do-mtx [^Fn body-fn ^Fn dbtx-commit-fn]
  (let [^Atom phase (atom 0) ;; Side-effect applied to this is used to detect MTX retries.
        ^Ref mtx-done? (ref false)
        ^Ref dyn-ctx (ref false
                          :validator
                          (fn [dyn-ctx]
                            (when dyn-ctx
                              ;; At this point the MTX is prepared or "held" and we can commit the DBTX. A conflict will rollback
                              ;; both the MTX and the DBTX.
                              (try
                                (dbtx-commit-fn)
                                (catch Throwable e
                                  (throw (ex-info "Deal with Clojure :VALIDATOR exception retardedness: http://goo.gl/2ynLdL"
                                                  {:dyn-ctx-validator-throwable e}))))
                              (reset! phase 2)

                              ;; Other end of this is in ADD-RESPONSE-CHUNK.
                              (try
                                (doseq [[^Ref viewport m] (:viewports dyn-ctx)]
                                  (locking viewport
                                    (.append ^StringBuilder (:response-str @viewport)
                                             (.toString ^StringBuilder (::comet-string-builder m)))
                                    ((::comet-response-trigger m))))
                                (catch Throwable e
                                  ;; TODO: STOP-SERVER here?
                                  ;; Will have to "eat" (ignore) any problem at this point since the DBTX has been commited.
                                  (println "DO-MTX: Eating exception:" e)
                                  (clojure.stacktrace/print-stack-trace e 50))))
                            true))]
    (try
      (dosync
       (binding [*dyn-ctx* (atom {})]
         (if-not (zero? ^long @phase)
           (retry-2pctx "DO-MTX: Retry.")
           (do
             (reset! phase 1)
             (do1 (body-fn)
               ;; Other end of this is in URL-ALTER-QUERY-PARAMS.
               (doseq [[^Ref viewport m] (:viewports (.deref ^Atom *dyn-ctx*))]
                 (when-let [^Fn f (::url-alter-query-params m)]
                   (f)))
               (commute dyn-ctx (with (.deref ^Atom *dyn-ctx*) (fn [_] it)))
               (ref-set mtx-done? true))))))
      (finally
        (when (and (= 2 (.deref phase)) ;; DBTX commited?
                   (not (.deref mtx-done?))) ;; ..but MTX not?   ;; ### http://dev.clojure.org/jira/browse/CLJ-1809 ###
          ;; At this point the MTX has been rolled back, but the DBTX has been committed. This cannot be dealt
          ;; with so we stop the server. See issue #48.
          (stop-server)
          (throw (ex-info "DO-MTX: Some :VALIDATOR failed or something else went wrong. Stopping server. See issue #48." {})))))))



(defn do-2pctx [^Fn body-fn]
  #_(assert (not (clojure.lang.LockingTransaction/isRunning))
            "DO-2PCTX: SWSYNC within DOSYNC (or another SWSYNC) not allowed.")
  (if (clojure.lang.LockingTransaction/isRunning)
    (body-fn)
    (let [done? (atom false)
          retval (atom nil)]
      (while (not @done?)
        (try
          (reset! retval
                  (with-db-conn (fn [] (retry-2pctx "DO-2PCTX: Retry."))
                    (do-dbtx (fn [^Fn dbtx-commit-fn]
                               (do-mtx body-fn dbtx-commit-fn)))))
          (reset! done? true)
          (catch clojure.lang.ExceptionInfo e
            (case (:2pctx-state (ex-data e))
              :retry
              (do
                #_(println "DO-2PCTX: :RETRY:" e))

              :abort
              (let [ex-retval (:return-value (ex-data e))]
                #_(println "DO-2PCTX: :ABORT, :RETURN-VALUE:" ex-retval)
                (reset! done? true)
                (reset! retval ex-retval))

              (throw e)))))
      @retval)))



(defn swsync-abort
  "Abort SWSYNC transaction in progress; rolls back all transactions in progress; MTX and DBTX."
  ([]
   (swsync-abort nil))

  ([retval]
   (abort-2pctx retval)))



(defn nsm-get [^String table-name ^Long branch-id]
  "Nested Set Model query."
  ;; TODO: Grab IDs only.
  (db-pstmt (str "SELECT node.name FROM " table-name " AS node, " table-name " AS parent"
                 " WHERE node.lft BETWEEN parent.lft AND parent.rgt"
                 " AND parent.id = ?"
                 " ORDER BY node.lft;")
            branch-id))



;; TODO: Finish this..
#_(defn nsm-insert [^String table-name ^Long right-of]
    "Nested Set Model insert right of existing node; creating a sibling."
    (db-pstmt (str "UPDATE " table-name " SET rgt = rgt + 2 WHERE rgt > ?;")
              right-of)
    (db-pstmt (str "UPDATE " table-name " SET lft = lft + 2 WHERE lft > ?;")
              right-of)
    (db-insert table-name {:name "GAME CONSOLES" "lft" (inc right-of ) "rgt" (+ 2 right-of)}))



;; TODO: Only works when parent doesn't already have children. In other cases, NSM-INSERT must be used.
;; TODO: Finish this..
#_(defn nsm-add-child [^String table-name ^long parent-id]
    (let [lft (:lft (first (db-pstmt (str "SELECT lft FROM " table-name " WHERE id = ? LIMIT 1;") parent-id)))]
      (db-pstmt (str "UPDATE " table-name " SET rgt = rgt + 2 WHERE rgt > ?;")
                lft)
      (db-pstmt (str "UPDATE " table-name " SET lft = lft + 2 WHERE lft > ?;")
                lft)
      (db-insert table-name {:name "FRS" :lft (inc lft) :rgt (+ 2 lft)})))



(defn al-descendants [^String table-name ^Long id & {:keys [id-name parent-name
                                                            columns
                                                            where order-by
                                                            params]
                                                     :or {id-name "id" parent-name "parent"}}]
  "Adjacency List: Get descendants."
  [(str "WITH RECURSIVE q AS "
        "((SELECT " id-name ", " parent-name (cl-format false "~{, ~A~}" columns)
        " FROM " table-name " WHERE " id-name " = ?"
        (when order-by
          (str " ORDER BY " order-by))
        ") UNION ALL "
        "SELECT self." id-name ", self." parent-name (cl-format false "~{, self.~A~}" columns)
        " FROM q JOIN " table-name " self ON self." parent-name " = q." id-name ") "
        "SELECT * FROM q")
   (concat [id] params)])



(defn al-descendants-to-level [^String table-name ^Long id ^Long level & {:keys [id-name parent-name
                                                                                 params]
                                                                          :or {id-name "id" parent-name "parent"}}]
  "Adjecency List: Get descendants down to a LEVEL or limit.
Returns :ID and :PARENT columns."
  [(str "WITH RECURSIVE q AS "
        "(SELECT " id-name ", " parent-name ", ARRAY[id] AS level FROM " table-name " hc WHERE " id-name " = ?"
        " UNION ALL "
        "SELECT hc." id-name ", hc." parent-name ", q.level || hc." id-name " FROM q JOIN " table-name
        "  hc ON hc." parent-name " = q." id-name " WHERE array_upper(level, 1) < ?) "
        "SELECT " id-name ", " parent-name " FROM q ORDER BY level")
   (concat [id level] params)])



(defn al-ancestors [^String table-name ^Long id & {:keys [id-name parent-name
                                                          columns]
                                                   :or {id-name "id" parent-name "parent"}}]
  "Adacency List: Get ancestors.
Returns :ID and :PARENT columns."
  [(str "WITH RECURSIVE q AS "
        "(SELECT h.*, 1 AS level FROM " table-name " h WHERE " id-name " = ?"
        " UNION ALL "
        "SELECT  hp.*, level + 1 FROM q JOIN " table-name " hp ON hp." id-name " = q." parent-name ") "
        "SELECT " id-name ", " parent-name (cl-format false "~{, ~A~}" columns) " FROM q ORDER BY level DESC")
   [id]])





























;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;




(defn db-delete-prepared-transactions []
  (swsync
   (doseq [res (db-stmt "SELECT gid FROM pg_prepared_xacts;")]
     (println "deleting prepared transaction:" (:gid res))
     (db-stmt (str "ROLLBACK PREPARED '" (:gid res) "';")))))




#_(defn db-common-test []
    (let [res (swsync
               (db-insert :test {:value 0}))]
      (let [r (ref 0)
            f1 (future
                 (swsync
                  (println "F1 starting")
                  (alter r inc)
                  (dbg-prin1 r)
                  (db-update :test {:value (ensure r)} `(= :id ~(:id (first res))))
                  (println "F1 sleeping")
                  (Thread/sleep 1000)
                  )
                 (println "F1 done"))
            f2 (future
                 (swsync
                  (println "F2 starting")
                  (alter r inc)
                  (dbg-prin1 r)
                  (db-update :test {:value (ensure r)} `(= :id ~(:id (first res))))
                  (println "F2 sleeping")
                  (Thread/sleep 1000)
                  )
                 (println "F2 done"))]
        @f1
        @f2
        (swsync
         (assert (= (dbg-prin1 (ensure r))
                    (:value (dbg-prin1 (first (db-pstmt "SELECT * FROM test WHERE id = ? LIMIT 1;" (:id (first res)))))))))
        nil)))
