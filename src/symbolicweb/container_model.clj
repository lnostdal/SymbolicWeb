(in-ns 'symbolicweb.core)

;; TODO: Add an async version of this with a Ref only at the "top" or "head" (I think).


;; Doubly linked list node.
(deftype ContainerModelNode [container-model left right data])

;; Doubly linked list.
(deftype ContainerModel [head-node
                         tail-node
                         length
                         ^:unsynchronized-mutable views-ref
                         ^:unsynchronized-mutable %notify-views-fn]
  clojure.lang.Counted
  (count [_]
    (dosync (ensure length)))

  IModel
  (add-view [_ view]
    (if (get (ensure views-ref) view)
      false
      (do
        (alter views-ref conj view)
        true)))

  (remove-view [_ view]
    (if (get (ensure views-ref) view)
      (do
        (alter views-ref disj view)
        true)
      false))

  (get-views [_]
    (ensure views-ref))

  (notify-views [cm args]
    (apply %notify-views-fn cm args)))



(defn make-ContainerModel []
  (ContainerModel. (ref nil)
                   (ref nil)
                   (ref 0)
                   (ref #{})
                   (fn [cm event-sym & event-args]
                     (doseq [container-view (get-views cm)]
                       ((:handle-model-event-fn @container-view) container-view (apply list event-sym event-args))))))


(defn cm []
  (make-ContainerModel))


(defn cm-tail-node [^ContainerModel cm]
  (ensure (. cm tail-node)))

(defn cm-set-tail-node [^ContainerModel cm new-tail-node]
  {:pre [(or (= ContainerModelNode (type new-tail-node))
             (not new-tail-node))]}
  (ref-set (. cm tail-node)
           new-tail-node))


(defn cm-head-node [^ContainerModel cm]
  (ensure (. cm head-node)))

(defn cm-set-head-node [^ContainerModel cm new-head-node]
  {:pre [(or (= ContainerModelNode (type new-head-node))
             (not new-head-node))]}
  (ref-set (. cm head-node)
           new-head-node))


(declare cm-prepend cmn-after)
(defn cm-append [^ContainerModel cm new-node]
  "Add NEW-NODE to end of the contained nodes in CM.
This mirrors the jQuery `append' function:
  http://api.jquery.com/append/"
  ;; http://en.wikipedia.org/wiki/Doubly-linked_list#Inserting_a_node
  ;;
  ;; function insertEnd(List list, Node newNode)
  (if (not (cm-tail-node cm)) ;; if list.lastNode == null
    (cm-prepend cm new-node) ;; insertBeginning(list, newNode)
  ;; else
    (cmn-after (cm-tail-node cm) new-node))) ;; insertAfter(list, list.lastNode, newNode)


(declare cmn-before cmn-set-left-node cmn-set-right-node container-model set-container-model)
(defn cm-prepend [^ContainerModel cm new-node]
  "Add NEW-NODE to beginning of the contained nodes in CM.
This mirrors the jQuery `prepend' function:
  http://api.jquery.com/prepend/"
  ;; http://en.wikipedia.org/wiki/Doubly-linked_list#Inserting_a_node
  ;;
  ;; function insertBeginning(List list, Node newNode)
  ;;   if list.firstNode == null
  (if (not (cm-head-node cm))
    (do
      ;; These 3 lines are specific to us.
      ;; Make sure NEW-NODE isn't used anywhere else before associating a ContainerModel with it.
      (assert (not (container-model new-node)))
      (set-container-model new-node cm)
      (alter (. cm length) inc)

      (cm-set-head-node cm new-node) ;; list.firstNode := newNode
      (cm-set-tail-node cm new-node) ;; list.lastNode  := newNode
      (cmn-set-left-node new-node nil) ;; newNode.prev := null
      (cmn-set-right-node new-node nil) ;; newNode.next := null

      (notify-views cm ['cm-prepend cm new-node]))
    ;; else
    (cmn-before (cm-head-node cm) new-node))) ;; insertBefore(list, list.firstNode, newNode)


(declare cmn-remove)
(defn cm-clear [^ContainerModel cm]
  ;; Remove head node of CM until trying to access the head node of CM returns NIL.
  (loop [node (cm-head-node cm)]
    (when node
      (cmn-remove node)
      (recur (cm-head-node cm)))))
