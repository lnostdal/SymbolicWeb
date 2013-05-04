(ns symbolicweb.examples.nostdal-org
  (:use symbolicweb.core)
  (:use hiccup.core))



(defn mk-nostdal-org-viewport [request  session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        viewport (mk-Viewport request session root-widget :page-title "nostdal.org")]
    (add-resource viewport :css "sw/css/common.css")
    (jqCSS root-widget "text-align" "'center'")
    (jqCSS root-widget "background-color" "'#dddddd'")
    (jqAppend root-widget
      (whc [:div]
        (html
         [:br] [:br]
         [:p "I can never seem to figure out what content I want on this &quot;homepage&quot;, but here's some bullshit related to what I regard as one of the most amazing things I've seen come about in recent years; "
          [:a {:href "https://en.wikipedia.org/wiki/Bitcoin"} "Bitcoin"] "."]
         [:br]
         [:img {:src "http://1.media.collegehumor.cvcdn.com/36/21/977be9afe4fb0797136c16077364711d.gif"}]
         [:br]
         "↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓"
         [:br]
         [:div {:style "border: 1px solid grey; display: inline-block; padding: 5px; margin: 10px;"}
          [:pre [:a {:href "https://blockchain.info/address/1GuiA3jcVSYTgmewtEZhJ5hC1B2is4bAf3"}
                 "1GuiA3jcVSYTgmewtEZhJ5hC1B2is4bAf3"]]
          [:img {:src "https://blockchain.info/qr?data=1GuiA3jcVSYTgmewtEZhJ5hC1B2is4bAf3&size=200"}]]
         [:br]
         [:em "&quot;In " [:a {:href "https://duckduckgo.com/?q=vote+for+nobody"} "No One"] " (i.e. Crypto!) I Trust&quot;"])))
    viewport))



(defapp
  [::Nostdal-org
   (fn [request]
     (= "/nostdal.org" (:uri request)))]

  (fn [session]
    (alter session assoc
           :mk-viewport-fn #'mk-nostdal-org-viewport)
    session))
