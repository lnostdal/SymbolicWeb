(in-ns 'symbolicweb.core)


;; NOTE: Moved to container_model.clj since forward declaring this doesn't seem to work.
;; (deftype ContainerModelNode [container-model left right data])


(defn make-ContainerModelNode [data]
  "Doubly linked list node."
  (ContainerModelNode. (ref nil) (ref nil) (ref nil) data))


(defn left-node [container-model-node]
  (ensure (. container-model-node left)))

(defn set-left-node [container-model-node new-left-node]
  {:pre [(or (= ContainerModelNode (type new-left-node))
             (not new-left-node))]}
  (ref-set (. container-model-node left)
           new-left-node))


(defn right-node [container-model-node]
  (ensure (. container-model-node right)))

(defn set-right-node [container-model-node new-right-node]
  {:pre [(or (= ContainerModelNode (type new-right-node))
             (not new-right-node))]}
  (ref-set (. container-model-node right)
           new-right-node))


(defn container-model [container-model-node]
  (ensure (. container-model-node container-model)))

(defn set-container-model [container-model-node new-container-model]
  {:pre [(= ContainerModel (type new-container-model))]}
  (ref-set (. container-model-node container-model) new-container-model))


(defn node-data [container-model-node]
  {:pre [(= ContainerModelNode (type container-model-node))]}
  (. container-model-node data))


(defn remove-container-model-node [node]
  "Pretty much does what you'd expect.
This mirrors the jQuery `remove' function:
  http://api.jquery.com/remove/"
  (let [container-model (ensure (. node container-model))]
    (alter (. container-model length) dec)

    ;; http://en.wikipedia.org/wiki/Doubly-linked_list#Removing_a_node
    ;;
    ;;   if node.prev == null
    ;;       list.firstNode := node.next
    ;;   else
    ;;       node.prev.next := node.next
    (if (not (left-node node))
      (set-head-node container-model (right-node node))
      (set-right-node (left-node node) (right-node node)))

    ;;   if node.next == null
    ;;       list.lastNode := node.prev
    ;;   else
    ;;       node.next.prev := node.prev
    (if (not (right-node node))
      (set-tail-node container-model (left-node node))
      (set-left-node (right-node node) (left-node node)))

    (notify-views container-model 'remove-container-model-node node)))


(defn after-container-model-node [existing-node new-node]
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
    (set-left-node new-node existing-node)
    ;;  newNode.next := node.next
    (set-right-node new-node (right-node existing-node))

    ;;  if node.next == null
    ;;    list.lastNode := newNode
    ;;  else
    ;;    node.next.prev := newNode
    (if (not (right-node existing-node))
      (set-tail-node container-model new-node)
      (set-left-node (right-node existing-node) new-node))

    ;;  node.next := newNode
    (set-right-node existing-node new-node)

    (notify-views container-model 'after-container-model-node existing-node new-node)))


(defn before-container-model-node [existing-node new-node]
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
    (set-left-node new-node (left-node existing-node))

    ;; newNode.next := node
    (set-right-node new-node existing-node)

    ;; if node.prev == null
    ;;   list.firstNode := newNode
    ;; else
    ;;   node.prev.next := newNode
    (if (not (left-node existing-node))
      (set-head-node container-model new-node)
      (set-right-node (left-node existing-node) new-node))

    ;; node.prev := newNode
    (set-left-node existing-node new-node)

    (notify-views container-model 'before-container-model-node existing-node new-node)))
