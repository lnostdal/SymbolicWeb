(in-ns 'symbolicweb.core)


(derive ::HTMLContainer ::HTMLElement)
(defn %make-HTMLContainer [[html-element-type & args] content-fn]
  (apply make-HTMLElement
         ::HTMLContainer
         (vm ::not-used)
         (fn [^WidgetBase html-container]
           (binding [*in-html-container?* html-container]
             (str "<" html-element-type " id='" (.id html-container) "'>"
                  (content-fn html-container)
                  "</" html-element-type ">")))
         (fn [_ _ _ _] #_(assert false "A HTMLContainer Model shouldn't be used (mutated)."))
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
(defn make-PostHTMLTemplate [id content-fn & args]
  "This applies templating to an already existing HTML element, specified by ID, on the page."
  (with1 (%make-HTMLContainer (into ["%PostHTMLTemplate"] (into args (list id :id)))
                              content-fn)
    (render-html it)))


(derive ::HTMLTemplate ::HTMLContainer)
(defn make-HTMLTemplate [^org.jsoup.nodes.Document html-resource
                         ^clojure.lang.Fn content-fn
                         & args]
  "HTML-RESOURCE is the return-value of a call to HTML-RESOURCE from the Enlive library.
CONTENT-FN is something like:
  (fn [html-template]
    [\".itembox\" html-template
     \".title\" (mk-p title-model)
     \"#sw-js-bootstrap\" (sw-js-bootstrap)]) ;; String."
  (apply make-HTMLElement
         ::HTMLTemplate
         (vm ::not-used)
         (fn [^WidgetBase template-widget]

           (let [transformation-data (content-fn template-widget)
                 html-resource (.clone html-resource)] ;; Always manipulate a copy to avoid any concurrency problems.
             (doseq [[selector content] (partition 2 transformation-data)]
               (let [^org.jsoup.nodes.Element element (.first (.select html-resource selector))]
                 (assert element (str "HTMLTemplate: No element found for selector: " selector))
                 (if (string? content)
                   ;; NOTE: I could do (.html content) here. That would actually parse the
                   ;; HTML and add it to our HTML-RESOURCE for the next iteration to pick up.
                   (.text element content)
                   (do
                     (.attr element "id" (.id content))
                     (when-not (= content template-widget)
                       (binding [*in-html-container?* template-widget]
                         (add-branch template-widget content)))))))
             (.html (.select html-resource "body"))))
         (fn [_ _ _ _] #_(assert false "A HTMLTemplate Model shouldn't be used (mutated)."))
         args))


(derive ::TemplateElement ::HTMLElement)
(defn make-TemplateElement ^WidgetBase [model & args]
  "TemplateElements are meant to be used in context of HTMLContainer and it's subtypes.
It already has static HTML defined for it via it's template in context, and only maintains the Model --> View relationship via
its OBSERVED-EVENT-HANDLER-FN field; defaulting to jqHTML there."
  (apply make-HTMLElement
         ::TemplateElement
         model
         (fn [_])
         (fn [^WidgetBase template-element model old-value new-value]
           (jqHTML template-element (escape-html new-value)))
         args))

(defn mk-te [model & args]
  "Short for make-TemplateElement."
  (apply make-TemplateElement model args))


(derive ::BlankTemplateElement ::HTMLElement)
(defn make-BlankTemplateElement ^WidgetBase [& args]
  "A TemplateElement which doesn't have a Model.
This might be used to setup events on some static content from a template."
  (apply make-HTMLElement
         ::BlankTemplateElement
         (vm ::not-used)
         (fn [_])
         (fn [_ _ _ _] #_(assert false "A BlankTemplateElement Model shouldn't be used (mutated)."))
         args))

(defn mk-bte ^WidgetBase [& args]
  "Short for make-BlankTemplateElement."
  (apply make-BlankTemplateElement args))
