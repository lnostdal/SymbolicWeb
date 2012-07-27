(in-ns 'symbolicweb.core)

(declare make-ContainerView make-ContainerModel add-branch)
(defn make-Viewport [request application ^WidgetBase root-widget & args]
  "This will instantiate a new Viewport and also 'register' it as a part of APPLICATION and the server via -VIEWPORTS-."
  (let [viewport-id (cl-format false "~36R" (generate-uid))
        viewport (ref (apply assoc {}
                             :type ::Viewport
                             :id viewport-id
                             :last-activity-time (atom (System/currentTimeMillis))
                             :aux-callbacks {} ;; {:name {:fit-fn .. :handler-fn ..}}
                             :response-str (StringBuilder.)
                             :response-sched-fn (atom nil)
                             :response-agent (agent nil)
                             args))]
    (dosync
     (vm-set (.viewport root-widget) viewport)
     (alter viewport assoc
            :application application
            :root-element root-widget
            :widgets {(.id root-widget) root-widget})
     (add-branch viewport root-widget)
     (alter application update-in [:viewports] assoc viewport-id viewport))
    (when (:session? @application)
      (swap! -viewports- #(assoc % viewport-id viewport)))
    viewport))


(defn add-response-chunk-agent-fn [viewport viewport-m ^String new-chunk]
  (with-errors-logged
    (locking viewport
      (let [response-sched-fn ^Atom (:response-sched-fn viewport-m)]
        (.append ^StringBuilder (:response-str viewport-m) new-chunk)
        (when @response-sched-fn
          (.run ^java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask
                (.job ^overtone.at_at.ScheduledJob @response-sched-fn)))))))


(defn add-response-chunk [^String new-chunk widget]
  "WIDGET: A WidgetBase or Viewport instance."
  (when-not *with-js?*
    (if (viewport? widget)
      (let [viewport widget
            viewport-m @widget]
        (send (:response-agent viewport-m)
              (fn [_] (add-response-chunk-agent-fn viewport viewport-m new-chunk))))
      (letfn [(do-it []
                (let [viewport (viewport-of widget)
                      viewport-m @viewport]
                  ;; TODO: M-m-m-mega hack. Why isn't REMOVE-VIEW called vs. TemplateElements and it seems some other widgets
                  ;; held in HTMLContainers in some cases?
                  ;; Are children not added to HTMLTemplate?
                  (if (< (* -viewport-timeout- 3) ;; NOTE: Times 3!
                         (- (System/currentTimeMillis) @(:last-activity-time viewport-m)))
                    (do
                      ;; Ok, it seems :VIEWPORT is set and :PARENT is not set to :DEAD so so this means ENSURE-NON-VISIBLE
                      ;; has _not_ been called yet -- which is strange.
                      ;;(remove-view (:model @widget) widget) ;; Ok, we might still leak; what about children of parent?
                      ;;(dbg-prin1 [(:type @widget) (:id @widget)])
                      (when (not= :dead (parent-of widget))
                        (dbg-prin1 [(:type widget) (.id ^WidgetBase widget)]) ;; This one is interesting; uncomment when working on this.
                        (remove-branch widget)
                        (def -lost-widget- widget)))
                    (send (:response-agent viewport-m)
                          (fn [_]
                            (add-response-chunk-agent-fn viewport viewport-m new-chunk))))))]
        (if (viewport-of widget) ;; Visible?
          (do-it)
          (add-on-visible-fn widget (fn [_ _] (do-it)))))))
  new-chunk)


;; TODO: This doesn't seem to be used any more? HANDLE-IN-CHANNEL-REQUEST calls callbacks directly instead.
(defn handle-widget-event [widget event-name]
  (let [w @widget]
    (if-let [handler-fn (get (:callbacks w) event-name)]
      (handler-fn w)
      (println (str "HANDLE-WIDGET-EVENT: No HANDLER-FN found for event '" event-name "' for Widget '" (:id w) "'"
                    " in Viewport '" (:id (viewport-of widget)) "'.")))))
