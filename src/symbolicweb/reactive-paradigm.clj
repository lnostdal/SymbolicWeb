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




#_(try
  (dosync
   (let [x (vm 0)]
     (add-observer (.observable x)
                   (fn [observer-fn observable old-value new-value]
                     (dbg-prin1 [old-value new-value])))
     (vm-set x 42)))
  (catch Throwable e
    (clojure.stacktrace/print-stack-trace e)))
;; [old-value new-value] => [0 42]
;; 42
