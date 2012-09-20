(in-ns 'symbolicweb.core)


(defn ^WidgetBase %make-HTMLContainer [[^String html-element-type & widget-base-args] ^clojure.lang.Fn content-fn]
  (make-WidgetBase (fn [^WidgetBase html-container]
                     (binding [*in-html-container?* html-container] ;; Target for calls to SW done in CONTENT-FN.
                       (str "<" html-element-type " id='" (.id html-container) "'>"
                            (content-fn html-container)
                            "</" html-element-type ">")))
                   (apply hash-map widget-base-args)))



(defmacro with-html-container [[html-element-type & widget-base-args] & body]
  `(%make-HTMLContainer (into [~html-element-type] ~widget-base-args)
                        (fn [^WidgetBase html-container#]
                          (html ~@body))))

(defmacro whc [[html-element-type & widget-base-args] & body]
  `(with-html-container ~(into [html-element-type] widget-base-args)
     ~@body))



;; TODO: This should support the same "syntax" as HTMLTemplate.
(defn ^WidgetBase make-PostHTMLTemplate [^String id ^clojure.lang.Fn content-fn & widget-base-args]
  "This applies templating to an already existing HTML element, specified by ID, on the page."
  (with1 (%make-HTMLContainer (into ["%PostHTMLTemplate"] (into widget-base-args (list id :id)))
                              content-fn)
    (render-html it)))



(defn ^WidgetBase make-HTMLTemplate [^org.jsoup.nodes.Document html-resource
                                     ^clojure.lang.Fn content-fn
                                     & widget-base-args]
  "  CONTENT-FN is something like:
  (fn [html-template]
    [\".itembox\" html-template
     \".title\" (mk-p title-model)
     \"#sw-js-bootstrap\" (sw-js-bootstrap)]) ;; String."
  (make-WidgetBase
   (fn [^WidgetBase template-widget]
     (let [transformation-data (content-fn template-widget)
           html-resource (.clone html-resource)] ;; Always manipulate a copy to avoid any concurrency problems.
       (doseq [[selector content] (partition 2 transformation-data)]
         (let [^org.jsoup.nodes.Element element (.first (.select html-resource selector))]
           (assert element (str "HTMLTemplate: No element found for selector: " selector))
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



(defn ^WidgetBase make-TemplateElement [^ValueModel value-model & widget-base-args]
  "TemplateElements are meant to be used in context of HTMLContainer and its subtypes."
  (mk-he "%TemplateElement" value-model :widget-base-args (apply hash-map widget-base-args)))


(defn ^WidgetBase mk-te [^ValueModel value-model & widget-base-args]
  "Short for make-TemplateElement."
  (apply make-TemplateElement value-model widget-base-args))


(defn ^WidgetBase make-BlankTemplateElement[& widget-base-args]
  "A TemplateElement which doesn't have a Model.
This might be used to setup a target for DOM events on some static content from a template."
  (make-WidgetBase (fn [_]) (apply hash-map widget-base-args)))


(defn ^WidgetBase mk-bte [& args]
  "Short for make-BlankTemplateElement."
  (apply make-BlankTemplateElement args))
