(in-ns 'symbolicweb.core)


(derive ::HTMLContainer ::Widget)
(defn %make-HTMLContainer [[html-element-type & attributes] content-fn]
  (apply make-HTMLElement html-element-type (ref nil)
         :type ::HTMLContainer
         :handle-model-event-fn (fn [widget old-value new-value])
         :connect-model-view-fn (fn [model widget])
         :disconnect-model-view-fn (fn [widget])
         :render-aux-html-fn (fn [_] (content-fn))
         attributes))


(defmacro with-html-container [[html-element-type & attributes] & body]
  `(%make-HTMLContainer (into [~html-element-type] ~attributes)
                        (fn [] (html ~@body))))


(defmacro whc [[html-element-type & attributes] & body]
  `(with-html-container ~(into [html-element-type] attributes)
     ~@body))