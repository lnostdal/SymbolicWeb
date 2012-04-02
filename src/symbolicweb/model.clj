(in-ns 'symbolicweb.core)

(declare mk-view ref? observe)


(def ^:dynamic *observed-vms-ctx*)
(def ^:dynamic *observed-vms-active-body-fns* #{})


;; TODO: Rename to ADD-OBSERVER, REMOVE-OBSERVER and GET-OBSERVERS?
(defprotocol IModel
  (add-view [model view])
  (remove-view [model view])
  (get-views [model])
  (notify-views [model args]))


(defprotocol IValueModel
  (vm-set [vm new-value]) ;; Get is DEREF or @ (via clojure.lang.IDeref).
  (%vm-ref [vm]))


(defn %vm-deref [value-model value-ref]
  (let [return-value (ensure value-ref)]
    (when (and (thread-bound? #'*observed-vms-ctx*)
               (not (get (ensure (:vms *observed-vms-ctx*)) value-model))) ;; Not already observed?
      (alter (:vms *observed-vms-ctx*) conj value-model)
      (let [observed-vms-ctx *observed-vms-ctx*]
        (observe value-model (:lifetime observed-vms-ctx) false
                 (fn [& _]
                   (when-not (get *observed-vms-active-body-fns* (:body-fn observed-vms-ctx))
                     (binding [*observed-vms-ctx* observed-vms-ctx
                               *observed-vms-active-body-fns* (conj *observed-vms-active-body-fns*
                                                                    (:body-fn observed-vms-ctx))]
                       ((:body-fn observed-vms-ctx))))))))
    return-value))



(deftype ValueModel [^:unsynchronized-mutable value-ref
                     ^:unsynchronized-mutable views-ref
                     ^:unsynchronized-mutable %notify-views-fn]
  clojure.lang.IDeref
  (deref [value-model]
    (%vm-deref value-model value-ref))


  IValueModel
  (vm-set [vm new-value]
    (when *in-sw-db?*
      (assert (or *pending-prepared-transaction?*
                  *in-db-cache-get?*)
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
    (if (get (ensure views-ref) view)
      false
      (do
        (alter views-ref conj view)
        true)))

  (remove-view [_ view]
    (if (get (ensure views-ref) view)
      (do
        (alter views-ref disj view)
        true)
      false))

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


(defn vm-sync
  "Returns a new ValueModel which is kept in sync with VALUE-MODEL via CALLBACK.
CALLBACK takes a two arguments, [OLD-VALUE and NEW-VALUE], and the continious (as VALUE-MODEL changes) return value of CALLBACK
will always be the value of the returned ValueModel.
The lifetime of this connection is governed by LIFETIME and can be a View/Widget or NIL for 'infinite' lifetime (as long as
VALUE-MODEL)."
  ([^ValueModel value-model lifetime callback]
     (vm-sync value-model lifetime callback true))
  ([^ValueModel value-model lifetime callback initial-sync?]
     (let [mid (vm nil)]
       (observe value-model lifetime initial-sync?
                #(vm-set mid (callback %1 %2)))
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
