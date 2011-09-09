(in-ns 'symbolicweb.core)


;; TODO: Stuff like :LENGTH sucks. I should be able to use the Clojure standard function COUNT with ContainerModel as an argument.
;; I guess I need to take a closer look at Clojure protocols, defrecord and deftype etc. when I have time. This might also make
;; type-checking easier.

(defn make-ContainerModel []
  {:type ::ContainerModel
   :event-router (ref []) ;; This is used to forward operations to one or more ContainerView instances.
   :head-node (ref nil)
   :tail-node (ref nil)
   :length (ref 0)
   :views (ref [])})


(defn tail-node [container-model]
  (assert (= ::ContainerModel (:type container-model)))
  @(:tail-node container-model))

(defn set-tail-node [container-model new-tail-node]
  (assert (= ::ContainerModel (:type container-model)))
  (ref-set (:tail-node container-model) new-tail-node))


(defn head-node [container-model]
  (assert (= ::ContainerModel (:type container-model)))
  @(:head-node container-model))

(defn set-head-node [container-model new-head-node]
  (assert (= ::ContainerModel (:type container-model)))
  (ref-set (:head-node container-model) new-head-node))


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


(declare before-container-model-node set-left-node set-right-node)
(defn prepend-container-model [container-model new-node]
  "Add NEW-NODE to beginning of the contained nodes in CONTAINER-MODEL.
This mirrors the jQuery `prepend' function:
  http://api.jquery.com/prepend/"
  ;; http://en.wikipedia.org/wiki/Doubly-linked_list#Inserting_a_node
  ;;
  ;; function insertBeginning(List list, Node newNode)
  ;;   if list.firstNode == null
  (if (not (head-node container-model))
    (do
      ;; These 3 lines are specific to us.
      (assert (not @(:container-model new-node)))
      (ref-set (:container-model new-node) container-model)
      (alter (:length container-model) inc)

      (set-head-node container-model new-node) ;; list.firstNode := newNode
      (set-tail-node container-model new-node) ;; list.lastNode  := newNode
      (set-left-node new-node nil) ;; newNode.prev := null
      (set-right-node new-node nil) ;; newNode.next := null
      (alter (:event-router container-model) conj ['prepend-container-model container-model new-node]))
    ;; else
    (before-container-model-node (head-node container-model) new-node))) ;; insertBefore(list, list.firstNode, newNode)


(declare remove-container-model-node)
(defn clear-container-model [container-model]
  ;; Remove head node of CONTAINER-MODEL until trying to access the head node of CONTAINER-MODEL returns NIL.
  (loop [node (head-node container-model)]
    (when node
      (remove-container-model-node node)
      (recur (head-node container-model)))))
