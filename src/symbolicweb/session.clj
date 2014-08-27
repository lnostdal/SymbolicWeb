(in-ns 'symbolicweb.core)



(defn %mk-SessionTable []
  (swsync
    (db-stmt
"
CREATE TABLE sessions (
    id bigserial NOT NULL,
    created timestamp without time zone NOT NULL,
    touched timestamp without time zone NOT NULL,
    json text DEFAULT '{}'::text NOT NULL,
    application text NOT NULL,
    uuid text NOT NULL,
    user_ref bigint
);
ALTER TABLE sessions ADD PRIMARY KEY (id);
ALTER TABLE sessions ADD UNIQUE (uuid);
")))



(defn session-model-clj-to-db-transformer [m]
  "SW --> DB"
  (-> m
      ((fn [m]
         (case (:key m)
           (:type :logged-in? :last-activity-time :viewports :mk-viewport-fn
            :request-handler :rest-handler :ajax-handler :aux-handler
            :one-shot? :temp-data
            :user-handle-login-token)
           (assoc m
             :key nil)

           :session-type
           (assoc m
             :key :application
             :value (name (:name (:value m))))

           :json
           (db-json-to-db-transformer m)

           :user-model
           (assoc m
             :key :user-ref
             :value (when-let [user-model @(:value m)]
                      @(:id @user-model)))

           m)))
      (dao-default-clj-to-db-transformer)))



(defn session-model-db-to-clj-transformer [m]
  "DB --> SW"
  (-> m
      (dao-default-db-to-clj-transformer)
      ((fn [m]
         (case (:key m)
           :user-ref
           (assoc m
             :key :user-model
             :value (when (:value m) (dao-get (:value m) "users")))

           :json
           (db-db-to-json-transformer m)

           m)))))



(defn mk-Session [& args]
  (let [session (ref (apply assoc {}
                            :id (vm nil)
                            :type ::Session
                            :uuid nil
                            :user-model (vm nil)
                            :last-activity-time (atom (System/currentTimeMillis))
                            :viewports (ref {})
                            :mk-viewport-fn (fn [request ^Ref session]
                                              (mk-Viewport request session (mk-bte :root-widget? true))) ;; Dummy.
                            :one-shot? false

                            :temp-data (ref {})

                            :created (datetime-to-sql-timestamp (time/now))
                            :touched (vm (datetime-to-sql-timestamp (time/now)))

                            :request-handler #'default-request-handler
                            :rest-handler #'default-rest-handler
                            :ajax-handler #'default-ajax-handler
                            :aux-handler #'default-aux-handler

                            :json (vm {} (constantly false))
                            args))]
    session))



(swap! -db-cache-constructors- assoc "sessions"
       #(mk-DBCache "sessions"
                    (fn [db-cache id] (mk-Session))
                    identity
                    #'session-model-clj-to-db-transformer
                    #'session-model-db-to-clj-transformer))



(defn spget [^Ref session ^Keyword k & not-found]
  "\"Session Permanent Get\"
Session data stored in DB; permanent."
  (db-json-get (:json @session) k not-found))



(defn spdel [^Ref session ^Keyword k]
  "\"Session Permanent Delete\"
Session data stored in DB; permanent."
  (vm-alter (:json @session) dissoc k))



(defn stput [^Ref session ^Keyword k value]
  "\"Session Temporary Put\"
Session data stored in memory; temporarly."
  (alter (:temp-data @session) assoc
         k value))



(defn stget
  "\"Session Temporary Get\"
Session data stored in memory; temporarly."
  ([^Ref session ^Keyword k]
     (stget session k (vm nil)))

  ([^Ref session ^Keyword k not-found]
     (with (get @(:temp-data @session) k ::not-found)
       (if (= it ::not-found)
         (do
           (stput session k not-found)
           not-found)
         it))))



(defn session-logout [^Ref session]
  (vm-alter (:sessions @@(:user-model @session)) disj session)
  (vm-set (spget session :logged-in?) false)
  (vm-set (:user-model @session) nil))



(defn session-login [^Ref session ^Ref user-model ^Ref viewport ^String login-type ^Fn after-login-fn]
  "Note that this is a non-blocking call. I.e. if you need to run something after (for sure) login, use the AFTER-LOGIN-FN
callback."
  (let [old-cookie-value (:uuid @session)
        new-cookie-value (generate-uuid)]
    (alter session assoc :uuid new-cookie-value)
    (db-update :sessions {:uuid new-cookie-value}
               `(= :id ~(deref (:id @session))))
    (alter -sessions- assoc new-cookie-value session) ;; Two cookies now point to SESSION.
    (set-viewport-event-handler "window" "sw_login" viewport
                                (fn [& _]
                                  ;; Ensure only one cookie (i.e. the "last one" when something goes wrong) points to SESSION.
                                  (doseq [[^String some-cookie-value ^Ref some-session] (ensure -sessions-)]
                                    (when (and (= some-session session)
                                               (not= some-cookie-value new-cookie-value))
                                      (alter -sessions- dissoc some-cookie-value)))
                                  (vm-alter (:sessions @user-model) conj session)
                                  (vm-set (:user-model @session) user-model)
                                  (vm-set (spget session :session-type) login-type)
                                  (vm-set (spget session :logged-in?) true)
                                  (after-login-fn))
                                :once? true)
    (js-run viewport ;; Only sent to VIEWPORT (i.e. not entire SESSION which might be 'stolen'!) doing the actual login.
      (set-session-cookie new-cookie-value (= "permanent" login-type))
      "$(window).trigger('sw_login');")))



(defn find-session-constructor [request]
  (loop [session-types @-session-types-]
    (when-first [session-type session-types]
      (let [session-type (val session-type)]
        (if ((:fit-fn session-type) request)
          session-type
          (recur (next session-types)))))))



(defn search-engine? [request]
  (when-let [^String user-agent (get (:headers request) "user-agent")]
    (not (or (neg? (.indexOf user-agent "bot"))
             (neg? (.indexOf user-agent "Mediapartners-Google"))
             (neg? (.indexOf user-agent "ia_archiver"))))))



(defn create-session [session-type one-shot?]
  (with1 (mk-Session :uuid (generate-uuid) :session-type session-type)
    (when-not one-shot?
      (dao-put it "sessions"))))



(declare json-parse)
;; NOTE: COOKIE-VALUE is stored in the :UUID field of Session.
(defn find-or-create-session [request]
  (let [cookie-value (:value (get (:cookies request) -session-cookie-name-))]
    (if-let [session (and cookie-value ;; Don't bother trying if this isn't given.
                          (get (ensure -sessions-) cookie-value))]
      session
      (if-let [session-type (find-session-constructor request)]
        (let [session-skeleton
              (or (and cookie-value
                       (when-let [res (first (db-pstmt "SELECT id FROM sessions WHERE uuid = ? LIMIT 1;" cookie-value))]
                         (with1 (dao-get (:id res) "sessions")
                           (vm-set (:touched @it) (datetime-to-sql-timestamp (time/now))))))
                  (mk-Session :uuid (generate-uuid)
                              :session-type session-type
                              :one-shot? (or (with (get (:query-params request) "_sw_session_one_shot_p")
                                               (if (nil? it)
                                                 false ;; Not supplied so we assume false.
                                                 (json-parse it)))
                                             (search-engine? request))))]
          (vm-alter -num-sessions-model- inc)
          (alter -sessions- assoc (:uuid @session-skeleton) session-skeleton)
          (with1 ((:session-constructor-fn session-type) request session-skeleton)
            (when-not (:one-shot? @it)
              (dao-put it "sessions"))))
        (do
          ;;(log "FIND-OR-CREATE-SESSION: 404 NOT FOUND:" request)
          (mk-Session :uuid cookie-value
                      :rest-handler #'not-found-page-handler
                      :one-shot? true))))))



(defn undefapp [name]
  (swap! -session-types- #(dissoc % name)))
