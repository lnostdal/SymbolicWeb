(ns symbolicweb.examples.history
  (:use symbolicweb.core)
  (:use hiccup.core))

;; Need two way observers for the Models in question. From server to client and the other way around.
;;
;; Server --> Client: can add to or replace browser history.
;; Client --> Server: can set state.




(defn mk-history-viewport [request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        a (vm 3)
        b (vm 2)
        sum (with-observed-vms nil
              (+ @a @b))]

    (jqAppend root-widget
      (whc [:div]
        (html
         [:p (sw (mk-LongInput a)) " + " (sw (mk-LongInput b)) " = " (sw (mk-span sum))]
         [:hr]
         [:em [:a {:href "https://github.com/lnostdal/SymbolicWeb/blob/master/src/symbolicweb/examples/history.clj"}
               "Source code"]])))

    (mk-Viewport request session root-widget
                 :page-title "SW: History example"
                 :genurl-fs-path "resources/web-design/"
                 :genurl-scheme "http"
                 :genurl-domain "static.nostdal.org"
                 :genurl-path "")))



(defapp
  [::History
   (fn [request]
     (re-find #"history$" (:uri request)))]

  (fn [id & args]
    (apply mk-Session id :mk-viewport-fn #'mk-history-viewport
           args)))
