(ns symbolicweb.examples.history
  (:use symbolicweb.core)
  (:use hiccup.core))



(defn mk-history-viewport [request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)

        a-input-model (vm 3)
        b-input-model (vm 2)
        a-model (vm-sync a-input-model nil #(parse-long %3))
        b-model (vm-sync b-input-model nil #(parse-long %3))
        sum-model (with-observed-vms nil
                    (+ @a-model @b-model))

        a-view (mk-TextInput a-input-model :change)
        b-view (mk-TextInput b-input-model :change)
        sum-view (mk-span sum-model)

        a-url-mapper (mk-URLMapper "a" a-input-model a-view)
        b-url-mapper (mk-URLMapper "b" b-input-model b-view)]

    (jqAppend root-widget
      (whc [:div]
        (html
         [:p (sw a-view) " + " (sw b-view) " = " (sw sum-view)]

         [:p "Random number for each page (re)load, " [:b (rand-int 9000)]
          ", for a visual confirmation that the page really does not reload as the URL changes."]


         [:p (sw (with (mk-ContainerView (mk-WB :a)
                                         (cm-append (cm) (cmn (vm "Increment A!")))
                                         #(mk-he :b (cmn-data %2)))
                   (mk-Link it a-url-mapper (vm-sync a-model (.lifetime it) #(inc %3)))))]
         [:p (sw (with (mk-ContainerView (mk-WB :a)
                                         (cm-append (cm) (cmn (vm "Increment B!")))
                                         #(mk-he :b (cmn-data %2)))
                   (mk-Link it b-url-mapper (vm-sync b-model (.lifetime it) #(inc %3)))))]

         [:hr]
         [:pre
          [:a {:href "https://github.com/lnostdal/SymbolicWeb/blob/master/src/symbolicweb/examples/history.clj"}
           "Source code"]])))

    (mk-Viewport request session root-widget :page-title "SW: History example")))




(defapp
  [::History
   (fn [request]
     (re-find #"history$" (:uri request)))]

  (fn [id & args]
    (apply mk-Session id :mk-viewport-fn #'mk-history-viewport
           args)))
