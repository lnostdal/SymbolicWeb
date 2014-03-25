(in-ns 'symbolicweb.core)


(defn mk-WidgetBase ^WidgetBase [^Fn render-fn args]
  (with1 (WidgetBase. (or (:id args) ;; ID
                          (str "sw-" (generate-uid)))
                      (if (:root-widget? args) ;; LIFETIME
                        (mk-LifetimeRoot)
                        (mk-Lifetime))
                      render-fn ;; RENDER-FN
                      (ref nil) ;; PARENT
                      (vm nil) ;; VIEWPORT
                      (ref {}) ;; CALLBACKS
                      ;; ESCAPE-HTML?
                      (if-let [entry (find args :escape-html?)]
                        (val entry)
                        true))

    (when-not (:root-widget? args)
      (add-lifetime-activation-fn (.lifetime it)
                                  (fn [^Lifetime lifetime]
                                    (let [parent-viewport (viewport-of (parent-of it))]
                                      #_(assert (not (get (:widgets @parent-viewport)
                                                        (.id it))))
                                      ;; Viewport --> Widget (DOM events).
                                      (alter parent-viewport update-in [:widgets]
                                             assoc (.id it) it)
                                      ;; Widget --> Viewport.
                                      (vm-set (.viewport it) parent-viewport)))))

    (add-lifetime-deactivation-fn (.lifetime it)
                                  (fn [^Lifetime lifetime]
                                    (let [viewport (viewport-of it)]
                                      ;; Viewport -/-> Widget (DOM events).
                                      (alter viewport update-in [:widgets]
                                             dissoc (.id it))
                                      ;; Widget -/-> Viewport.
                                      (vm-set (.viewport it) nil))))))



(defn ^String render-html [^WidgetBase widget]
  "Return HTML structure which will be the basis for further initialization."
  ((.render-fn widget) widget))



(defn ^String sw [^WidgetBase widget]
  "Render WIDGET as part of a HTMLContainer; WITH-HTML-CONTAINER."
  (attach-branch *in-html-container?* widget)
  (render-html widget))



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
        (js-run widget
          "$('#" (.id widget) "')"
          ".off('" event-type "')"
          (if once? ".one" ".on") "('" event-type "', "
          "function(event){"
          "swWidgetEvent('" (.id widget) "', '" event-type "', function(){" js-before "}, '"
          (apply str (interpose \& (map #(str (url-encode-component (str %1)) "=" %2)
                                        (keys callback-data)
                                        (vals callback-data))))
          "', function(){" js-after "});"
          "});")
        widget)
      (do
        (alter (.callbacks widget) dissoc event-type)
        (js-run widget "$('#" (.id widget) "').off('" event-type "');")))))



(defn ^WidgetBase mk-HTMLElement [^ValueModel value-model
                                  ^Fn render-fn
                                  ^Fn observer-fn
                                  args]
  "  RENDER-FN: (fn [widget] ..)
  OBSERVER-FN: (fn [widget value-model old-value new-value] ..)"
  (with1 (mk-WidgetBase render-fn args)
    (vm-observe value-model (.lifetime it) true
                (fn [^Lifetime lifetime old-value new-value]
                  (observer-fn it old-value new-value)))))



(defn ^WidgetBase mk-he [html-element-type ^ValueModel value-model & args]
  "  :OBSERVER-FN: (fn [widget old-value new-value] ..)"
  (let [args (apply hash-map args)]
    (mk-HTMLElement value-model
                    (or (:render-fn args)
                        (fn [^ValueModel widget]
                          (html [html-element-type (-> (dissoc args :wb-args :render-fn :observer-fn)
                                                       (assoc :id (.id widget)))])))
                    (or (:observer-fn args)
                        (fn [^WidgetBase widget old-value new-value]
                          (jqHTML widget (if (.escape-html? widget)
                                           (escape-html new-value)
                                           new-value))))
                    (dissoc args :render-fn :observer-fn))))



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
         (js-run context-widget
           "$('<link id=\"sw-" id "\" rel=\"stylesheet\" href=\"" (gen-url viewport href) "\">').appendTo('head');")
         (add-lifetime-deactivation-fn
          (.lifetime context-widget)
          (fn [_]
            (js-run viewport "$('#sw-" id "').remove();"))))))))
