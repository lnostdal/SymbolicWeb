(in-ns 'symbolicweb.core)

(declare mk-view ref?)


;; TODO: Rename to ADD-OBSERVER, REMOVE-OBSERVER and GET-OBSERVERS?
(defprotocol IModel
  (add-view [model view])
  (remove-view [model view])
  (get-views [model])
  (notify-views [model args]))


(defprotocol IValueModel
  (vm-set [vm new-value]) ;; Get is DEREF or @ (via clojure.lang.IDeref).
  (%vm-ref [vm]))


(deftype ValueModel [^:unsynchronized-mutable value-ref
                     ^:unsynchronized-mutable views-ref
                     ^:unsynchronized-mutable %notify-views-fn]
  clojure.lang.IDeref
  (deref [_]
    (ensure value-ref))


  IValueModel
  (vm-set [vm new-value]
    (when *in-sw-db?*
      (assert *pending-prepared-transaction?*
              "ValueModel: Mutation of ValueModel within WITH-SW-DB not allowed while DB transaction is not held (HOLDING-TRANSACTION)."))
    (let [old-value (ensure value-ref)]
      (when-not (= old-value new-value)
        (ref-set value-ref new-value)
        (notify-views vm [old-value new-value])))
    new-value)

  (%vm-ref [_]
    value-ref)


  IModel
  (add-view [_ view]
    (alter views-ref conj view))

  (remove-view [_ view]
    (alter views-ref disj view))

  (get-views [_]
    (ensure views-ref))

  (notify-views [vm args]
    (apply %notify-views-fn vm args)))


(defmethod print-method ValueModel [^ValueModel value-model stream]
  (print-method (%vm-ref value-model) stream))



(defn vm [value]
  (ValueModel. (ref value)
               (ref #{})
               (fn [^ValueModel value-model old-value new-value]
                 (doseq [view (get-views value-model)]
                   (let [view-m @view
                         new-value ((:output-parsing-fn view-m) new-value)]
                     (when-not (= old-value new-value) ;; After translation via :OUTPUT-PARSING-FN.
                       ((:handle-model-event-fn view-m) view old-value new-value)))))))


(defn vm-alter [^ValueModel value-model fn & args]
  (vm-set value-model (apply fn @value-model args)))


(defn vm-copy [^ValueModel value-model]
  "Creates a ValueModel. The initial value of it will be extracted from SOURCE-VM. Further changes (mutation of) to SOURCE-VM will
not affect the ValueModel created and returned here.
See VM-SYNC if you need a copy that is synced with the original VALUE-MODEL."
  (vm @value-model))


(defn vm-sync [^ValueModel value-model lifetime callback]
  "Returns a new ValueModel which is kept in sync with VALUE-MODEL via CALLBACK.
CALLBACK takes the same arguments as the MK-VIEW callback, [VM OLD-VALUE NEW-VALUE], and the return value of CALLBACK will be the
the returned ValueModel."
  (let [mid (vm nil)]
    (mk-view value-model lifetime #(vm-set mid (apply callback %&)))
    mid))


(defn vm-syncs [value-models lifetime callback]
  (let [mid (vm nil)]
    (with-local-vars [once? false] ;; We only want to trigger an initial update once.
      (doseq [value-model value-models]
        (mk-view value-model lifetime #(vm-set mid (apply callback %&))
                 :trigger-initial-update? (when-not (var-get once?)
                                            (var-set once? true)
                                            true))))
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




#_(defn dosync-and-finally []
  (let [r (ref 0)]
    (letfn [(test []
              (dosync
               (try
                 (println "blah")
                 (alter r inc)
                 (Thread/sleep 500)
                 (finally (println "i'm always here")))))]
      (let [f (future (test))]
        (test)
        @f)
      @r)))