(in-ns 'symbolicweb.core)


(defn make-Application [& app-args]
  "This will instantiate a new Application (browser session) and also 'register' it as a part of the server via -APPLICATIONS-.
On page load (or refresh), the order of things executed are:

  :MAKE-VIEWPORT-FN
  :REST-HANDLER"
  (let [application-id (generate-uuid)
        application (apply assoc {}
                           :type ::Application
                           :agent (agent ::ApplicationAgent)
                           :cookies (atom nil)
                           :id application-id
                           :id-generator (let [last-id (atom 0N)]
                                           (fn [] (swap! last-id inc')))
                           :logged-in? (vm false)
                           :user-model (vm nil) ;; Reference to UserModel so we can remove ourself from it when we are GCed.
                           :last-activity-time (atom (System/currentTimeMillis))
                           :viewports {}
                           :session-data (ref {})
                           :make-viewport-fn (fn [request application]
                                               (throw (Exception. "make-Application: No :MAKE-VIEWPORT-FN given.")))
                           :request-handler #'default-request-handler
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


(defn session-get [application key]
  "KEY is KEYWORD."
  (dosync
   (when-let [res (get @(:session-data @application)
                       key)]
     @res)))


(defn session-set [application m]
  (dosync
   (doseq [map-entry m]
     (if (contains? @(:session-data @application) (key map-entry))
       ;; An entry with that key already exists; set its value.
       (vm-set ((key map-entry) @(:session-data @application))
               (val map-entry))
       ;; An entry with that key doesn't exist; add the key and value (wrapped in a ValueModel).
       (alter (:session-data @application)
              assoc (key map-entry) (vm (val map-entry)))))))


(defn find-application-constructor [request]
  (loop [app-types @-application-types-]
    (when-first [app-type app-types]
      (let [app-type (val app-type)]
        (if ((:fit-fn app-type) request)
          (:application-constructor-fn app-type)
          (recur (next app-types)))))))


(declare json-parse)
(defn find-or-create-application-instance [request]
  (with1 (if-let [cookie-value (:value (get (:cookies request) "_sw_application_id"))]
           ;; Session cookie sent.
           (if-let [application (get @-applications- cookie-value)]
             ;; Session cookie sent, and Application found on server end.
             application
             ;; Session cookie sent, but Application not found on server end.
             (make-Application :rest-handler #'clear-session-page-handler :session? false))
           ;; Session cookie not sent; the user is requesting a brand new session or Application.
           (if-let [application-constructor (find-application-constructor request)]
             (application-constructor :session?
                                      (with (get (:query-params request) "_sw_session_p")
                                        (if (nil? it)
                                          true
                                          (json-parse it))))
             (make-Application :rest-handler not-found-page-handler :session? false)))
    (reset! (:cookies @it) (:cookies request))))


(defn undefapp [name]
  (swap! -application-types- #(dissoc % name)))
