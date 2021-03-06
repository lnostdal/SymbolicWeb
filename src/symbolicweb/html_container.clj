(in-ns 'symbolicweb.core)


(defn ^WidgetBase %mk-HTMLContainer [^Keyword html-element-type args ^Fn content-fn]
  (mk-WidgetBase (fn [^WidgetBase html-container]
                   (binding [*in-html-container?* html-container] ;; Target for calls to SW done in CONTENT-FN.
                     (html [html-element-type (-> (dissoc args :wb-args)
                                                  (assoc :id (.id html-container)))
                            (content-fn html-container)])))
                 (if-let [id (:id args)]
                   (assoc (:wb-args args) :id id)
                   (:wb-args args))))



(defmacro whc "WITH-HTML-CONTAINER. Some examples:

  (swsync
  (render-html
   (whc [:div {:style (style {:color 'red})}]
     \"Hello World! This is: \" (.id html-container))))
  \"<div id=\"sw-15570\" style=\"color: red;\">Hello World! This is: sw-15570</div>\"

  (swsync
  (let [some-model (vm \"hello\")
        some-widget (mk-span some-model)]
    (render-html
     (whc [:div]
      [:p \"Here's some widget: \" (sw some-widget)]))))
  \"<div id='sw-15591'><p>Here's some widget: <span id='sw-15590'></span></p></div>\"
  "
  [[html-element-type args] & body]
  (let [body (if (zero? (count body))
               '("")
               body)]
    `(%mk-HTMLContainer ~html-element-type
                        ~args
                        (fn [^WidgetBase ~'html-container]
                          (html ~@body)))))



(defn mk-PostHTMLTemplate "This applies templating to an already existing HTML element, specified by ID, on the page."
  ^WidgetBase [^String id ^Fn content-fn & args]
  (with1 (%mk-HTMLContainer :%PostHTMLTemplate
                            (into args (list id :id)) ;; TODO: This seems hacky.
                            content-fn)
    (render-html it)))
