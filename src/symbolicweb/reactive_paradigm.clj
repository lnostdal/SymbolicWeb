(in-ns 'symbolicweb.core)


;;; Reactive programming
;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; http://en.wikipedia.org/wiki/Reactive_programming


(defprotocol IObservable
  (add-observer [observable observer]
    "Returns OBSERVER if OBSERVER was added or false if OBSERVER had already been added before.")

  (remove-observer [observable observer]
    "Returns OBSERVER if OBSERVER was removed or false if OBSERVER was not found to be an observer of OBSERVABLE."))



(deftype Observable [^clojure.lang.Ref observers ;; #{}
                     ^clojure.lang.Fn notify-observers-fn]
  IObservable
  (add-observer [_ observer]
    (if (get (ensure observers) observer)
      false
      (do
        (alter observers conj observer)
        observer)))

  (remove-observer [_ observer]
    (if (get (ensure observers) observer)
      (do
        (alter observers disj observer)
        observer)
      false)))



(defn mk-Observable ^Observable [^clojure.lang.Fn notify-observers-fn]
  (Observable. (ref #{}) ;; OBSERVERS
               notify-observers-fn))



(defn notify-observers [^Observable observable & args]
  (apply (.notify-observers-fn observable) observable args))



(defn observe [^Observable observable lifetime ^clojure.lang.Fn callback]
  "  LIFETIME: An instance of Lifetime, or NIL/FALSE which denotes an infinite lifetime.
  CALLBACK: (fn [observable & args] ..)

Returns a (new) instance of Lifetime if LIFETIME was an instance of Lifetime, or FALSE otherwise."
  (letfn [(do-it []
            (add-observer observable callback))]
    (if lifetime
      (let [our-lifetime (mk-Lifetime)]
        (add-lifetime-activation-fn our-lifetime (fn [_] (do-it)))
        (add-lifetime-deactivation-fn our-lifetime (fn [_] (remove-observer observable callback)))
        (attach-lifetime lifetime our-lifetime)
        our-lifetime)
      (do
        (do-it)
        false))))



#_(try
  (dosync
   (let [x (vm 0)]
     (vm-observe x nil false (fn [new-value old-value & _]
                               (dbg-prin1 [new-value old-value])))
     (vm-set x 42)))
  (catch Throwable e
    (clojure.stacktrace/print-stack-trace e)))
;; [new-value old-value] => [42 0]
;; 42


#_(dosync
 (let [x (vm 0)
       squared-x (with-observed-vms nil
                   (* @x @x))] ;;(vm-sync x nil (fn [x & _] (* x x)))]
   (dbg-prin1 [x squared-x])
   (vm-set x 2)
   (dbg-prin1 [x squared-x])))





;;; Playing around with "functional" stuff here.
;;
;;
;


(def -symbolicweb-world- (agent {}))


(defn sw-notify-observers [world ks k v old-value]
  "Returns WORLD transformed."
  ;; Look up observers in WORLD via [KS K]. An observer is a vector of FNs.
  ;; TODO: Is it possible to serialize the FNs somehow? I guess the [KS K] lookup will lead to code doing the same thing
  ;; for each SW server restart. ...or, there's: https://github.com/technomancy/serializable-fn
  )


(defn sw-update [world ks k v]
  "Returns WORLD transformed."
  (let [old-value (with (get-in world ks ::not-found)
                    (if (= ::not-found it)
                      ::initial-update
                      (get it k ::initial-update)))]
    (sw-notify-observers (update-in world ks
                                    assoc k v)
                         ks k v old-value)))


(defn do-sw-update [ks k v]
  (send -symbolicweb-world- sw-update ks k v))
