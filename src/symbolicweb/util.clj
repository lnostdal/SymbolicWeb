(in-ns 'symbolicweb.core)



(defn ^Long parse-long [input]
  (if (string? input)
    (Long/parseLong input)
    (long input)))



(defn ^Double parse-double [input]
  (if (string? input)
    (Double/parseDouble input)
    (double input)))



(defn boolean? [x]
  (= java.lang.Boolean (class x)))


(defn json-parse [^String json-string]
  "Parse JSON-STRING returning Clojure object."
  (json/parse-string json-string true))


(defn ^String json-generate [clojure-object]
  "Returns JSON string from CLOJURE-OBJECT."
  (json/generate-string clojure-object))



(defmacro strb [sb-name & args]
  `(do ~@(map (fn [arg] `(.append ~sb-name ~arg))
              args)))



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



(defn ^String gen-url [^Ref viewport ^String path]
  "Generates an absolute URL to resource denoted by PATH.
Appends a timestamp to the URL based on file mtime."
  ;; TODO: Check whether the JVM caches calls to lastModified. If not, add a cache based on PATH here.
  (let [fs-path (str (:genurl-fs-path @viewport) path) ;; resources/web-design/
        mtime (.lastModified (java.io.File. fs-path))]
    (assert (not (zero? mtime))
            (str "gen-url: " fs-path " not found."))
    (str "//"
         (:genurl-domain @viewport)
         "/"
         (:genurl-path @viewport) ;; static/
         path ;; js/ajax.js
         "?_=" mtime)))



(defn mk-rest-css-entry [^String url]
  {:url url})



(defn mk-rest-js-entry
  ([^String url]
   (mk-rest-js-entry url false false))
  ([^String url ^Boolean defer?]
   (mk-rest-js-entry url defer? false))
  ([^String url ^Boolean defer? ^Boolean async?]
   {:url url :defer? defer? :async? async?}))



(defn ^String generate-rest-head [rest-head-entries]
  (with-out-str
    (doseq [rest-entry rest-head-entries]
      (println rest-entry))))



(defn ^java.sql.Timestamp datetime-to-sql-timestamp [^org.joda.time.DateTime datetime]
  (java.sql.Timestamp. (clj-time.coerce/to-long datetime)))



(defn float-to-string
  "If `x` is float, double or ratio converts it to a simple string representation of the number suitable
  for e.g. APIs and similar. I.e. not ratio or scientific notation format. This will also round the number down (java.math.RoundingMode/DOWN).

  If `x` is something else pass it through as is."
  ([x]
   (float-to-string x "0.0000"))

  ([x ^String decimal-format]
   (float-to-string x decimal-format java.math.RoundingMode/DOWN))

  ([x ^String decimal-format rounding-mode]
   (if-let [v (or (and (float? x) x)
                  (and (ratio? x) (double x)))]
     (.format (with1 (java.text.DecimalFormat. decimal-format)
                (.setRoundingMode it rounding-mode)
                (.setGroupingUsed it false)
                (.setDecimalFormatSymbols it (let [dfs (.getDecimalFormatSymbols it)]
                                               (.setDecimalSeparator dfs \.)
                                               dfs)))
              v)
     x)))
