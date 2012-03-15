(in-ns 'symbolicweb.core)


(try
  (dosync
   (let [c (atom 0)
         x (vm 0)]
     (with-observed-vms nil
       (dosync
        (swap! c inc)
        (vm-set x (+ @x 1))))
     (assert (= @x 1))
     (assert (= @c 1))))
  (catch Throwable e
    (clojure.stacktrace/print-stack-trace e)))



(try
  (dosync
   (let [c (atom 0)
         x (vm 0)
         y (vm 0)]
     (with-observed-vms nil
       (dosync
        (swap! c inc)
        (vm-set x (+ @x 1))))
     (with-observed-vms nil
       (dosync
        (swap! c inc)
        (vm-set x (+ @y 1))))
     (vm-set x 10)
     (vm-set y 20)
     (assert (= @x 22))
     (assert (= @y 20))
     (assert (= @c 5))))
  (catch Throwable e
    (clojure.stacktrace/print-stack-trace e)))



(try
  (dosync
   (let [c (atom 0)
         x (vm false)
         y (vm nil)]
     (with-observed-vms nil
       (when @x
         @y
         (swap! c inc)))
     (assert (= 0 @c))
     (vm-set y "y1")
     (assert (= 0 @c))
     (vm-set x true)
     (assert (= 1 @c))
     (vm-set y "y2")
     (assert (= 2 @c))
     (vm-set x false)
     (assert (= 2 @c))
     (vm-set x true)
     (assert (= 3 @c))))
  (catch Throwable e
    (clojure.stacktrace/print-stack-trace e)))
