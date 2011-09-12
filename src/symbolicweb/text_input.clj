(in-ns 'symbolicweb.core)

;; TODO:
;; * Better and more flexible parameter handling; static-attributes/CSS etc..
;; * Error handling and feedback to user.


(defn make-TextInput [model & attributes]
  (with1 (apply make-HTMLElement "input" model
                :static-attributes {:type "text"}
                :handle-model-event-fn (fn [widget new-value]
                                         (jqVal widget new-value))
                :connect-model-view-fn (fn [model widget]
                                         (alter (:views model) conj widget)
                                         (jqVal widget (get-value model)))
                attributes)
    (set-event-handler "change" it
                       (fn [& {:keys [new-value]}]
                         (set-value model (if-let [input-parsing-fn (:input-parsing-fn @it)]
                                            (input-parsing-fn new-value)
                                            new-value)))
                       :callback-data {:new-value "' + $(this).val() + '"})))