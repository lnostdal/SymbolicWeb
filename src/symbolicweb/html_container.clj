(in-ns 'symbolicweb.core)


(derive ::HTMLContainer ::Widget)
(defn %make-HTMLContainer [content-fn]
  (make-HTMLElement "div" content-fn
                    :type ::HTMLContainer
                    :connect-model-view-fn (fn [model view])
                    :disconnect-model-view-fn (fn [widget])
                    :render-aux-html-fn (fn [_] (content-fn))))


(defmacro with-html-container [& body]
  "The interesting or 'tricky' code related to HTMLContainer is mostly found in the RENDER-HTML (widget_base.clj) function."
  `(%make-HTMLContainer (fn [] (html ~@body))))