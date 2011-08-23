(in-ns 'symbolicweb.core)

(declare make-HTMLElement)

(defn make-Viewport []
  "This will instantiate a new Viewport and also 'register' it as a part of *APPLICATION* and the server via -VIEWPORTS-."
  (let [viewport-id (generate-aid)]
    (binding [*viewport*
              (ref {:type 'Viewport
                    :id viewport-id
                    :agent (agent 42)
                    :last-activity-time (System/currentTimeMillis)
                    :aux-callbacks {} ;; {:name {:fit-fn .. :handler-fn ..}}
                    :response-str ""
                    :response-str-promise (promise)})]
      (dosync
       (with1 (make-HTMLElement ["div" :viewport *viewport*])
         (alter *viewport* assoc
                :root-element it
                :widgets {(:id @it) it}))
       (alter *application* update-in [:viewports] assoc viewport-id *viewport*))
      (when (:session? *application*)
        (swap! -viewports- #(assoc % viewport-id *viewport*)))
      *viewport*)))


(defn add-on-visible-fn [widget fn]
  "FN is code to execute when WIDGET is made visible on the client/DOM end."
  (dosync
   (alter widget update-in [:on-visible-fns] conj fn)))


(defn add-response-chunk
  ([new-chunk] (add-response-chunk new-chunk (root-element)))
  ([new-chunk widget]
     (letfn [(do-it []
               (let [viewport (:viewport @widget)]
                 (assert viewport)
                 (if (and *in-channel-request?* (= *viewport* viewport))
                   (set! *in-channel-request?* (str *in-channel-request?* new-chunk \newline)) ;; AJAX.
                   (dosync ;; Comet.
                    (alter viewport
                           #(let [promise (:response-str-promise %)]
                              (when-not (realized? promise)
                                (deliver promise 42))
                              (update-in % [:response-str] str new-chunk \newline))))))
               new-chunk)]
       (if *with-js?*
         new-chunk
         (if (:viewport @widget)
           (do-it)
           (add-on-visible-fn widget do-it))))
     new-chunk))


(defn handle-widget-event [widget event-name]
  (let [w @widget]
    (if-let [handler-fn (get (:callbacks w) event-name)]
      (handler-fn w)
      (println (str "HANDLE-WIDGET-EVENT: No HANDLER-FN found for event '" event-name "' for Widget '" (:id w) "'"
                    " in Viewport '" (:id @*viewport*) "'.")))))
