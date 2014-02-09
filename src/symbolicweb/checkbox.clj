(in-ns 'symbolicweb.core)



(defn mk-Checkbox [^ValueModel model & args]
  ;; TODO: Handle ARGS?
  (with1 (whc [:input {:type :checkbox}])
    (vm-observe model (.lifetime it) true
                (fn [^Lifetime lifetime old-value new-value]
                  (jqProp it "checked" (if new-value "true" ""))))
    (set-event-handler "change" it
                       (fn [& {:keys [new-state]}]
                         (vm-set model (= new-state "true")))
                       :callback-data {:new-state (str "' + encodeURIComponent($(this).prop('checked')) + '")})))
