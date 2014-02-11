(in-ns 'symbolicweb.core)


;; TODO: Macro this shit.

(defn mk-pre [model & args]
  (apply mk-he "pre" model args))

(defn mk-p [model & args]
  (apply mk-he "p" model args))

(defn sw-p [model & args]
  (sw (apply mk-p model args)))


(defn mk-b [model & args]
  (apply mk-he "b" model args))

(defn sw-b [model & args]
  (sw (apply mk-b model args)))


(defn mk-h1 [model & args]
  (apply mk-he "h1" model args))

(defn sw-h1 [model & args]
  (sw (apply mk-h1 model args)))


(defn mk-h2 [model & args]
  (apply mk-he "h2" model args))

(defn sw-h2 [model & args]
  (sw (apply mk-h2 model args)))


(defn mk-td [model & args]
  (apply mk-he "td" model args))

(defn sw-td [model & args]
  (sw (apply mk-td model args)))


(defn mk-a [model href & args]
  (with1 (apply mk-he "a" model args)
    (jqAttr it "href" href)))


(defn sw-a [model href & args]
  (sw (apply mk-a model href args)))


(defn mk-span [model & args]
  (apply mk-he "span" model args))

(defn sw-span [model & args]
  (sw (apply mk-span model args)))


(defn mk-div [model & args]
  (apply mk-he "div" model args))

(defn sw-div [model & args]
  (sw (apply mk-div model args)))
