(in-ns 'symbolicweb.core)



(defn %mk-SessionTable []
  (with-db-conn
    (jdbc/do-commands
"
CREATE TABLE sessions (
    id text NOT NULL,
    created timestamp without time zone NOT NULL,
    touched timestamp without time zone NOT NULL,
    data text DEFAULT '{}'::text NOT NULL,
    application text
);
")))



(defn mk-Session [id & args]
  "ID is session cookie value or NIL when creating new session."
  (let [session (ref (apply assoc {}
                            :type ::Session
                            :id id

                            :logged-in? (vm false)
                            :user-model (vm nil) ;; Reference to UserModel so we can remove ourself from it when we are GCed.
                            :last-activity-time (atom (System/currentTimeMillis))

                            :viewports (ref {})

                            :mk-viewport-fn (fn [request ^Ref session]
                                              (throw (Exception. "mk-Session: No :MK-VIEWPORT-FN given.")))

                            :request-handler #'default-request-handler
                            :rest-handler #'default-rest-handler
                            :ajax-handler #'default-ajax-handler
                            :aux-handler #'default-aux-handler

                            :one-shot? false
                            args))]

    (letfn [(add-new-db-entry []
              ;; TODO: _Very_ small chance of UUID collision, but not a security problem since the DB col is UNIQUE.
              (let [cookie-value (generate-uuid)
                    db-entry (db-insert :sessions (let [ts (datetime-to-sql-timestamp (time/now))]
                                                    {:id cookie-value
                                                     :application (name (:name (:session-type @session)))
                                                     :touched ts
                                                     :created ts}))]
                (alter session assoc
                       :id cookie-value
                       :json-store (db-json-store-get "sessions" cookie-value :data))))]

      (when-not (:one-shot? @session)
        (if id
          (if-let [db-entry (first (db-pstmt "SELECT * FROM sessions WHERE id = ? LIMIT 1;" id))]
            (do
              (db-update :sessions {:touched (datetime-to-sql-timestamp (time/now))}
                         ["id = ?" (:id db-entry)])
              (alter session assoc
                     :json-store (db-json-store-get "sessions" (:id db-entry) :data)))
            (add-new-db-entry))
          (add-new-db-entry)))

      (when-not (:id @session)
        (alter session assoc :id (generate-uuid)))

      (alter -sessions- assoc (:id @session) session)
      (vm-alter -num-sessions-model- + 1)

      session)))



(defn session-get [^Ref session ^Keyword k]
  (db-json-get (:json-store @session) k))



(defn session-del [^Ref session ^Keyword k]
  (vm-alter (:json-store @session)
            dissoc k))



(defn find-session-constructor [request]
  (loop [session-types @-session-types-]
    (when-first [session-type session-types]
      (let [session-type (val session-type)]
        (if ((:fit-fn session-type) request)
          session-type
          (recur (next session-types)))))))



(defn search-engine? [request]
  (let [^String user-agent (get (:headers request) "user-agent")]
    (not (or (neg? (.indexOf user-agent "bot"))
             (neg? (.indexOf user-agent "Mediapartners-Google"))
             (neg? (.indexOf user-agent "ia_archiver"))
             ))))



(declare json-parse)
(defn find-or-create-session [request]
  (let [cookie-value (:value (get (:cookies request) -session-cookie-name-))]
    (if-let [session (get (ensure -sessions-) cookie-value)]
      session
      (if-let [session-type (find-session-constructor request)]
        ((:session-constructor-fn session-type)
         cookie-value
         :session-type session-type
         :one-shot? (or (with (get (:query-params request) "_sw_session_one_shot_p")
                          (if (nil? it)
                            false
                            (json-parse it)))
                        (search-engine? request)))
        (do
          (log "FIND-OR-CREATE-SESSION: 404 NOT FOUND:" request)
          (mk-Session cookie-value
                      :rest-handler not-found-page-handler
                      :mk-viewport-fn (fn [request session]
                                        (mk-Viewport request session (mk-bte :root-widget? true))) ;; Dummy.
                      :one-shot? true))))))



(defn undefapp [name]
  (swap! -session-types- #(dissoc % name)))
