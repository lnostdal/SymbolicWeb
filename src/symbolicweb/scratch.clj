(in-ns 'symbolicweb.core)


;;;; SQUARE-OF-X!
;;;;;;;;;;;;;;;;;

(do
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

(dosync
 (clear-root)
 (jqAppend (root-element)
           (with-html-container
             [:h2 "Header.."]
             (sw (make-HTMLElement "p" "test-a"))
             (sw (make-HTMLElement "b" "test-b"))
             [:h2 "..footer."])))


(dosync
 (clear-root)
 (jqAppend (root-element)
           (with-html-container
             [:em (sw (with-html-container
                        [:p "nested html"]
                        (sw (make-HTMLElement "p" "nested widget"))))]
             [:b (sw (make-HTMLElement "p" "non-nested widget 1"))]
             [:b (sw (make-HTMLElement "p" "non-nested widget 2"))])))



(dosync
 (clear-root)
 (jqAppend (root-element)
           (with-html-container
             [:em
              (sw (make-HTMLElement "p" "test-a"))
              (sw (make-HTMLElement "p" "test-b"))
              (sw (with-html-container
                    [:b
                     (sw (make-HTMLElement "p" "test-c"))
                     (sw (make-HTMLElement "p" "test-d"))]))])))



(dosync
 (clear-root)
 (jqAppend (root-element)
           (with1 (make-Container "em")
             (add-branch it (make-HTMLElement "p" "nested-a"))
             (add-branch it (make-HTMLElement "p" "nested-b"))
             (add-branch it (with1 (make-Container "b")
                              (add-branch it (make-HTMLElement "p" "nested-c"))
                              (add-branch it (make-HTMLElement "p" "nested-d")))))))





;           (set-children! widget *html-container-accu-children*)
;           (doseq [child *html-container-accu-children*]
;             (set-parent! child widget)
;             (when (:viewport widget-m)
;               (ensure-visible child widget)))))
