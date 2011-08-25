(in-ns 'symbolicweb.core)


(derive ::HTMLContainer ::Widget)
(defn %make-HTMLContainer [content]
  (make-HTMLElement ["div"
                     :type ::HTMLContainer
                     :set-model-fn (fn [widget model])
                     :render-aux-html-fn (fn [_] (content))]
                    content))


(defmacro with-html-container [& body]
  "The interesting or 'tricky' code related to HTMLContainer is mostly found in the RENDER-HTML (widget_base.clj) function."
  `(%make-HTMLContainer (ref (fn [] (html ~@body)))))