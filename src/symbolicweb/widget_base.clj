(in-ns 'symbolicweb.core)



(defn ^WidgetBase set-event-handler [^String event-type ^WidgetBase widget ^Fn callback-fn
                                     & {:keys [js-before callback-data js-after once?]
                                        :or {js-before "return(true);"
                                             callback-data ""
                                             js-after ""}}]
  "Set an event handler for WIDGET.
Returns WIDGET."
  (if callback-fn
    (do
      ;; TODO: Check if EVENT-TYPE is already bound? Think about this ..
      (alter (.callbacks widget) assoc event-type
             [(if once?
                ;; Unbind event handler on server side before executing it (once).
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
                          widget))))



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



;; TODO: Use ContainerView similar to what mk-Link (below) does.
(defn ^WidgetBase mk-Button [label & widget-base-args]
  "LABEL: \"Some Label\" or (vm \"Some Label\")"
  (mk-he "button"
         (if (= ValueModel (class label))
           label
           (vm label))
         :widget-base-args (merge {:escape-html? false} (apply hash-map widget-base-args))))



(defn ^WidgetBase mk-Link
  "  URL-MAPPERS: {(vm-sync-from-url ..) (vm ..) ...}
  M: :SCROLL-TO-TOP?, :ON-CLICK-FN, :EVENT-STOP-PROPAGATION?"
  ([^WidgetBase widget url-mappers]
     (mk-Link widget url-mappers nil))

  ([^WidgetBase widget url-mappers m]
     (let [query-str-vm (vm "")
           query-params (vm nil)]

       (vm-observe query-str-vm (.lifetime widget) false
                   #(jqAttr widget "href" (str "window.location.pathname + '?' + " (url-encode-wrap %3)) true))

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
                            ;; Extract this here before the VM-SET as that might lead to NIL for the VIEWPORT field in WIDGET.
                            (let [viewport (viewport-of widget)]
                              (doseq [[url-mapper ^ValueModel url-mapper-mutator-vm] url-mappers]
                                (vm-set (:model url-mapper) @url-mapper-mutator-vm))
                              (when (or (not (find m :scroll-to-top?))
                                        (:scroll-to-top? m))
                                ;; TODO: scrollLeft?
                                (add-response-chunk "$('html, body').scrollTop(0);\n" viewport))
                              (when-let [f (with (:on-click-fn m)
                                             (when (fn? it) it))]
                                (f widget))))
                          ;; TODO: Remove this when IE9 is gone.
                          ;; NOTE: Super, mega, hack for IE 9. :(. We clear the page while (before) re-rendering it to avoid some
                          ;; flickering the user isn't used to seeing. MS needs to just go away.
                          :js-before
                          (str (with (:on-click-fn m)
                                 (when (string? it)
                                   it))
                               " if(navigator.userAgent.search('MSIE 9') != -1) { $('#_body').css('display', 'none'); } return(true);")
                          :js-after (str (when (:event-stop-propagation? m) "event.stopPropagation(); ")
                                         "event.preventDefault(); return(false);"))

       widget)))



(defn ^WidgetBase mk-Img [^ValueModel src]
  (mk-he :img src :observer-fn (fn [^WidgetBase widget _ new-value]
                                 (jqAttr widget "src" new-value))))



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
