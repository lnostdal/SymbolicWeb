(in-ns 'symbolicweb.core)




(defn make-HTMLElement ^WidgetBase [^clojure.lang.Keyword type
                                    ^ValueModel value-model
                                    ^clojure.lang.Fn render-fn
                                    ^clojure.lang.Fn observer-cb
                                    & args]
  "TYPE: ::P   (i.e. the P HTML element)
VALUE-MODEL: (vm 42)
RENDER-FN: (fn [widget] ..)
OBSERVER-CB: (fn [widget value-model old-value new-value] ..)"
  (with (apply make-WidgetBase
               type
               render-fn
               args)
    (vm-observe value-model (.lifetime it) true
                (fn [new-value old-value observer-fn observable]
                  (observer-cb it value-model old-value new-value)))
    it))



(derive ::GenericHTMLElement ::HTMLElement)
(defn mk-he ^WidgetBase [^String html-element-type ^ValueModel value-model & args]
  (apply make-HTMLElement
         ::GenericHTMLElement
         value-model
         (fn [^WidgetBase widget] (str "<" html-element-type " id='" (.id widget) "'></" html-element-type ">"))
         (fn [^WidgetBase widget ^ValueModel model old-value new-value]
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
