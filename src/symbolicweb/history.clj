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
