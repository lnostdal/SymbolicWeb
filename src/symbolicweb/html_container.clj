(in-ns 'symbolicweb.core)

;; TODO: Use MVC here too? For now I just dodge it.


(derive ::HTMLContainer ::HTMLElement)
(defn %make-HTMLContainer [[html-element-type & attributes] content-fn]
  (apply make-HTMLElement html-element-type (vm nil)
         :type ::HTMLContainer
         :handle-model-event-fn (fn [widget old-value new-value])
         :connect-model-view-fn (fn [model widget])
         :disconnect-model-view-fn (fn [widget])
         :render-aux-html-fn (fn [_] (content-fn))
         attributes))


(defmacro with-html-container [[html-element-type & attributes] & body]
  `(%make-HTMLContainer (into [~html-element-type] ~attributes)
                        (fn [] (html ~@body))))


(defmacro whc [[html-element-type & attributes] & body]
  `(with-html-container ~(into [html-element-type] attributes)
     ~@body))


;; TODO: This should support the same "syntax" as HTMLTemplate.
(derive ::PostHTMLTemplate ::HTMLContainer)
(defn make-PostHTMLTemplate [id content-fn & attributes]
  "This applies templating to an already existing HTML element, specified by ID, on the page."
  (with1 (%make-HTMLContainer (into ["%PostHTMLTemplate"] (into attributes (list id :id)))
                              content-fn)
    (render-html it)))


(derive ::HTMLTemplate ::HTMLContainer)
(defn make-HTMLTemplate [html-resource content-fn & attributes]
  "HTML-RESOURCE is the return-value of a call to HTML-RESOURCE from the Enlive library.
CONTENT-FN is something like:
  (fn [html-template]
    [[:.itembox] html-template
     [:.title] (mk-p title-model)
     [:#sw-js-bootstrap] (sw-js-bootstrap)]) ;; String."
  (apply make-HTMLElement "%HTMLTemplate" (vm nil)
         :type ::HTMLTemplate
         :handle-model-event-fn (fn [widget old-value new-value])
         :connect-model-view-fn (fn [model widget])
         :disconnect-model-view-fn (fn [widget])
         :render-html-fn
         (fn [w]
           (let [w-m @w
                 transformation-data (content-fn w)]
             (with-local-vars [res html-resource]
               (doseq [td (seq (partition 2 transformation-data))]
                 (if (string? (second td))
                   (var-set res (transform (var-get res) (first td)
                                           (html-content (second td))))
                   (do
                     (var-set res (transform (var-get res) (first td)
                                             (set-attr "id" (widget-id-of (second td)))))
                     (when-not (= (second td) w) ;; We don't want a circular parent / child relationship.
                       (sw (second td))))))
               (apply str (emit* (var-get res))))))
         attributes))


(derive ::TemplateElement ::HTMLElement)
(defn make-TemplateElement [model & attributes]
  "A TemplateElement has already got its static HTML (:html-element-type etc.) defined for it via a template (make-HTMLTemplate).
It still maintains the same Model <-> View relationship (jqHTML) as a HTMLElement unless it is overridden."
  (apply make-HTMLElement "%make-TemplateElement" model
         :type ::TemplateElement
         attributes))


(defn mk-te [model & attributes]
  "Short for make-TemplateElement."
  (apply make-TemplateElement model attributes))


(derive ::BlankTemplateElement ::TemplateElement)
(defn make-BlankTemplateElement [& attributes]
  "A TemplateElement which doesn't have a Model.
This might be used to setup events on some content from a template."
  (apply make-TemplateElement (vm "%make-BlankTemplateElement")
         :type ::BlankTemplateElement
         :handle-model-event-fn (fn [widget old-value new-value])
         attributes))

(defn mk-bte [& attributes]
  "Short for make-BlankTemplateElement."
  (apply make-BlankTemplateElement attributes))
