(in-ns 'symbolicweb.core)

;; TODO:
;; * Better and more flexible parameter handling; static-attributes/CSS etc..
;; * Error handling and feedback to user.


(defn make-TextInput [model & {:keys [input-parsing-fn]
                               :or {input-parsing-fn identity}}]
  (with1 (make-HTMLElement ["input"
                            :static-attributes {:type "text"}
                            :input-parsing-fn input-parsing-fn
                            :set-model-fn (fn [widget model]
                                            (let [watch-key (generate-uid)]
                                              (add-watch model watch-key
                                                         (fn [_ _ _ new-value]
                                                           (jqVal widget (str new-value))))
                                              (ref-set model @model) ;; Trigger initual update.
                                              watch-key))]
                           model)
    (let [model (:model @it)]
      (set-event-handler "change" it
                         (fn [& {:keys [new-value]}]
                           (ref-set model ((:input-parsing-fn @it) new-value)))
                         :callback-data {:new-value "' + $(this).val() + '"}))))