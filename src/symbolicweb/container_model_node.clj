(in-ns 'symbolicweb.core)


;; NOTE: ContainerModelNode class (deftype) moved to container_model.clj since forward declaring this doesn't seem to work.
;; (deftype ContainerModelNode [container-model left right data])


(defn ^ContainerModelNode make-ContainerModelNode [data]
  "Doubly linked list node."
  (ContainerModelNode. (ref nil) (ref nil) (ref nil) data))


(defn ^ContainerModelNode cmn [data]
  (make-ContainerModelNode data))


(defn cmn-remove [^ContainerModelNode node]
  "Pretty much does what you'd expect.
This mirrors the jQuery `remove' function:
  http://api.jquery.com/remove/"
  (let [^ContainerModel cm (cmn-container-model node)]
    (cm-set-count cm (dec (count cm)))

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

    (notify-observers (.observable cm) 'cmn-remove node)))


(defn cmn-after [^ContainerModelNode existing-node ^ContainerModelNode new-node]
  "Add NEW-NODE to right side of EXISTING-NODE.
This mirrors the jQuery `after' function:
  http://api.jquery.com/after/"
  (let [^ContainerModel cm (cmn-container-model existing-node)]
    ;; Make sure NEW-NODE isn't used anywhere else before associating a ContainerModel with it.
    (assert (not (cmn-container-model new-node)))
    (cmn-set-container-model new-node cm)
    (cm-set-count cm (inc (count cm)))

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
      (cm-set-tail-node cm new-node)
      (cmn-set-left-node (cmn-right-node existing-node) new-node))

    ;;  node.next := newNode
    (cmn-set-right-node existing-node new-node)

    (notify-observers (.observable cm) 'cmn-after existing-node new-node)))


(defn cmn-before [^ContainerModelNode existing-node ^ContainerModelNode new-node]
  "Add NEW-NODE to left side of EXISTING-NODE.
This mirrors the jQuery `before' function:
  http://api.jquery.com/before/"
  (let [^ContainerModel cm (cmn-container-model existing-node)]
    ;; Make sure NEW-NODE isn't used anywhere else before associating a ContainerModel with it.
    (assert (not (cmn-container-model new-node)))
    (cmn-set-container-model new-node cm)
    (cm-set-count cm (inc (count cm)))

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
      (cm-set-head-node cm new-node)
      (cmn-set-right-node (cmn-left-node existing-node) new-node))

    ;; node.prev := newNode
    (cmn-set-left-node existing-node new-node)

    (notify-observers (.observable cm) 'cmn-before existing-node new-node)))
