(in-ns 'symbolicweb.core)


(defn sortable [widget & callback]
  (add-response-chunk (str "$('#" (:id @widget) "').sortable();") widget)
  (set-event-handler
   "sortupdate" widget
   (fn [& {:keys [new-order]}]
     (let [new-order-ids (json/decode new-order)
           new-order-nodes (with-local-vars [nodes []]
                             (doseq [widget-id new-order-ids]
                               (var-set nodes (conj (var-get nodes) (dosync (:node-of-view @(get-widget widget-id))))))
                             (var-get nodes))
           container-model (container-model (:node-of-view @(get-widget (first new-order-ids))))
           existing-order-nodes (with-local-vars [nodes []]
                                  (loop [node (head-node container-model)]
                                    (var-set nodes (conj (var-get nodes) node))
                                    (when-let [right-node (right-node node)]
                                      (recur right-node)))
                                  (var-get nodes))]
       ;; TODO: Quite crude redraw.
       ;; This also fucks up stuff that still thinks the old nodes are what holds our current view of auctions.
       (doseq [node new-order-nodes]
         (append-container-model container-model (make-ContainerModelNode (node-data node))))
       (doseq [node existing-order-nodes]
         (remove-container-model-node node))
       (when callback (callback new-order-nodes))))
   :callback-data {:new-order (str "' + encodeURIComponent(JSON.stringify($('#" (:id @widget) "').sortable('toArray'))) + '")}))
