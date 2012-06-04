(in-ns 'symbolicweb.core)


(derive ::HTMLContainer ::HTMLElement)
(defn %make-HTMLContainer [[html-element-type & args] content-fn]
  (apply make-HTMLElement
         ::HTMLContainer
         (vm ::not-used)
         (fn [html-container]
           (binding [*in-html-container?* html-container]
             (str "<" html-element-type " id='" (.id html-container) "'>"
                  (content-fn html-container)
                  "</" html-element-type ">")))
         (fn [_ _ _ _] (assert false "A HTMLContainers Model shouldn't be used (mutated)."))
         args))


(defmacro with-html-container [[html-element-type & args] & body]
  `(%make-HTMLContainer (into [~html-element-type] ~args)
                        (fn [html-container#]
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
  (apply make-HTMLElement "%HTMLTemplate" (vm nil)
         :type ::HTMLTemplate
         :handle-model-event-fn (fn [widget old-value new-value])
         :connect-model-view-fn (fn [model widget])
         :disconnect-model-view-fn (fn [widget])
         :render-html-fn
         (fn [template-widget]
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
                     (.attr element "id" (widget-id-of content))
                     (when-not (= content template-widget)
                       (add-branch *in-html-container?* content))))))
             (.html (.select html-resource "body"))))
         args))


(derive ::TemplateElement ::HTMLElement)
(defn make-TemplateElement [model & attributes]
  "A TemplateElement has already got its static HTML (:html-element-type etc.) defined for it via a template (make-HTMLTemplate).
It still maintains the same Model <-> View relationship (jqHTML) as a HTMLElement unless it is overridden."
  (apply make-HTMLElement "%make-TemplateElement" model
         :type ::TemplateElement
         :render-html-fn (fn [w])
         attributes))

(defn mk-te [model & attributes]
  "Short for make-TemplateElement."
  (apply make-TemplateElement model attributes))


(derive ::BlankTemplateElement ::TemplateElement)
(defn make-BlankTemplateElement [& attributes]
  "A TemplateElement which doesn't have a Model.
This might be used to setup events on some static content from a template."
  (apply make-TemplateElement (vm "%make-BlankTemplateElement")
         :type ::BlankTemplateElement
         :handle-model-event-fn (fn [widget old-value new-value])
         attributes))

(defn mk-bte [& attributes]
  "Short for make-BlankTemplateElement."
  (apply make-BlankTemplateElement attributes))
