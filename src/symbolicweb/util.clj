(in-ns 'symbolicweb.core)


(defn boolean? [x]
  (= java.lang.Boolean (class x)))


(defn json-parse [^String string]
  "Parse JSON STRING returning Clojure object."
  (json/parse-string string true))


(defn json-generate [clojure-object]
  "Returns JSON string from CLOJURE-OBJECT."
  (json/generate-string clojure-object))


(defn strb
  (^StringBuilder
   [^StringBuilder sb]
   (.append sb ""))

  (^StringBuilder
   [^StringBuilder sb ^Object x]
   (.append sb (if (nil? x) "" (.toString x))))

   (^StringBuilder
    [^StringBuilder sb x & ys]
    ((fn [^StringBuilder sb more]
       (if more
         (recur (.append sb (str (first more)))
                (next more))
         sb))
     (.append sb (str x))
     ys)))


(defmacro with-string-builder [name & body]
  "For use with STRB."
  `(let [~name (StringBuilder.)]
     ~@body
     (.toString ~name)))


(defn http-build-query [query-map & php-compatible-boolean-output?]
  ;; TODO: Consider getting rid of the & character at the very start.
  (with-string-builder query
    (letfn [(add-url-entry [^String url-key ^String url-value]
              (strb query "&" (url-encode-component url-key) "=" (url-encode-component url-value)))

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
                (add-url-entry key-prefix
                               (if php-compatible-boolean-output?
                                 (if value "1" "0")
                                 (if value "true" "false")))

                true
                (add-url-entry key-prefix (str value))))]
      (doseq [map-entry query-map]
        (handle-url-value (name (key map-entry))
                          (val map-entry))))))
