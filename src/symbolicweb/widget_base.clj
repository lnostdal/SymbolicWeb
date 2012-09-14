(in-ns 'symbolicweb.core)


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
  "TYPE: ::Button
MODEL: (vm 42)
RENDER-FN: (fn [widget])
MODEL-EVENT-HANDLER: (fn [widget model old-value new-value])"
  (apply make-WidgetBase
         type
         model
         (fn [^WidgetBase widget] (render-fn widget))
         observed-event-handler-fn
         args))


(derive ::GenericHTMLElement ::HTMLElement)
(defn mk-he ^WidgetBase [^String html-element-type ^symbolicweb.core.IModel model & args]
  (apply make-HTMLElement
         ::GenericHTMLElement
         model
         (fn [^WidgetBase widget] (str "<" html-element-type " id='" (.id widget) "'></" html-element-type ">"))
         (fn [^WidgetBase widget ^symbolicweb.core.ValueModel model old-value new-value]
           (jqHTML widget (if (:escape-html? widget)
                            (escape-html new-value)
                            new-value)))
         args))


;; TODO: Button should actually be a container (HTMLContainer?).
(derive ::Button ::HTMLElement)
(defn make-Button [label-str & args]
  "LABEL-STR: \"Some Label\" or (vm \"Some Label\")"
  (apply mk-he "button"
         (if (= (class label-str)
                symbolicweb.core.ValueModel)
           label-str
           (vm label-str))
         :escape-html? false
         :type ::Button
         args))


(derive ::Link ::HTMLElement)
(defn make-Link [^symbolicweb.core.ValueModel model & args]
  "HTML Link (a href) element. MODEL represents the HREF attribute."
  (apply mk-he "a"
         model
         :type ::Link
         :observed-event-handler-fn
         (fn [^WidgetBase widget ^symbolicweb.core.ValueModel model
              old-value new-value]
           (jqAttr widget "href" new-value))
         args))
