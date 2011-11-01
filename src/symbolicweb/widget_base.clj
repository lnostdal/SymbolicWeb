(in-ns 'symbolicweb.core)


(defn set-parent! [child new-parent]
  (alter child assoc :parent new-parent))


(defn set-children! [parent & children]
  (alter parent assoc :children (flatten children)))


(defn render-event [widget event-type & {:keys [js-before callback-data js-after]
                                         :or {js-before "return(true);"
                                              callback-data ""
                                              js-after ""}}]
  (let [widget @widget]
    (str "$('#" (:id widget) "').bind('" event-type "', "
         "function(event){"
         "swMsg('" (:id widget) "', '" event-type "', function(){" js-before "}, '"
         (reduce (fn [acc key_val] (str acc (url-encode (str (key key_val))) "=" (val key_val) "&"))
                 ""
                 callback-data)
         "', function(){" js-after "});"
         "});")))


(defn render-aux-js [widget]
  "Return JS which will initialize WIDGET."
  (when-let [render-aux-js-fn (:render-aux-js-fn @widget)]
    (render-aux-js-fn widget)))


(defn render-aux-html [widget]
  "Return \"aux\" HTML for WIDGET."
  (when-let [render-aux-html-fn (:render-aux-html-fn @widget)]
    (render-aux-html-fn widget)))


(declare add-branch)
(defn render-html [widget]
  "Return HTML structure which will be the basis for further initialization via RENDER-AUX-JS."
  (let [widget-m (ensure widget)
        widget-type (:type widget-m)]
    (cond
     (isa? widget-type ::Widget)
     (if (isa? widget-type ::HTMLContainer)
       (binding [*in-html-container?* widget]
         ((:render-html-fn widget-m) widget))
       ((:render-html-fn widget-m) widget))

     true
     (throw (Exception. (str "Can't render: " widget-m))))))


(defn sw [widget]
  "Render WIDGET as part of a HTMLContainer; WITH-HTML-CONTAINER."
  (assert *in-html-container?*)
  (add-branch *in-html-container?* widget)
  (render-html widget))


(defn set-event-handler [event-type widget callback-fn & {:keys [callback-data]}]
  "Set an event handler for WIDGET.
Returns WIDGET."
  ;; TODO: Check if EVENT-TYPE is already bound? Think about this ..
  (alter widget update-in [:callbacks] assoc event-type [callback-fn callback-data])
  (add-response-chunk (render-event widget event-type :callback-data callback-data)
                      widget)
  widget)


(defn make-ID
  ([] (make-ID {}))
  ([m] (assoc m :id (format "sw-%x" (generate-uid)))))


(defn make-WidgetBase [& key_vals]
  (ref (apply assoc (make-ID)
              :type ::WidgetBase
              :on-visible-fns []
              :on-non-visible-fns []
              :children [] ;; Note that order etc. doesn't matter here; this is only used to track visibility on the client-end.
              :callbacks {} ;; event-name -> [handler-fn callback-data]
              :render-html-fn #(throw (Exception. (str "No :RENDER-HTML-FN defined for this widget (ID: " (:id %) ").")))
              :parse-callback-data-handler #'default-parse-callback-data-handler
              key_vals)))


(derive ::Widget ::WidgetBase)
(defn make-Widget [& key_vals]
  (apply make-WidgetBase key_vals))


(derive ::HTMLElement ::Widget)
(defn make-HTMLElement [html-element-type model & attributes]
  (assert (isa? (:type model) ::Model))
  (apply make-Widget
         :type ::HTMLElement
         :html-element-type html-element-type
         :model model
         :escape-html? true
         :output-parsing-fn (fn [new-value] new-value)
         :handle-model-event-fn (fn [widget old-value new-value]
                                  (jqHTML widget (if (:escape-html? @widget)
                                                   (escape-html new-value)
                                                   new-value)))
         :trigger-initial-update? true
         :connect-model-view-fn (fn [model widget]
                                  (alter (:views model) conj widget)
                                  (when (:trigger-initial-update? @widget)
                                    ((:handle-model-event-fn @widget)
                                     widget nil ((:output-parsing-fn @widget) (get-value model)))))
         :disconnect-model-view-fn (fn [widget]
                                     (alter (:views model) disj widget))
         :render-static-attributes-fn (fn [w]
                                        (let [w-m @w]
                                          (with-out-str
                                            (doseq [key_val (:static-attributes w-m)]
                                              (print (str " "
                                                          (name (key key_val))
                                                          "='"
                                                          (name (val key_val)) ;; TODO: Escaping.
                                                          "'"))))))
         :render-html-fn (fn [w]
                           (let [w-m @w]
                             (str "<" (:html-element-type w-m) " id='" (:id w-m) "'" ((:render-static-attributes-fn w-m) w) ">"
                                  (render-aux-html w)
                                  "</" (:html-element-type w-m) ">"
                                  (let [script (str (render-aux-js w))]
                                    (when (seq script)
                                      (str "<script type='text/javascript'>" script "</script>"))))))
         attributes))


;; TODO: Button should actually be a container (HTMLContainer?).
(derive ::Button ::HTMLElement)
(defn make-Button [label-str & attributes]
  "Supply :MODEL as attribute if needed. This will override what's provided via LABEL-STR."
  (assert (string? label-str))
  (apply make-HTMLElement "button" (vm label-str)
         :type ::Button
         :escape-html? false ;; TODO: This is not safe wrt. XSS. Converting Button into a HTMLContainer will fix this though.
         attributes))


(defn make-View [model lifetime & attributes]
  "Supply :HANDLE-MODEL-EVENT-FN and you'll have an observer ('callback') of MODEL that is not a Widget.
The lifetime of this observer is governed by LIFETIME and can be a View/Widget or NIL for 'infinite' lifetime (as long as MODEL)."
  #_(assert (or (= :root lifetime)
                (isa? lifetime ::Widget)))
  (with (apply make-HTMLElement "%make-View" model
               :type ::View
               :handle-model-event-fn (fn [view old-value new-value]
                                        (assert false "make-View: No callback (:handle-model-event-fn) supplied."))
               attributes)
    (if lifetime
      (add-branch lifetime it)
      ((:connect-model-view-fn @it) model it))))


(defn mk-view [model lifetime handle-model-event-fn & attributes]
  (apply make-View model lifetime :handle-model-event-fn handle-model-event-fn
         attributes))


(derive ::Link ::HTMLElement)
(defn make-Link [model & attributes]
  "HTML Link (a href) element. MODEL represents the HREF attribute."
  (apply make-HTMLElement "a" model
         :type ::Link
         :handle-model-event-fn (fn [widget old-value new-value]
                                  (jqAttr widget "href" new-value))
         attributes))
