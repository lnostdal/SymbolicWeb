(in-ns 'symbolicweb.core)

(defn make-Application []
  (agent {:type 'Application
          :id (generate-uuid)
          :last-activity-time (System/currentTimeMillis)
          :viewports {}
          }))


(defn find-or-create-application-instance []
  "Returns two values; a map structure representing an user session/Application, and a map structure representing the current
Viewport."
  (if-let [application (get @-applications- (:value (get (:cookies *request*) "sw")))]
    (list application (binding [*application* application]
                        (find-or-create-viewport-instance)))
    (binding [*application* (make-Application)]
      (swap! -applications- #(assoc % (:id @*application*) *application*))
      (list *application* (find-or-create-viewport-instance true)))))
