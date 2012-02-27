(in-ns 'symbolicweb.core)


(defn app-agent []
  (:agent @*application*))


(defn make-Application [& app-args]
  "This will instantiate a new Application and also 'register' it as a part of the server via -APPLICATIONS-. On page load (or refresh), the order of things executed are:

  :MAKE-VIEWPORT-FN
  :RELOAD-HANDLER
  :REST-HANDLER"
  (let [application-id (generate-uuid)
        application (apply assoc {}
                           :type ::Application
                           :agent (agent ::ApplicationAgent)
                           :id application-id
                           :id-generator (let [last-id (atom 0N)]
                                           (fn [] (swap! last-id inc')))
                           :logged-in? (vm false)
                           :user-model (vm nil) ;; Reference to UserModel so we can remove ourself from it when we are GCed.
                           :last-activity-time (atom (System/currentTimeMillis))
                           :viewports {}
                           :make-viewport-fn #'make-Viewport
                           :request-handler #'default-request-handler
                           :reload-handler (fn [])
                           :rest-handler #'default-rest-handler
                           :ajax-handler #'default-ajax-handler
                           :aux-handler #'default-aux-handler
                           :session? true
                           :html-title "[SymbolicWeb]"
                           app-args)
        application-ref (ref application)]
    (when (:session? application)
      (swap! -applications- #(assoc % application-id application-ref))
      (dosync
       (vm-alter -num-applications-model- + 1)))
    application-ref))


(defn find-application-constructor []
  (loop [app-types @-application-types-]
    (when-first [app-type app-types]
      (let [app-type (val app-type)]
        (if ((:fit-fn app-type))
          (:application-constructor-fn app-type)
          (recur (next app-types)))))))


(defn find-or-create-application-instance []
  (if-let [cookie-value (:value (get (:cookies *request*) "_sw_application_id"))]
    ;; Session cookie sent.
    (if-let [application (get @-applications- cookie-value)]
      ;; Session cookie sent, and Application found on server end.
      application
      ;; Session cookie sent, but Application not found on server end.
      (make-Application :rest-handler clear-session-page-handler :session? false))
    ;; Session cookie not sent; the user is requesting a brand new session or Application.
    (if-let [application-constructor (find-application-constructor)]
      (application-constructor)
      (make-Application :rest-handler not-found-page-handler :session? false))))


(defn undefapp [name]
  (swap! -application-types- #(dissoc % name)))
