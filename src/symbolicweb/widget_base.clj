(in-ns 'symbolicweb.core)


(defn set-parent! [child new-parent]
  (alter child assoc :parent new-parent))


(defn set-children! [parent & children]
  (alter parent assoc :children (flatten children)))


(defn render-event ^String [^WidgetBase widget
                            ^String event-type
                            & {:keys [js-before callback-data js-after]
                               :or {js-before "return(true);"
                                    callback-data ""
                                    js-after ""}}]
  (str "$('#" (.id widget) "').bind('" event-type "', "
       "function(event){"
       "swMsg('" (.id widget) "', '" event-type "', function(){" js-before "}, '"
       (reduce (fn [acc key_val] (str acc (url-encode-component (str (key key_val))) "=" (val key_val) "&"))
               ""
               callback-data)
       "', function(){" js-after "});"
       "});"))


(defn render-html ^String [^WidgetBase widget]
  "Return HTML structure which will be the basis for further initialization."
  ((.render-fn widget) widget))


(defn sw ^String [^WidgetBase widget]
  "Render WIDGET as part of a HTMLContainer; WITH-HTML-CONTAINER."
  (assert *in-html-container?*)
  (add-branch *in-html-container?* widget)
  (render-html widget))


(defn set-event-handler [^String event-type
                         ^WidgetBase widget
                         ^clojure.lang.Fn callback-fn
                         & {:keys [callback-data]}]
  "Set an event handler for WIDGET.
Returns WIDGET."
  ;; TODO: Check if EVENT-TYPE is already bound? Think about this ..
  (alter (.callbacks widget) assoc event-type [callback-fn callback-data])
  (add-response-chunk (render-event widget event-type :callback-data callback-data)
                      widget)
  widget)



(defn make-HTMLElement ^WidgetBase [^clojure.lang.Keyword type
                                    ^symbolicweb.core.IModel model
                                    ^clojure.lang.Fn render-fn
                                    ^clojure.lang.Fn observed-event-handler-fn
                                    & args]
  "HTML-ELEMENT-TYPE: \"p\"
MODEL: (vm 42)
MODEL-EVENT-HANDLER: (fn [widget model old-value new-value])"
  (apply make-WidgetBase
         type
         model
         #(render-fn %)
         observed-event-handler-fn
         args))


;; TODO: Button should actually be a container (HTMLContainer?).
(derive ::Button ::HTMLElement)
(defn make-Button [label-str & args]
  "LABEL-STR: \"Some Label\" or (vm \"Some Label\")"
  (apply make-HTMLElement
         ::Button
         (if (= (class label-str)
                symbolicweb.core.ValueModel)
           label-str
           (vm label-str))
         #(str "<button id='" (.id %) "'></button>")
         ;; TODO: This (no escaping) is not safe wrt. XSS. Converting Button into a HTMLContainer will fix this though.
         (fn [^WidgetBase widget model old-value new-value]
           (jqHTML widget new-value))
         args))


(derive ::Link ::HTMLElement)
(defn make-Link [model & args]
  "HTML Link (a href) element. MODEL represents the HREF attribute."
  (apply make-HTMLElement
         ::Link
         model
         #(str "<a id='" (.id %) "'></a>")
         (fn [^WidgetBase widget model old-value new-value]
           (jqAttr widget "href" new-value))
         args))
