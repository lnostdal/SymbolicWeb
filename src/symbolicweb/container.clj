(in-ns 'symbolicweb.core)

;; View and Viewport related concerns
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn ensure-visible [^WidgetBase child parent]
  "Ensure CHILD and its children in turn is declared visible in context of PARENT.
This will also call any FNs stored in :ON-VISIBLE-FNS for the children in question."
  (let [viewport (if (viewport? parent)
                   parent
                   (viewport-of parent))]
    ;; Viewport --> Widget.
    (alter viewport update-in
           [:widgets] assoc (.id child) child)
    ;; Widget --> Viewport.
    (vm-set (.viewport child) viewport) ;; Widget wil know which Viewport to send JS code to now.
    ;; Model --> Widget
    ;;(observe-start child) ;; NOTE: DO-ON-VISIBLE already calls this.
    (do-on-visible child)
    ;; Recurse down to all children of CHILD and so on.
    (doseq [child-of-child (visibility-children-of child)]
      (ensure-visible child-of-child child))))

(defn add-branch [parent ^WidgetBase child]
  "PARENT: A WidgetBase or Viewport instance.
CHILD: A WidgetBase instance.
Declares CHILD to be a part of PARENT.
This is used to track visibility on the server-end. Use e.g. jqAppend to actually display the widget on the client
end."
  (assert (not (parent-of child))
          (str "CHILD already has a parent assigned for it: " (parent-of child)))
  ;; PARENT <-- CHILD.
  (ref-set (.parent child) parent)
  ;; PARENT --> CHILD.
  (when-not (viewport? parent) ;; A Viewport only has a single child; the :ROOT-ELEMENT, and it's already set. TODO: Cludge.
    (alter (.children parent) conj child))
  ;; When PARENT is visible, the CHILD and its children in turn should be declared visible too.
  (when (or (viewport? parent)
            (viewport-of parent))
    (ensure-visible child parent)))


(defn ensure-non-visible [^WidgetBase widget]
  "Remove WIDGET and its children from the DOM."
  ;; Model -/-> Widget.
  (observe-stop widget)
  ;; Remove WIDGET from children of parent of widget.
  (when-let [parent (parent-of widget)]
    (when-not (viewport? parent)
      (ref-set (.children parent) (remove widget (visibility-children-of parent))))
    (doseq [child (visibility-children-of widget)]
      (ensure-non-visible child))
    (do-on-non-visible widget)
    ;; Viewport -/-> Widget.
    (alter (viewport-of widget) update-in
           [:widgets] dissoc (.id widget))
    ;; Widget -/-> Viewport.
    (ref-set (.children widget) [])
    (vm-set (.viewport widget) nil)))


(defn remove-branch [^WidgetBase branch-root-node]
  "Remove BRANCH-ROOT-NODE and its children."
  (ensure-non-visible branch-root-node))

(defn empty-branch [^WidgetBase branch-root-node]
  "Remove children from BRANCH-ROOT-NODE."
  (doseq [child (visibility-children-of branch-root-node)]
    (ensure-non-visible child)))

(defn clear-root [viewport]
  (jqEmpty (:root-element @viewport)))
