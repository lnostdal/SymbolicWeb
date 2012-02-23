(in-ns 'symbolicweb.core)

(declare mk-view ref?)


(defprotocol IModel
  (add-view [vm view])
  (remove-view [vm view])
  (get-views [vm]))

(defprotocol IValueModel
  (vm-set [vm new-value]))


(deftype ValueModel [^:unsynchronized-mutable value-fn
                     ^:unsynchronized-mutable views
                     notify-views-fn]

  clojure.lang.IDeref
  (deref [_]
    (value-fn :get nil))

  IValueModel
  (vm-set [vm new-value]
    (let [old-value @vm]
      (when-not (= old-value new-value)
        (value-fn :set new-value)
        ((. vm notify-views-fn) vm old-value new-value)))
    new-value)

  IModel
  (add-view [_ view]
    (set! views (conj views view)))


  (remove-view [_ view]
    (set! views (disj views view)))

  (get-views [_]
    views))


(defn mk-db-value [initial-value table-name id field-name]
  (let [value (atom initial-value)]
    (fn [op new-value]
      (case op
        :get
        (do
          ;; Avoid write-skew.
          (db-stmt (str "SELECT id FROM " table-name " WHERE id = " id " FOR UPDATE;"))
          @value)

        :set
        (do
          (update-values table-name ["id = ?" id]
                         {field-name new-value})
          (reset! value new-value))))))


(defn vm [value]
  (ValueModel. (let [value (atom value)]
                 (fn [op new-value]
                   (case op
                     :get @value
                     :set (reset! value new-value))))
               #{}
               (fn [value-model old-value new-value]
                 (doseq [view (get-views value-model)]
                   (let [view-m @view
                         new-value ((:output-parsing-fn view-m) new-value)]
                     ;; TODO:  Since we're not using Refs, we can't "gather up" things to send to Viewports using calls to
                     ;; Agents anymore. In short it seems any use of Clojure STM is a bad idea here and pretty much everywhere.
                     ((:handle-model-event-fn view-m) view old-value new-value))))))


(defn vm-alter [^ValueModel value-model fn & args]
  "Alters (calls clojure.core/alter on) VALUE-MODEL using FN and ARGS and notifies Views of VALUE-MODEL of the change.
Note that the Views are only notified when the resulting value of FN and ARGS wasn't = to the old value of VALUE-MODEL."
  #_(let [old-value @value-model
        new-value (apply alter (%vm-get-inner-ref value-model) fn args)]
    (when-not (= old-value new-value)
      ((:notify-views-fn value-model) value-model old-value new-value))))


(defn vm-copy [^ValueModel value-model]
  "Creates a ValueModel. The initial value of it will be extracted from SOURCE-VM. Further changes (mutation of) to SOURCE-VM will
not affect the ValueModel created and returned here.
See VM-SYNC if you need a copy that is synced with the original VALUE-MODEL."
  (vm @value-model))


(defn vm-sync [^ValueModel value-model lifetime callback]
  "Returns a new ValueModel which is kept in sync with VALUE-MODEL via F.
F takes the same arguments as the MK-VIEW callback; NEW-VALUE can be referred to using %3."
  (let [mid (vm nil)]
    (mk-view value-model lifetime #(vm-set mid (apply callback %&)))
    mid))


(defn vm-syncs [value-models lifetime callback]
  (let [mid (vm nil)]
    (with-local-vars [once? false] ;; We only want to trigger an initial update once.
      (dorun (map (fn [value-model]
                    (mk-view value-model lifetime #(vm-set mid (apply callback %&))
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