(in-ns 'symbolicweb.core)



(defn mk-HTMLTemplate "Bind Widgets to existing, static HTML.

  CONTENT-FN is something like:

  (fn [html-template]
    [\".itembox\" html-template
     \".title\" (mk-p title-model)
     \".picture\" [:attr :src \"logo.png\"]
     \"#sw-js-bootstrap\" (sw-js-bootstrap)]) ;; String."
  ^WidgetBase [^org.jsoup.nodes.Document html-resource ^Fn content-fn & args]
  (mk-WidgetBase
   (fn [^WidgetBase template-widget]
     (let [transformation-data (content-fn template-widget)
           html-resource (.clone html-resource)] ;; Always manipulate a copy to avoid any concurrency problems.
       ;; Assign id to template instance.
       (with (.first (.select html-resource "body"))
         (assert (count (.children it)) 1)
         (.attr (.child it 0) "id" ^String (.id template-widget)))
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
           (let [content-class (class content)]
             (cond ;; COND like this is as of Clojure 1.5 faster than e.g. (case (.toString (class content)) ...).
               (= java.lang.String content-class)
               (.text element content)

               (= clojure.lang.PersistentVector content-class)
               (let [^Keyword cmd (first content)]
                 (case cmd
                   :attr
                   (let [[^Keyword attr-key ^String attr-value] (rest content)]
                     (.attr element (name attr-key) attr-value))

                   :html
                   (.html element ^String (second content))))

               (= symbolicweb.core.WidgetBase content-class)
               (do
                 (.attr element "id" ^String (.id ^WidgetBase content))
                 (attach-branch template-widget content))))))
       (.html (.select html-resource "body"))))
   (apply hash-map args)))



(defn mk-HTMLCTemplate "Like mk-HTMLTemplate, but does the templating client side; in the browser, using jQuery."
  ^WidgetBase [^String html-resource ^Fn content-fn & args]
  (let [resource-hash (str "_sw_htmlctemplate-" (hash html-resource))]
    (mk-WidgetBase
     (fn [^WidgetBase template-widget]
       (vm-observe (.viewport template-widget) (.lifetime template-widget) true
                   #(when %3
                      ;; Add template to Viewport once. It will be cloned and filled in (templated) later.
                      (when-not (get (ensure (:html-templates @%3)) resource-hash)
                        (alter (:html-templates @%3) conj resource-hash)
                        (js-run template-widget
                          "$('#_sw_htmlctemplates').append($(" (js-handle-value html-resource false) ").attr('id', '" resource-hash "'));"))
                      (let [transformation-data (content-fn template-widget)]
                        ;; Instantiate (clone) template and assign HTML attr. ID for it.
                        (js-run template-widget
                          "$('#" (.id template-widget) "').replaceWith($('#" resource-hash "').clone().attr('id', '" (.id template-widget) "'));")
                        (doseq [[^String selector content] (partition 2 transformation-data)]
                          (let [content-class (class content)]
                            (cond
                              (= java.lang.String content-class)
                              (js-run template-widget
                                "$('#" (.id template-widget) "').find('" selector "').text(" (js-handle-value content false) ");")

                              (= clojure.lang.PersistentVector content-class)
                              (let [^Keyword cmd (first content)]
                                (case cmd
                                  :attr
                                  (let [[^Keyword attr-key ^String attr-value] (rest content)]
                                    (js-run template-widget
                                      "$('#" (.id template-widget) "').find('" selector "').attr('" (name attr-key) "', "
                                      (js-handle-value attr-value false) ");"))

                                  :html
                                  (js-run template-widget
                                    "$('#" (.id template-widget) "').find('" selector "').html(" (js-handle-value (second content) false) ");")))

                              (= symbolicweb.core.WidgetBase content-class)
                              (do
                                (js-run template-widget
                                  "$('#" (.id template-widget) "').find('" selector "').attr('id', '" (.id ^WidgetBase content) "');")
                                (attach-branch template-widget content))))))))
       (str "<div id='" (.id template-widget) "' style='display: none;'></div>"))
     (apply hash-map args))))



(defn  mk-TemplateElement "TemplateElements are meant to be used in context of HTMLContainer and its subtypes."
  ^WidgetBase [^ValueModel value-model & args]
  (apply mk-he "%TemplateElement" value-model args))


(defn mk-te "Short for mk-TemplateElement."
  ^WidgetBase [^ValueModel value-model & args]
  (apply mk-TemplateElement value-model args))



(defn mk-BlankTemplateElement "A TemplateElement which doesn't have a Model.
  This might be used to setup a target for DOM events on some static content from a template."
  ^WidgetBase [& args]
  (mk-WidgetBase (fn [_]) (apply hash-map args)))


(defn mk-bte "Short for mk-BlankTemplateElement."
  ^WidgetBase [& args]
  (apply mk-BlankTemplateElement args))
