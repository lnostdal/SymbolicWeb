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

        ;; Do some simple checks to ensure the client doesn't have less (or different) nodes than the server as we start removing
        ;; EXISTING-ORDER-NODES on the server end the adding only a few of them back via NEW-ORDER-NODES.
        (assert (= (count new-order-nodes)
                   (count existing-order-nodes)))
        (doseq [new-node new-order-nodes]
          (assert (not= -1 (.indexOf existing-order-nodes new-node))))

        ;; TODO: Quite crude redraw.
        ;; This also fucks up stuff (uhm, what stuff?) that still thinks the old nodes are what holds our current view of auctions.
        (doseq [node existing-order-nodes]
          (cmn-remove node))
        (time
         (doseq [node new-order-nodes]
           (cm-append container-model (cmn (cmn-data node)))))

        (when callback (callback new-order-nodes))))
     (println "sortable: done!"))
   :callback-data {:new-order (str "' + encodeURIComponent(JSON.stringify($('#" (:id @widget) "').sortable('toArray'))) + '")}))
