(in-ns 'symbolicweb.core)

(derive ::Img ::HTMLElement)
(defn make-Img [^symbolicweb.core.ValueModel model & args]
  "HTML IMG element. MODEL represents the SRC attribute."
  (apply make-HTMLElement
         ::Img
         model
         #(str "<img id='" (.id %) "' alt=''></img>")
         (fn [^WidgetBase widget ^symbolicweb.core.ValueModel model
              old-value new-value]
           (jqAttr widget "src" new-value))
         args))
