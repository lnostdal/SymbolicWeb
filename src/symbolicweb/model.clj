(in-ns 'symbolicweb.core)

;; TODO: I guess it is possible to make this thing implement the Ref interface or whatever in such a way that @some-vm can be used instead of having to do
;; (get-value some-vm).

(declare ref?)

(derive ::ValueModel ::Model)
(let [notify-views-fn (fn [model old-value new-value]
                        (doseq [view (ensure (:views model))]
                          (let [view-m @view
                                new-value ((:output-parsing-fn view-m) new-value)]
                            ((:handle-model-event-fn view-m) view old-value new-value))))]
  (defn make-ValueModel [value-ref]
    "A Model meant to hold a single value. See ContainerModel when composition is needed."
    (assert (ref? value-ref))
    {:type ::ValueModel
     :value value-ref
     :views (ref #{})
     :notify-views-fn notify-views-fn}))


(defn vm [initial-value]
  "Creates a ValueModel. INITIAL-VALUE will be wrapped in a Ref."
  (assert (not (ref? initial-value)))
  (make-ValueModel (ref initial-value)))


(defn get-value [value-model]
  "Returns the value held in VALUE-MODEL. It will be safe from write-skew (STM)."
  (assert (= ::ValueModel (:type value-model)))
  (ensure (:value value-model)))


(defn set-value [value-model new-value]
  "Sets VALUE-MODEL to NEW-VALUE and notifies Views of VALUE-MODEL of the change.
Note that the Views are only notified when NEW-VALUE isn't = to the old value of VALUE-MODEL. This (read of the old value) is safe with regards to
write-skew (STM)."
  (assert (= ::ValueModel (:type value-model)))
  (let [old-value (ensure (:value value-model))]
    (when-not (= old-value new-value)
      (ref-set (:value value-model) new-value)
      ((:notify-views-fn value-model) value-model old-value new-value)))
  new-value)


(defn alter-value [value-model fn & args]
  "Alters (calls clojure.core/alter on) VALUE-MODEL using FN and ARGS and notifies Views of VALUE-MODEL of the change.
Note that the Views are only notified when the resulting value of FN and ARGS wasn't = to the old value of VALUE-MODEL."
  (assert (= ::ValueModel (:type value-model)))
  (let [old-value @(:value value-model)] ;; TODO: Think about ensure vs. @ etc. here.
    (apply alter (:value value-model) fn args)
    (let [new-value (ensure (:value value-model))]
      (when-not (= old-value new-value)
        ((:notify-views-fn value-model) value-model old-value new-value)))))


(defn vm-copy [source-vm]
  "Creates a ValueModel. The initial value of it will be extracted from SOURCE-VM. Further changes (mutation of) to SOURCE-VM will not affect the
ValueModel created and returned here."
  (assert (isa? ::ValueModel (:type source-vm)))
  (vm (get-value source-vm)))