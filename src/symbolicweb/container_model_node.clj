(in-ns 'symbolicweb.core)


;; NOTE: ContainerModelNode class (deftype) moved to container_model.clj since forward declaring this doesn't seem to work.
;; (deftype ContainerModelNode [container-model left right data])


(defn make-ContainerModelNode [data]
  "Doubly linked list node."
  (ContainerModelNode. (ref nil) (ref nil) (ref nil) data))


(defn cmn [data]
  (make-ContainerModelNode data))


(defn cmn-left-node [^ContainerModelNode node]
  (ensure (. node left)))

(defn cmn-set-left-node [^ContainerModelNode node ^ContainerModelNode new-left-node]
  {:pre [(or (= ContainerModelNode (type new-left-node))
             (not new-left-node))]}
  (ref-set (. node left)
           new-left-node))


(defn cmn-right-node [node]
  (ensure (. node right)))

(defn cmn-set-right-node [^ContainerModelNode node ^ContainerModelNode new-right-node]
  {:pre [(or (= ContainerModelNode (type new-right-node))
             (not new-right-node))]}
  (ref-set (. node right)
           new-right-node))


(defn container-model [^ContainerModelNode node]
  (ensure (. node container-model)))

(defn set-container-model [^ContainerModelNode node ^ContainerModel new-container-model]
  {:pre [(= ContainerModel (type new-container-model))]}
  (ref-set (. node container-model) new-container-model))


(defn node-data [^ContainerModelNode node]
  {:pre [(= ContainerModelNode (type node))]}
  (. node data))


(defn cmn-remove [^ContainerModelNode node]
  "Pretty much does what you'd expect.
This mirrors the jQuery `remove' function:
  http://api.jquery.com/remove/"
  (let [cm (ensure (. node container-model))]
    (alter (. cm length) dec)

    ;; http://en.wikipedia.org/wiki/Doubly-linked_list#Removing_a_node
    ;;
    ;;   if node.prev == null
    ;;       list.firstNode := node.next
    ;;   else
    ;;       node.prev.next := node.next
    (if (not (cmn-left-node node))
      (cm-set-head-node cm (cmn-right-node node))
      (cmn-set-right-node (cmn-left-node node) (cmn-right-node node)))

    ;;   if node.next == null
    ;;       list.lastNode := node.prev
    ;;   else
    ;;       node.next.prev := node.prev
    (if (not (cmn-right-node node))
      (cm-set-tail-node cm (cmn-left-node node))
      (cmn-set-left-node (cmn-right-node node) (cmn-left-node node)))

    (notify-views cm ['cmn-remove node])))


(defn cmn-after [^ContainerModelNode existing-node ^ContainerModelNode new-node]
  "Add NEW-NODE to right side of EXISTING-NODE.
This mirrors the jQuery `after' function:
  http://api.jquery.com/after/"
  {:pre [(and (= ContainerModelNode (type new-node))
              (= ContainerModelNode (type existing-node)))]}
  (let [container-model (ensure (. existing-node container-model))]
    ;; Make sure NEW-NODE isn't used anywhere else before associating a ContainerModel with it.
    (assert (not (ensure (. new-node container-model))))
    (ref-set (. new-node container-model) container-model)
    (alter (. (ensure (. new-node container-model))
              length)
           inc)

    ;; http://en.wikipedia.org/wiki/Doubly-linked_list#Inserting_a_node
    ;;
    ;; function insertAfter(List list, Node node, Node newNode)
    ;;  newNode.prev := node
    (cmn-set-left-node new-node existing-node)
    ;;  newNode.next := node.next
    (cmn-set-right-node new-node (cmn-right-node existing-node))

    ;;  if node.next == null
    ;;    list.lastNode := newNode
    ;;  else
    ;;    node.next.prev := newNode
    (if (not (cmn-right-node existing-node))
      (cm-set-tail-node container-model new-node)
      (cmn-set-left-node (cmn-right-node existing-node) new-node))

    ;;  node.next := newNode
    (cmn-set-right-node existing-node new-node)

    (notify-views container-model ['cmn-after existing-node new-node])))


(defn cmn-before [^ContainerModelNode existing-node ^ContainerModelNode new-node]
  "Add NEW-NODE to left side of EXISTING-NODE.
This mirrors the jQuery `before' function:
  http://api.jquery.com/before/"
  {:pre [(and (= ContainerModelNode (type new-node))
              (= ContainerModelNode (type existing-node)))]}
  (let [container-model (ensure (. existing-node container-model))]
    ;; Make sure NEW-NODE isn't used anywhere else before associating a ContainerModel with it.
    (assert (not (ensure (. new-node container-model))))
    (ref-set (. new-node container-model) container-model)
    (alter (. (ensure (. new-node container-model))
              length)
           inc)

    ;; http://en.wikipedia.org/wiki/Doubly-linked_list#Inserting_a_node
    ;;
    ;; function insertBefore(List list, Node node, Node newNode)
    ;; newNode.prev := node.prev
    (cmn-set-left-node new-node (cmn-left-node existing-node))

    ;; newNode.next := node
    (cmn-set-right-node new-node existing-node)

    ;; if node.prev == null
    ;;   list.firstNode := newNode
    ;; else
    ;;   node.prev.next := newNode
    (if (not (cmn-left-node existing-node))
      (cm-set-head-node container-model new-node)
      (cmn-set-right-node (cmn-left-node existing-node) new-node))

    ;; node.prev := newNode
    (cmn-set-left-node existing-node new-node)

    (notify-views container-model ['cmn-before existing-node new-node])))
