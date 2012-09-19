(in-ns 'symbolicweb.core)

(declare mk-view ref? observe %vm-deref)



(defprotocol IValueModel
  (vm-set [vm new-value])) ;; Get is DEREF or @ (via clojure.lang.IDeref).


(deftype ValueModel [^clojure.lang.Ref value
                     ^Observable observable]
  clojure.lang.IDeref
  (deref [value-model]
    (%vm-deref value-model value))


  IValueModel
  (vm-set [value-model new-value]
    (when *in-sw-db?*
      (assert (or *pending-prepared-transaction?*
                  *in-db-cache-get?*)
              "ValueModel: Mutation of ValueModel within WITH-SW-DB not allowed while DB transaction is not held (HOLDING-TRANSACTION)."))
    (let [old-value (ensure value)]
      (ref-set value new-value)
      (notify-observers observable old-value new-value))
    new-value))



(defmethod print-method ValueModel [^ValueModel value-model stream]
  (print-method (.value value-model) stream))



(defn vm-observe [^ValueModel value-model lifetime ^Boolean initial-sync? ^clojure.lang.Fn callback]
  "  LIFETIME: An instance of Lifetime, or NIL/FALSE which denotes an infinite lifetime.
  INITIAL-SYNC?: If true, CALLBACK will be called when LIFETIME is activated (i.e., no change in VALUE-MODEL is needed). If
  LIFETIME is NIL, CALLBACK will be called instantly.
  CALLBACK: (fn [new-value old-value observer-fn observable] ..) where OBSERVER-FN can be sent to REMOVE-OBSERVER.

Returns a (new) instance of Lifetime if LIFETIME was an instance of Lifetime, or FALSE otherwise."
  (let [observe-res (observe (.observable value-model) lifetime callback)]
    (when initial-sync?
      (letfn [(do-it []
                (callback @value-model ::-initial-sync- callback (.observable value-model)))]
        (if observe-res
          (add-lifetime-activation-fn observe-res (fn [_] (do-it)))
          (do-it))))
    observe-res))



(defn %vm-deref [^ValueModel value-model ^clojure.lang.Ref value]
  (do1 (ensure value)
    (when (and *observed-vms-ctx*
               (not (get (ensure (:vms *observed-vms-ctx*)) value-model))) ;; Not already observed?
      (alter (:vms *observed-vms-ctx*) conj value-model)
      (let [observed-vms-ctx *observed-vms-ctx*]
        (vm-observe value-model (:lifetime observed-vms-ctx) false
                    (fn [& _]
                      (when-not (get *observed-vms-active-body-fns* (:body-fn observed-vms-ctx))
                        (binding [*observed-vms-ctx* observed-vms-ctx
                                  *observed-vms-active-body-fns* (conj *observed-vms-active-body-fns*
                                                                       (:body-fn observed-vms-ctx))]
                          ((:body-fn observed-vms-ctx))))))))))



(defn vm ^ValueModel [value]
  (ValueModel. (ref value)
               (mk-Observable (fn [^Observable observable old-value new-value]
                                (when-not (= old-value new-value) ;; TODO: = is a magic value.
                                  (doseq [^clojure.lang.Fn observer-fn (ensure (.observers observable))]
                                    (observer-fn new-value old-value observer-fn observable)))))))



(defn vm-alter [^ValueModel value-model fn & args]
  (vm-set value-model (apply fn @value-model args)))



(defn vm-copy [^ValueModel value-model]
  "Creates a ValueModel. The initial value of it will be extracted from VALUE-MODEL. Further changes (mutation of) to
VALUE-MODEL will not affect the ValueModel created and returned here.
See VM-SYNC if you need a copy that is synced with the original VALUE-MODEL."
  (vm @value-model))



(defn vm-sync
  "Returns a new ValueModel which is kept in sync with VALUE-MODEL via CALLBACK.
  CALLBACK: Takes the same arguments as CALLBACK for VM-OBSERVE does, and the return value of CALLBACK will be the continious
value of the returned ValueModel.
  LIFETIME: The lifetime of this connection is governed by LIFETIME and can be an instance of Lifetime or NIL for 'infinite'
lifetime (as long as VALUE-MODEL exists)."
  (^ValueModel [^ValueModel value-model lifetime callback]
     (vm-sync value-model lifetime callback true))
  (^ValueModel [^ValueModel value-model lifetime callback ^Boolean initial-sync?]
     (let [mid (vm nil)]
       (vm-observe value-model lifetime initial-sync?
                   #(vm-set mid (apply callback %&)))
       mid)))



(defn vm-syncs
  "CALLBACK takes no arguments.
The lifetime of these connections are governed by LIFETIME and can be a View/Widget or NIL for 'infinite' lifetime (as long as
VALUE-MODEL)."
  ([value-models lifetime callback]
     (vm-syncs value-models lifetime callback true))
  ([value-models lifetime callback initial-sync?]
     (let [mid (vm nil)]
       (with-local-vars [already-synced? false] ;; We only want to trigger an initial sync once.
         (doseq [value-model value-models]
           (check-type value-model ValueModel)
           (observe value-model lifetime (when initial-sync?
                                           (when-not (var-get already-synced?)
                                             (var-set already-synced? true)
                                             true))
                    (fn [_ _] (vm-set mid (callback))))))
       mid)))



(defn %with-observed-vms [lifetime body-fn]
  (binding [*observed-vms-ctx* {:vms (ref #{})
                                :lifetime lifetime
                                :body-fn body-fn}
            *observed-vms-active-body-fns* (conj *observed-vms-active-body-fns* body-fn)]
    (body-fn)))

(defmacro with-observed-vms [lifetime & body]
  `(%with-observed-vms ~lifetime (fn [] ~@body)))
