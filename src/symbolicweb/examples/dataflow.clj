(ns symbolicweb.examples.dataflow
  (:use symbolicweb.core)
  (:use hiccup.core))

;;; Shows some basic dataflow.



(defn mk-dataflow-viewport [request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        a (vm 3)
        b (vm 2)
        sum (with-observed-vms nil
              (+ @a @b))]

    (jqAppend root-widget
      (whc [:div]
        (html
         [:p
          (sw (mk-TextInput a :change :input-parsing-fn parse-long))
          " + "
          (sw (mk-TextInput b :change :input-parsing-fn parse-long))
          " = "
          (sw (mk-span sum))]

         [:hr]
         [:pre
          [:a {:href "https://github.com/lnostdal/SymbolicWeb/blob/master/src/symbolicweb/examples/dataflow.clj"} "Source code"]
          " | "
          (sw (mk-span -now-))]))) ;; -NOW- is a ValueModel updated (VM-SET) by a background thread.

    (mk-Viewport request session root-widget
                 :page-title "SW: Dataflow example"
                 :genurl-fs-path "resources/web-design/"
                 :genurl-scheme "http"
                 :genurl-domain "static.nostdal.org"
                 :genurl-path "")))



(defapp
  [::Dataflow
   (fn [request]
     (re-find #"dataflow$" (:uri request)))]

  (fn [id & args]
    (apply mk-Session id :mk-viewport-fn #'mk-dataflow-viewport
           args)))
