(in-ns 'symbolicweb.core)


;; TODO: Generalize this into something that tracks the "lifetime" of something via some third party context.
(defprotocol Visibility
  (add-on-visible-fn [widget cb] "Add CB to run when Widget changes from non-visible (initial state) to visible.")
  (do-on-visible [widget] "Executes CBs; used when Widget changes from non-visible (initial state) to visible.")

  (add-on-non-visible-fn [widget cb] "Add CB to run when Widget changes from visible to non-visible.")
  (do-on-non-visible [widget] "Executes CBs; used when Widget changes from visible to non-visible. ")

  (add-visibility-child [widget child-widget])
  (remove-visibility-child [widget child-widget])
  (visibility-children-of [widget])

  (viewport-of [widget])
  (children-of [widget])
  (parent-of [widget]))


(defrecord WidgetBase [^String id
                       ^clojure.lang.Keyword type
                       ^symbolicweb.core.IModel model
                       ^clojure.lang.Fn render-fn

                       ;; Visibility.
                       ^clojure.lang.Ref on-visible-fns ;; []
                       ^clojure.lang.Ref on-non-visible-fns ;; []
                       ^clojure.lang.Ref children ;; []
                       ^clojure.lang.Ref parent
                       ^symbolicweb.core.IModel viewport ;; Viewport

                       ;; Observer.
                       ^clojure.lang.Fn observer-event-handler-fn

                       ^clojure.lang.Ref callbacks] ;; {} ;; CB-NAME -> [HANDLER-FN CALLBACK-DATA]
  Visibility
  (add-on-visible-fn [widget cb]
    (alter on-visible-fns conj cb))
  (do-on-visible [widget]
    (doseq [f (ensure on-visible-fns)]
      (f widget model)))

  (add-on-non-visible-fn [widget cb]
    (alter on-non-visible-fns conj cb))
  (do-on-non-visible [widget]
    (doseq [f (ensure on-non-visible-fns)]
      (f widget model)))

  (add-visibility-child [_ child-widget]
    (alter children conj child-widget))

  (remove-visibility-child [_ child-widget]
    (alter children disj child-widget))

  (visibility-children-of [_]
    (ensure children))

  (viewport-of [_]
    @viewport)

  (parent-of [_]
    (ensure parent))


  Observer
  (start-observing [widget]
    (when (add-observer (.observable model) widget)
      (when (isa? (class model) ValueModel)
        (handle-observer-event widget model ::-initial-update- @model))))

  (stop-observing [widget]
    (remove-observer (.observable model) widget))

  (handle-observer-event [widget model old-value new-value]
    (observer-event-handler-fn widget model old-value new-value)))



(defn make-WidgetBase ^WidgetBase [^clojure.lang.Keyword type
                                   ^symbolicweb.core.IModel model
                                   ^clojure.lang.Fn render-fn
                                   ^clojure.lang.Fn observer-event-handler-fn
                                   & args]
  (with (WidgetBase. (str "sw-" (generate-uid)) ;; ID
                     type
                     model
                     render-fn
                     (ref [(fn [^WidgetBase widget _] (observe-start widget))]) ;; ON-VISIBLE-FNS
                     (ref [(fn [^WidgetBase widget _] (observe-stop widget))]) ;; ON-NON-VISIBLE-FNS
                     (ref #{}) ;; CHILDREN
                     (ref nil) ;; PARENT
                     (vm nil) ;; VIEWPORT
                     observer-event-handler-fn
                     (ref {})) ;; CALLBACKS
    (apply assoc it
           :escape-html? true
           args)))


(declare add-branch)
(derive ::Observer ::WidgetBase)
;; TODO: ### Rename to VM-OBSERVE etc. ###
(defn observe [observee lifetime initial-sync? callback & args]
  "CALLBACK: (fn [old-value new-value])
LIFETIME: Governs the lifetime of this connection (Model --> OBSERVER-EVENT-HANDLER-FN) and can be a View/Widget or NIL for 'infinite' lifetime (as long as MODEL exists).
INITIAL-SYNC?: If true CALLBACK will be called even though OLD-VALUE is = :symbolicweb.core/-initial-update-. I.e., on construction
of this observer."
  (with1 (apply make-WidgetBase
                ::Observer
                observee
                (fn [_] (assert false "Observers are not meant to be rendered!"))
                (fn [observer observee old-value new-value]
                  (if (= old-value :symbolicweb.core/-initial-update-)
                    (when initial-sync?
                      (callback observer old-value new-value))
                    (callback observer old-value new-value)))
                args)
    ;; Interesting; the change I commented out here seems to make SW-TITLE on big view not to render correctly for :admin users.
    ;;(if lifetime
    ;;  (add-branch lifetime it)
    ;;  (observe-start it))
    (when lifetime
      (add-branch lifetime it))
    (observe-start it)))
