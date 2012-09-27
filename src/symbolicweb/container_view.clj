(in-ns 'symbolicweb.core)


(defn observer-of-node-in-context
  "Find or create new Observer of NODE in context of CONTEXT.
CONTEXT is an Observer of the ContainerModel NODE is a member of.
If FIND-ONLY? is true no new Observer will be constructed if an existing one is not found."
  ([^WidgetBase  context ^ContainerModelNode node]
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
    ;; but doing it at this end (Model) instead of at the View end seems more correct.
    (loop [node (cm-head-node container-model)]
      (when node
        (jqAppend it (view-of-node-in-context it node))
        (recur (cmn-right-node node))))))




;;; sync-ContainerModel stuff follows
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn node-of-node-in-context
  "Used by sync-ContainerModel to find Node directly or closest (location in sequence of Nodes) represented by NODE."
  ([^WidgetBase observer ^ContainerModelNode node]
     (node-of-node-in-context observer node false))
  ([^WidgetBase observer ^ContainerModelNode node ^Boolean find-only?]
     ;; Iterate from NODE starting position outwards; left and right, finding closest relative Node.
     (if-let [node-pos (if-let [view (view-of-node-in-context observer node find-only?)]
                         [(:-node view) :direct]
                         (loop [l-node (cmn-left-node node)
                                r-node (cmn-right-node node)]
                           (when (or l-node r-node)
                             (if-let [view (and l-node (view-of-node-in-context observer l-node true))]
                               [(:-node view) :left]
                               (if-let [view (and r-node (view-of-node-in-context observer r-node true))]
                                 [(:-node view) :right]
                                 (recur (when l-node (cmn-left-node l-node))
                                        (when r-node (cmn-right-node r-node))))))))]
       node-pos
       [nil :empty-synced-container-model])))



(defn handle-container-observer-event [^ContainerModel synced-container-model ^WidgetBase observer event-args]
  ;;(dbg-prin1 event-args)
  (let [[event-sym & event-args] event-args]
    (case event-sym

      cm-prepend
      (let [^ContainerModel container-model (nth event-args 0)
            ^ContainerModelNode new-node (nth event-args 1)]
        (when ((:filter-node-fn observer) synced-container-model new-node)
          (cm-prepend synced-container-model
                      (first (node-of-node-in-context observer new-node false)))))

      cmn-after
      (let [^ContainerModelNode existing-node (nth event-args 0)
            ^ContainerModelNode new-node (nth event-args 1)]
        (when ((:filter-node-fn observer) synced-container-model new-node)
          (let [[^ContainerModelNode rel-existing-node rel-pos] (node-of-node-in-context observer existing-node true)
                ^ContainerModelNode rel-new-node (:-node (view-of-node-in-context observer new-node false))]
            (case rel-pos
              (:direct :left)
              (cmn-after rel-existing-node rel-new-node)

              :right
              (cmn-before rel-existing-node rel-new-node)

              :empty-synced-container-model
              (cm-prepend synced-container-model rel-new-node)))))

      cmn-before
      (let [^ContainerModelNode existing-node (nth event-args 0)
            ^ContainerModelNode new-node (nth event-args 1)]
        (when ((:filter-node-fn @observer) synced-container-model new-node)
          (let [[^ContainerModelNode rel-existing-node rel-pos] (node-of-node-in-context observer existing-node true)
                ^ContainerModelNode rel-new-node (:-node (view-of-node-in-context observer new-node false))]
            (case rel-pos
              (:direct :right)
              (cmn-before rel-existing-node rel-new-node)

              :left
              (cmn-after rel-existing-node rel-new-node)

              :empty-synced-container-model
              (cm-prepend synced-container-model rel-new-node)))))

      cmn-remove
      (let [^ContainerModelNode node (nth event-args 0)]
        (when-let [^ContainerModelNode rel-existing-node (first (node-of-node-in-context observer node true))]
          (cmn-remove rel-existing-node)
          (alter (:view-of-node observer) dissoc rel-existing-node))))))



