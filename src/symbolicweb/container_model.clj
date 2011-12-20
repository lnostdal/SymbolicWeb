(in-ns 'symbolicweb.core)


;; Doubly linked list node.
(deftype ContainerModelNode [container-model left right data])

;; Doubly linked list.
(deftype ContainerModel [head-node tail-node length views]
  clojure.lang.Counted
  (count [_]
    (ensure length))

  IModel
  (add-view [_ view]
    (alter views conj view))

  (remove-view [_ view]
    (alter views disj view)))


(defn make-ContainerModel []
  (ContainerModel. (ref nil) (ref nil) (ref 0) (ref #{})))



(defn tail-node [container-model]
  (ensure (. container-model tail-node)))

(defn set-tail-node [container-model new-tail-node]
  {:pre [(or (= ContainerModelNode (type new-tail-node))
             (not new-tail-node))]}
  (ref-set (. container-model tail-node)
           new-tail-node))


(defn head-node [container-model]
  (ensure (. container-model head-node)))

(defn set-head-node [container-model new-head-node]
  {:pre [(or (= ContainerModelNode (type new-head-node))
             (not new-head-node))]}
  (ref-set (. container-model head-node)
           new-head-node))


(defn notify-views [container-model event-sym & event-args]
  (doseq [container-view (ensure (. container-model views))]
    ((:handle-model-event-fn (ensure container-view)) container-view (apply list event-sym event-args))))


(declare prepend-container-model after-container-model-node)
(defn append-container-model [container-model new-node]
  "Add NEW-NODE to end of the contained nodes in CONTAINER-MODEL.
This mirrors the jQuery `append' function:
  http://api.jquery.com/append/"
  ;; http://en.wikipedia.org/wiki/Doubly-linked_list#Inserting_a_node
  ;;
  ;; function insertEnd(List list, Node newNode)
  (if (not (tail-node container-model)) ;; if list.lastNode == null
    (prepend-container-model container-model new-node) ;; insertBeginning(list, newNode)
  ;; else
    (after-container-model-node (tail-node container-model) new-node))) ;; insertAfter(list, list.lastNode, newNode)


(declare before-container-model-node set-left-node set-right-node container-model set-container-model)
(defn prepend-container-model [container-m new-node]
  "Add NEW-NODE to beginning of the contained nodes in CONTAINER-MODEL.
This mirrors the jQuery `prepend' function:
  http://api.jquery.com/prepend/"
  ;; http://en.wikipedia.org/wiki/Doubly-linked_list#Inserting_a_node
  ;;
  ;; function insertBeginning(List list, Node newNode)
  ;;   if list.firstNode == null
  (if (not (head-node container-m))
    (do
      ;; These 3 lines are specific to us.
      ;; Make sure NEW-NODE isn't used anywhere else before associating a ContainerModel with it.
      (assert (not (container-model new-node)))
      (set-container-model new-node container-m)
      (alter (. container-m length) inc)

      (set-head-node container-m new-node) ;; list.firstNode := newNode
      (set-tail-node container-m new-node) ;; list.lastNode  := newNode
      (set-left-node new-node nil) ;; newNode.prev := null
      (set-right-node new-node nil) ;; newNode.next := null
      (notify-views container-m 'prepend-container-model container-model new-node))
    ;; else
    (before-container-model-node (head-node container-m) new-node))) ;; insertBefore(list, list.firstNode, newNode)


(declare remove-container-model-node)
(defn clear-container-model [container-model]
  ;; Remove head node of CONTAINER-MODEL until trying to access the head node of CONTAINER-MODEL returns NIL.
  (loop [node (head-node container-model)]
    (when node
      (remove-container-model-node node)
      (recur (head-node container-model)))))
