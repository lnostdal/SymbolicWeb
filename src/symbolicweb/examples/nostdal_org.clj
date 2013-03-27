(ns symbolicweb.examples.nostdal-org
  (:use symbolicweb.core)
  (:use hiccup.core))



(defn mk-nostdal-org-viewport [request  session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        viewport (mk-Viewport request session root-widget :page-title "nostdal.org")]
    (jqCSS root-widget "text-align" "'center'")
    (jqAppend root-widget
      (whc [:div]
        (html
         [:img {:src "http://1.media.collegehumor.cvcdn.com/36/21/977be9afe4fb0797136c16077364711d.gif"}]
         [:br] [:br]
         "↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓"
         [:br] [:br]
         [:div {:style "border: 1px solid grey; display: inline-block; padding: 5px; margin: 10px;"}
          [:pre "1PAPy82AheWGyGcUpMGrk7wyzUsM5APAdb"]
          [:img {:src "https://blockchain.info/qr?data=1PAPy82AheWGyGcUpMGrk7wyzUsM5APAdb&size=250"}]]
         )))
    viewport))



(defapp
  [::Nostdal-org
   (fn [request]
     (re-find #"nostdal.org$" (:uri request)))]

  (fn [session]
    (alter session assoc
           :mk-viewport-fn #'mk-nostdal-org-viewport)
    session))
