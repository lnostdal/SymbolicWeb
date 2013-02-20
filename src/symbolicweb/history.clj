(ns symbolicweb.core)

;; TODO: Need some (de)serialization thing here.



(defn map-to-url
  ([model name lifetime viewport]
     (map-to-url model name lifetime viewport true))

  ([model name lifetime viewport ^Boolean initial-sync-from-url?]
     ;; Initialization.
     (if-let [value (and initial-sync-from-url?
                         (get @(:query-params @viewport) name))]
       (vm-set model value)
       (do
         (vm-alter (:query-params @viewport) assoc name @model)
         (add-response-chunk (str "window.history.replaceState(null, '', '?"
                                  (ring.util.codec/form-encode @(:query-params @viewport))
                                  "');\n")
                             viewport)))

     ;; Server --> Client.
     (vm-observe model lifetime false
                 (fn [inner-lifetime old-value new-value]
                   (when-not (or (= old-value new-value)
                                  (= new-value (get @(:query-params @viewport) name)))
                     (vm-alter (:query-params @viewport) assoc name new-value)
                     (add-response-chunk (str "window.history.pushState(null, '', '?"
                                              (ring.util.codec/form-encode @(:query-params @viewport))
                                              "');\n")
                                         viewport))))

     ;; Client --> Server.
     (vm-observe (:popstate-observer @viewport) lifetime false
                 (fn [inner-lifetime old-value new-value]
                   (doseq [[k v] new-value]
                     (when (= k name)
                       (vm-alter (:query-params @viewport) assoc name v)
                       (vm-set model v)))))))
