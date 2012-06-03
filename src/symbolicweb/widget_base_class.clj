(in-ns 'symbolicweb.core)


(defprotocol Visibility
  (add-on-visible-fn [widget cb] "Add CB to run when Widget changes from non-visible (initial state) to visible.")
  (on-visible [widget] "Returns CBs to run when Widget changes from non-visible (initial state) to visible. ")

  (add-on-non-visible-fn [widget cb] "Add CB to run when Widget changes from visible to non-visible.")
  (on-non-visible [widget] "Returns CBs to run when Widget changes from visible to non-visible. ")

  (add-visibility-child [widget child-widget])
  (remove-visibility-child [widget child-widget])
  (visibility-children-of [widget])

  (viewport-of [widget]))


(defprotocol Observer
  (observe-start [observer] "Start observing something.")
  (observe-stop [observer] "Stop observing something."))



(defrecord WidgetBase [^String id
                       ^clojure.lang.Keyword type
                       ^symbolicweb.core.IModel model
                       ^clojure.lang.Fn render

                       ;; Visibility.
                       ^clojure.lang.Ref on-visible-fns ;; []
                       ^clojure.lang.Ref on-non-visible-fns ;; []
                       ^clojure.lang.Ref children ;; []
                       ^clojure.lang.Ref viewport ;; Viewport

                       ;; Observer.
                       ^clojure.lang.Fn observed-event-handler

                       ^clojure.lang.Ref callbacks] ;; {} ;; CB-NAME -> [HANDLER-FN CALLBACK-DATA]
  Visibility
  (on-visible [widget]
    (doseq [f (ensure on-visible-fns)]
      (f widget model)))

  (on-non-visible [widget]
    (doseq [f (ensure on-non-visible-fns)]
      (f widget model)))

  (add-visibility-child [_ child-widget]
    (alter children conj child-widget))

  (remove-visibility-child [_ child-widget]
    (alter children disj child-widget))

  (visibility-children-of [_]
    (ensure children))

  (viewport-of [_]
    (ensure viewport))


  Observer
  (observe-start [widget]
    (when (add-view model widget)
      (observed-event-handler widget model ::-initial-update- @model)))

  (observe-stop [widget]
    (remove-view model widget)))



(defn make-WidgetBase ^WidgetBase [^clojure.lang.Keyword type
                                   ^symbolicweb.core.IModel model
                                   ^clojure.lang.Fn render-fn
                                   ^clojure.lang.Fn model-event-handler-fn
                                   & args]
  (with (WidgetBase. (str "sw-" (generate-uid)) ;; ID
                     type
                     model
                     render-fn
                     (ref [observe-start]) ;; ON-VISIBLE-FNS
                     (ref [observe-stop]) ;; ON-NON-VISIBLE-FNS
                     (ref []) ;; CHILDREN
                     (ref nil) ;; VIEWPORT
                     model-event-handler-fn
                     (ref {})) ;; CALLBACKS
    (if (empty? args)
      it
      (apply assoc it args))))
