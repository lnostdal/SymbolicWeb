(in-ns 'symbolicweb.core)



(defn mk-Checkbox [^ValueModel model & args]
  ;; TODO: Merge in ARGS without overwriting :html-attrs -> :type here.
  (with1 (mk-WB :input {:html-attrs {:type :checkbox}})
    (vm-observe model (.lifetime it) true
                (fn [^Lifetime lifetime old-value new-value]
                  (jqProp it "checked" (if new-value "true" ""))))
    (set-event-handler "change" it
                       (fn [& {:keys [new-state]}]
                         (vm-set model (= new-state "true")))
                       :callback-data {:new-state (str "' + encodeURIComponent($(this).prop('checked')) + '")})))
