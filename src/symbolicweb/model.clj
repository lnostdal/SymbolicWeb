(in-ns 'symbolicweb.core)

;; MVC core and persistence (DB) abstraction
;; -----------------------------------------
;;
;; TODO: Foreign keys. This should be easy, and fun.
;; TODO: send-off -> with-errors-logged -> with-sw-db is repeated several times.


(declare mk-view ref?)


(defprotocol IModel
  (add-view [vm view])
  (remove-view [vm view]))

(defprotocol IValueModel
  (%vm-inner-ref [vm]))


(deftype ValueModel [^clojure.lang.Ref value-ref
                     views
                     notify-views-fn]
  clojure.lang.IDeref
  (deref [_]
    (ensure value-ref))

  IValueModel
  (%vm-inner-ref [_]
    value-ref)

  IModel
  (add-view [_ view]
    (alter views conj view))

  (remove-view [_ view]
    (alter views disj view)))

(defmethod print-method ValueModel [value-model stream]
  (print-method (. value-model %vm-inner-ref) stream))

(defn make-ValueModel [^clojure.lang.Ref value-ref]
  "Creates a ValueModel wrapping VALUE-REF."
  (assert (ref? value-ref))
  (ValueModel. value-ref
               (ref #{})
               (fn [value-model old-value new-value]
                 (doseq [view (ensure (. value-model views))]
                   (let [view-m (ensure view)
                         new-value ((:output-parsing-fn view-m) new-value)]
                     ((:handle-model-event-fn view-m) view old-value new-value))))))

(defn vm [initial-value]
  "Creates a ValueModel. INITIAL-VALUE will be wrapped in a Ref."
  (make-ValueModel (ref initial-value)))

(defn get-value [^ValueModel value-model]
  "[DEPRECATED] Returns the value held in VALUE-MODEL. It will be safe from write-skew (STM)."
  (assert (isa? (type value-model) ValueModel))
  @value-model)

(defn set-value [^ValueModel value-model new-value]
  "Sets VALUE-MODEL to NEW-VALUE and notifies Views of VALUE-MODEL of the change.
Note that the Views are only notified when NEW-VALUE isn't = to the old value of VALUE-MODEL. This (read of the old value) is safe with regards to
write-skew (STM)."
  (assert (isa? (type value-model) ValueModel))
  (let [old-value @value-model]
    (when-not (= old-value new-value)
      (ref-set (%vm-inner-ref value-model) new-value)
      ((. value-model notify-views-fn) value-model old-value new-value)))
  new-value)

(defn vm-set [^ValueModel value-model new-value]
  (set-value value-model new-value))

(defn alter-value [^ValueModel value-model fn & args]
  "Alters (calls clojure.core/alter on) VALUE-MODEL using FN and ARGS and notifies Views of VALUE-MODEL of the change.
Note that the Views are only notified when the resulting value of FN and ARGS wasn't = to the old value of VALUE-MODEL."
  (assert (isa? (type value-model) ValueModel))
  (let [old-value @value-model]
    (apply alter (%vm-inner-ref value-model) fn args)
    (let [new-value @value-model]
      (when-not (= old-value new-value)
        ((. value-model notify-views-fn) value-model old-value new-value)))))

(defn vm-alter [^ValueModel value-model fn & args]
  (apply alter-value value-model fn args))

(defn vm-copy [^ValueModel value-model]
  "Creates a ValueModel. The initial value of it will be extracted from SOURCE-VM. Further changes (mutation of) to SOURCE-VM will not affect the
ValueModel created and returned here."
  (assert (isa? (type value-model) ValueModel))
  (vm @value-model))


;; TODO: Watch multiple values.
(defn vm-sync [^ValueModel value-model callback]
  "Returns a new ValueModel which is kept in sync with VALUE-MODEL via F.
F takes the same arguments as the MK-VIEW callback; NEW-VALUE can be referred to using %3."
  (let [mid (vm nil)]
    (mk-view value-model nil #(vm-set mid (apply callback %&)))
    mid))


(defn vm-syncs [value-models callback]
  (let [mid (vm nil)]
    (with-local-vars [once? false] ;; We only want to trigger an initial update once.
      (dorun (map (fn [value-model]
                    (mk-view value-model nil #(vm-set mid (apply callback %&))
                             :trigger-initial-update? (when-not (var-get once?)
                                                        (var-set once? true)
                                                        true)))
                  value-models)))
    mid))


#_(defn vm-syncs-test []
  (dosync
   (let [x (vm 0)
         y (vm 0)
         vms (vm-syncs [x y] (fn [v o n]
                               (dbg-prin1 [@x @y])))]
     (println "---")
     (vm-set x 1)
     (println "---")
     (vm-set y 1))))