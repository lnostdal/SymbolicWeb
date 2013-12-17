(in-ns 'symbolicweb.core)

;;; TODO: Add proper logging stuff here. Log4j?


(defn log [& args]
  (with-sw-agent nil
    (binding [*print-level* 5]
      (flush)
      (apply println "\n\n[SW]:" args)
      (flush))))
