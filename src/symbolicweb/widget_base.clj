(in-ns 'symbolicweb.core)


(defn set-parent! [child new-parent]
  (alter child assoc :parent new-parent))


(defn set-children! [parent & children]
  (alter parent assoc :children (flatten children)))


(defn render-event ^String [widget event-type & {:keys [js-before callback-data js-after]
                                                 :or {js-before "return(true);"
                                                      callback-data ""
                                                      js-after ""}}]
  (let [widget @widget]
    (str "$('#" (.id widget) "').bind('" event-type "', "
         "function(event){"
         "swMsg('" (.id widget) "', '" event-type "', function(){" js-before "}, '"
         (reduce (fn [acc key_val] (str acc (url-encode-component (str (key key_val))) "=" (val key_val) "&"))
                 ""
                 callback-data)
         "', function(){" js-after "});"
         "});")))


(defn render-html ^String [widget]
  "Return HTML structure which will be the basis for further initialization via RENDER-AUX-JS."
  (let [widget-m @widget
        widget-type (:type widget-m)]
    (cond
     (isa? widget-type ::Widget)
     ;; TODO: Why can't the :render-html-fn for the widget in question set up this binding itself?
     (if (isa? widget-type ::HTMLContainer)
       (binding [*in-html-container?* widget]
         ((:render-html-fn widget-m) widget))
       ((:render-html-fn widget-m) widget))

     true
     (throw (Exception. (str "Can't render: " widget-m))))))


(defn sw ^String [widget]
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



(defn make-HTMLElement ^WidgetBase [^String html-element-type
                                    ^symbolicweb.core.IModel model
                                    ^clojure.lang.Fn model-event-handler
                                    & args]
  (apply make-WidgetBase
         ::WidgetBase
         model
         #(str "<" html-element-type " id='" (.id %) "'></" html-element-type ">")
         model-event-handler
         args))


;; TODO: Button should actually be a container (HTMLContainer?).
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
The lifetime of this connection is governed by LIFETIME and can be a View/Widget or NIL for 'infinite' lifetime (as long as
MODEL)."
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
  "HANDLE-MODEL-EVENT-FN: Takes 3 arguments; VIEW OLD-VALUE NEW-VALUE.
The lifetime of this connection is governed by LIFETIME and can be a View/Widget or NIL for 'infinite' lifetime (as long as
MODEL)."
  (apply make-View model lifetime
         :type ::Observer
         :handle-model-event-fn handle-model-event-fn
         attributes))


(defn observe [model lifetime initial-sync? callback & attributes]
  "CALLBACK takes two arguments; [OLD-VALUE NEW-VALUE].
The lifetime of this connection is governed by LIFETIME and can be a View/Widget or NIL for 'infinite' lifetime (as long as
MODEL)."
  (apply mk-view model lifetime (fn [_ old-value new-value] (callback old-value new-value))
         :type ::Observer
         :trigger-initial-update? initial-sync?
         attributes))
