(in-ns 'symbolicweb.core)


;; TODO: Macro this shit.

(defn mk-p [model]
  (make-HTMLElement "p" model))

(defn sw-p [model]
  (sw (mk-p model)))


(defn mk-b [model]
  (make-HTMLElement "b" model))

(defn sw-b [model]
  (sw (mk-b model)))


(defn mk-h1 [model]
  (make-HTMLElement "h1" model))

(defn sw-h1 [model]
  (sw (mk-h1 model)))


(defn mk-h2 [model]
  (make-HTMLElement "h2" model))

(defn sw-h2 [model]
  (sw (mk-h2 model)))
