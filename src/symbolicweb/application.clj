(in-ns 'symbolicweb.core)

(defn make-Application []
  "This will instantiate a new Application and also 'register' it as part of the server via -APPLICATIONS-."
  (let [application-id (generate-uuid)
        application (agent {:type 'Application
                            :id application-id
                            :last-activity-time (System/currentTimeMillis)
                            :viewports {}
                            })]
    (swap! -applications- #(assoc % application-id application))
    application))


(defn find-or-create-application-instance []
  "Returns two values; a map structure representing an user session/Application, and a map structure representing the current
Viewport."
  (assert (thread-bound? #'*request*))
  (if-let [cookie-value (:value (get (:cookies *request*) "sw"))]
    ;; Session cookie sent.
    (if-let [application (get @-applications- cookie-value)]
      ;; Session cookie sent, and Application found on server end.
      (binding [*application* application]
        (if-let [viewport-id (get (:query-params *request*) "_sw_viewport-id")]
          ;; Viewport ID sent.
          (if-let [viewport (get (:viewports @*application*) viewport-id)]
            ;; Viewport ID sent, and Viewport found on server end.
            (list application viewport)
            ;; Viewport ID sent, but Viewport not found on server end.
            (list nil nil))
          ;; Viewport ID not sent.
          (list application (make-Viewport))))
      ;; Session cookie sent, but Application not found on server end.
      (list nil nil))
    ;; Session cookie not sent; the user is requesting a brand new session or Application.
    (binding [*application* (make-Application)]
      (list *application* (make-Viewport)))))
