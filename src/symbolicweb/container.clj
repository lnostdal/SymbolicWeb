(in-ns 'symbolicweb.core)

;; View and Viewport related concerns
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn ensure-visible [child parent]
  "Ensure CHILD and its children in turn is declared visible in context of PARENT.
This will also call any FNs stored in :ON-VISIBLE-FNS for the children in question."
  (let [child-m (ensure child)
        parent-m (ensure parent)
        viewport (let [parent-type (:type parent-m)]
                   (cond
                    (isa? parent-type ::Viewport) parent
                    (isa? parent-type ::Widget) (:viewport parent-m)))]
    ;; Viewport --> Widget.
    (alter viewport update-in [:widgets] assoc (:id child-m) child) ;; DOM-events will find the widget now.
    (alter child assoc :viewport viewport) ;; Widget will know which Viewport to send JS code to now.
    ;; Model --> Widget.
    ((:connect-model-view-fn child-m) (:model child-m) child)
    (doseq [on-visible-fn @(:on-visible-fns child-m)]
      (on-visible-fn))
    ;; Recurse down to all children of CHILD and so on.
    (doseq [child-of-child (:children child-m)]
      (ensure-visible child-of-child child))))

(defn add-branch [parent child]
  "Declare CHILD to be a part of PARENT.
This is used to track visibility on the server-end. Use e.g. jqAppend to actually display the widget on the client
end."
  (let [parent-m (ensure parent)
        child-m (ensure child)]
    (assert (not (:parent child-m))) ;; Make sure CHILD doesn't have a parent already.
    ;; Parent <-- Child.
    (alter child assoc :parent parent)
    (when-not (isa? (:type parent-m) ::Viewport) ;; Viewport only has a single child; the :ROOT-ELEMENT.
      ;; Parent --> Child.
      (alter parent update-in [:children] conj child))
    ;; When PARENT is visible, the CHILD and its children in turn should be declared visible too.
    (when (or (isa? (:type parent-m) ::Viewport) ;; :ROOT-ELEMENT?
              (:viewport parent-m)) ;; Is the widget visible (added to a Viewport)?
      (ensure-visible child parent))))


(defn ensure-non-visible [widget]
  "Remove WIDGET and its children from the DOM."
  (let [widget-m @widget]
    ;; Model -/-> Widget.
    ((:disconnect-model-view-fn widget-m) widget)
    ;; Remove WIDGET from children of parent of WIDGET.
    (when (:parent widget-m)
      (alter (:parent widget-m)
             assoc :children (remove widget (:children (:parent widget-m))))
      (doseq [child (:children widget-m)]
        (ensure-non-visible child))
      (doseq [on-non-visible-fn @(:on-non-visible-fns widget-m)]
        (on-non-visible-fn))
      ;; Viewport -/-> Widget.
      (alter (:viewport widget-m) update-in [:widgets]
             dissoc (:id widget-m))
      (alter widget assoc
             :parent nil
             :viewport nil
             :children []))))

(defn remove-branch [branch-root-node]
  "Remove BRANCH-ROOT-NODE and its children."
  (ensure-non-visible branch-root-node))

(defn empty-branch [branch-root-node]
  "Remove children from BRANCH-ROOT-NODE."
  (doseq [child (:children @branch-root-node)]
    (remove-branch child)))


(defn clear-root []
  (jqEmpty (root-element)))
