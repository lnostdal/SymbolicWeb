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
  (if-let [application (get @-applications- (:value (get (:cookies *request*) "sw")))]
    (list application (binding [*application* application]
                        (find-viewport-instance)))
    (binding [*application* (make-Application)]
      (list *application* (make-Viewport)))))
