(in-ns 'symbolicweb.core)



(defn view-of-node-in-context
  "Find or create (if not found) new View of NODE in context of CONTAINER-VIEW.
  If FIND-ONLY? is true no new View will be constructed if an existing one was not found."
  ([^WidgetBase container-view
    ^Ref views-of-nodes
    ^Fn view-from-node-fn
    ^ContainerModelNode node]
   (view-of-node-in-context container-view views-of-nodes view-from-node-fn node false))


  ([^WidgetBase container-view
    ^Ref views-of-nodes
    ^Fn view-from-node-fn
    ^ContainerModelNode node
    ^Boolean find-only?]
   (if-let [existing-view (get (ensure views-of-nodes) node)]
     existing-view
     (when-not find-only?
       (let [new-view (view-from-node-fn container-view node)]
         (alter views-of-nodes assoc node new-view) ;; Node --> View  (via context; CONTAINER-VIEW)
         new-view)))))



(defn handle-container-view-event "Forward CONTAINER-MODEL related operations/events to the CONTAINER-VIEW end."
  [^WidgetBase container-view
   ^ContainerModel container-model
   ^Ref views-of-nodes
   ^Fn view-from-node-fn
   event-args]
  (let [[event-sym & event-args] event-args]
    (case event-sym

      cm-prepend
      (let [^ContainerModelNode new-node (nth event-args 0)]
        (jqPrepend container-view
                   (view-of-node-in-context container-view views-of-nodes view-from-node-fn new-node)))

      cmn-after
      (let [^ContainerModelNode existing-node (nth event-args 0)
            ^ContainerModelNode new-node (nth event-args 1)]
        (jqAfter (view-of-node-in-context container-view views-of-nodes view-from-node-fn existing-node true)
                 (view-of-node-in-context container-view views-of-nodes view-from-node-fn new-node)))

      cmn-before
      (let [^ContainerModelNode existing-node (nth event-args 0)
            ^ContainerModelNode new-node (nth event-args 1)]
        (jqBefore (view-of-node-in-context container-view views-of-nodes view-from-node-fn existing-node true)
                  (view-of-node-in-context container-view views-of-nodes view-from-node-fn new-node)))

      cmn-remove
      (let [^ContainerModelNode node (nth event-args 0)]
        (if-let [view (view-of-node-in-context container-view views-of-nodes view-from-node-fn node true)]
          (do
            (jqRemove view)
            (alter views-of-nodes dissoc node))
          (println "ContainerView: Tried to remove Node, but no existing View of that Node was found.")))

      cm-clear
      (jqEmpty container-view))))



(defn mk-ContainerView "CONTAINER-VIEW: The Widget things will be contained in.
  VIEW-FROM-NODE-FN: ^WidgetBase (fn [^WidgetBase container-view ^ContainerModelNode container-model-node] ..).

  Returns CONTAINER-VIEW."
  ^WidgetBase [^WidgetBase container-view ^ContainerModel container-model ^Fn view-from-node-fn]
  (let [views-of-nodes (ref {})]

    (add-lifetime-activation-fn (.lifetime container-view)
                                (fn [^Lifetime lifetime]
                                  (cm-iterate container-model node data
                                              (jqAppend container-view
                                                (view-of-node-in-context container-view views-of-nodes view-from-node-fn node))
                                              false)))

    (observe (.observable container-model) (.lifetime container-view)
             (fn [^Lifetime inner-lifetime event-args]
               (handle-container-view-event container-view container-model views-of-nodes view-from-node-fn event-args)))

    container-view))
