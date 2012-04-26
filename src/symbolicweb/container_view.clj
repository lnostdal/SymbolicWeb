(in-ns 'symbolicweb.core)


(defn view-of-node-in-context
  "Find or create (if not found) new View of NODE in context of CONTAINER-VIEW.
If FIND-ONLY? is true no new View will be constructed if an existing one was not found."
  ([container-view node] (view-of-node-in-context container-view node false))
  ([container-view node find-only?]
     (if-let [existing-view (get @(:view-of-node @container-view) node)]
       existing-view
       (when-not find-only?
         (let [new-view ((:view-from-node-fn @container-view) container-view node)]
           (alter (:view-of-node @container-view) assoc node new-view)
           (alter new-view assoc :node-of-view node) ;; View --> Node
           ;; TODO: This is too specific; "visibility" shouldn't be mentioned here.
           (add-on-non-visible-fn new-view (fn [] (alter (:view-of-node @container-view) dissoc node)))
            new-view)))))


(defn node-of-view-in-context [container-model view]
  (loop [node (cm-head-node container-model)]
    (let [widget (cmn-data node)]
      (when-let [next-node (cmn-right-node node)]
        (recur next-node)))))


(defn handle-container-view-event [container-view event-args]
  "Forward Container related operations/events to the View end."
  (let [[event-sym & event-args] event-args]
    (case event-sym
      cm-prepend
      (let [container-model (nth event-args 0)
            new-node (nth event-args 1)]
        (jqPrepend container-view
          (view-of-node-in-context container-view new-node)))

      cmn-after
      (let [existing-node (nth event-args 0)
            new-node (nth event-args 1)]
        (jqAfter (view-of-node-in-context container-view existing-node true)
          (view-of-node-in-context container-view new-node)))

      cmn-before
      (let [existing-node (nth event-args 0)
            new-node (nth event-args 1)]
        (jqBefore (view-of-node-in-context container-view existing-node true)
          (view-of-node-in-context container-view new-node)))

      cmn-remove
      (let [node (nth event-args 0)]
        (if-let [view (view-of-node-in-context container-view node true)]
          (jqRemove view)
          (println "ContainerView: Tried to remove Node, but no existing View of that Node was found."))))))



(derive ::ContainerView ::HTMLElement)
(defn make-ContainerView [html-element-type container-model & attributes]
  (apply make-HTMLElement html-element-type container-model
         :type ::ContainerView

         :html-element-type html-element-type

         :handle-model-event-fn
         (fn [container-view operation-args]
           (handle-container-view-event container-view operation-args))

         ;; TODO: I think I can get rid of this and use ADD-ON-VISIBLE instead.
         :connect-model-view-fn
         (fn [container-model container-view]
           (when (add-view container-model container-view)
             ;; Clear out stuff; e.g. "dummy content" from templating.
             ;; TODO: Not sure why I've commented this out, or why it was needed in the first place.
             #_(add-response-chunk (with-js (jqEmpty container-view))
                                   container-view)
             ;; Add any already existing nodes to CONTAINER-VIEW.
             (loop [node (cm-head-node container-model)]
               (when node
                 (jqAppend container-view (view-of-node-in-context container-view node))
                 (recur (cmn-right-node node))))))

         :view-from-node-fn
         (fn [container-view node]
           (make-HTMLElement "li" (cmn-data node)))

         :view-of-node (ref {})
         attributes))




;;; sync-ContainerModel stuff follows
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn node-of-node-in-context
  "Used by sync-ContainerModel to find Node directly or closest (location in sequence of Nodes) represented by NODE."
  ([container-view node] (node-of-node-in-context container-view node false))
  ([container-view node find-only?]
     ;; Iterate from NODE starting position outwards; left and right, finding closest relative Node.
     (if-let [node_pos (if-let [view (view-of-node-in-context container-view node find-only?)]
                         [(:-node @view) :direct]
                         (loop [l-node (cmn-left-node node)
                                r-node (cmn-right-node node)]
                           (when (or l-node r-node)
                             (if-let [view (and l-node (view-of-node-in-context container-view l-node true))]
                               [(:-node @view) :left]
                               (if-let [view (and r-node (view-of-node-in-context container-view r-node true))]
                                 [(:-node @view) :right]
                                 (recur (when l-node (cmn-left-node l-node))
                                        (when r-node (cmn-right-node r-node))))))))]
       node_pos
       [nil :empty-container-proxy])))


