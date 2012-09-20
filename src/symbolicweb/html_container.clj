(in-ns 'symbolicweb.core)


(derive ::HTMLContainer ::HTMLElement)
(defn %make-HTMLContainer ^WidgetBase [[^String html-element-type & args] ^clojure.lang.Fn content-fn]
  (apply make-WidgetBase
         ::HTMLContainer
         (fn [^WidgetBase html-container]
           (binding [*in-html-container?* html-container] ;; Target for calls to SW done in CONTENT-FN.
             (str "<" html-element-type " id='" (.id html-container) "'>"
                  (content-fn html-container)
                  "</" html-element-type ">")))
         args))

(defmacro with-html-container [[html-element-type & args] & body]
  `(%make-HTMLContainer (into [~html-element-type] ~args)
                        (fn [^WidgetBase html-container#]
                          (html ~@body))))

(defmacro whc [[html-element-type & args] & body]
  `(with-html-container ~(into [html-element-type] args)
     ~@body))



;; TODO: This should support the same "syntax" as HTMLTemplate.
(derive ::PostHTMLTemplate ::HTMLContainer)
(defn make-PostHTMLTemplate ^WidgetBase [^String id ^clojure.lang.Fn content-fn & args]
  "This applies templating to an already existing HTML element, specified by ID, on the page."
  (with1 (%make-HTMLContainer (into ["%PostHTMLTemplate"] (into args (list id :id)))
                              content-fn)
    (render-html it)))



(derive ::HTMLTemplate ::HTMLContainer)
(defn make-HTMLTemplate ^WidgetBase [^org.jsoup.nodes.Document html-resource
                                     ^clojure.lang.Fn content-fn
                                     & args]
  "CONTENT-FN is something like:
  (fn [html-template]
    [\".itembox\" html-template
     \".title\" (mk-p title-model)
     \"#sw-js-bootstrap\" (sw-js-bootstrap)]) ;; String."
  (apply make-WidgetBase
         ::HTMLTemplate
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
         args))


(derive ::TemplateElement ::HTMLElement)
(defn make-TemplateElement ^WidgetBase [^ValueModel value-model & args]
  "TemplateElements are meant to be used in context of HTMLContainer and it's subtypes.
It already has static HTML defined for it via it's template in context, and only maintains the Model --> View relationship via
its OBSERVED-EVENT-HANDLER-FN field; defaulting to using jqHTML there."
  (apply mk-he
         "%TemplateElement"
         value-model
         :type ::TemplateElement
         args))

(defn mk-te ^WidgetBase [^ValueModel value-model & args]
  "Short for make-TemplateElement."
  (apply make-TemplateElement value-model args))


(derive ::BlankTemplateElement ::HTMLElement)
(defn make-BlankTemplateElement ^WidgetBase [& args]
  "A TemplateElement which doesn't have a Model.
This might be used to setup a target for DOM events on some static content from a template."
  (apply make-HTMLElement
         ::BlankTemplateElement
         (vm ::not-used)
         (fn [_])
         (fn [_ _ _ _])
         args))

(defn mk-bte ^WidgetBase [& args]


  "Short for make-BlankTemplateElement."
  (apply make-BlankTemplateElement args))
