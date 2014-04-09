(in-ns 'symbolicweb.core)


;;; http://en.wikipedia.org/wiki/Two-phase_commit_protocol
;;
;; Builds on stuff in database_jdbc.clj.



(defmacro with-db-conn [on-serialization-failure-fn & body]
  `(with-jdbc-conn -pooled-db-spec- ~on-serialization-failure-fn
     ~@body))



(defn db-stmt [^String sql]
  (jdbc-stmt (.deref ^Delay *db*) sql))



(defn db-pstmt [^String sql & params]
  (jdbc-pstmt (.deref ^Delay *db*) sql params))



(defn db-insert
  "E.g. (db-insert :testing {:value 42})"
  ([table-name m]
     (db-insert table-name m true))

  ([table-name m ^Boolean return-result?]
     (let [res (sql/sql (sql/insert table-name [] (sql/values m)
                                    (when return-result? (sql/returning :*))))
           ^String sql (first res)
           params (rest res)]
       (apply db-pstmt sql params))))



(defn db-update [table-name m where]
  "E.g. (db-update :testing {:value 42} [\"id = ?\" 100])"
  (let [res (sql/sql (sql/update table-name m (sql/where where)))
        ^String sql (first res)
        params (rest res)]
    (apply db-pstmt sql params)))



(defn db-delete [table-name where]
  "E.g. (db-delete :testing [\"id = ?\" 100])"
  (let [res (sql/sql (sql/delete table-name (sql/where where)))
        ^String sql (first res)
        params (rest res)]
    (apply db-pstmt sql params)))



(defn abort-2pctx [return-value]
  (throw (ex-info "ABORT-2PCTX" {:2pctx-state :abort, :return-value return-value})))



(defn retry-2pctx [^String msg]
  (throw (ex-info msg {:2pctx-state :retry})))



(defn do-dbtx [^Fn body-fn]
  "  BODY-FN: (fn [DBTX-PREPARE-FN DBTX-COMMIT-FN] ..)"
  (let [db-stmt (delay (.createStatement (.deref ^Delay *db*)))
        dbtx-id (.toString (generate-uid))
        dbtx-phase (atom 0)]
    (try
      #_(println "DO-DBTX: Start transaction.. (0th phase)")

      (body-fn (fn []
                 #_(println "DO-DBTX: ..hold-transaction.. (1st phase)")
                 (assert (= 0 @dbtx-phase))
                 (.execute @db-stmt (str "PREPARE TRANSACTION '" dbtx-id "';"))
                 (.commit @*db*)
                 (.setAutoCommit @*db* true)
                 (reset! dbtx-phase 1))
               (fn []
                 #_(println "DO-DBTX: ..commit transaction. (2st phase)")
                 (assert (= 1 @dbtx-phase))
                 (.execute @db-stmt (str "COMMIT PREPARED '" dbtx-id "';"))
                 (reset! dbtx-phase 2)))

      (catch Throwable e
        #_(println "DO-DBTX: Rolling back phase" @dbtx-phase "DBTX:" e)
        (case @dbtx-phase
          0 nil
          1 (.execute @db-stmt (str "ROLLBACK PREPARED '" dbtx-id "';"))
          2 (assert false "DO-DBTX: This shouldn't happen."))
        (throw e))

      (finally
        (when (.isRealized db-stmt)
          (.close @db-stmt))))))



;; TODO: Very specific *DYN-CTX* stuff being mixed in here sucks.
(defn do-mtx [^Fn body-fn ^Fn prepare-fn ^Fn commit-fn]
  "  PREPARE-FN is called just before the MTX is held; we might still roll back.
  COMMIT-FN is called while the MTX is held; we will not roll back here."
  (let [mtx-phase (atom 0) ;; Side-effect applied to this is used to detect MTX retries.
        dyn-ctx (atom nil)]
    (do1 (dosync
          (binding [*dyn-ctx* (atom {})]
            (if-not (zero? @mtx-phase)
              (retry-2pctx "DO-MTX: Retry.")
              (do
                (reset! mtx-phase 1)
                (do1 (body-fn)
                  ;; Other end of this is in URL-ALTER-QUERY-PARAMS.
                  (doseq [[^Ref viewport m] (:viewports @*dyn-ctx*)]
                    (when-let [^Fn f (::url-alter-query-params m)]
                      (f)))
                  (when (.isRealized ^Delay *db*)
                    (prepare-fn))
                  (reset! dyn-ctx @*dyn-ctx*))))))
      (try
        ;; Other end of this is in ADD-RESPONSE-CHUNK.
        (doseq [[^Ref viewport m] (:viewports @dyn-ctx)]
          (locking viewport
            (.append ^StringBuilder (:response-str @viewport)
                     (.toString ^StringBuilder (::comet-string-builder m)))
            ((::comet-response-trigger m))))
        ;; At this point the MTX has been committed, and we are ready to commit the prepared (held) DBTX. Note how this means that
        ;; view of memory will be more recent than view of DB from other 2PCTX' point of view.
        (when (.isRealized ^Delay *db*)
          (commit-fn))
        (catch Throwable e
          ;; TODO: Retry a few times (perhaps the DB is restarting) before giving up like this?
          (when (.isRealized ^Delay *db*)
            (println "## DO-MTX: Failure while pushing data to client or committing data to DB. Giving up; shutting down server to avoid data inconsistency and corruption.")
            (stop-server))
          (throw e))))))



(defn do-2pctx [^Fn body-fn]
  (assert (not (clojure.lang.LockingTransaction/isRunning))
          "DO-2PCTX: SWSYNC within DOSYNC (or another SWSYNC) not allowed.")
  (let [done? (atom false)
        retval (atom nil)]
    (while (not @done?)
      (try
        (reset! retval
                (with-db-conn (fn [] (retry-2pctx "DO-2PCTX: Retry."))
                  (do-dbtx (fn [^Fn dbtx-prepare-fn ^Fn dbtx-commit-fn]
                             (do-mtx body-fn dbtx-prepare-fn dbtx-commit-fn)))))
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
    @retval))



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
  (with-db-conn nil
    (.setAutoCommit @*db* true)
    (doseq [res (db-stmt "SELECT gid FROM pg_prepared_xacts;")]
      (println "deleting prepared transaction:" (:gid res))
      (db-stmt (str "ROLLBACK PREPARED '" (:gid res) "';")))
    (.setAutoCommit @*db* false)))



#_(defn db-common-test []
  (let [r (ref 0)
        f1 (future
             (swsync
              (println "F1 starting..")
              (db-update :testing {:value (ensure r)} ["id = ?" 391878])
              (Thread/sleep 1000))
             (println "F1 done."))
        f2 (future
             (swsync
              (println "F2 starting..")
              (db-update :testing {:value -2} ["id = ?" 391878])
              (ref-set r 42)
              (Thread/sleep 1000))
             (println "F2 done."))]
    @f1
    @f2
    nil))