(defn handle-container-observer-event [container-model-proxy observer event-args]
  (let [[event-sym & event-args] event-args]
    (case event-sym
      cm-prepend
      (let [container-model (nth event-args 0)
            new-node (nth event-args 1)]
        (when ((:filter-node-fn @observer) container-model-proxy new-node)
          (cm-prepend container-model-proxy
                      (first (node-of-node-in-context observer new-node false)))))

      cmn-after
      (let [existing-node (nth event-args 0)
            new-node (nth event-args 1)]
        (when ((:filter-node-fn @observer) container-model-proxy new-node)
          (let [[rel-existing-node rel-pos] (node-of-node-in-context observer existing-node true)
                rel-new-node (:-node @(view-of-node-in-context observer new-node false))]
            (case rel-pos
              (:direct :left)
              (cmn-after rel-existing-node rel-new-node)

              :right
              (cmn-before rel-existing-node rel-new-node)

              :empty-container-proxy
              (cm-prepend container-model-proxy rel-new-node)))))

      cmn-before
      (let [existing-node (nth event-args 0)
            new-node (nth event-args 1)]
        (when ((:filter-node-fn @observer) container-model-proxy new-node)
          (let [[rel-existing-node rel-pos] (node-of-node-in-context observer existing-node true)
                rel-new-node (:-node @(view-of-node-in-context observer new-node false))]
            (case rel-pos
              (:direct :right)
              (cmn-before rel-existing-node rel-new-node)

              :left
              (cmn-after rel-existing-node rel-new-node)

              :empty-container-proxy
              (cm-prepend container-model-proxy rel-new-node)))))

      cmn-remove
      (let [node (nth event-args 0)]
        (when-let [rel-existing-node (first (node-of-node-in-context observer node true))]
          (cmn-remove rel-existing-node))))))


(defn sync-ContainerModel [container-model filter-node-fn & attributes]
  "Returns a ContainerModel that is synced with CONTAINER-MODEL via an intermediate :FILTER-NODE-FN.
To state this differently; operations done vs CONTAINER-MODEL are forwarded to the ContainerModel returned by this function."
  (let [container-model-proxy (make-ContainerModel)
        ;; OBSERVER will forward (after filtering) events from CONTAINER-MODEL to CONTAINER-MODEL-PROXY.
        observer (apply make-ContainerView "%ContainerModelProxyDummyView" container-model
                        :view-from-node-fn
                        (fn [observer node]
                          ;; TODO: This is somewhat hacky indeed.
                          ;; We basically do this because of the call to ADD-ON-NON-VISIBLE-FN in VIEW-OF-NODE-IN-CONTEXT.
                          (make-HTMLElement "%ContainerModelProxyNodeDummyView" (cmn-data node)
                                            :-proxied-node node
                                            :-node (make-ContainerModelNode (cmn-data node))))

                        :handle-model-event-fn
                        (fn [observer operation-args]
                          (handle-container-observer-event container-model-proxy observer operation-args))

                        :connect-model-view-fn
                        (fn [container-model observer]
                          ;; Add any already existing nodes to CONTAINER-MODEL-PROXY.
                          (loop [node (cm-head-node container-model)]
                            (when node
                              (when ((:filter-node-fn @observer) container-model-proxy node)
                                (cm-append container-model-proxy
                                           (:-node @(view-of-node-in-context observer node false))))
                              (recur (cmn-right-node node))))
                          (add-view container-model observer))

                        :disconnect-model-view-fn
                        (fn [observer]
                          (println "TODO: sync-ContainerModel, :disconnect-model-view-fn")
                          (remove-view container-model observer))

                        :filter-node-fn
                        filter-node-fn

                        attributes)]
    ;; TODO: The connection has an infinite lifetime. See make-View in widget_base.clj for how more View based stuff deal with this.
    ((:connect-model-view-fn @observer) container-model observer)
    container-model-proxy))
