(in-ns 'symbolicweb.core)


(defn boolean? [x]
  (= java.lang.Boolean (class x)))


(defn json-parse [^String string]
  "Parse JSON STRING returning Clojure object."
  (json/parse-string string true))


(defn json-generate [clojure-object]
  "Returns JSON string from CLOJURE-OBJECT."
  (json/generate-string clojure-object))


;; TODO: Macro for StringBuilder, append and toString combo seen here.
(defn http-build-query [query-map & php-compatible-boolean-output?]
  (let [result-string (StringBuilder.)]
    (letfn [(add-url-entry [^String url-key ^String url-value]
              (.append result-string "&")
              (.append result-string (url-encode url-key))
              (.append result-string "=")
              (.append result-string (url-encode url-value)))

            (handle-url-vector [^String key-prefix elements]
              (loop [elements elements
                     index 0]
                (when-first [element elements]
                  (handle-url-value (str key-prefix "[" index "]")
                                    element)
                  (recur (rest elements) (inc index)))))

            (handle-url-map [^String key-prefix m]
              (doseq [map-entry m]
                (handle-url-value (str key-prefix "[" (name (key map-entry)) "]")
                                  (val map-entry))))

            (handle-url-value [^String key-prefix value]
              (cond
                (vector? value)
                (handle-url-vector key-prefix value)

                (map? value)
                (handle-url-map key-prefix value)

                (boolean? value)
                (if php-compatible-boolean-output?
                  (handle-url-value key-prefix (if value 1 0))
                  (handle-url-value key-prefix (if value "true" "false")))

                true
                (add-url-entry key-prefix (str value))))]
      (doseq [map-entry query-map]
        (handle-url-value (name (key map-entry))
                          (val map-entry))))
    ;; TODO: Consider (subs .. 1) here to get rid of the & character at the very start.
    (.toString result-string)))
