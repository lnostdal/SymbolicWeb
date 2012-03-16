(in-ns 'symbolicweb.core)


(defn set-parent! [child new-parent]
  (alter child assoc :parent new-parent))


(defn set-children! [parent & children]
  (alter parent assoc :children (flatten children)))


(defn render-event [widget event-type & {:keys [js-before callback-data js-after]
                                         :or {js-before "return(true);"
                                              callback-data ""
                                              js-after ""}}]
  (let [widget @widget]
    (str "$('#" (:id widget) "').bind('" event-type "', "
         "function(event){"
         "swMsg('" (:id widget) "', '" event-type "', function(){" js-before "}, '"
         (reduce (fn [acc key_val] (str acc (url-encode (str (key key_val))) "=" (val key_val) "&"))
                 ""
                 callback-data)
         "', function(){" js-after "});"
         "});")))


(defn render-aux-js [widget]
  "Return JS which will initialize WIDGET."
  (when-let [render-aux-js-fn (:render-aux-js-fn @widget)]
    (render-aux-js-fn widget)))


(defn render-aux-html [widget]
  "Return \"aux\" HTML for WIDGET."
  (when-let [render-aux-html-fn (:render-aux-html-fn @widget)]
    (render-aux-html-fn widget)))


(declare add-branch)
(defn render-html [widget]
  "Return HTML structure which will be the basis for further initialization via RENDER-AUX-JS."
  (let [widget-m @widget
        widget-type (:type widget-m)]
    (cond
     (isa? widget-type ::Widget)
     (if (isa? widget-type ::HTMLContainer)
       (binding [*in-html-container?* widget]
         ((:render-html-fn widget-m) widget))
       ((:render-html-fn widget-m) widget))

     true
     (throw (Exception. (str "Can't render: " widget-m))))))


(defn sw [widget]
  "Render WIDGET as part of a HTMLContainer; WITH-HTML-CONTAINER."
  (assert *in-html-container?*)
  (add-branch *in-html-container?* widget)
  (render-html widget))


(defn set-event-handler [event-type widget callback-fn & {:keys [callback-data]}]
  "Set an event handler for WIDGET.
Returns WIDGET."
  ;; TODO: Check if EVENT-TYPE is already bound? Think about this ..
  (swap! (:callbacks @widget) assoc event-type [callback-fn callback-data])
  (add-response-chunk (render-event widget event-type :callback-data callback-data)
                      widget)
  widget)


(defn make-ID
  ([] (make-ID {}))
  ([m] (assoc m :id (cl-format false "sw-~36R" (generate-uid)))))


(defn make-WidgetBase [& key_vals]
  (ref (apply assoc (make-ID)
              :type ::WidgetBase
              :viewport (vm nil)
              :on-visible-fns (atom [])
              :on-non-visible-fns (atom [])
              :children [] ;; Note that order etc. doesn't matter here; this is only used to track visibility on the client-end.
              :callbacks (atom {}) ;; event-name -> [handler-fn callback-data]
              :render-html-fn #(throw (Exception. (str "No :RENDER-HTML-FN defined for this widget (ID: " (:id %) ").")))
              :parse-callback-data-handler #'default-parse-callback-data-handler
              key_vals)))


(derive ::Widget ::WidgetBase)
(defn make-Widget [& key_vals]
  (apply make-WidgetBase key_vals))


(derive ::HTMLElement ::Widget)
(defn make-HTMLElement [html-element-type model & attributes]
  (apply make-Widget
         :type ::HTMLElement
         :html-element-type html-element-type
         :model model
         :escape-html? true
         :output-parsing-fn #(identity %)
         :handle-model-event-fn (fn [widget old-value new-value]
                                  (jqHTML widget (if (:escape-html? @widget)
                                                   (escape-html new-value)
                                                   new-value)))
         :trigger-initial-update? true
         :connect-model-view-fn (fn [model widget]
                                  (when (add-view model widget)
                                    (when (:trigger-initial-update? @widget)
                                      ((:handle-model-event-fn @widget) widget nil ((:output-parsing-fn @widget) @model)))))
         :disconnect-model-view-fn (fn [widget]
                                     (remove-view model widget))
         :render-static-attributes-fn #(cl-format false "~{ ~A='~A'~}"
                                                  (flatten (map (fn [e] [(name (key e)) (val e)])
                                                                (:static-attributes @%))))
         :render-html-fn (fn [w]
                           (let [w-m @w]
                             (str "<" (:html-element-type w-m) " id='" (:id w-m) "'" ((:render-static-attributes-fn w-m) w) ">"
                                  (render-aux-html w)
                                  "</" (:html-element-type w-m) ">"
                                  (let [script (str (render-aux-js w))]
                                    (when (seq script)
                                      (str "<script type='text/javascript'>" script "</script>"))))))
         attributes))


;; TODO: Button should actually be a container (HTMLContainer?).
(derive ::Button ::HTMLElement)
(defn make-Button [label-str & attributes]
  "Supply :MODEL as attribute if needed. This will override what's provided via LABEL-STR."
  (assert (string? label-str))
  (apply make-HTMLElement "button" (vm label-str)
         :type ::Button
         :escape-html? false ;; TODO: This is not safe wrt. XSS. Converting Button into a HTMLContainer will fix this though.
         attributes))


(derive ::Link ::HTMLElement)
(defn make-Link [model & attributes]
  "HTML Link (a href) element. MODEL represents the HREF attribute."
  (apply make-HTMLElement "a" model
         :type ::Link
         :handle-model-event-fn (fn [widget old-value new-value]
                                  (jqAttr widget "href" new-value))
         attributes))


(defn make-View [model lifetime & attributes]
  "Supply :HANDLE-MODEL-EVENT-FN and you'll have an observer ('callback') of MODEL that is not a Widget.
The lifetime of this observer is governed by LIFETIME and can be a View/Widget or NIL for 'infinite' lifetime (as long as MODEL)."
  ;; TODO: HTMLElement is waay too specific for this; need some sort of common base type.
  (with1 (apply make-HTMLElement "%make-View" model
                :type ::Observer
                :handle-model-event-fn (fn [view old-value new-value]
                                         (assert false "make-View: No callback (:handle-model-event-fn) supplied."))
                attributes)
    (when lifetime
      ;; TODO: ADD-BRANCH might also call :CONNECT-MODEL-VIEW-FN if LIFETIME is or turns visible (:VIEWPORT),
      ;; but it won't have any effect if called more than once.
      (add-branch lifetime it))
    ((:connect-model-view-fn @it) model it)))


(derive ::Observer ::WidgetBase)
(defn mk-view [model lifetime handle-model-event-fn & attributes]
  "HANDLE-MODEL-EVENT-FN: Takes 3 arguments; VIEW OLD-VALUE NEW-VALUE."
  (apply make-View model lifetime
         :type ::Observer
         :handle-model-event-fn handle-model-event-fn
         attributes))


(defn observe [model lifetime initial-sync? callback & attributes]
  "CALLBACK takes two arguments; [OLD-VALUE NEW-VALUE]."
  (apply mk-view model lifetime (fn [_ old-value new-value] (callback old-value new-value))
         :type ::Observer
         :trigger-initial-update? initial-sync?
         attributes))
