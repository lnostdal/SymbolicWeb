(in-ns 'symbolicweb.core)

(defn json-parse [^String string]
  "Parse JSON STRING returning Clojure object."
  (json/parse-string string true))


(defn json-generate [clojure-object]
  "Returns JSON string from CLOJURE-OBJECT."
  (json/generate-string clojure-object))