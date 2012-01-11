(in-ns 'symbolicweb.core)


(defn view-of-node-in-context
  "Find or create (if not found) new View of NODE in context of CONTAINER-VIEW."
  ([container-view node] (view-of-node-in-context container-view node false))
  ([container-view node find-only?]
     (if-let [existing-view (get @(:view-of-node @container-view) node)]
       existing-view
       (when-not find-only?
         (let [new-view ((:view-from-node-fn @container-view) container-view node)]
           (alter (:view-of-node @container-view) assoc node new-view)
           ;; TODO: This is too specific; "visibility" shouldn't be mentioned here.
            (add-on-non-visible-fn new-view (fn [] (alter (:view-of-node @container-view) dissoc node)))
           new-view)))))


(defn handle-container-view-event [container-view event-args]
  "Forward Container related operations/events to the View end."
  (let [[event-sym & event-args] event-args]
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
        (if-let [view (view-of-node-in-context container-view node true)]
          (jqRemove view)
          (println "ContainerView: Tried to remove Node, but no existing View of that Node was found."))))))


(derive ::ContainerView ::HTMLElement)
(defn make-ContainerView [html-element-type container-model & attributes]
  (apply make-HTMLElement html-element-type container-model
         :type ::ContainerView

         :html-element-type html-element-type

         ;; TODO: This thing isn't actually implemented proper yet. E.g., see the TODO in container_model.clj.
         :filter-node-fn (fn [node] true)

         :handle-model-event-fn
         (fn [container-view operation-args]
           (handle-container-view-event container-view operation-args))

         :connect-model-view-fn
         (fn [container-model container-view]
           ;; Clear out stuff; e.g. "dummy content" from templating.
           ;; TODO: Since TM is using custom templating syntax for his PHP work, this doesn't seem to be needed any more. I.e.,
           ;; Soup will drop the custom (invalid) content.
           #_(add-response-chunk (with-js (jqEmpty container-view))
                                 container-view)
           ;; Add any already existing nodes to CONTAINER-VIEW.
           (loop [node (head-node container-model)]
             (when node
               (when ((:filter-node-fn @container-view) node)
                 (jqAppend container-view (view-of-node-in-context container-view node)))
               (recur (right-node node))))
           (add-view container-model container-view))

         :disconnect-model-view-fn
         (fn [container-view]
           (remove-view container-model container-view))

         :view-from-node-fn
         (fn [container-view node]
           (make-HTMLElement "li" (node-data node)))

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
                         (loop [l-node (left-node node)
                                r-node (right-node node)]
                           (when (or l-node r-node)
                             (if-let [view (and l-node (view-of-node-in-context container-view l-node true))]
                               [(:-node @view) :left]
                               (if-let [view (and r-node (view-of-node-in-context container-view r-node true))]
                                 [(:-node @view) :right]
                                 (recur (when l-node (left-node l-node))
                                        (when r-node (right-node r-node))))))))]
       node_pos
       [nil :empty-container-proxy])))


(defn handle-container-observer-event [container-model-proxy observer event-args]
  (let [[event-sym & event-args] event-args]
    (case event-sym
      prepend-container-model
      (let [container-model (nth event-args 0)
            new-node (nth event-args 1)]
        (when ((:filter-node-fn @observer) new-node)
          (prepend-container-model container-model-proxy
                                   (first (node-of-node-in-context observer new-node false)))))

      after-container-model-node
      (let [existing-node (nth event-args 0)
            new-node (nth event-args 1)]
        (when ((:filter-node-fn @observer) new-node)
          (let [[rel-existing-node rel-pos] (node-of-node-in-context observer existing-node true)
                rel-new-node (:-node @(view-of-node-in-context observer new-node false))]
            (case rel-pos
              (:direct :left)
              (after-container-model-node rel-existing-node rel-new-node)

              :right
              (before-container-model-node rel-existing-node rel-new-node)

              :empty-container-proxy
              (prepend-container-model container-model-proxy rel-new-node)))))

      before-container-model-node
      (let [existing-node (nth event-args 0)
            new-node (nth event-args 1)]
        (when ((:filter-node-fn @observer) new-node)
          (let [[rel-existing-node rel-pos] (node-of-node-in-context observer existing-node true)
                rel-new-node (:-node @(view-of-node-in-context observer new-node false))]
            (case rel-pos
              (:direct :right)
              (before-container-model-node rel-existing-node rel-new-node)

              :left
              (after-container-model-node rel-existing-node rel-new-node)

              :empty-container-proxy
              (prepend-container-model container-model-proxy rel-new-node)))))

      remove-container-model-node
      (let [node (nth event-args 0)]
        (when ((:filter-node-fn @observer) node)
          (let [[rel-existing-node rel-pos] (node-of-node-in-context observer node true)]
            (if-not (= :empty-container-proxy)
              (remove-container-model-node rel-existing-node)
              (println "sync-ContainerModel: Tried to remove Node, but no existing View (proxied Node) of that Node was found."))))))))


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
                          (make-HTMLElement "%ContainerModelProxyNodeDummyView" (node-data node)
                                            :-proxied-node node
                                            :-node (make-ContainerModelNode (node-data node))))

                        :handle-model-event-fn
                        (fn [observer operation-args]
                          (handle-container-observer-event container-model-proxy observer operation-args))

                        :connect-model-view-fn
                        (fn [container-model observer]
                          ;; Add any already existing nodes to CONTAINER-MODEL-PROXY.
                          (loop [node (head-node container-model)]
                            (when node
                              (when ((:filter-node-fn @observer) node)
                                (append-container-model container-model-proxy
                                                        (:-node @(view-of-node-in-context observer node false))))
                              (recur (right-node node))))
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
