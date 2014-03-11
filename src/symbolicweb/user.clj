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
