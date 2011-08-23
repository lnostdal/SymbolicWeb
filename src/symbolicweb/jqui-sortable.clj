(in-ns 'symbolicweb.core)

(derive ::JQSortable ::HTMLElement)
(defn make-Sortable [& children]
  (set-event-handler "sort" (apply make-HTMLElement ["ul"
                                                     :type ::JQUSortable
                                                     :render-aux-js-fn #(str "$('#" (:id %) "').sortable();")]
                                   children)
                     (fn [& rest]
                       (println "HI!"))))
