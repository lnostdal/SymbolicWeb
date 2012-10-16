(in-ns 'symbolicweb.core)


(defn sortable [^WidgetBase container-view ^ContainerModel container-model & callback]
  (add-response-chunk (str "$('#" (.id container-view) "').sortable();") container-view)
  (set-event-handler
   "sortupdate" container-view
   (fn [& {:keys [new-order]}]
     (dosync
      (println "sortable: retry?")
      (let [^clojure.lang.PersistentVector
            new-order-ids (json-parse new-order)

            ^clojure.lang.PersistentVector
            new-order-nodes (with-local-vars [nodes []]
                              (doseq [widget-id new-order-ids]
                                (var-alter nodes conj (:node-of-view @(:aux (get-widget widget-id (viewport-of container-view))))))
                              (var-get nodes))

            ^clojure.lang.PersistentVector
            existing-order-nodes (with-local-vars [nodes []]
                                   (loop [node (cm-head-node container-model)]
                                     (var-set nodes (conj (var-get nodes) node))
                                     (when-let [right-node (cmn-right-node node)]
                                       (recur right-node)))
                                   (var-get nodes))]

        ;; Do some simple checks to ensure the client doesn't have less (or different) nodes than the server as we start removing
        ;; EXISTING-ORDER-NODES on the server end the adding (wrongly) only a few of them back via NEW-ORDER-NODES.
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
   :callback-data {:new-order (str "' + encodeURIComponent(JSON.stringify($('#"
                                   (.id container-view)
                                   "').sortable('toArray'))) + '")}))
