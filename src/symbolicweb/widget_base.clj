(in-ns 'symbolicweb.core)


(defn ^WidgetBase make-HTMLElement [^ValueModel value-model
                                    ^clojure.lang.Fn render-fn
                                    ^clojure.lang.Fn observer-fn
                                    widget-base-args]
  "  RENDER-FN: (fn [widget] ..)
  OBSERVER-FN: (fn [widget value-model old-value new-value] ..)"
  (with1 (make-WidgetBase render-fn widget-base-args)
    (vm-observe value-model (.lifetime it) true
                (fn [^Lifetime lifetime old-value new-value]
                  (observer-fn it old-value new-value)))))



(defn ^WidgetBase mk-he [^String html-element-type ^ValueModel value-model
                         & {:keys [observer-fn widget-base-args]
                            :or {widget-base-args {}
                                 observer-fn (fn [^WidgetBase widget old-value new-value]
                                               (jqHTML widget (if (.escape-html? widget)
                                                                (escape-html new-value)
                                                                new-value)))}}]
  (make-HTMLElement value-model
                    (fn [^WidgetBase widget] (str "<" html-element-type " id='" (.id widget) "'></" html-element-type ">"))
                    observer-fn
                    widget-base-args))



(defn ^WidgetBase make-Button [label & widget-base-args]
  "LABEL: \"Some Label\" or (vm \"Some Label\")"
  (mk-he "button"
         (if (= ValueModel (class label))
           label
           (vm label))
         :widget-base-args (merge {:escape-html? false} (apply hash-map widget-base-args))))



(defn ^WidgetBase make-Link [^ValueModel value-model & widget-base-args]
  "HTML Link (a href) element. VALUE-MODEL represents the HREF attribute."
  (mk-he "a" value-model
         :observer-fn (fn [^WidgetBase widget old-value new-value]
                        (jqAttr widget "href" new-value))
         :widget-base-args widget-base-args))
