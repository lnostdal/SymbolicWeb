(in-ns 'symbolicweb.core)


;; * Operations can happen either directly (explicitly) when done in context of the "outer" (observed) ContainerModel,
;;   or they can happen "implicitly" when the CMN VSs change.
;;
;; * Check the FILTER-FNs for the FilteredContainerModels the Node is already a part of and remove it from each now returning
;;   false.
;;
;; * Check the FILTER-FNs for the FilteredContainerModels the Node is not already part of and add it to each now returning
;;   true. ...forgot this. Hm.
;;
;; I guess these things can happen in observers held in each FilteredContainerModel.


;; Another scenario is the actual filter changing, but I think I might as well create a new FilteredContainerModel then(?).


(defn filtered-container-model-test []
  (dosync
   (let [common (with1 (mk-ContainerModel)
                  (let [lifetime-root (mk-LifetimeRoot)]
                    (do-lifetime-activation lifetime-root)
                    (attach-lifetime lifetime-root (.lifetime it)))
                  #_(cm-append it (cmn (vm 4)))
                  #_(cm-append it (cmn (vm 5))))
         odd-only (mk-FilteredContainerModel common #(odd? @(cmn-data %2)))
         even-only (mk-FilteredContainerModel common #(even? @(cmn-data %2)))
         larger-than-5 (mk-FilteredContainerModel common #(> @(cmn-data %2) 5))
         less-than-5 (mk-FilteredContainerModel common #(< @(cmn-data %2) 5))
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
         (let [n (rand-int 10)]
           (println "Setting NODE-ZERO to" n)
           (vm-set (cmn-data node-zero) n)
           (print-state))
         (Thread/sleep 5000) ;; yeah, milliseconds; not seconds..
         (recur))))))










#_(defn issue-39 []
  (try
    (dosync
     (let [model (cm)
           view (make-ContainerView "div" model)]
       (observe-start view)
       (cm-append model (cmn (vm 1)))
       (cm-append model (cmn (vm 2)))
       (cm-append model (cmn (vm 3)))))
    (catch Throwable e
      (clojure.stacktrace/print-stack-trace e))))





;;;; SQUARE-OF-X!
;;;;;;;;;;;;;;;;;

#_(do
  (def x (ref 2))

  (def square-of-x (with1 (ref (* @x @x))
                          (add-watch x :x (fn [_ _ _ x-new]
                                            (dosync
                                             (ref-set it (* x-new x-new)))))))

  (dosync
   (clear-root)
   (jqAppend (root-element) (make-HTMLElement "p" square-of-x))))




;;;; HTML-CONTAINER ;;;;
;;;;;;;;;;;;;;;;;;;;;;;;

#_(dosync
 (clear-root)
 (jqAppend (root-element)
   (with-html-container
     [:h2 "Header.."]
     (sw-p "test-a")
     (sw-p "test-b")
     [:h2 "..footer."])))


#_(dosync
 (clear-root)
 (jqAppend (root-element)
   (with-html-container
     [:h2 "Header.."]
     [:em (sw (with-html-container
                [:p "nested html"]
                (sw-p "nested widget")))]
     (sw-p "non-nested widget 1")
     [:b (sw-p "non-nested widget 2")]
     [:h2 "..footer."])))


#_(dosync
 (clear-root)
 (jqAppend (root-element)
   (with-html-container
     [:em
      (sw-p "test-a")
      (sw-p "test-b")
      (sw (with-html-container
            [:b
             (sw-p "test-c")
             (sw-p "test-d")]))])))





#_(defn diff-operations [old-cnt new-cnt]
  (with-local-vars [old-elts old-cnt
                    new-elts new-cnt]
    ;; Determine remove operations.
    ;; TODO: What about cases where there are multiple elements of the same Model instance? Perhaps I need to wrap
    ;; things in Node instances anyway. Uh, or a Node might really be a ContainerModel with a single value in it?
    (doseq [old-elt (var-get old-elts)]
      (when-not (some #(= % old-elt) (var-get new-elts))
        (println (str "to remove: " old-elt))
        (var-set old-elts (into [] (remove #(= old-elt %) (var-get old-elts)))))) ;; Ugly, but done locally.

    ;; Determine exchange operations.
    (with-local-vars [already-exchanged []]
      (doseq [old-elt (var-get old-elts)
              new-elt (var-get new-elts)]
        (println (str "old-elt: " old-elt " new-elt:" new-elt))
        (when (and (not (= old-elt new-elt))
                   (not (some #(= % new-elt) (var-get already-exchanged))))
          (var-set already-exchanged (conj (var-get already-exchanged) old-elt))
          (println (str "to exchange: " old-elt " " new-elt)))))

    ;; Determine insert operations.

    ))
