(in-ns 'symbolicweb.core)

(defn ensure-agent [& objects]
  (map #(if (= clojure.lang.Agent (type %))
          %
          (agent %))
       objects))


(defn render-event [widget event-type & {:keys [js-before callback-data js-after]
                                         :or {js-before "return(true);"
                                              callback-data ""
                                              js-after ""}}]
  (str "$('#" (:id widget) "').bind('" event-type "', "
       "function(event){"
       "swMsg('" (:id widget) "', '" event-type "', function(){" js-before "}, '"
       (reduce (fn [acc key_val] (str acc (url-encode (str (key key_val))) "=" (val key_val) "&"))
               ""
               callback-data)
       "', function(){" js-after "});"
       "});"))


(defn render-events [widget]
  "Returns JS which will set up client/DOM side events."
  (when (:callbacks widget)
    (with-out-str
      (loop [callbacks (:callbacks widget)]
        (when-first [[event-type [callback-fn callback-data]] callbacks]
          (print (render-event widget event-type :callback-data callback-data))
          (recur (rest callbacks)))))))


(defn render-aux [widget]
  "Return JS which will initialize WIDGET."
  (when-let [render-aux-fn (:render-aux-fn widget)]
    (render-aux-fn widget)))


(defn render-html [widget]
  "Return HTML structure which will be the basis for further initialization via RENDER-AUX.
The return value of RENDER-AUX will be inlined within this structure."
  (let [widget (if (= (type widget) clojure.lang.Agent)
                 @(await1 widget)
                 widget)]
    (cond
     (map? widget)
     ((:render-html-fn widget) widget)

     (string? widget)
     (escape-html widget)

     true
     (throw (Exception. (str "Can't render: " widget))))))


(defn render-children [widget]
  (with-out-str []
    (loop [children (:children widget)]
      (when-first [child children]
        (print (render-html child))
        (recur (rest children))))))


(defn render-static-attributes [widget]
  "")


(defn add-children [root-Container & children]
  (send root-Container #(update-in % [:children] into (apply ensure-agent children))))


(defn set-event-handler [event-type widget callback-fn & {:keys [callback-data]}]
  "Set an event handler for WIDGET.
Returns WIDGET."
  (send widget #(update-in % [:callbacks] conj [event-type [callback-fn callback-data]])))


(defn update-widget-data [widget new-widget-data]
  {:pre (= clojure.lang.Agent (type widget))}
  (send widget (fn [_] new-widget-data)))


(defn make-ID
  ([] (make-ID {}))
  ([m] (assoc m :id (generate-aid))))


(defn make-WidgetBase [& key_vals]
  (with1 (agent (apply assoc (make-ID)
                       :type ::WidgetBase
                       :in-dom? false
                       :parent nil
                       :callbacks {} ;; event-name -> [handler-fn callback-data]
                       :render-html-fn #(throw (Exception. (str "No :RENDER-HTML-FN defined for this widget (ID: " (:id %) ").")))
                       :parse-callback-data-handler #'default-parse-callback-data-handler
                       key_vals))
         ;; There's no way we'll create a Widget in the context of one Viewport and use it or "send" it to another Viewport anyway.
         (send *viewport* #(update-in % [:widgets] conj [(:id @it) it]))
         (await *viewport*)))


(defn make-Widget [& key_vals]
  (apply make-WidgetBase key_vals))


(defn make-HTMLElement [html-element-type children & key_vals]
  (apply make-Widget
         :type ::HTMLElement
         :html-element-type html-element-type
         :children (if children
                     (into [] (apply ensure-agent children))
                     [])
         :render-html-fn #(str "<" (:html-element-type %) " id='" (:id %) "'" (render-static-attributes %) ">"
                               (render-children %)
                               (let [script (str (render-aux %) (render-events %))]
                                 (when (seq script)
                                   (str "<script type='text/javascript'>" script "</script>")))
                               "</" (:html-element-type %) ">")
         key_vals))


(defn make-Button [& children]
  (make-HTMLElement "button" children
                    :type ::Button))


(defn make-Sortable [& children]
  (make-HTMLElement "ul" children
                    :type ::JQUSortable
                    :render-aux-fn #(str "$(#" (:id %) ").sortable();")
                    ))
