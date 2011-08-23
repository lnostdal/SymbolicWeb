(in-ns 'symbolicweb.core)


(derive ::HTMLContainer ::Widget)
(defn %make-HTMLContainer [content]
  (make-HTMLElement ["div"
                     :type ::HTMLContainer
                     :set-model-fn (fn [widget model])
                     :render-aux-html-fn (fn [_] (content))]
                    content))


(defmacro with-html-container [& body]
  `(%make-HTMLContainer (ref (fn [] (html ~@body)))))