(ns symbolicweb.tiny-widget
  (:use [symbolicweb.core :exclude (viewport-of
                                    add-on-visible-fn
                                    add-on-non-visible-fn)]))



(defprotocol Visibility
  (add-on-visible-fn [widget cb] "Add CB to run when Widget changes from non-visible (initial state) to visible.")
  (on-visible [widget] "CBs to run when Widget changes from non-visible (initial state) to visible. ")

  (add-visibility-child [widget child-widget])
  (remove-visibility-child [widget child-widget])
  (visibility-children-of [widget])

  (viewport-of [widget]))


(defprotocol Observer
  (observe-start [observer observed] "Start observing something (OBSERVED).")
  (observe-stop [observer observed] "Stop observing something (OBSERVED)."))



(deftype NewWidgetBase [^String id
                        ^symbolicweb.core.IModel model
                        ^clojure.lang.Fn render
                        ;; Visibility.
                        ^clojure.lang.Ref on-visible-fns ;; []
                        ^clojure.lang.Ref children ;; []
                        ^clojure.lang.Ref viewport ;; Ref -> Viewport
                        ;; Observer.
                        ^clojure.lang.Fn observed-event-handler

                        ^clojure.lang.Ref callbacks] ;; {} ;; CB-NAME -> [HANDLER-FN CALLBACK-DATA]
  Visibility
  (on-visible [widget]
    (doseq [f (ensure on-visible-fns)]
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
  (observe-start [widget model]
    (when (add-view model widget)
      (observed-event-handler widget model ::-initial-update- @model)))

  (observe-stop [widget model]
    (remove-view model widget)))



(defn make-NewWidgetBase ^NewWidgetBase [^symbolicweb.core.IModel model
                                         ^clojure.lang.Fn render-fn
                                         ^clojure.lang.Fn model-event-handler-fn]
  (NewWidgetBase. (str "sw-" (generate-uid)) ;; ID
                  model
                  render-fn
                  (ref [observe-start]) ;; ON-VISIBLE-FNS
                  (ref []) ;; CHILDREN
                  (ref nil) ;; VIEWPORT
                  model-event-handler-fn
                  (ref {}))) ;; CALLBACKS


(defn make-NewHTMLElement
  (^NewWidgetBase
   [^String html-element-type
    ^symbolicweb.core.IModel model]
   (make-NewHTMLElement html-element-type
                        model
                        (fn [widget model old-value new-value]
                          (println "jqHTML:" old-value "," new-value)
                          #_(jqHTML widget (escape-html new-value)))))

  (^NewWidgetBase
   [^String html-element-type
    ^symbolicweb.core.IModel model
    ^clojure.lang.Fn model-event-handler]
   (make-NewWidgetBase model
                       #(str "<" html-element-type " id='" (.id %) "'></" html-element-type ">")
                       model-event-handler)))
