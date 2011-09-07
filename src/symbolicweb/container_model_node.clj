(in-ns 'symbolicweb.core)


(defn make-ContainerModelNode [left right data]
  "Doubly linked list node."
  {:type ::ContainerModelNode
   :container-model (ref nil)
   :left (ref left)
   :right (ref right)
   :data data
   :views (ref [])})


(defn remove-container-model-node [node]
  (let [left-node @(:left node)
        right-node @(:right node)
        container-model @(:container-model node)
        event-router (:event-router container-model)]
    (cond
     (and left-node right-node)
     (do
       (ref-set (:left right-node) left-node)
       (ref-set (:right left-node) right-node))

     left-node
     (ref-set (:right left-node) nil)

     right-node
     (ref-set (:left right-node) nil)

     true
     (println "REMOVE-CONTAINER-MODEL-NODE: TODO"))
    (alter event-router conj ['remove-container-model-node node])))


(defn after-container-model-node [existing-node new-node]
  "Add NEW-NODE to right side of EXISTING-NODE.
This mirrors the jQuery `after' function:
  http://api.jquery.com/after/"
  (let [left-node @(:left existing-node)
        right-node @(:right existing-node)
        container-model @(:container-model existing-node)
        event-router (:event-router container-model)]
    ;; Make sure NEW-NODE isn't used anywhere else before associating a ContainerModel with it.
    (assert (not @(:container-model new-node)))
    (ref-set (:container-model new-node) container-model)
    ;; Adding a node behind the tail.
    (when (= existing-node (:tail container-model))
      (ref-set (:tail container-model) new-node))

    (cond
     right-node
     (do
       (ref-set (:right existing-node) new-node)
       (ref-set (:left new-node) existing-node)
       (ref-set (:right new-node) right-node)
       (ref-set (:left right-node) new-node))

     true
     (do
       (ref-set (:left new-node) existing-node)
       (ref-set (:right existing-node) new-node)))

    (alter event-router conj ['after-container-model-node existing-node new-node])))


(defn before-container-model-node [existing-node new-node]
  "Add NEW-NODE to left side of EXISTING-NODE.
This mirrors the jQuery `before' function:
  http://api.jquery.com/before/"
  (let [left-node @(:left existing-node)
        right-node @(:right existing-node)
        container-model @(:container-model existing-node)
        event-router @(:event-router container-model)]
    ;; Make sure NEW-NODE isn't used anywhere else before associating a ContainerModel with it.
    (assert (not @(:container-model new-node)))
    (ref-set (:container-model new-node) container-model)
    ;; Adding a node in front of the head?
    (when (= existing-node (:head container-model))
      (ref-set (:head container-model) new-node))

    (cond
     left-node
     (do
       (ref-set (:right left-node) new-node)
       (ref-set (:left new-node) left-node)
       (ref-set (:right new-node) existing-node)
       (ref-set (:left existing-node) new-node))

     true
     (do
       (ref-set (:left existing-node) new-node)
       (ref-set (:right new-node) existing-node)))

    (alter event-router conj ['before-container-model-node existing-node new-node])))


;; jqAppend and jqPrepend are mirrored in the code found in container_model.clj via append-container-model-node
;; and prepend-container-model-node respectively.