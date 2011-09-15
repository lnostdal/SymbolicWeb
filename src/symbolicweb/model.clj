(in-ns 'symbolicweb.core)


(derive ::ValueModel ::Model)
(let [notify-views-fn (fn [model new-value]
                        (doseq [view (ensure (:views model))]
                          (let [view-m @view
                                new-value ((:output-parsing-fn view-m) new-value)]
                            ((:handle-model-event-fn view-m) view new-value))))]
  (defn make-ValueModel [value-ref]
    {:type ::ValueModel
     :value value-ref
     :views (ref #{})
     :notify-views-fn notify-views-fn}))


(defn vm [initial-value]
  "Creates a ValueModel. INITIAL-VALUE will be wrapped in a Ref."
  (make-ValueModel (ref initial-value)))


(defn get-value [value-model]
  (assert (= ::ValueModel (:type value-model)))
  @(:value value-model))


(defn set-value [value-model new-value]
  (assert (= ::ValueModel (:type value-model)))
  (ref-set (:value value-model) new-value)
  ((:notify-views-fn value-model) value-model new-value))


(defn alter-value [value-model fn & args]
  (assert (= ::ValueModel (:type value-model)))
  (apply alter (:value value-model) fn args)
  ((:notify-views-fn value-model) value-model (get-value value-model)))
