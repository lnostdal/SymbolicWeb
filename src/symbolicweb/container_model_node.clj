(in-ns 'symbolicweb.core)


;; TODO: Generalize this so we can recycle it; it's Doubly Linked List structure.
(defrecord ContainerModelNode [left right data])


(defn make-ContainerModelNode [left right data]
  (ContainerModelNode. (ref left) (ref right) data))


(defn remove-container-model-node [node]
  (let [left-node (:left node)
        right-node (:right node)]
    (cond
     (and left-node right-node)
     (do
       (ref-set (:left right-node) left-node)
       (ref-set (:right left-node) right-node))

     left-node
     (ref-set (:right left-node) nil)

     right-node
     (ref-set (:left right-node) nil))))


(defn append-container-model-node [existing-node new-node]
  "Append NEW-NODE to right side of EXISTING-NODE."
  (let [left-node (:left existing-node)
        right-node (:right existing-node)]
    (cond
     (and left-node right-node)
     (do
       (ref-set (:right existing-node) new-node)
       (ref-set (:left new-node) existing-node)
       (ref-set (:right new-node) right-node)
       (ref-set (:left right-node) new-node))

     left-node
     (do
       (ref-set (:right existing-node) new-node)
       (ref-set (:left new-node) existing-node))

     right-node
     (do
       (ref-set (:right existing-node) new-node)
       (ref-set (:left new-node) existing-node)
       (ref-set (:right new-node) right-node)
       (ref-set (:left right-node) new-node)))))


(defn prepend-container-model-node [existing-node new-node]
  "Prepend NEW-NODE to left side of EXISTING-NODE."
  (let [left-node (:left existing-node)
        right-node (:right existing-node)]
    (cond
     (and left-node right-node)
     (do
       (ref-set (:right left-node) new-node)
       (ref-set (:left new-node) left-node)
       (ref-set (:right new-node) existing-node)
       (ref-set (:left existing-node) new-node))

     left-node
     (do
       (ref-set (:right left-node) new-node)
       (ref-set (:left new-node) left-node)
       (ref-set (:right new-node) existing-node)
       (ref-set (:left existing-node) new-node))

     right-node
     (do
       (ref-set (:right new-node) existing-node)
       (ref-set (:left existing-node) new-node)))))
