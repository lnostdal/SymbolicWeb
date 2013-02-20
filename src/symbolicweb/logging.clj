(in-ns 'symbolicweb.core)

;;; TODO: Add proper logging stuff here. Log4j?


(defn log [& args]
  (with-sw-io nil
    (apply println "\n\n[SW]:" args)))
