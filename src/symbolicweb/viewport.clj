(in-ns 'symbolicweb.core)

(declare make-ContainerView make-ContainerModel add-branch)
(defn make-Viewport
  "This will instantiate a new Viewport and also 'register' it as a part of *APPLICATION* and the server via -VIEWPORTS-."
  ([] (make-Viewport (make-ContainerView "div" (make-ContainerModel))))

  ([root-widget & args]
     (let [viewport-id (cl-format false "~36R" (generate-uid))]
       (binding [*viewport*
                 (ref (apply assoc {}
                             :type ::Viewport
                             :id viewport-id
                             :last-activity-time (System/currentTimeMillis)
                             :aux-callbacks {} ;; {:name {:fit-fn .. :handler-fn ..}}
                             :response-str (atom "")
                             :response-promise (atom (promise))
                             :response-sched-fn (atom nil) ;;(atom (fn []))
                             :response-agent (agent nil)
                             args))]
         (dosync
          (alter root-widget assoc
                 :viewport *viewport*)
          (alter *viewport* assoc
                 :application *application*
                 :root-element root-widget
                 :widgets {(:id @root-widget) root-widget})
          (add-branch :root root-widget)
          (alter *application* update-in [:viewports] assoc viewport-id *viewport*))
         (when (:session? @*application*)
           (swap! -viewports- #(assoc % viewport-id *viewport*)))
         *viewport*))))


(defn add-on-visible-fn [widget fn]
  "FN is code to execute when WIDGET is added to the client/DOM end."
  (alter widget update-in [:on-visible-fns] conj fn))


(defn add-on-non-visible-fn [widget fn]
  "FN is code to execute when WIDGET is removed from the client/DOM end."
  (alter widget update-in [:on-non-visible-fns] conj fn))


(defn add-response-chunk
  "Mutation is done in an agent; if a transaction fails, nothing happens."
  ([new-chunk] (add-response-chunk new-chunk (if *with-js?* nil (root-element))))
  ([new-chunk widget]
     (letfn [(do-it []
               (let [viewport (viewport-of widget)
                     viewport-m @viewport]
                 (if (and (thread-bound? #'*in-channel-request?*)
                          *in-channel-request?*
                          (= *viewport* viewport))
                   (set! *in-channel-request?* (str *in-channel-request?* new-chunk \newline))
                   (send (:response-agent viewport-m)
                         (fn [_]
                           (with-errors-logged
                             (locking viewport
                               (let [response-str (:response-str viewport-m)
                                     response-promise (:response-promise viewport-m)
                                     response-sched-fn (:response-sched-fn viewport-m)]
                                 (reset! response-str (str @response-str new-chunk \newline))
                                 (when-not (realized? @response-promise)
                                   (deliver @response-promise 42)
                                   (when @response-sched-fn
                                     ;; TODO: This blocks! Hmmm.
                                     (.run @response-sched-fn))))))))))
               new-chunk)]
       (when-not *with-js?*
         (if (viewport-of widget) ;; Visible?
           (do-it)
           (add-on-visible-fn widget do-it))))
     new-chunk))


(defn handle-widget-event [widget event-name]
  (let [w @widget]
    (if-let [handler-fn (get (:callbacks w) event-name)]
      (handler-fn w)
      (println (str "HANDLE-WIDGET-EVENT: No HANDLER-FN found for event '" event-name "' for Widget '" (:id w) "'"
                    " in Viewport '" (:id @*viewport*) "'.")))))
