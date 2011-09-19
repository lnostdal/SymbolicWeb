(in-ns 'symbolicweb.core)

(declare make-ContainerView make-ContainerModel add-branch)
(defn make-Viewport []
  "This will instantiate a new Viewport and also 'register' it as a part of *APPLICATION* and the server via -VIEWPORTS-."
  (let [viewport-id (str (generate-uid))]
    (binding [*viewport*
              (ref {:type ::Viewport
                    :id viewport-id
                    :last-activity-time (System/currentTimeMillis)
                    :aux-callbacks {} ;; {:name {:fit-fn .. :handler-fn ..}}
                    :response-str ""
                    :response-str-promise (promise)})]

      (dosync
       (with1 (make-ContainerView "div" (make-ContainerModel))
         (alter *viewport* assoc
                :root-element it
                :widgets {(:id @it) it})
         (add-branch :root it))
       (alter *application* update-in [:viewports] assoc viewport-id *viewport*))

      (when (:session? @*application*)
        (swap! -viewports- #(assoc % viewport-id *viewport*)))
      *viewport*)))


(defn add-on-visible-fn [widget fn]
  "FN is code to execute when WIDGET is added to the client/DOM end."
  (alter widget update-in [:on-visible-fns] conj fn))


(defn add-on-non-visible-fn [widget fn]
  "FN is code to execute when WIDGET is removed from the client/DOM end."
  (alter widget update-in [:on-non-visible-fns] conj fn))


(defn add-response-chunk
  "Viewport is a REF so this will work. I.e., all changes done to Viewport will reset if a transaction fails."
  ([new-chunk] (add-response-chunk new-chunk (root-element)))
  ([new-chunk widget]
     (letfn [(do-it []
               (let [viewport (:viewport @widget)]
                 (assert viewport)
                 (if (and (thread-bound? #'*in-channel-request?*) (= *viewport* viewport)) ;; AJAX?
                   (set! *in-channel-request?* (str *in-channel-request?* new-chunk \newline))
                   (alter viewport
                          #(let [promise (:response-str-promise %)]
                             (when-not (realized? promise)
                               (deliver promise 42))
                             (update-in % [:response-str] str new-chunk \newline)))))
               new-chunk)]
       (if *with-js?*
         new-chunk
         (if (:viewport @widget) ;; Visible?
           (do-it)
           (add-on-visible-fn widget do-it))))
     new-chunk))


(defn handle-widget-event [widget event-name]
  (let [w @widget]
    (if-let [handler-fn (get (:callbacks w) event-name)]
      (handler-fn w)
      (println (str "HANDLE-WIDGET-EVENT: No HANDLER-FN found for event '" event-name "' for Widget '" (:id w) "'"
                    " in Viewport '" (:id @*viewport*) "'.")))))
