(in-ns 'symbolicweb.core)


(defn synced-container-models-with-filtering []
  (dosync
   (let [common (make-ContainerModel)
         odd-only (add-view common (make-ContainerModelProxy
                                    container-model :filter-node-fn #(odd? (node-data %2))))
         even-only (add-view common (make-ContainerModelProxy
                                     container-model :filter-node-fn #(even? (node-data %2))))
         obj-0 (vm 0)
         obj-1 (vm 1)
         obj-2 (vm 2)
         obj-3 (vm 3)]
     (append-container-model common (make-ContainerModelNode obj-0))
     (append-container-model common (make-ContainerModelNode obj-1))
     (append-container-model common (make-ContainerModelNode obj-2))
     (append-container-model common (make-ContainerModelNode obj-3))
     common)))

















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
