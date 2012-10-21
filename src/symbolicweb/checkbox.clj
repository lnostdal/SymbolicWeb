(in-ns 'symbolicweb.core)

(derive ::CheckBox ::HTMLElement)
(defn mk-CheckBox [model & attributes]
  (with1 (apply mk-HTMLElement "input" model
                :type ::CheckBox
                :static-attributes {:type "checkbox"}
                :handle-model-event-fn (fn [widget old-state new-state]
                                         (jqProp widget "checked" new-state))
                attributes)
    (set-event-handler "change" it
                       (fn [& {:keys [new-state]}]
                         (vm-set model (= "true" new-state)))
                       :callback-data {:new-state (str "' + encodeURIComponent($(this).prop('checked')) + '")})))
