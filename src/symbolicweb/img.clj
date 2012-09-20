(in-ns 'symbolicweb.core)

(defn ^WidgetBase make-Img [^ValueModel value-model & widget-base-args]
  "HTML IMG element. MODEL represents the SRC attribute."
  (make-HTMLElement value-model
                    (fn [^WidgetBase widget] (str "<img id='" (.id widget) "' alt=''></img>"))
                    (fn [^WidgetBase widget old-value new-value]
                      (jqAttr widget "src" new-value))
                    (apply hash-map widget-base-args)))
