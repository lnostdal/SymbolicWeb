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



(defn ^WidgetBase mk-HTMLTemplate [^org.jsoup.nodes.Document html-resource
                                   ^Fn content-fn
                                   & widget-base-args]
  "  CONTENT-FN is something like:
  (fn [html-template]
    [\".itembox\" html-template
     \".title\" (mk-p title-model)
     \"#sw-js-bootstrap\" (sw-js-bootstrap)]) ;; String."
  (mk-WidgetBase
   (fn [^WidgetBase template-widget]
     (let [transformation-data (content-fn template-widget) ;; NOTE: Using a Vector since it maintains order; Clojure Maps do not.
           html-resource (.clone html-resource)] ;; Always manipulate a copy to avoid any concurrency problems.
       (doseq [[^String selector content] (partition 2 transformation-data)]
         (when-let [^org.jsoup.nodes.Element element
                    (with (.select html-resource selector)
                      (if (zero? (count it))
                        (do
                          (println "mk-HTMLTemplate: No element found for" selector "in context of" (.id template-widget))
                          nil)
                        (do
                          (assert (= 1 (count it))
                                  (str "mk-HTMLTemplate: " (count it) " (i.e. not 1) elements found for for" selector
                                       "in context of" (.id template-widget)))
                          (.first it))))]
           (if (string? content)
             ;; NOTE: I could do (.html content) here. That would actually parse the HTML and add it to our HTML-RESOURCE
             ;; for the next iteration to pick up for possible templating.
             (.text element content)
             (do
               (.attr element "id" ^String (.id ^WidgetBase content))
               (when-not (= content template-widget)
                 (binding [*in-html-container?* template-widget]
                   (attach-branch template-widget content)))))))
       (.html (.select html-resource "body"))))
   (apply hash-map widget-base-args)))



(defn ^WidgetBase mk-TemplateElement [^ValueModel value-model & widget-base-args]
  "TemplateElements are meant to be used in context of HTMLContainer and its subtypes."
  (mk-he "%TemplateElement" value-model
         :widget-base-args (apply hash-map widget-base-args)))


(defn ^WidgetBase mk-te [^ValueModel value-model & widget-base-args]
  "Short for mk-TemplateElement."
  (apply mk-TemplateElement value-model widget-base-args))



(defn ^WidgetBase mk-BlankTemplateElement[& widget-base-args]
  "A TemplateElement which doesn't have a Model.
This might be used to setup a target for DOM events on some static content from a template."
  (mk-WidgetBase (fn [_]) (apply hash-map widget-base-args)))



(defn ^WidgetBase mk-bte [& args]
  "Short for mk-BlankTemplateElement."
  (apply mk-BlankTemplateElement args))
