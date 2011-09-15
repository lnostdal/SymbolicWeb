(in-ns 'symbolicweb.core)

(defn make-Application [[& {:keys [request-handler reload-handler rest-handler ajax-handler aux-handler
                                   session?]
                            :or {request-handler #'default-request-handler
                                 reload-handler  (fn [])
                                 rest-handler    #'default-rest-handler
                                 ajax-handler    #'default-ajax-handler
                                 aux-handler     #'default-aux-handler
                                 session?        true}}]
                        & app-args]
  "This will instantiate a new Application and also 'register' it as a part of the server via -APPLICATIONS-."
  (let [application-id (generate-uuid)
        application (ref (apply assoc {}
                                :type ::Application
                                :session? session?
                                :id application-id
                                :id-generator (let [last-id (atom 0N)]
                                                (fn [] (swap! last-id inc')))
                                :last-activity-time (System/currentTimeMillis)
                                :viewports {}
                                :request-handler request-handler
                                :reload-handler  reload-handler
                                :rest-handler    rest-handler
                                :ajax-handler    ajax-handler
                                :aux-handler     aux-handler
                                :html-title "[SymbolicWeb]"
                                app-args))]
    (when session?
      (swap! -applications- #(assoc % application-id application))
      (dosync
       (set-value -num-applications-model- (count @-applications-))))
    application))


(defn find-application-constructor []
  (assert (thread-bound? #'*request*)) ;; Most :FIT-FNs will want to look at the HTTP request.
  (loop [app-types @-application-types-]
    (when-first [app-type app-types]
      (let [app-type (val app-type)]
        (if ((:fit-fn app-type))
          (:application-constructor-fn app-type)
          (recur (next app-types)))))))


(defn find-or-create-application-instance []
  "Returns two values; a map structure representing an user session/Application, and a map structure representing the current
Viewport."
  (assert (thread-bound? #'*request*))
  (if-let [cookie-value (:value (get (:cookies *request*) "_sw_application_id"))]
    ;; Session cookie sent.
    (if-let [application (get @-applications- cookie-value)]
      ;; Session cookie sent, and Application found on server end.
      (binding [*application* application]
        (if-let [viewport-id (get (:query-params *request*) "_sw_viewport_id")]
          ;; Viewport ID sent.
          (if-let [viewport (get (:viewports @*application*) viewport-id)]
            ;; Viewport ID sent, and Viewport found on server end.
            (list application viewport)
            ;; Viewport ID sent, but Viewport not found on server end.
            (binding [*application* (make-Application [:rest-handler clear-session-page-handler :session? false])]
              (list *application* (make-Viewport))))
          ;; Viewport ID not sent.
          (list application (make-Viewport))))
      ;; Session cookie sent, but Application not found on server end.
      (binding [*application* (make-Application [:rest-handler clear-session-page-handler :session? false])]
        (list *application* (make-Viewport))))
    ;; Session cookie not sent; the user is requesting a brand new session or Application.
    (binding [*application* (if-let [application-constructor (find-application-constructor)]
                              (application-constructor)
                              (make-Application [:rest-handler not-found-page-handler :session? false]))]
      (list *application* (make-Viewport)))))


(defn undefapp [name]
  (swap! -application-types- #(dissoc % name)))
