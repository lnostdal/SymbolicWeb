(in-ns 'symbolicweb.core)

;;; When talking about the "inner node" it is meant in context of (member of) FILTERED-CONTAINER-MODEL.
;;; When talking about the "outer node" it is meant in context of the ContainerModel observed by FILTERED-CONTAINER-MODEL.


(defn node-of-node-in-context [our-context ^ContainerModelNode outer-node]
  "Used by mk-FilteredContainerModel instance to find Node closest to OUTER-NODE in OUR-CONTEXT.

Returns two values: [ContainerModelNode relative-position] where relative-position can be :direct, :left, :right or :none."
  (let [our-context (ensure our-context)]
    (if-let [closest-node (get our-context outer-node)]
      [closest-node :direct]
      ;; Iterate from OUTER-NODE starting position outwards; left and right; finding closest relative Node.
      (loop [l-node (cmn-left-node outer-node)
             r-node (cmn-right-node outer-node)]
        (if (or l-node r-node)
          (if-let [closest-node (and l-node (get our-context l-node))]
            [closest-node :left]
            (if-let [closest-node (and r-node (get our-context r-node))]
              [closest-node :right]
              (recur (when l-node (cmn-left-node l-node))
                     (when r-node (cmn-right-node r-node)))))
          [nil :none])))))



(defn handle-filtered-container-event [^ContainerModel filtered-container-model
                                       ^clojure.lang.Ref context
                                       ^clojure.lang.Fn filter-node-fn
                                       event-args]

  (letfn [(add-node [^ContainerModelNode inner-new-node]
            (let [[event-sym & event-args] event-args]
              (case event-sym
                cm-prepend
                (cm-prepend filtered-container-model inner-new-node)

                cmn-after
                (let [^ContainerModelNode outer-existing-node (nth event-args 0)
                      [^ContainerModelNode inner-closest-existing-node rel-pos] (node-of-node-in-context context
                                                                                                         outer-existing-node)]
                  (case rel-pos
                    (:direct :left)
                    (cmn-after inner-closest-existing-node inner-new-node)

                    :right
                    (cmn-before inner-closest-existing-node inner-new-node)

                    :none
                    (cm-prepend filtered-container-model inner-new-node)))

                cmn-before
                (let [^ContainerModelNode outer-existing-node (nth event-args 0)
                      [^ContainerModelNode inner-closest-existing-node rel-pos] (node-of-node-in-context context
                                                                                                         outer-existing-node)]
                  (case rel-pos
                    (:direct :right)
                    (cmn-before inner-closest-existing-node inner-new-node)

                    :left
                    (cmn-after inner-closest-existing-node inner-new-node)

                    :none
                    (cm-prepend filtered-container-model inner-new-node))))))


          (setup-observer-for-seen-node [^ContainerModelNode inner-new-node ^ContainerModelNode outer-new-node]
            (vm-observe (cmn-data inner-new-node) (.lifetime outer-new-node) true
                        (fn [^Lifetime inner-lifetime _ _]
                          (when (filter-node-fn filtered-container-model inner-new-node)
                            ;; TODO: Ok, this sems to work, but think some more about why the call to alter here needs to happen
                            ;; after the call to add-node.. getting tired here; most likely.
                            (add-node inner-new-node)
                            (alter context assoc outer-new-node inner-new-node)
                            (detach-lifetime inner-lifetime) ;; Stop observing with regards to adding.
                            (vm-observe (cmn-data inner-new-node) (.lifetime inner-new-node) false
                                        (fn [inner-lifetime _ _]
                                          (when-not (filter-node-fn filtered-container-model inner-new-node)
                                            (cmn-remove inner-new-node)
                                            (alter context dissoc outer-new-node)
                                            (detach-lifetime inner-lifetime)
                                            ;; Start all over again.
                                            (handle-filtered-container-event filtered-container-model
                                                                             context
                                                                             filter-node-fn
                                                                             event-args))))))))]
    (let [[event-sym & event-args] event-args]
      (case event-sym
        cm-prepend
        (let [^ContainerModelNode outer-new-node (nth event-args 0)
              ^ContainerModelNode inner-new-node (cmn (cmn-data outer-new-node))]
          (setup-observer-for-seen-node inner-new-node outer-new-node))

        (cmn-after cmn-before)
        (let [^ContainerModelNode outer-new-node (nth event-args 1)
              ^ContainerModelNode inner-new-node (cmn (cmn-data outer-new-node))]
          (setup-observer-for-seen-node inner-new-node outer-new-node))

        cmn-remove
        (let [^ContainerModelNode outer-existing-node (nth event-args 0)]
          (when-let [^ContainerModelNode inner-existing-node (get context outer-existing-node)]
            (cmn-remove inner-existing-node)
            (alter context dissoc outer-existing-node)))))))





(defn ^ContainerModel mk-FilteredContainerModel [^ContainerModel container-model ^clojure.lang.Fn filter-node-fn]
  "Returns a ContainerModel that is synced with CONTAINER-MODEL via FILTER-NODE-FN."
  (let [^ContainerModel filtered-container-model (cm)
        context (ref {})] ;; Mapping between CMNs in CONTAINER-MODEL and CMNs in FILTERED-CONTAINER-MODEL.

    ;; Make FILTERED-CONTAINER-MODEL aware of existing CMNs in CONTAINER-MODEL.
    (loop [^ContainerModelNode outer-node (cm-tail-node container-model)]
      (when outer-node
        (handle-filtered-container-event filtered-container-model
                                         context
                                         filter-node-fn
                                         ['cm-prepend outer-node])
        (recur (cmn-left-node outer-node))))

    (observe (.observable container-model) (.lifetime filtered-container-model)
             (fn [_ event-args]
               (handle-filtered-container-event filtered-container-model
                                                context
                                                filter-node-fn
                                                event-args)))

    (attach-lifetime (.lifetime container-model) (.lifetime filtered-container-model))

    filtered-container-model))
