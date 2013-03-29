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
          [:pre "1GuiA3jcVSYTgmewtEZhJ5hC1B2is4bAf3"]
          [:img {:src "https://blockchain.info/qr?data=1GuiA3jcVSYTgmewtEZhJ5hC1B2is4bAf3&size=250"}]]

         [:br] [:br]
         [:iframe {:width 640 :height 360 :frameborder 0 :allowfullscreen 1
                   :src "http://www.youtube.com/embed/OMAI-OIxLPo?autoplay=1"}]
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
