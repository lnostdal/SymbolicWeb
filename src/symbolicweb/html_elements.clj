(in-ns 'symbolicweb.core)


;; TODO: Macro this shit.

(defn mk-pre [model & attributes]
  (apply mk-he "pre" model attributes))

(defn mk-p [model & attributes]
  (apply mk-he "p" model attributes))

(defn sw-p [model & attributes]
  (sw (apply mk-p model attributes)))


(defn mk-b [model & attributes]
  (apply mk-he "b" model attributes))

(defn sw-b [model & attributes]
  (sw (apply mk-b model attributes)))


(defn mk-h1 [model & attributes]
  (apply mk-he "h1" model attributes))

(defn sw-h1 [model & attributes]
  (sw (apply mk-h1 model attributes)))


(defn mk-h2 [model & attributes]
  (apply mk-he "h2" model attributes))

(defn sw-h2 [model & attributes]
  (sw (apply mk-h2 model attributes)))


(defn mk-td [model & attributes]
  (apply mk-he "td" model attributes))

(defn sw-td [model & attributes]
  (sw (apply mk-td model attributes)))


(defn mk-a [model href & attributes]
  (with1 (apply mk-he "a" model attributes)
    (jqAttr it "href" href)))


(defn sw-a [model href & attributes]
  (sw (apply mk-a model href attributes)))


(defn mk-span [model & attributes]
  (apply mk-he "span" model attributes))

(defn sw-span [model & attributes]
  (sw (apply mk-span model attributes)))


(defn mk-div [model & attributes]
  (apply mk-he "div" model attributes))

(defn sw-div [model & attributes]
  (sw (apply mk-div model attributes)))
