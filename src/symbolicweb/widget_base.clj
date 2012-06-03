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



;;; The following are not really widgets, but observers of Models, like widgets, none the less.

(derive ::Observer ::WidgetBase)
(defn make-View [model lifetime observed-event-handler-fn & args]
  "OBSERVED-EVENT-HANDLER-FN: (fn [widget model old-value new-value])
LIFETIME: Governs the lifetime of this connection (Model --> OBSERVED-EVENT-HANDLER-FN) and can be a View/Widget or NIL for 'infinite' lifetime (as long as MODEL)."
  (assert (or (= WidgetBase (class lifetime))
              (= nil lifetime)))
  ;; TODO: HTMLElement is waay too specific for this; need some sort of common base type. Well, use WidgetBase directly then?
  (with1 (apply make-HTMLElement
                ::Observer
                model
                (fn [_] (assert false "Observers are not meant to be rendered!"))
                observed-event-handler-fn
                args)
    #_(when lifetime
      ;; TODO: ADD-BRANCH might also call :CONNECT-MODEL-VIEW-FN if LIFETIME is or turns visible (:VIEWPORT),
      ;; but it won't have any effect if called more than once.
      (add-branch lifetime it))
    #_((:connect-model-view-fn it) model it)))


(defn mk-view [model lifetime observed-event-handler-fn & args]
  "OBSERVED-EVENT-HANDLER-FN: (fn [widget model old-value new-value])
LIFETIME: Governs the lifetime of this connection (Model --> OBSERVED-EVENT-HANDLER-FN) and can be a View/Widget or NIL for 'infinite' lifetime (as long as MODEL)."
  (apply make-View model lifetime observed-event-handler-fn
         args))


(defn observe [model lifetime initial-sync? callback & args]
  "CALLBACK: (fn [old-value new-value])
LIFETIME: Governs the lifetime of this connection (Model --> OBSERVED-EVENT-HANDLER-FN) and can be a View/Widget or NIL for 'infinite' lifetime (as long as MODEL).
INITIAL-SYNC?: If true CALLBACK will be called even though OLD-VALUE is = :symbolicweb.core/-initial-update-. I.e., on construction
of this observer."
  (apply mk-view model lifetime
         (fn [_ _ old-value new-value]
           (if (= old-value :symbolicweb.core/-initial-update-)
             (when initial-sync?
               (callback old-value new-value))
             (callback old-value new-value)))
         args))
