(in-ns 'symbolicweb.core)

(defn make-Viewport []
  "This will instantiate a new Viewport and also 'register' it as a part of *APPLICATION* and the server via -VIEWPORTS-."
  (let [viewport-id (generate-aid)
        viewport (agent {:type 'Viewport
                         :id viewport-id
                         :last-activity-time (System/currentTimeMillis)
                         :widgets {} ;; ID -> Widget
                         :aux-callbacks {} ;; {:name {:fit-fn .. :handler-fn ..}}
                         :response-chunks []
                         :response-chunks-promise (promise)})]
    (swap! -viewports- #(assoc % viewport-id viewport))
    (send *application* #(update-in % [:viewports] conj [viewport-id viewport]))
    (await *application*)
    viewport))


(defn add-response-chunk
  ([new-chunk] (add-response-chunk new-chunk *viewport*))
  ([new-chunk viewport]
     (send viewport #(let [promise (:response-chunks-promise %)]
                       (when (not (realized? promise))
                         (deliver promise 42))
                       (update-in % [:response-chunks] conj (str new-chunk \newline \newline))))
     nil))


(defn handle-widget-event [widget event-name]
  (let [w @widget]
    (if-let [handler-fn (get (:callbacks w) event-name)]
      (handler-fn w)
      (println (str "HANDLE-WIDGET-EVENT: No HANDLER-FN found for event '" event-name "' for Widget '" (:id w) "'"
                    " in Viewport '" (:id @*viewport*) "'.")))))
