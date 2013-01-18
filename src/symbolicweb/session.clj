(in-ns 'symbolicweb.core)



(defn %mk-SessionTable []
  (with-db-conn
    (jdbc/do-commands
     (str "CREATE TABLE sessions ("
          "id text PRIMARY KEY"
          ", created timestamp without time zone NOT NULL"
          ", touched timestamp without time zone NOT NULL"
          ");"))))



(defn mk-Session [id & args]
  "ID is session cookie value or NIL when creating new session."
  (let [session (ref (apply assoc {}
                            :type ::Session
                            :id id

                            :logged-in? (vm false)
                            :user-model (vm nil) ;; Reference to UserModel so we can remove ourself from it when we are GCed.
                            :last-activity-time (atom (System/currentTimeMillis))
                            :viewports {}

                            :session-data (ref {})

                            :mk-viewport-fn (fn [request ^Ref session]
                                              (throw (Exception. "mk-Session: No :MK-VIEWPORT-FN given.")))

                            :request-handler #'default-request-handler
                            :rest-handler #'default-rest-handler
                            :ajax-handler #'default-ajax-handler
                            :aux-handler #'default-aux-handler

                            :one-shot? false
                            args))]

    (letfn [(add-new-db-entry []
              ;; TODO: _Very_ small chance of UUID collision, but not a security problem though since the DB col is UNIQUE.
              (let [cookie-value (generate-uuid)
                    db-entry (jdbc/insert-record :sessions (let [ts (datetime-to-sql-timestamp (time/now))]
                                                             {:id cookie-value
                                                              :touched ts
                                                              :created ts}))]
                (alter session assoc
                       :db-entry db-entry
                       :id cookie-value)))]

      (when-not (:one-shot? @session)
        (if id
          (if-let [db-entry (jdbc/with-query-results res ["SELECT * FROM sessions WHERE id = ? LIMIT 1;" id]
                              (doall (first res)))]
            (do
              (jdbc/update-values :sessions ["id = ?" (:id db-entry)]
                                  {:touched (datetime-to-sql-timestamp (time/now))})
              (alter session assoc :db-entry db-entry)) ;; TODO: Update timestamp.
            (add-new-db-entry))
          (add-new-db-entry))
        (vm-alter -num-sessions-model- + 1)
        (swap! -sessions- #(assoc % (:id @session) session))))

    session))



(defn session-get [^Ref session key]
  "KEY is KEYWORD."
  (dosync
   (when-let [res (get @(:session-data @session)
                       key)]
     @res)))



(defn session-set [^Ref session m]
  (dosync
   (doseq [map-entry m]
     (if (contains? @(:session-data @session) (key map-entry))
       ;; An entry with that key already exists; set its value.
       (vm-set ((key map-entry) @(:session-data @session))
               (val map-entry))
       ;; An entry with that key doesn't exist; add the key and value (wrapped in a ValueModel).
       (alter (:session-data @session)
              assoc (key map-entry) (vm (val map-entry)))))))



(defn session-del [^Ref session key]
  (dosync
   (alter (:session-data @session)
          dissoc key)))



(defn find-session-constructor [request]
  (loop [session-types @-session-types-]
    (when-first [session-type session-types]
      (let [session-type (val session-type)]
        (if ((:fit-fn session-type) request)
          (:session-constructor-fn session-type)
          (recur (next session-types)))))))



(declare json-parse)
(defn find-or-create-session [request]
  (let [cookie-value (:value (get (:cookies request) -session-cookie-name-))]
    (if-let [session (get @-sessions- cookie-value)]
      session
      (if-let [^Fn session-constructor (find-session-constructor request)]
        (swsync
         (session-constructor cookie-value
                              :one-shot? (with (get (:query-params request) "_sw_session_one_shot_p")
                                           (if (nil? it)
                                             false
                                             (json-parse it)))))
        (mk-Session cookie-value
                    :rest-handler not-found-page-handler
                    :one-shot? true)))))



(defn undefapp [name]
  (swap! -session-types- #(dissoc % name)))
