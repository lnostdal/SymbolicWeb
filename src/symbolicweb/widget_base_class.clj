(in-ns 'symbolicweb.core)


;; TODO: Perhaps it is possible to do this without having a PARENT field? Just pass information about the parent along
;; as an argument to ATTACH-LIFETIME to "store" for the later invocation of the ADD-LIFETIME-ACTIVATION-FN callback.


(defprotocol IWidgetBase
  (viewport-of [widget-base])
  (parent-of [widget-base])

  (attach-branch [widget-base child-widget-base])
  (detach-branch [widget-base])
  (empty-branch [widget-base]))



(deftype WidgetBase [^String id
                     ^Lifetime lifetime
                     ^Fn render-fn
                     ^Ref parent ;; WidgetBase
                     ^ValueModel viewport ;; Viewport
                     ^Ref callbacks ;; {CB-NAME -> [HANDLER-FN CALLBACK-DATA], ...}   (DOM events)
                     ^Boolean escape-html?]
  IWidgetBase
  (viewport-of [_]
    @viewport)


  (parent-of [_]
    (ensure parent))


  (attach-branch [parent child]
    #_(assert (not (parent-of child))
            (str "ATTACH-BRANCH: CHILD already has a parent assigned for it: " (parent-of child)
                 ", EXISTING-PARENT-ID: " (.id (parent-of child))
                 ", CHILD-ID: " (.id child)))
    ;; CHILD -> PARENT.
    (ref-set (.parent ^WidgetBase child) parent)
    (attach-lifetime (.lifetime parent) (.lifetime ^WidgetBase child)))


  (detach-branch [widget]
    (detach-lifetime (.lifetime widget)))


  (empty-branch [widget]
    ;; TODO: This won't do since there might be child Lifetimes "outside of" the Widget hierarchy that shouldn't be removed.
    ;; Another way to think about this is that _we_ aren't dead yet; only our child _wigets_ are.
    #_(doseq [^Lifetime child-lifetime (lifetime-children-of (.lifetime widget))]
        (detach-lifetime child-lifetime))
    ;; ..so, a hack (e.g. can .VIEWPORT return NIL?) instead – we scan all Widgets in the Viewport of WIDGET.
    (when-let [viewport @(.viewport widget)]
      (doseq [some-widget (vals (:widgets @viewport))]
        (when (= widget @(.parent ^WidgetBase some-widget))
          (detach-branch some-widget))))))
