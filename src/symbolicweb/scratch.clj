(in-ns 'symbolicweb.core)


(defn synced-container-models-with-filtering []
  (dosync
   (let [common (with1 (make-ContainerModel)
                  (append-container-model it (make-ContainerModelNode (vm 4)))
                  (append-container-model it (make-ContainerModelNode (vm 5))))
         odd-only (sync-ContainerModel common #(odd? @(cmn-data %2)))
         even-only (sync-ContainerModel common #(even? @(cmn-data %2)))
         node-zero (make-ContainerModelNode (vm 0))]

     (append-container-model common node-zero)
     (append-container-model common (make-ContainerModelNode (vm 1)))
     (append-container-model common (make-ContainerModelNode (vm 2)))
     (append-container-model common (make-ContainerModelNode (vm 3)))

     ;; TODO: Ok, this is where the tricky part is. Do I monitor all values then re-apply
     ;; filtering on change via r-c-m-n and a-c-m (or similar; depending on ordering..)?
     ;; Let's think about what a change of either value or even the filter itself might mean:
     ;;
     ;;   * It might mean the value must be added to a ContainerModel where it did not exist before.
     ;;   * It might mean the value must be removed from a ContainerModel where it did exist before.
     ;;
     ;; Removing is quite trivial, and to add properly with regards to positioning we need the "outer" node.
     ;;
     ;; Ok,for the "outer" ContainerModel, I'll need

     (vm-set (cmn-data node-zero) 7)
     (remove-container-model-node node-zero)
     (append-container-model common (make-ContainerModelNode (cmn-data node-zero)))

     (println "COMMON")
     (loop [node (head-node common)]
       (when node
         (println @(cmn-data node))
         (recur (right-node node))))

     (println "\nODD-ONLY")
     (loop [node (head-node odd-only)]
       (when node
         (println @(cmn-data node))
         (recur (right-node node))))

     (println "\nEVEN-ONLY")
     (loop [node (head-node even-only)]
       (when node
         (println @(cmn-data node))
         (recur (right-node node)))))))












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
