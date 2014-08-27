(in-ns 'symbolicweb.core)


(defn %mk-UserTable []
  (swsync
    (db-stmt
"
CREATE TABLE users (
    id bigserial NOT NULL,
    email text NOT NULL ,
    password text NOT NULL,
    uuid text NOT NULL
);
ALTER TABLE users ADD PRIMARY KEY (id);
ALTER TABLE users ADD UNIQUE (email);
ALTER TABLE users ADD UNIQUE (uuid);
")))



(defn user-model-base-clj-to-db-transformer [m]
  "SW --> DB"
  (-> m
      ((fn [m]
         (case (:key m)
           :sessions
           (assoc m
             :key nil)

           m)))))



(defn mk-UserModelBase [& args]
  (ref (apply assoc {}
              :id (vm nil)
              ;; A single user can have several sessions running on several computers/browsers at the same time.
              :sessions (vm #{}) ;; NOTE: GC-SESSION depends on this field.
              :uuid (generate-uuid)
              args)))



(swap! -db-cache-constructors- assoc "users"
       #(mk-DBCache "users"
                    (fn [db-cache id] (mk-UserModelBase))
                    identity
                    (fn [m]
                      (-> m
                          (user-model-base-clj-to-db-transformer)
                          (dao-default-clj-to-db-transformer)))
                    #'dao-default-db-to-clj-transformer))



(defn user-get-or-create
  ([^String email]
     (user-get-or-create email nil))

  ([^String email ^Fn create-fn]
     (if-let [id (:id (first (db-pstmt "SELECT id FROM users WHERE email = ? LIMIT 1;" email)))]
       (dao-get id "users")
       (create-fn email))))



(defn strip-email ^String [^String email]
  (-> (clojure.string/trim email)
      (clojure.string/lower-case)))
