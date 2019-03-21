(in-ns 'symbolicweb.core)


;;; Reactive programming
;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; https://en.wikipedia.org/wiki/Reactive_programming



(defn clear-observers [^Observable observable]
  (ref-set (.observers observable) #{}))



(defn ^Observable mk-Observable [^Fn notify-observers-fn]
  (Observable. (ref #{}) ;; OBSERVERS
               notify-observers-fn))



(defn notify-observers [^Observable observable & args]
  (apply (.notify-observers-fn observable) observable args))



(def ^:dynamic *observables-stack* #{})



(defn observe "LIFETIME: If given an instance of Lifetime, observation will start once that Lifetime is activated and last until it is
  deactivated. If given FALSE, observation will start at once and last forever; as long as OBSERVABLE exists.

  CALLBACK: (fn [inner-lifetime & args] ..), where INNER-LIFETIME may be an instance of Lifetime or FALSE.

  Returns a (new) instance of Lifetime if LIFETIME was an instance of Lifetime, or FALSE otherwise. This is also the value passed
  as the first argument to CALLBACK."
  ;; 3 choices:
  ;;   * Allow circular references fully, which might lead to infinite loops in some cases; so it's not really "allowed" anyway.
  ;;   * Allow circular refeneces "partially"; let it recurse up until some limit then, say, show a warning or similar.
  ;;   * Disallow circular references.
  ;;
  ;; I've been playing around with these options, and it seems the first option is risky as it might lead to infinite loops and
  ;; stack overflows. The second option means stuff will break "sometimes". The third option is simple; things will always fail
  ;; early.
  ^Lifetime [^Observable observable lifetime ^Fn callback]
  (let [callback (fn [& args]
                   (if (contains? *observables-stack* observable)
                     (throw (Exception. (str "OBSERVE: Circular reference; bailing out: " observable)))
                     (binding [*observables-stack* (conj *observables-stack* observable)]
                       (apply callback args))))]
    (if lifetime
      (let [inner-lifetime (mk-Lifetime)
            callback (partial callback inner-lifetime)]
        (add-lifetime-activation-fn inner-lifetime (fn [_] (add-observer observable callback)))
        (add-lifetime-deactivation-fn inner-lifetime (fn [_] (remove-observer observable callback)))
        (attach-lifetime lifetime inner-lifetime)
        inner-lifetime)
      (do
        (add-observer observable (partial callback false))
        false))))








#_(try
    (dosync
     (let [x (vm 0)]
       (vm-observe x nil false (fn [lifetime old-value new-value]
                                 (dbg [new-value old-value])))
       (vm-set x 42)))
    (catch Throwable e
      (clojure.stacktrace/print-stack-trace e)))
;; [new-value old-value] => [42 0]
;; 42


#_(dosync
   (let [x (vm 0)
         squared-x (with-observed-vms nil
                     (* @x @x))] ;;(vm-sync x nil (fn [x & _] (* x x)))]
     (println [@x @squared-x])
     (vm-set x 2)
     (println [@x @squared-x])))





;;; Playing around with "functional" stuff here.
;;
;;
                                        ;


;;(def -symbolicweb-world- (agent {}))


#_(defn sw-notify-observers [world ks k v old-value]
    "Returns WORLD transformed."
    ;; Look up observers in WORLD via [KS K]. An observer is a vector of FNs.
    ;; TODO: Is it possible to serialize the FNs somehow? I guess the [KS K] lookup will lead to code doing the same thing
    ;; for each SW server restart. ...or, there's: https://github.com/technomancy/serializable-fn
    )


#_(defn sw-update [world ks k v]
    "Returns WORLD transformed."
    (let [old-value (with (get-in world ks ::not-found)
                      (if (= ::not-found it)
                        ::initial-update
                        (get it k ::initial-update)))]
      (sw-notify-observers (update-in world ks
                                      assoc k v)
                           ks k v old-value)))


#_(defn do-sw-update [ks k v]
    (send -symbolicweb-world- sw-update ks k v))
