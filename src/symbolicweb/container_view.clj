(in-ns 'symbolicweb.core)


(defn observer-of-node-in-context
  "Find or create new Observer of NODE in context of CONTEXT.
CONTEXT is an Observer of the ContainerModel NODE is a member of.
If FIND-ONLY? is true no new Observer will be constructed if an existing one is not found."
  ([^WidgetBase context ^ContainerModelNode node]
     (observer-of-node-in-context context node false))

  ([^WidgetBase context ^ContainerModelNode node ^Boolean find-only?]
     (if-let [existing-observer (get (ensure (:observer-of-node context))
                                     node)]
       existing-observer
       (when-not find-only?
         (let [new-observer (assoc ((:observer-from-node-fn context) context node)
                              :node-of-observer node)] ;; Observer --> Node (used by e.g. Sortable)
           ;; The following connection is removed when NODE is removed from the observed ContainerModel.
           (alter (:observer-of-node context) assoc node new-observer) ;; Node --> Observer (via CONTEXT)
           new-observer)))))



(defn view-of-node-in-context
  "Find or create (if not found) new View of NODE in context of CONTAINER-VIEW.
If FIND-ONLY? is true no new View will be constructed if an existing one was not found."
  ([^WidgetBase container-view ^ContainerModelNode node]
     (view-of-node-in-context container-view node false))
  ([^WidgetBase container-view ^ContainerModelNode node ^Boolean find-only?]
     (if-let [existing-view (get (ensure (:view-of-node container-view))
                                 node)]
       existing-view
       (when-not find-only?
         (let [new-view (assoc ((:view-from-node-fn container-view) container-view node)
                          :node-of-view node)] ;; View --> Node (used by e.g. Sortable)
           (alter (:view-of-node container-view) assoc node new-view) ;; Node --> View (via context)
           ;; TODO: This is too specific; "visibility" shouldn't be mentioned here.
           ;;(add-on-non-visible-fn new-view (fn [_ _] (alter (:view-of-node container-view) dissoc node))) ;; Node -/-> View
           new-view)))))



(defn handle-container-view-event [^WidgetBase container-view ^ContainerModel container-model event-args]
  "Forward Container related operations/events to the View end."
  (let [[event-sym & event-args] event-args]
    (case event-sym
      cm-prepend
      (let [^ContainerModelNode new-node (nth event-args 0)]
        (jqPrepend container-view
          (view-of-node-in-context container-view new-node)))

      cmn-after
      (let [^ContainerModelNode existing-node (nth event-args 0)
            ^ContainerModelNode new-node (nth event-args 1)]
        (jqAfter (view-of-node-in-context container-view existing-node true)
          (view-of-node-in-context container-view new-node)))

      cmn-before
      (let [^ContainerModelNode existing-node (nth event-args 0)
            ^ContainerModelNode new-node (nth event-args 1)]
        (jqBefore (view-of-node-in-context container-view existing-node true)
          (view-of-node-in-context container-view new-node)))

      cmn-remove
      (let [^ContainerModelNode node (nth event-args 0)]
        (if-let [view (view-of-node-in-context container-view node true)]
          (do
            (jqRemove view)
            (alter (:view-of-node container-view) dissoc node))
          (println "ContainerView: Tried to remove Node, but no existing View of that Node was found."))))))



(defn ^WidgetBase make-ContainerView [^ContainerModel container-model ^clojure.lang.Fn view-from-node-fn & args]
  "  VIEW-FROM-NODE-FN: ^WidgetBase (fn [^WidgetBase container-view ^ContainerModelNode container-model-nodel] ..)"
  (with1 (make-WidgetBase (fn [^WidgetBase view] (str "<div id='" (.id view) "'></div>"))
                          (merge {:view-of-node (ref {})
                                  :view-from-node-fn view-from-node-fn}
                                 (apply hash-map args)))

    (observe (.observable container-model) (.lifetime it)
             (fn [^Lifetime inner-lifetime event-args]
               (handle-container-view-event it container-model event-args)))

    ;; Add any already existing nodes to CONTAINER-VIEW.
    ;; TODO: Is this really needed with the Lifetime tracking facility we have now? I guess it sort of leads to the same in effect,
    ;; but doing it at this end (Model) instead of at the View end (ADD-RESPONSE-CHUNK) seems more correct.
    (loop [node (cm-head-node container-model)]
      (when node
        (jqAppend it (view-of-node-in-context it node))
        (recur (cmn-right-node node))))))
