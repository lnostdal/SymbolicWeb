(in-ns 'symbolicweb.core)

(derive ::Img ::HTMLElement)
(defn make-Img [model & attributes]
  "HTML IMG element. MODEL represents the SRC attribute."
  (apply make-HTMLElement "img" model
         :type ::Img
         :handle-model-event-fn (fn [widget old-value new-value]
                                  (jqAttr widget "src" new-value))
         attributes))
