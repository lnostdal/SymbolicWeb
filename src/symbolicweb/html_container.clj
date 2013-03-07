(in-ns 'symbolicweb.core)



(defn ^WidgetBase %mk-HTMLContainer [html-element-type ^Fn content-fn widget-base-args]
  (let [html-element-type (name html-element-type)]
    (mk-WidgetBase (fn [^WidgetBase html-container]
                     (binding [*in-html-container?* html-container] ;; Target for calls to SW done in CONTENT-FN.
                       (str "<" html-element-type " id='" (.id html-container) "'>"
                            (content-fn html-container)
                            "</" html-element-type ">")))
                   (apply hash-map widget-base-args))))



(defmacro whc [[html-element-type & widget-base-args] & body]
  "WITH-HTML-CONTAINER."
  `(%mk-HTMLContainer ~html-element-type
                      (fn [_#] ~@body)
                      '~widget-base-args))



(defn ^WidgetBase mk-PostHTMLTemplate [^String id ^Fn content-fn & widget-base-args]
  "This applies templating to an already existing HTML element, specified by ID, on the page."
  (with1 (%mk-HTMLContainer "%PostHTMLTemplate"
                            content-fn
                            (into widget-base-args (list id :id))) ;; TODO: This seems hacky.
    (render-html it)))
