(in-ns 'symbolicweb.core)


(defn boolean? [x]
  (= java.lang.Boolean (class x)))


(defn json-parse [^String json-string]
  "Parse JSON-STRING returning Clojure object."
  (json/parse-string json-string true))


(defn ^String json-generate [clojure-object]
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



(defn ^String gen-url [^Ref viewport ^String path]
  "Generates an absolute URL to resource denoted by PATH.
Appends a timestamp to the URL based on file mtime."
  ;; TODO: Check whether the JVM caches calls to lastModified. If not, add a cache based on PATH here.
  (let [fs-path (str (:genurl-fs-path @viewport) path) ;; resources/web-design/
        mtime (.lastModified (java.io.File. fs-path))]
    (assert (not (zero? mtime))
            (str "gen-url: " fs-path " not found."))
    (str (:genurl-scheme @viewport) ;; http
         "://"
         (:genurl-domain @viewport) ;; static.site.com
         "/"
         (:genurl-path @viewport) ;; some-common-path/      (note trailing slash)
         path ;; js/ajax.js
         "?_=" mtime)))



(defn mk-rest-css-entry [^String url]
  {:url url})



(defn ^String generate-rest-css [rest-css-entries]
  (with-out-str
    (doseq [css-entry rest-css-entries]
      (print (str "<link rel='stylesheet' href='" (:url css-entry) "'>")))))



(defn mk-rest-js-entry [^String url ^Boolean defer? ^Boolean async?]
  {:url url :defer? defer? :async? async?})



(defn ^String generate-rest-js [rest-js-entries]
  (with-out-str
    (doseq [js-entry rest-js-entries]
      (print (str "<script src='" (:url js-entry) "'"
                  (when (:defer? js-entry)
                    " defer ")
                  (when (:async? js-entry)
                    " async ")
                  "></script>")))))



(defn ^java.sql.Timestamp datetime-to-sql-timestamp [^org.joda.time.DateTime datetime]
  (java.sql.Timestamp. (clj-time.coerce/to-long datetime)))



(def -now- (with1 (vm (clj-time.core/now))
             (overtone.at-at/every 1000
                                   (fn [] (swsync (vm-set it (clj-time.core/now))))
                                   -overtone-pool-)))
