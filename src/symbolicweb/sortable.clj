(in-ns 'symbolicweb.core)


(defn sortable [^WidgetBase container-view ^ContainerModel container-model & callback]
  (letfn [(update-dom-hash-codes []
            ;; Assign the hashCodes of the CMNs of CONTAINER-MODEL to the DOM so we can find the CMNs later.
            (loop [node (cm-head-node container-model)
                   index 1]
              (when node
                (js-run container-view
                  "$('#" (.id container-view) " *:nth-child(" index ")')"
                  ".data('cmn_hash_code', '" (.hashCode node) "');")
                (when-let [right-node (cmn-right-node node)]
                  (recur right-node
                         (inc index))))))]

    (js-run container-view "$('#" (.id container-view) "').sortable();")
    (update-dom-hash-codes)

    (set-event-handler
     "sortupdate" container-view
     (fn [& {:keys [new-order]}]
       (dosync
        (println "sortable: retry?")
        (let [^clojure.lang.PersistentVector
              new-order-hash-codes (json-parse new-order)

              ^clojure.lang.PersistentVector
              existing-order-nodes (with-local-vars [nodes []]
                                     (loop [node (cm-head-node container-model)]
                                       (var-alter nodes conj node)
                                       (when-let [right-node (cmn-right-node node)]
                                         (recur right-node)))
                                     (var-get nodes))

              ^clojure.lang.PersistentVector
              new-order-nodes (with-local-vars [nodes []]
                                (doseq [hash-code new-order-hash-codes]
                                  (loop [node (cm-head-node container-model)]
                                    (when (= hash-code (str (.hashCode node)))
                                      (var-alter nodes conj node))
                                    (when-let [right-node (cmn-right-node node)]
                                      (recur right-node))))
                                (var-get nodes))]


          ;; Do some simple checks to ensure the client doesn't have less (or different) nodes than the server as we start
          ;; removing EXISTING-ORDER-NODES on the server end the adding (wrongly) only a few of them back via NEW-ORDER-NODES.
          (assert (= (count new-order-nodes)
                     (count existing-order-nodes)))
          (doseq [new-node new-order-nodes]
            (assert (not= -1 (.indexOf existing-order-nodes new-node))))

          ;; TODO: Quite crude redraw. This also fucks up stuff (uhm, what stuff?) that still thinks the old nodes are what holds
          ;; our current view of auctions.
          (doseq [node existing-order-nodes]
            (cmn-remove node))
          (time
           (doseq [node new-order-nodes]
             (cm-append container-model (cmn (cmn-data node)))))

          (update-dom-hash-codes)

          (when callback (callback new-order-nodes))))
       (println "sortable: done!"))
     :callback-data {:new-order
                     (str "' + encodeURIComponent(JSON.stringify(jQuery.makeArray($('#" (.id container-view) "')"
                          ".children().map(function(index, element){ return($(element).data('cmn_hash_code')); })"
                          "))) + '")})))
