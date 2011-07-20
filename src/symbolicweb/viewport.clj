(in-ns 'symbolicweb.core)

(defn make-Viewport []
  "This will instantiate a new Viewport and also 'register' it as a part of *APPLICATION* and the server via -VIEWPORTS-."
  (assert (thread-bound? #'*application*))
  (let [viewport-id (generate-uuid)
        viewport (agent {:type 'Viewport
                         :id viewport-id
                         :last-activity-time (System/currentTimeMillis)

                         :response-chunks []
                         :response-chunks-promise (promise)

                         :application *application*
                         })]
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
                       (update-in % [:response-chunks] conj new-chunk)))))


(defn find-viewport-instance []
  (assert (thread-bound? #'*application* #'*request*))
  (let [viewport-id (get (:query-params *request*) "_sw_viewport-id")]
    (assert viewport-id)
    (get (:viewports @*application*) viewport-id)))
