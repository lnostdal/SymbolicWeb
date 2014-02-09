(in-ns 'symbolicweb.core)

;; TODO:

;; * Better and more flexible parameter handling; static-attributes/CSS etc..



;; TODO: Add support for :HTML-ATTRS.
(defn ^WidgetBase mk-TextInput [^ValueModel input-vm ^Keyword trigger-event & args]
  "<input type='text' ..> type widget.

INPUT-VM:
  Takes a ValueModel that is two-way synced server <--> client unless :OUTPUT-VM is also given (see below).


TRIGGER-EVENT:
  :CHANGE: Sync from client to server on DOM onchange event.
  :ENTERPRESS: Sync from client to server on enterpress event.


ARGS:
  :INITIAL-SYNC-SERVER?: if True the value on the client will be set to the value of INPUT-VM on render.
  :ONE-WAY-SYNC-CLIENT?: if True only changes originating from the client will be sent to the server; not the other way around.
  :CLEAR-ON-SUBMIT?: If True the widget will be cleared on 'submit'.
  :BLUR-ON-SUBMIT?: If True the widget will be blurred on 'submit'.
  :CALLBACK-DATA: Can be used to change data sent from client to server on the client end – before actual send.
  :OUTPUT-VM: Takes a ValueModel that will be synced one way: server --> client.
              This will also cause INPUT-VM to be synced only one way: client --> server."
  (let [args (apply hash-map args)]
    (with1 (mk-WidgetBase (fn [^WidgetBase widget]
                            (str "<input type='" (or (and (:type args) (name (:type args))) "text")
                                 "' id='" (.id widget) "'>"))
                          (dissoc args
                                  :initial-sync-server? :one-way-sync-client? :clear-on-submit? :blur-on-submit?
                                  :callback-data :output-vm))

      ;; INPUT-VM: Server --> client.
      (if (or (:one-way-sync-client? args)
              (:output-vm args))
        (when (get args :initial-sync-server? true)
          (jqVal it @input-vm))
        (vm-observe input-vm (.lifetime it) (case (get args :initial-sync-server? ::not-found)
                                                 (true ::not-found) true
                                                 (false nil) false)
                    #(jqVal it (or %3 ""))))

      ;; INPUT-VM: Client --> server.
      (case trigger-event
        :change
        (set-event-handler "change" it
                           (fn [& {:keys [value]}]
                             ;; TODO: Perhaps this widget shouldn't deal with input parsing at all? Dataflow via an additional
                             ;; VM could do it instead. The benefit of that would be that several input sources could make use
                             ;; of the same VM. If not, this code needs to be called by the :ENTEPRESS case, below, also.
                             (let [value (if-let [f (:input-parsing-fn args)]
                                               (try
                                                 (f value)
                                                 (catch Throwable e
                                                   (if-let [f (:input-parsing-error-fn args)]
                                                     (f value)
                                                     (throw
                                                      (ex-info (str "mk-TextInput: Input parsing error for widget: " (.id it))
                                                               {:widget it :model input-vm :value value}
                                                               e)))))
                                               value)]
                               (vm-set input-vm value)
                               (when (:clear-on-submit? args)
                                 (vm-set input-vm nil))))
                           :callback-data (if-let [cd (:callback-data args)]
                                            cd
                                            {:value "' + encodeURIComponent($(this).val()) + '"})
                           :js-after (when (:blur-on-submit? args)
                                       (str "$('#" (.id it) "').blur();")))

        :enterpress
        (set-event-handler "keydown" it
                           (fn [& {:keys [value]}]
                             (vm-set input-vm value)
                             (when (:clear-on-submit? args)
                               (vm-set input-vm nil)))
                           :callback-data (if-let [cd (:callback-data args)]
                                            cd
                                            {:value "' + encodeURIComponent($(this).val()) + '"})
                           :js-before "if(event.keyCode == 10 || event.keyCode == 13) return(true); else return(false);"
                           :js-after (when (:blur-on-submit? args)
                                        (str "$('#" (.id it) "').blur();")))

        nil
        nil ;; Assume the user wants to assign something later.

        (trigger-event)) ;; Assume TRIGGER-EVENT is a Fn that will e.g. assign a custom event.

      ;; OUTPUT-VM: Server --> client.
      (when-let [output-vm (:output-vm args)]
        (vm-observe output-vm (.lifetime it) false
                    #(jqVal it (or %3 "")))))))



;; TODO: Switch to something like bcrypt or scrypt. http://crackstation.net/hashing-security.htm
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
