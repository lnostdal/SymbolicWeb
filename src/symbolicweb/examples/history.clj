(ns symbolicweb.examples.history
  (:use symbolicweb.core)
  (:use hiccup.core))



(defn mk-history-viewport [request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        a-input (vm 3)
        a (vm-sync a-input nil #(parse-long %3))
        b-input (vm 2)
        b (vm-sync b-input nil #(parse-long %3))
        sum (with-observed-vms nil
              (+ @a @b))]

    (jqAppend root-widget
      (whc [:div]
        (html
         [:p (sw (mk-TextInput a-input :change)) " + " (sw (mk-TextInput b-input :change)) " = " (sw (mk-span sum))]

         [:p "Random number for each page (re)load, " [:b (rand-int 9000)]
          ", for a visual confirmation that the page really does not reload as the URL changes."]


         [:p (sw (with (mk-ContainerView (mk-WB :a)
                                         (cm-append (cm) (cmn (vm "Increment A!")))
                                         #(mk-he :b (cmn-data %2)))
                   (mk-Link it a-input "a" (vm-sync a (.lifetime it) #(inc %3)))))]
         [:p (sw (with (mk-ContainerView (mk-WB :a)
                                         (cm-append (cm) (cmn (vm "Increment B!")))
                                         #(mk-he :b (cmn-data %2)))
                   (mk-Link it b-input "b" (vm-sync b (.lifetime it) #(inc %3)))))]


         [:hr]
         [:pre
          [:a {:href "https://github.com/lnostdal/SymbolicWeb/blob/master/src/symbolicweb/examples/history.clj"}
           "Source code"]])))

    (let [viewport (mk-Viewport request session root-widget :page-title "SW: History example")]
      (vm-map-to-url a-input "a" (.lifetime root-widget) viewport)
      (vm-map-to-url b-input "b" (.lifetime root-widget) viewport)

      viewport)))



(defapp
  [::History
   (fn [request]
     (re-find #"history$" (:uri request)))]

  (fn [id & args]
    (apply mk-Session id :mk-viewport-fn #'mk-history-viewport
           args)))
