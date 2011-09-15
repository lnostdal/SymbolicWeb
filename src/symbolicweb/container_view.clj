(in-ns 'symbolicweb.core)


(defn view-of-node-in-context [container-view node & find-only?]
  "Find or create (if not found) new View of NODE in context of CONTAINER-VIEW."
  (if-let [existing-view (get @(:view-of-node @container-view) node)]
    existing-view
    (do
      (assert (not find-only?) (str "View of NODE not found."))
      (let [new-view ((:view-from-node-fn @container-view) container-view node)]
        (alter (:view-of-node @container-view) assoc node new-view)
        (add-on-non-visible-fn new-view (fn []
                                          (alter (:view-of-node @container-view) dissoc node)))
        new-view))))


(defn handle-container-view-event [container-view event-sym event-args]
  "Forward Container related operations/events to the View end."
  (case event-sym
    prepend-container-model
    (let [container-model (nth event-args 0)
          new-node (nth event-args 1)]
      (jqPrepend container-view (view-of-node-in-context container-view new-node)))

    after-container-model-node
    (let [existing-node (nth event-args 0)
          new-node (nth event-args 1)]
      (jqAfter (view-of-node-in-context container-view existing-node true)
               (view-of-node-in-context container-view new-node)))

    before-container-model-node
    (let [existing-node (nth event-args 0)
          new-node (nth event-args 1)]
      (jqBefore (view-of-node-in-context container-view existing-node true)
                (view-of-node-in-context container-view new-node)))

    remove-container-model-node
    (let [node (nth event-args 0)]
      (jqRemove (view-of-node-in-context container-view node true)))))


(derive ::ContainerView ::HTMLElement)
(defn make-ContainerView [html-element-type container-model & attributes]
  (assert (= ::ContainerModel (:type container-model)))
  (apply make-HTMLElement html-element-type container-model
         :type ::ContainerView

         :connect-model-view-fn
         (fn [container-model container-view]
           ;; Add any already existing nodes to CONTAINER-VIEW.
           (loop [node (ensure (:head-node container-model))]
             (when node
               (jqAppend container-view (view-of-node-in-context container-view node))
               (recur (ensure (:right node)))))
           ;; Let CONTAINER-VIEW know about any future changes to CONTAINER-MODEL.
           (alter (:views container-model) conj container-view))

         :disconnect-model-view-fn
         (fn [widget]
           (alter (:views container-model) disj widget))

         :view-from-node-fn ;; TODO: Should this simply pass (:data node) directly?
         (fn [container-view node]
           (make-HTMLElement "p" (make-ValueModel (:data node))))

         :view-of-node (ref {})
         attributes))
