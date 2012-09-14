(in-ns 'symbolicweb.core)


(defprotocol IWidgetBase
  (viewport-of [widget-base])
  (parent-of [widget-base])

  (attach-branch [widget-base child-widget-base])
  (detach-branch [widget-base])
  (empty-branch [widget-base]))


(defrecord WidgetBase [^String id
                       ^clojure.lang.Keyword type
                       ^Lifetime lifetime
                       ^clojure.lang.Fn render-fn
                       ^clojure.lang.Ref parent ;; WidgetBase
                       ^clojure.lang.Ref viewport ;; Viewport
                       ^clojure.lang.Ref callbacks] ;; {CB-NAME -> [HANDLER-FN CALLBACK-DATA], ...}   (DOM events)

  IWidgetBase
  (viewport-of [_]
    (ensure viewport))

  (parent-of [_]
    (ensure parent))


  (attach-branch [parent child]
    (assert (not (parent-of child))
            (str "ATTACH-BRANCH: CHILD already has a parent assigned for it: " (parent-of child)
                 ", EXISTING-PARENT-ID: " (.id (parent-of child))
                 ", CHILD-ID: " (.id child)))
    ;; CHILD -> PARENT.
    (ref-set (.parent child) parent)
    (attach-lifetime (.lifetime parent) (.lifetime child)))


  (detach-branch [widget]
    (detach-lifetime (.lifetime widget)))


  (empty-branch [widget]
    (doseq [^Lifetime child-lifetime (.children (.lifetime widget))]
      (detach-lifetime child-lifetime))))



(defn make-WidgetBase ^WidgetBase [^clojure.lang.Keyword type
                                   ^clojure.lang.Fn render-fn
                                   & args]
  (with (WidgetBase. (str "sw-" (generate-uid)) ;; ID
                     type
                     (mk-Lifetime)
                     render-fn
                     (ref nil) ;; PARENT
                     (ref nil) ;; VIEWPORT
                     (ref {})) ;; CALLBACKS
    (add-lifetime-activation-fn (.lifetime it)
                                (fn [^Lifetime lifetime]
                                  (let [parent-viewport (viewport-of (parent-of it))]
                                    ;; Viewport --> Widget (DOM events).
                                    (alter parent-viewport update-in [:widgets]
                                           assoc (.id it) it)
                                    ;; Widget --> Viewport.
                                    (vm-set (.viewport it) parent-viewport))))
    (add-lifetime-deactivation-fn (.lifetime it)
                                  (fn [^Lifetime lifetime]
                                    (let [viewport (viewport-of it)]
                                      ;; Viewport -/-> Widget (DOM events).
                                      (alter viewport update-in [:widgets]
                                             dissoc (.id it) it)
                                      ;; Widget -/-> Viewport.
                                      (vm-set (.viewport it) nil))))
    (apply assoc it
           :escape-html? true
           args)))


