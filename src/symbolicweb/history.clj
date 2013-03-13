(ns symbolicweb.core)



(defn vm-map-to-url
  "HTML5 History support for ValueModels."
  ([^ValueModel model ^String name lifetime ^Ref viewport]
     (vm-map-to-url model name lifetime viewport true))


  ([^ValueModel model ^String name lifetime ^Ref viewport ^Boolean initial-sync-from-url?]

     ;; Initialization.
     (if-let [value (and initial-sync-from-url?
                         (get @(:query-params @viewport) name))]
       ;; Client --> server.
       (vm-set model value)
       ;; Server --> client.
       (do
         (vm-alter (:query-params @viewport) assoc name @model)
         (add-response-chunk (str "window.history.replaceState(null, '', '?"
                                  (ring.util.codec/form-encode @(:query-params @viewport))
                                  "');\n")
                             viewport)))

     ;; Server --> client.
     (vm-observe model lifetime false
                 (fn [inner-lifetime old-value new-value]
                   (when-not (= new-value (get @(:query-params @viewport) name))
                     (vm-alter (:query-params @viewport) assoc name new-value)
                     (add-response-chunk (str "window.history.pushState(null, '', '?"
                                              (ring.util.codec/form-encode @(:query-params @viewport))
                                              "');\n")
                                         viewport))))

     ;; Client --> server.
     (vm-observe (:popstate-observer @viewport) lifetime false
                 (fn [inner-lifetime old-value new-value]
                   (doseq [[k v] new-value]
                     (when (= k name)
                       (vm-alter (:query-params @viewport) assoc name v)
                       (vm-set model v)))))))



(defn url-alter-query-params [^Ref viewport ^Boolean replace? f & args]
  "Directly alters query-params of URL for Viewport.

  REPLACE?: If True, a history entry will be added at the client end."
  (apply vm-alter (:query-params @viewport) f args)
  (add-response-chunk (str "window.history." (if replace? "replaceState" "pushState")
                           "(null, '', '?" (ring.util.codec/form-encode @(:query-params @viewport)) "');\n")
                      viewport))



(defn assoc-URLMapper
  "Assosiate URL-MAPPER with CONTEXT-WIDGET.
The URL-MAPPER will be mapped to the Viewport (URL) of CONTEXT-WIDGET for the duration of that widgets Lifetime."
  ([url-mapper]
     (assoc-URLMapper url-mapper (:view url-mapper)))

  ([url-mapper ^WidgetBase context-widget]
     (vm-observe (.viewport context-widget) (.lifetime context-widget) true
                 (fn [_ _ viewport]
                   (when viewport
                     (vm-map-to-url (:model url-mapper) (:name url-mapper)
                                    (.lifetime context-widget)
                                    viewport))))))



(defn mk-URLMapper
  "Maps MODEL to URL with NAME as key for the duration of the Lifetime of VIEW."
  ([^String name ^ValueModel model]
     (mk-URLMapper name model nil))

  ([^String name ^ValueModel model ^WidgetBase view]
     (assert (string? name))
     (with1 {:name name
             :model model
             :view view}
       (when view
         (assoc-URLMapper it view)))))
