(in-ns 'symbolicweb.core)



(defn mk-WB [html-element-type]
  (let [html-element-type-str (name html-element-type)]
    (mk-WidgetBase (fn [^WidgetBase widget]
                     (str "<" html-element-type-str " id='" (.id widget) "'></" html-element-type-str ">"))
                   {})))



(defn ^WidgetBase mk-HTMLElement [^ValueModel value-model
                                  ^Fn render-fn
                                  ^Fn observer-fn
                                  widget-base-args]
  "  RENDER-FN: (fn [widget] ..)
  OBSERVER-FN: (fn [widget value-model old-value new-value] ..)"
  (with1 (mk-WidgetBase render-fn widget-base-args)
    (vm-observe value-model (.lifetime it) true
                (fn [^Lifetime lifetime old-value new-value]
                  (observer-fn it old-value new-value)))))



(defn ^WidgetBase mk-he [html-element-type ^ValueModel value-model
                         & {:keys [observer-fn widget-base-args]
                            :or {widget-base-args {}
                                 observer-fn (fn [^WidgetBase widget old-value new-value]
                                               (jqHTML widget (if (.escape-html? widget)
                                                                (escape-html new-value)
                                                                new-value)))}}]
  (let [html-element-type-str (name html-element-type)]
    (mk-HTMLElement value-model
                    (fn [^WidgetBase widget]
                      (str "<" html-element-type-str " id='" (.id widget) "'></" html-element-type-str ">"))
                    observer-fn
                    widget-base-args)))



;; TODO: Use ContainerView similar to what mk-Link (below) does.
(defn ^WidgetBase mk-Button [label & widget-base-args]
  "LABEL: \"Some Label\" or (vm \"Some Label\")"
  (mk-he "button"
         (if (= ValueModel (class label))
           label
           (vm label))
         :widget-base-args (merge {:escape-html? false} (apply hash-map widget-base-args))))



(defn ^WidgetBase mk-Link [^WidgetBase widget url-mappers]
  "  URL-MAPPERS: [(mk-URLMapper ..) (vm ..) ...]"
  (let [query-str-vm (vm "")
        query-params (vm nil)]
    (vm-observe query-str-vm (.lifetime widget) false
                (fn [_ _ query-str]
                  (jqAttr widget "href"
                          (str "window.location.pathname + '?' + " (url-encode-wrap query-str)))))
    (doseq [[url-mapper url-mapper-mutator-vm] url-mappers]
      (with-observed-vms (.lifetime widget)
        (when-let [viewport (viewport-of widget)]
          (when-not @query-params
            (vm-set query-params @(:query-params @viewport)))
          (vm-set query-str-vm (ring.util.codec/form-encode
                                @(with1 query-params
                                   ;; QUERY-PARAMS is a Sorted Map, and result of MERGE will be too.
                                   (vm-set it (merge @it {(:name url-mapper) @url-mapper-mutator-vm}))))))))
    (set-event-handler "click" widget
                       (fn [& _]
                         ;; TODO: This causes multiple client side History entries; one for each iteration..
                         (doseq [[url-mapper url-mapper-mutator-vm] url-mappers]
                           (vm-set (:model url-mapper) @url-mapper-mutator-vm)))
                       :js-before "event.preventDefault(); return(true);")
    widget))
