(ns symbolicweb.examples.nostdal-org
  (:use symbolicweb.core)
  (:use hiccup.core))



(defn mk-nostdal-org-viewport [request  session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        viewport (mk-Viewport request session root-widget :page-title "nostdal.org")]
    (add-resource viewport :css "sw/css/common.css")
    (add-rest-head viewport "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0' />")
    (jqCSS root-widget "text-align" "center")
    (jqCSS root-widget "background-color" "#dddddd")
    (jqAppend root-widget
      (whc [:div]
        (html
         [:img {:src "http://1.media.collegehumor.cvcdn.com/36/21/977be9afe4fb0797136c16077364711d.gif"}]
         [:br]
         "↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓"
         [:br]
         [:div {:style "border: 1px solid grey; display: inline-block; padding: 5px; margin: 10px;"}
          [:pre [:a {:href "https://blockchain.info/address/1GuiA3jcVSYTgmewtEZhJ5hC1B2is4bAf3"}
                 "1GuiA3jcVSYTgmewtEZhJ5hC1B2is4bAf3"]]
          [:img {:src "https://blockchain.info/qr?data=1GuiA3jcVSYTgmewtEZhJ5hC1B2is4bAf3&size=200"}]]
         [:br]


         [:p [:em "&quot;In " [:a {:href "https://duckduckgo.com/?q=vote+for+nobody"} "No One"] " (i.e. Crypto!) I Trust.&quot; -- Lars"]]

         [:blockquote {:style "background-color: white; padding: 1em; margin: 1em; text-align: left;"}
          [:p"&quot;I come in peace,&quot; it said, adding after a long moment of further grinding, &quot;take me to your Lizard.&quot;"]
          [:p "Ford Prefect, of course, had an explanation for this, as he sat with Arthur and watched the nonstop frenetic news reports on television, none of which had anything to say other than to record that the thing had done this amount of damage which was valued at that amount of billions of pounds and had killed this totally other number of people, and then say it again, because the robot was doing nothing more than standing there, swaying very slightly, and emitting short incomprehensible error messages."]
          [:p "&quot;It comes from a very ancient democracy, you see...&quot;"]
          [:p "&quot;You mean, it comes from a world of lizards?&quot;"]
          [:p "&quot;No,&quot; said Ford, who by this time was a little more rational and coherent than he had been, having finally had the coffee forced down him, &quot;nothing so simple. Nothing anything like to straightforward. On its world, the people are people. The leaders are lizards. The people hate the lizards and the lizards rule the people.&quot;"]
          [:p "&quot;Odd,&quot; said Arthur, &quot;I thought you said it was a democracy.&quot;"]
          [:p "&quot;I did,&quot; said ford. &quot;It is.&quot;"]
          [:p "&quot;So,&quot; said Arthur, hoping he wasn't sounding ridiculously obtuse, &quot;why don't the people get rid of the lizards?&quot;"]
          [:p "&quot;It honestly doesn't occur to them,&quot; said Ford. &quot;They've all got the vote, so they all pretty much assume that the government they've voted in more or less approximates to the government they want.&quot;"]
          [:p "&quot;You mean they actually vote for the lizards?&quot;"]
          [:p "&quot;Oh yes,&quot; said Ford with a shrug, &quot;of course.&quot;"]
          [:p "&quot;But,&quot; said Arthur, going for the big one again, &quot;why?&quot;"]
          [:p "&quot;Because if they didn't vote for a lizard,&quot; said Ford, &quot;the wrong lizard might get in. Got any gin?&quot;"]
          [:p "&quot;What?&quot;"]
          [:p "&quot;I said,&quot; said Ford, with an increasing air of urgency creeping into his voice, &quot;have you got any gin?&quot;"]
          [:p "&quot;I'll look. Tell me about the lizards.&quot;"]
          [:p "Ford shrugged again."]
          [:p "&quot;Some people say that the lizards are the best thing that ever happened to them,&quot; he said. &quot;They're completely wrong of course, completely and utterly wrong, but someone's got to say it.&quot;"]]
         [:p "-- Douglas Adams"]
         [:br] [:br]
         )))
    viewport))



(defapp
  [::Nostdal-org
   (fn [request]
     (= "/nostdal.org" (:uri request)))]

  (fn [session]
    (alter session assoc
           :mk-viewport-fn #'mk-nostdal-org-viewport)
    session))
