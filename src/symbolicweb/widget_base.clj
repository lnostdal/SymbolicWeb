(in-ns 'symbolicweb.core)



(defn ^WidgetBase set-event-handler [^String event-type ^WidgetBase widget ^Fn callback-fn
                                     & {:keys [js-before callback-data js-after once?]
                                        :or {js-before "return(true);"
                                             callback-data {}
                                             js-after ""}}]
  "Set an event handler for WIDGET.
Returns WIDGET."
  (let [;; CSRF security check token. Check is done in HANDLE-IN-CHANNEL-REQUEST.
        callback-data (conj callback-data [:sw-token (subs (generate-uuid) 0 8)])]
    (if callback-fn
      (do
        (alter (.callbacks widget) assoc event-type
               [(if once?
                  ;; Unbind event handler on server side before executing it once.
                  (comp callback-fn
                        (fn [& args]
                          (alter (.callbacks widget) dissoc event-type)
                          args))
                  callback-fn)
                callback-data])
        (add-response-chunk
         (str "$('#" (.id widget) "')"
              ".off('" event-type "')"
              (if once?
                (str ".one('" event-type "', ")
                (str ".on('" event-type "', "))
              "function(event){"
              "swWidgetEvent('" (.id widget) "', '" event-type "', function(){" js-before "}, '"
              (apply str (interpose \& (map #(str (url-encode-component (str %1)) "=" %2)
                                            (keys callback-data)
                                            (vals callback-data))))
              "', function(){" js-after "});"
              "});\n")
         widget)
        widget)
      (do
        (alter (.callbacks widget) dissoc event-type)
        (add-response-chunk (str "$('#" (.id widget) "').off('" event-type "');\n")
                            widget)))))



(defn mk-WB
  (^WidgetBase [^Keyword html-element-type]
     (mk-WB html-element-type {}))

  (^WidgetBase [^Keyword html-element-type args]
     (mk-WidgetBase (fn [^WidgetBase widget]
                      (if (empty? args) ;; TODO: Check :HTML-ATTRS instead?
                        (let [html-element-type-str (name html-element-type)]
                          (str "<" html-element-type-str " id='" (.id widget) "'></" html-element-type-str ">"))
                        (html [html-element-type
                               (let [attrs (:html-attrs args)]
                                 (if (:id attrs)
                                   attrs
                                   (assoc attrs :id (.id widget))))])))
                    ;; :ID from :HTML-ATTRS (if supplied) should be used as ID server side also.
                    (if-let [id (:id (:html-attrs args))]
                      (assoc args :id id)
                      args))))



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



;; TODO: Use ContainerView similar to what mk-Link does.
(defn ^WidgetBase mk-Button [label & widget-base-args]
  "LABEL: \"Some Label\" or (vm \"Some Label\")"
  (mk-he "button"
         (if (= ValueModel (class label))
           label
           (vm label))
         :widget-base-args (merge {:escape-html? false} (apply hash-map widget-base-args))))



(defn ^WidgetBase assoc-Stylesheet [^String href m]
  "Adds CSS stylesheet (HREF) to Viewport of :CONTEXT-WIDGET for the Lifetime of :CONTEXT-WIDGET or :VIEWPORT (root widget
really)."
  (let [context-widget (or (and (:viewport m) (:root-element @(:viewport m)))
                           (:context-widget m))]
    (add-lifetime-activation-fn
     (.lifetime context-widget)
     (fn [_]
       (let [id (generate-uid)
             viewport (viewport-of context-widget)]
         (add-response-chunk
          (str "$('<link id=\"sw-" id "\" rel=\"stylesheet\" href=\"" (gen-url viewport href) "\">').appendTo('head');\n")
          context-widget)
         (add-lifetime-deactivation-fn
          (.lifetime context-widget)
          (fn [_]
            (add-response-chunk (str "$('#sw-" id "').remove();\n")
                                viewport))))))))
