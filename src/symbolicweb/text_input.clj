(in-ns 'symbolicweb.core)


;; No input parsing here since it seems doing that on the Model end of things is better. E.g. sometimes input can come from
;; several sources.

;; TODO: Autofill does not trigger onchange event ref. issue #35. Dealing with this using polling or similar leads to races
;; with other things; e.g. form submit event. The currently best way of dealing with this that I know of is to on submit
;; "manually" trigger 'change' events for each form field that might (no way to tell) have been autofilled. :(



(defn ^WidgetBase mk-TextInput [^ValueModel input-vm ^Keyword trigger-event & args]
  "<input type='text' ..> type widget.

INPUT-VM:
  Takes a ValueModel that is two-way synced server <--> client unless :OUTPUT-VM is also given (see below).


TRIGGER-EVENT:
  :CHANGE: Sync from client to server on DOM onchange event.
  :ENTERPRESS: Sync from client to server on enterpress event.


ARGS:
  :INITIAL-SYNC-SERVER?: If True (default), the value on the client will be set to the value of INPUT-VM or
                         OUTPUT-VM (if supplied) on render.
  :ONE-WAY-SYNC-CLIENT?: If True only changes originating from the client will be sent to the server; not the other way around.
  :CLEAR-ON-SUBMIT?: If True the widget will be cleared on 'submit'.
  :BLUR-ON-SUBMIT?: If True the widget will be blurred on 'submit'.
  :CALLBACK-DATA: Can be used to change data sent from client to server on the client end – before actual send.
  :OUTPUT-VM: Takes a ValueModel that will be synced one way: server --> client.
              This will also cause INPUT-VM to be synced only one way: client --> server."

  (let [args (apply hash-map args)]
    (with1 (mk-WidgetBase (fn [^WidgetBase widget]
                            (html [:input (-> (dissoc args :wb-args :initial-sync-server? :one-way-sync-client?
                                                      :clear-on-submit? :blur-on-submit? :callback-data :output-vm)
                                              (assoc :id (.id widget)
                                                     :type (or (:type args) 'text)))]))
                          (if-let [id (:id args)]
                            (assoc (:wb-args args) :id id)
                            (:wb-args args)))


      ;; Continious sync: server --> client.
      ;; NOTE: Chrome autofill will actually overwrite "" values ref. issue: http://goo.gl/aaHmyU
      (when-not (or (= trigger-event :input)
                    (:one-way-sync-client? args)
                    (:output-vm args))
        (vm-observe input-vm (.lifetime it) (get args :initial-sync-server? true)
                    #(jqVal it %3)))


      ;; Continious sync: client --> server.
      (letfn [(handle-input [input-value]
                (vm-set input-vm input-value)
                (when (:clear-on-submit? args)
                  (vm-set input-vm nil)))]

        (case trigger-event
          :input
          (set-event-handler "input" it
                             (fn [& {:keys [value]}]
                               (handle-input value))
                             :callback-data (or (:callback-data args)
                                                {:value "' + encodeURIComponent($(this).val()) + '"}))

          :change
          (set-event-handler "change" it
                             (fn [& {:keys [value]}]
                               (handle-input value))
                             :callback-data (or (:callback-data args)
                                                {:value "' + encodeURIComponent($(this).val()) + '"})
                             :js-after (when (:blur-on-submit? args)
                                         (str "$('#" (.id it) "').blur();")))

          :enterpress
          (set-event-handler "keydown" it
                             (fn [& {:keys [value]}]
                               (handle-input value))
                             :callback-data (or (:callback-data args)
                                                {:value "' + encodeURIComponent($(this).val()) + '"})
                             :js-before "if(event.keyCode == 10 || event.keyCode == 13) return(true); else return(false);"
                             :js-after (when (:blur-on-submit? args)
                                         (str "$('#" (.id it) "').blur();")))

          nil
          nil ;; Assume the user wants to assign something later.

          (trigger-event))) ;; Assume TRIGGER-EVENT is a Fn that will e.g. assign a custom event.


      ;; OUTPUT-VM: server --> client.
      (when-let [output-vm (:output-vm args)]
        (vm-observe output-vm (.lifetime it) (get args :initial-sync-server? true)
                    #(jqVal it %3))))))



;; TODO: Switch to something like bcrypt or scrypt. http://crackstation.net/hashing-security.htm
;; TODO: Don't bother salting on the client? It must be encrypted anyway.
#_(defn mk-HashedInput [model server-salt & attributes]
  "<input type='password' ..> type widget using SHA256 hashing on the client and server end. It is salted on the client and server
 end: (sha (str server-salt (sha hash)))"
  (with1 (apply mk-HTMLElement "input" model
                :static-attributes {:type "password"}
                :handle-model-event-fn (fn [_ _ _])
                :input-parsing-fn (fn [input-str]
                                    (sha (str salt input-str))) ;; Salt then hash a second time on server end.
                attributes)
    (set-event-handler "change" it
                       (fn [& {:keys [value]}]
                         (dosync
                          (vm-set model (if-let [input-parsing-fn (:input-parsing-fn @it)]
                                          (input-parsing-fn value)
                                          value))))
                       :callback-data
                       {:value "' + encodeURIComponent($.sha256($(this).val())) + '"}))) ;; Hash once on client end.



#_(defn mk-TextArea [model & attributes]
  (with1 (apply mk-HTMLElement "textarea" model
                :type ::TextArea
                :handle-model-event-fn (fn [widget _ value]
                                         (jqVal widget value))
                attributes)
    (set-event-handler "change" it
                       (fn [& {:keys [value]}]
                         (dosync
                          (vm-set model (if-let [input-parsing-fn (:input-parsing-fn @it)]
                                          (input-parsing-fn value)
                                          value))))
                       :callback-data {:value "' + encodeURIComponent($(this).val()) + '"})))



#_(defn mk-CKEditor [model & attributes]
  (apply mk-TextArea model
         :type ::CKEditor
         :render-aux-js-fn (fn [widget]
                             (let [w-m @widget
                                   id (:id w-m)]
                               (str "CKEDITOR.replace('" id "');"
                                    "CKEDITOR.instances['" id "'].on('blur', function(e){"
                                    "  if(CKEDITOR.instances['" id "'].checkDirty()){"
                                    "    CKEDITOR.instances['" id "'].updateElement();"
                                    "    $('#" id "').trigger('change');"
                                    "  }"
                                    "  CKEDITOR.instances['" id "'].resetDirty();"
                                    "});")))
         attributes))
