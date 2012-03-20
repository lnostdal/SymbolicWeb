(in-ns 'symbolicweb.core)


(defn sortable [widget & callback]
  (add-response-chunk (str "$('#" (:id @widget) "').sortable();") widget)
  (set-event-handler
   "sortupdate" widget
   (fn [& {:keys [new-order]}]
     (dosync
      (println "sortable: retry?")
      (let [new-order-ids (json/decode new-order)
            new-order-nodes (with-local-vars [nodes []]
                              (doseq [widget-id new-order-ids]
                                (var-set nodes (conj (var-get nodes) (:node-of-view @(get-widget widget-id (viewport-of widget))))))
                              (var-get nodes))
            container-model (:model @widget)
            existing-order-nodes (with-local-vars [nodes []]
                                   (loop [node (cm-head-node container-model)]
                                     (var-set nodes (conj (var-get nodes) node))
                                     (when-let [right-node (cmn-right-node node)]
                                       (recur right-node)))
                                   (var-get nodes))]
        ;; TODO: Quite crude redraw.
        ;; This also fucks up stuff that still thinks the old nodes are what holds our current view of auctions.
        (doseq [node existing-order-nodes]
          (cmn-remove node))
        (time
         (doseq [node new-order-nodes]
           (cm-append container-model (cmn (node-data node)))))

        (when callback (callback new-order-nodes))))
     (println "sortable: done!"))
   :callback-data {:new-order (str "' + encodeURIComponent(JSON.stringify($('#" (:id @widget) "').sortable('toArray'))) + '")}))
