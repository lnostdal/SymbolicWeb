(in-ns 'symbolicweb.core)


(defn mk-UserModelBase [& args]
  (ref (apply assoc {}
              :id (vm nil)
              ;; A single user can have several sessions running on several computers/browsers at the same time.
              :sessions (vm #{}) ;; NOTE: GC-SESSION depends on this field.
              args)))
