(in-ns 'symbolicweb.core)

(declare ref? observe %vm-deref)



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
  "  LIFETIME: If given an instance of Lifetime, observation will start once that Lifetime is activated and last until it is
deactivated. If given FALSE, observation will start at once and last forever; as long as VALUE-MODEL exists.

  INITIAL-SYNC?: If TRUE, CALLBACK will be triggered once as soon as observation starts.

  CALLBACK: (fn [inner-lifetime old-value new-value] ..)

Returns a (new) instance of Lifetime if LIFETIME was an instance of Lifetime, or FALSE otherwise. This is also the value passed
as the first argument to CALLBACK."
  (let [observe-res (observe (.observable value-model) lifetime callback)]
    (when initial-sync?
      (letfn [(initial-sync []
                (callback observe-res ::initial-update @value-model))]
        (if observe-res
          (add-lifetime-activation-fn observe-res (fn [_] (initial-sync)))
          (initial-sync))))
    observe-res))



(defn %vm-deref [^ValueModel value-model ^clojure.lang.Ref value]
  (do1 (ensure value)
    (when (and *observed-vms-ctx*
               (not (get (ensure (:vms *observed-vms-ctx*)) value-model))) ;; Not already observed?
      (alter (:vms *observed-vms-ctx*) conj value-model)
      (let [observed-vms-ctx *observed-vms-ctx*]
        (vm-observe value-model (:lifetime observed-vms-ctx) true
                    (fn [& _]
                      ;; Avoid infinite recursion; body-fn triggering a change that leads back to the same body-fn.
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
                                    (observer-fn old-value new-value)))))))



(defn vm-alter [^ValueModel value-model ^clojure.lang.Fn fn & args]
  (vm-set value-model (apply fn @value-model args)))



(defn ^ValueModel vm-copy [^ValueModel value-model]
  "Creates a ValueModel. The initial value of it will be extracted from VALUE-MODEL. Further changes (mutation of) to
VALUE-MODEL will not affect the ValueModel created and returned here.
See VM-SYNC if you need a copy that is synced with the original VALUE-MODEL."
  (vm @value-model))



(defn ^ValueModel vm-sync
  "Returns a new ValueModel which is kept in sync with VALUE-MODEL via CALLBACK.
  CALLBACK: Takes the same arguments as CALLBACK for VM-OBSERVE does, and the return value of CALLBACK will be the continious
value of the returned ValueModel.
  LIFETIME: The lifetime of this connection is governed by LIFETIME and can be an instance of Lifetime or NIL for 'infinite'
lifetime (as long as VALUE-MODEL exists)."
  ([^ValueModel value-model lifetime ^clojure.lang.Fn callback]
     (vm-sync value-model lifetime callback true))

  ([^ValueModel value-model lifetime ^clojure.lang.Fn callback ^Boolean initial-sync?]
     (let [^ValueModel mid (vm nil)]
       (vm-observe value-model lifetime initial-sync?
                   #(vm-set mid (apply callback %&)))
       mid)))



(defn ^ValueModel vm-syncs
  "CALLBACK takes no arguments."
  ([value-models lifetime ^clojure.lang.Fn callback]
     (vm-syncs value-models lifetime callback true))

  ([value-models lifetime ^clojure.lang.Fn callback ^Boolean initial-sync?]
     (let [^ValueModel mid (vm nil)]
       (with-local-vars [already-synced? false] ;; We only want to trigger an initial sync once if at all.
         (doseq [^ValueModel value-model value-models]
           (vm-observe value-model lifetime (if (and initial-sync? (not (var-get already-synced?)))
                                              (do
                                                (var-set already-synced? true)
                                                true)
                                              false)
                       (fn [_ &] (vm-set mid (callback))))))
       mid)))



(defn %with-observed-vms [lifetime ^clojure.lang.Fn body-fn]
  (binding [*observed-vms-ctx* {:vms (ref #{})
                                :lifetime lifetime
                                :body-fn body-fn}
            *observed-vms-active-body-fns* (conj *observed-vms-active-body-fns* body-fn)]
    (body-fn)))


;; TODO: RETURN-VM ..?
(defmacro with-observed-vms [lifetime & body]
  `(%with-observed-vms ~lifetime (fn [] ~@body)))
