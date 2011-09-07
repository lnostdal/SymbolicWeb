(in-ns 'symbolicweb.core)


;; TODO: Does having a "root" make sense? Perhaps forcing the user to use jqAppend initially then continue with
;; jqAppend or jqAfter (vs. the ContainerModelNodes) is a good idea ..

(defn make-ContainerModel []
  {:type ::ContainerModel
   :event-router (ref [])
   :head-node (ref nil) ;; TODO: Code to keep this in sync with changes.
   :tail-node (ref nil) ;; TODO: Code to keep this in sync with changes.
   :views (ref [])})


(defn append-container-model [container-model new-node]
  "Add NEW-NODE to end of the contained nodes in CONTAINER-MODEL.
This mirrors the jQuery `append' function."
  (let [tail-node (:tail-node container-model)]
    (if @tail-node
      (do
        (after-container-model-node tail-node new-node)
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
This mirrors the jQuery `prepend' function."
  (let [head-node (:head-node container-model)]
    (if @head-node
      (do
        (before-container-model-node head-node new-node)
        (ref-set head-node new-node))
      (let [tail-node (:tail-node container-model)]
        (assert (not @tail-node)) ;; TODO: Remove or make compile-time optional-later.
        (assert (not @(:container-model new-node)))
        (ref-set (:container-model new-node) container-model)
        (ref-set head-node new-node)
        (ref-set tail-node new-node)
        (alter (:event-router container-model) conj ['prepend-container-model container-model new-node])))))
