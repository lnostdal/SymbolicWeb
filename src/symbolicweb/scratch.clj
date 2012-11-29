(in-ns 'symbolicweb.core)


(dosync
 (let [x (vm 2)
       y (vm-sync x nil (fn [_ _ x-value]
                          (dbg-prin1 x-value)
                          (vm-set x (* x-value x-value))))]
   (dbg-prin1 x)
   (dbg-prin1 y)))















































;; TODO: Another scenario is the actual filter changing, but I think I might as well create a new FilteredContainerModel then(?).
(defn filtered-container-model-test []
  (dosync
   (let [common (with1 (mk-ContainerModel)
                  (let [lifetime-root (mk-LifetimeRoot)]
                    (do-lifetime-activation lifetime-root)
                    (attach-lifetime lifetime-root (.lifetime it)))
                  (cm-append it (cmn (vm 4)))
                  (cm-append it (cmn (vm 5)))
                  (cm-append it (cmn (vm 6)))
                  (cm-append it (cmn (vm 7))))
         odd-only (mk-FilteredContainerModel common #(odd? @(cmn-data %)))
         even-only (mk-FilteredContainerModel common #(even? @(cmn-data %)))
         larger-than-5 (mk-FilteredContainerModel common #(> @(cmn-data %) 5))
         less-than-5 (mk-FilteredContainerModel common #(< @(cmn-data %) 5))
         node-zero (cmn (vm 0))]
     ;; Yes, I know this code sucks.
     (letfn [(print-state []
               (println "COMMON")
               (loop [node (cm-head-node common)]
                 (when node
                   (println @(cmn-data node))
                   (recur (cmn-right-node node))))

               (println "\nODD-ONLY")
               (loop [node (cm-head-node odd-only)]
                 (when node
                   (println @(cmn-data node))
                   (recur (cmn-right-node node))))

               (println "\nEVEN-ONLY")
               (loop [node (cm-head-node even-only)]
                 (when node
                   (println @(cmn-data node))
                   (recur (cmn-right-node node))))

               (println "\nLARGER-THAN-5")
               (loop [node (cm-head-node larger-than-5)]
                 (when node
                   (println @(cmn-data node))
                   (recur (cmn-right-node node))))

               (println "\nLESS-THAN-5")
               (loop [node (cm-head-node less-than-5)]
                 (when node
                   (println @(cmn-data node))
                   (recur (cmn-right-node node))))
               (println "----------\n"))]

       (cm-append common (cmn (vm 1)))
       (cm-append common (cmn (vm 2)))
       (cm-append common node-zero)
       (cm-append common (cmn (vm 3)))

       (print-state)

       (loop []
         (Thread/sleep 5000)
         (let [n (rand-int 10)]
           (println "Setting NODE-ZERO to" n)
           (vm-set (cmn-data node-zero) n)
           (print-state))
         (recur))))))
