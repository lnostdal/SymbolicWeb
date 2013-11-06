(in-ns 'symbolicweb.core)



(defprotocol IObservable
  (add-observer [observable observer]
    "Returns OBSERVER if OBSERVER was added or false if OBSERVER had already been added before.")

  (remove-observer [observable observer]
    "Returns OBSERVER if OBSERVER was removed or false if OBSERVER was not found to be an observer of OBSERVABLE."))



(deftype Observable [^Ref observers ;; #{}
                     ^Fn notify-observers-fn]
  IObservable
  (add-observer [_ observer]
    (if (contains? (ensure observers) observer)
      false
      (do
        (alter observers conj observer)
        observer)))

  (remove-observer [_ observer]
    (if (contains? (ensure observers) observer)
      (do
        (alter observers disj observer)
        observer)
      false)))
