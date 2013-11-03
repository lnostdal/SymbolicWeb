(ns symbolicweb.examples.history
  (:import symbolicweb.core.WidgetBase)
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

        a-url-mapper (vm-sync-from-url {:name "a" :model a-input-model :context-widget a-view})
        b-url-mapper (vm-sync-from-url {:name "b" :model b-input-model :context-widget b-view})]

    (jqAppend root-widget
      (whc [:div]
        (html
         [:p (sw a-view) " + " (sw b-view) " = " (sw sum-view)]

         [:p "Random number for each page (re)load, " [:b (rand-int 9000)]
          ", for a visual confirmation that the page really does not reload as the URL changes."]

         [:p (sw (with (mk-WB :a)
                   (jqHTML it "Increment A!")
                   (mk-Link it {a-url-mapper (vm-sync a-model (.lifetime it) #(inc %3))})))]
         [:p (sw (with (mk-WB :a)
                   (jqHTML it "Increment B!")
                   (mk-Link it {b-url-mapper (vm-sync b-model (.lifetime it) #(inc %3))})))]

         [:hr]
         [:pre
          [:a {:target "_blank" :href "https://github.com/lnostdal/SymbolicWeb/blob/master/src/symbolicweb/examples/history.clj"}
           "Source code"]])))

    (mk-Viewport request session root-widget :page-title "SW: History example")))
