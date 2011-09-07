(in-ns 'symbolicweb.core)


(defn view-of-node-in-context [container-view node & find-only?]
  "Find or create (if not found) new View of NODE in context of CONTAINER-VIEW."
  (if-let [existing-view (get @(:view-of-node @container-view) node)]
    existing-view
    (do
      (assert (not find-only?))
      (let [new-view ((:view-from-node-fn @container-view) container-view node)]
        (alter (:view-of-node @container-view) assoc node new-view)
        new-view))))


(defn handle-container-view-event [container-view event-sym event-args]
  "Forward Container related operations/events to the View end."
  (case event-sym
    append-container-model
    (let [container-model (nth event-args 0)
          new-node (nth event-args 1)]
      (dosync
       (jqAppend container-view (view-of-node-in-context container-view new-node))))

    prepend-container-model
    (let [container-model (nth event-args 0)
          new-node (nth event-args 1)]
      (dosync
       (jqPrepend container-view (view-of-node-in-context container-view new-node))))


    after-container-model-node
    (let [existing-node (nth event-args 0)
          new-node (nth event-args 1)]
      (dosync
       (jqAfter (view-of-node-in-context container-view existing-node true)
                (view-of-node-in-context container-view new-node))))

    before-container-model-node
    (let [existing-node (nth event-args 0)
          new-node (nth event-args 1)]
      (dosync
       (jqBefore (view-of-node-in-context container-view existing-node true)
                 (view-of-node-in-context container-view new-node))))

    remove-container-model-node
    (let [node (nth event-args 0)]
      (dosync
       (jqRemove (view-of-node-in-context container-view node true))))))


(defn make-ContainerView [element-type_element-attributes container-model]
  (with1 (make-HTMLElement (conj (ensure-vector element-type_element-attributes)
                                 :set-model-fn
                                 (fn [widget model]
                                   (alter (:views model) conj widget)
                                   (let [watch-key (generate-uid)]
                                     (add-watch (:event-router model) (generate-uid)
                                                (fn [_ _ _ event-router-entries]
                                                  (dosync
                                                   (doseq [entry event-router-entries]
                                                     (handle-container-view-event widget
                                                                                  (first entry)
                                                                                  (rest entry)))
                                                   (when (seq event-router-entries)
                                                     (ref-set (:event-router model) [])))))
                                     watch-key))

                                 :view-from-node-fn ;; TODO: Should this simply pass (:data node) directly?
                                 (fn [container-view node]
                                   (make-HTMLElement "p" (:data node)))

                                 :view-of-node (ref {}))
                           container-model)))
