(in-ns 'symbolicweb.core)


(derive ::ContainerModelNode ::Model)
(defn make-ContainerModelNode [data]
  "Doubly linked list node."
  {:type ::ContainerModelNode
   :container-model (ref nil)
   :left (ref left)
   :right (ref right)
   :data data})


(defn left-node [container-model-node]
  @(:left container-model-node))

(defn set-left-node [container-model-node new-left-node]
  (assert (= ::ContainerModelNode (:type container-model-node)))
  (ref-set (:left container-model-node) new-left-node))


(defn right-node [container-model-node]
  (assert (= ::ContainerModelNode (:type container-model-node)))
  @(:right container-model-node))

(defn set-right-node [container-model-node new-right-node]
  (assert (= ::ContainerModelNode (:type container-model-node)))
  (ref-set (:right container-model-node) new-right-node))


(defn container-model [container-model-node]
  (assert (= ::ContainerModelNode (:type container-model-node)))
  @(:container-model container-model-node))

(defn set-container-model [container-model-node new-container-model]
  (assert (= ::ContainerModelNode (:type container-model-node)))
  (assert (= ::ContainerModel (:type new-container-model)))
  (ref-set (:container-model container-model-node) new-container-model))


(defn remove-container-model-node [node]
  "Pretty much does what you'd expect.
This mirrors the jQuery `remove' function:
  http://api.jquery.com/remove/"
  (assert (= ::ContainerModelNode (:type node)))
  (let [container-model @(:container-model node)]
    (alter (:length @(:container-model node)) dec)

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
  (assert (= ::ContainerModelNode (:type new-node)))
  (assert (= ::ContainerModelNode (:type existing-node)))
  (let [container-model @(:container-model existing-node)]
    ;; Make sure NEW-NODE isn't used anywhere else before associating a ContainerModel with it.
    (assert (not @(:container-model new-node)))
    (ref-set (:container-model new-node) container-model)
    (alter (:length @(:container-model new-node)) inc)

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
  (assert (= ::ContainerModelNode (:type new-node)))
  (assert (= ::ContainerModelNode (:type existing-node)))
  (let [container-model @(:container-model existing-node)]
    ;; Make sure NEW-NODE isn't used anywhere else before associating a ContainerModel with it.
    (assert (not @(:container-model new-node)))
    (ref-set (:container-model new-node) container-model)
    (alter (:length @(:container-model new-node)) inc)

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
