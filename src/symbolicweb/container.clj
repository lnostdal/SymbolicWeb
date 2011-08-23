(in-ns 'symbolicweb.core)


;; TODO: The JQ-APPEND? stuff is wonky.
(defn ensure-visible
  ([child parent] (ensure-visible child parent false))
  ([child parent jq-append?]
     "Ensure CHILD is made visible in context of PARENT.
This will also call any FNs stored in :ON-VISIBLE-FNS of all CHILD."
     (let [parent-m @parent
           viewport (:viewport parent-m)
           child-m @child]
       (alter viewport update-in [:widgets] assoc (:id child-m) child)
       (alter child assoc :viewport viewport)
       (when jq-append?
         (jqAppend parent (render-html child)))
       (doseq [on-visible-fn (:on-visible-fns child-m)]
         (on-visible-fn))
       (doseq [child-of-child (:children child-m)]
         (ensure-visible child-of-child child jq-append?)))))


(defn ensure-non-visible [widget]
  "Remove WIDGET and its children from the DOM."
  (let [widget-m @widget]
    ;; Remove WIDGET from children of parent of WIDGET.
    (alter (:parent widget-m)
           assoc :children (remove widget (:children (:parent widget-m))))
    (doseq [child (:children widget-m)]
      (ensure-non-visible child))
    (alter (:viewport widget-m) update-in [:widgets]
           dissoc (:id widget-m))
    (alter widget assoc
           :parent nil
           :viewport nil
           :children [])))


(defn add-branch [parent child]
  "Add CHILD to children of PARENT using CONJ (server side) / jqAppend (client side)."
  (let [parent-m @parent
        child-m @child]
    (assert (not (:parent child-m))) ;; TODO: MOVE-BRANCH?
    (alter child assoc :parent parent)
    (alter parent update-in [:children] conj child)
    ;; When PARENT is visible, the CHILD and its children in turn should be declared and made visible too.
    (when (:viewport parent-m)
      (ensure-visible child parent true))))


(defn remove-branch [branch-root-node]
  "Remove BRANCH-ROOT-NODE and its children."
  (let [root-m @branch-root-node]
    (jqRemove branch-root-node)
    (ensure-non-visible branch-root-node)))


(defn empty-branch [branch-root-node]
  "Remove children from BRANCH-ROOT-NODE."
  (doseq [child (:children @branch-root-node)]
    (remove-branch child)))


(defn clear-root []
  (dosync
   (empty-branch (root-element))))
