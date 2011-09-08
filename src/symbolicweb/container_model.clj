(in-ns 'symbolicweb.core)


(defn make-ContainerModel []
  {:type ::ContainerModel
   :event-router (ref [])
   :head-node (ref nil) ;; TODO: Code to keep this in sync with changes.
   :tail-node (ref nil) ;; TODO: Code to keep this in sync with changes.
   :views (ref [])})


(defn append-container-model [container-model new-node]
  "Add NEW-NODE to end of the contained nodes in CONTAINER-MODEL.
This mirrors the jQuery `append' function:
  http://api.jquery.com/append/"
  (let [tail-node (:tail-node container-model)]
    (if @tail-node
      (do
        (after-container-model-node @tail-node new-node)
        (ref-set tail-node new-node))
      (let [head-node (:head-node container-model)]
        (assert (not @head-node)) ;; TODO: Remove or make compile-time optional later.
        (assert (not @(:container-model new-node)))
        (ref-set (:container-model new-node) container-model)
        (ref-set head-node new-node)
        (ref-set tail-node new-node)
        (alter (:event-router container-model) conj ['append-container-model container-model new-node])))))


(defn prepend-container-model [container-model new-node]
  "Add NEW-NODE to beginning of the contained nodes in CONTAINER-MODEL.
This mirrors the jQuery `prepend' function:
  http://api.jquery.com/prepend/"
  (let [head-node (:head-node container-model)]
    (if @head-node
      (do
        (before-container-model-node @head-node new-node)
        (ref-set head-node new-node))
      (let [tail-node (:tail-node container-model)]
        (assert (not @tail-node)) ;; TODO: Remove or make compile-time optional-later.
        (assert (not @(:container-model new-node)))
        (ref-set (:container-model new-node) container-model)
        (ref-set head-node new-node)
        (ref-set tail-node new-node)
        (alter (:event-router container-model) conj ['prepend-container-model container-model new-node])))))


(defn clear-container-model [container-model]
  ;; Remove head node of CONTAINER-MODEL until trying to access the head node of CONTAINER-MODEL returns NIL.
  (loop [node (head-node container-model)]
    (when node
      (remove-container-model-node node)
      (recur (head-node container-model)))))
