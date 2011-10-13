(in-ns 'symbolicweb.core)


;; TODO: Macro this shit.

(defn mk-pre [model & attributes]
  (apply make-HTMLElement "pre" model attributes))


(defn mk-p [model & attributes]
  (apply make-HTMLElement "p" model attributes))

(defn sw-p [model & attributes]
  (sw (apply mk-p model attributes)))


(defn mk-b [model & attributes]
  (apply make-HTMLElement "b" model attributes))

(defn sw-b [model & attributes]
  (sw (apply mk-b model attributes)))


(defn mk-h1 [model & attributes]
  (apply make-HTMLElement "h1" model attributes))

(defn sw-h1 [model & attributes]
  (sw (apply mk-h1 model attributes)))


(defn mk-h2 [model & attributes]
  (apply make-HTMLElement "h2" model attributes))

(defn sw-h2 [model & attributes]
  (sw (apply mk-h2 model attributes)))


(defn mk-td [model & attributes]
  (apply make-HTMLElement "td" model attributes))

(defn sw-td [model & attributes]
  (sw (apply mk-td model attributes)))


(defn mk-a [model href & attributes]
  (with1 (apply make-HTMLElement "a" model attributes)
    (jqAttr it "href" href)))


(defn sw-a [model href & attributes]
  (sw (apply mk-a model href attributes)))


(defn mk-span [model & attributes]
  (apply make-HTMLElement "span" model attributes))

(defn sw-span [model & attributes]
  (sw (apply mk-span model attributes)))