(defn sync-ContainerModel [^ContainerModel container-model lifetime filter-node-fn & args]
    "Returns a ContainerModel that is synced with CONTAINER-MODEL via FILTER-NODE-FN.
To state this differently; operations done vs CONTAINER-MODEL are forwarded to the ContainerModel returned by this function.
LIFETIME: Governs the extent of the connection between CONTAINER-MODEL and the returned ContainerModel and be a View/Widget or
NIL for 'infinite' lifetime (as long as CONTAINER-MODEL exists)."
  (let [synced-container-model (cm)
        ;; OBSERVER will forward (after filtering) events from CONTAINER-MODEL to SYNCED-CONTAINER-MODEL.
        observer (observe container-model nil true
                          (fn [observer _ event-args]
                            (handle-container-observer-event synced-container-model observer event-args))

                          :view-from-node-fn
                          (fn [^WidgetBase observer ^ContainerModelNode node]
                          ;; TODO: This is somewhat hacky indeed.
                          ;; We basically do this because of the call to ADD-ON-NON-VISIBLE-FN in VIEW-OF-NODE-IN-CONTEXT.
                          ;; Hm, but why should :VIEW-FROM-NODE-FN be called (from OBSERVED-EVENT-HANDLER-FN) at all?
                          (make-HTMLElement ::ContainerModelNodeProxiedView
                                            (cmn-data node)
                                            (fn [^WidgetBase view] (str "<div id='" (.id view) "'></div>"))
                                            (fn [^WidgetBase view ^ValueModel model old-value new-value]
                                              (dbg-prin1 new-value))
                                            :-proxied-node node
                                            :-node (make-ContainerModelNode (cmn-data node))))

                          :view-of-node (ref {})
                          :filter-node-fn filter-node-fn
                          args)]
    ;; TODO: Add any existing nodes to OBSERVER. ..but uh, a problem with this is that OBSERVER has already started
    ;; observing, so make sure we do this before OBSERVER is constructed above.
    synced-container-model))

















#_(defn sync-ContainerModel [container-model filter-node-fn & attributes]
  "Returns a ContainerModel that is synced with CONTAINER-MODEL via FILTER-NODE-FN.
To state this differently; operations done vs CONTAINER-MODEL are forwarded to the ContainerModel returned by this function."
  (let [synced-container-model (make-ContainerModel)
        ;; OBSERVER will forward (after filtering) events from CONTAINER-MODEL to SYNCED-CONTAINER-MODEL.
        observer (apply make-ContainerView "%ContainerModelProxyDummyView" container-model
                        :view-from-node-fn
                        (fn [container-view node]
                          ;; TODO: This is somewhat hacky indeed.
                          ;; We basically do this because of the call to ADD-ON-NON-VISIBLE-FN in VIEW-OF-NODE-IN-CONTEXT.
                          (make-HTMLElement "%ContainerModelProxyNodeDummyView" (cmn-data node)
                                            :-proxied-node node
                                            :-node (make-ContainerModelNode (cmn-data node))))

                        :handle-model-event-fn
                        (fn [container-view operation-args]
                          (handle-container-observer-event synced-container-model container-view operation-args))

                        :connect-model-view-fn
                        (fn [container-model container-view]
                          ;; Add any already existing nodes to SYNCED-CONTAINER-MODEL.
                          (loop [node (cm-head-node container-model)]
                            (when node
                              (when ((:filter-node-fn @container-view) synced-container-model node)
                                (cm-append synced-container-model
                                           (:-node @(view-of-node-in-context container-view node false))))
                              (recur (cmn-right-node node))))
                          (add-view container-model container-view))

                        :disconnect-model-view-fn
                        (fn [container-view]
                          (println "TODO: sync-ContainerModel, :disconnect-model-view-fn")
                          (remove-view container-model container-view))

                        :filter-node-fn
                        filter-node-fn

                        attributes)]
    ;; TODO: The connection has an infinite lifetime. See make-View in widget_base.clj for how more View based stuff deal with this.
    ;;((:connect-model-view-fn @observer) container-model observer)
    (observe-start observer)
    synced-container-model))
