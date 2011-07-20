(in-ns 'symbolicweb.core)

(defn make-ID
  ([] (make-ID {}))
  ([m] (assoc m :id (generate-uid))))


(defn make-WidgetBase []
  (assoc (make-ID)
    :callbacks {}
    :parent nil
    :in-dom? nil
    :delayed-operations []))


(defn make-Widget [& {:keys [type html-element-type]
                      :or {html-element-type "div"}
                      :as all}]
  {:pre [type]}
  (assoc (make-WidgetBase)
    :type type
    :html-element-type html-element-type))


(defn make-HTMLElement [html-element-type]
  "Represents a block or inline HTML element."
  (make-Widget :type ::HTMLElement
               :html-element-type html-element-type))


(defn make-Container [& children]
  (assoc (make-Widget :type ::Container)
    :children (if children
                (into [] children)
                [])))


(defn add-children [root-Container & children]
  {:pre [(= (type root-Container) clojure.lang.Agent)]}
  (send root-Container
        (fn [m]
          (assoc m :children
                 (into (:children root-Container)
                       children)))))
