(in-ns 'symbolicweb.core)

(defn make-Viewport []
  (agent {:type 'Viewport
          :id (generate-uuid)
          :last-activity-time (System/currentTimeMillis)

          :response-chunks []
          :response-chunks-promise (promise)

          :application nil
          }))


(defn add-response-chunk
  ([new-chunk] (add-response-chunk new-chunk *viewport*))
  ([new-chunk viewport]
     (send viewport #(let [promise (:response-chunks-promise %)]
                       (when (not (realized? promise))
                         (deliver promise 42))
                       (update-in % [:response-chunks] conj new-chunk)))))


(defn find-or-create-viewport-instance
  ([] (find-or-create-viewport-instance false))
  ([just-create?]
     (if-let [viewport-id (and (not just-create?) (get (:query-params *request*) "_sw_viewport-id"))]
       (let [viewport (get (:viewports @*application*) viewport-id)]
         (assert viewport)
         viewport)
       (let [viewport (make-Viewport)]
         (swap! -viewports- #(assoc % (:id @viewport) viewport))
         (send *application* #(update-in % [:viewports] conj [(:id @viewport) viewport]))
         (await *application*)
         viewport))))
