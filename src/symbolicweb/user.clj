(in-ns 'symbolicweb.core)


(derive ::UserModelBase ::Model)
(defn make-UserModelBase [& attributes]
  (ref (apply assoc {}
              :type ::UserModelBase

              ;; A single user can have several sessions running on several computers/browsers at the same time.
              :applications (vm #{})
              attributes)))
