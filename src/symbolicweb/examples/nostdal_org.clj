(ns symbolicweb.examples.nostdal-org
  (:use symbolicweb.core)
  (:use hiccup.core))



(defn mk-nostdal-org-viewport [request  session]
  (let [root-widget (with1 (mk-bte :id "_body" :root-widget? true)
                      (jqCSS it "background-color" "rgb(171,191,181)")
                      (jqCSS it "color" "black")
                      (jqCSS it "font-family" "sans-serif"))

        viewport (mk-Viewport request session root-widget :page-title "nostdal.org")]
    (add-resource viewport :css "sw/css/common.css")
    (add-rest-head viewport "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0' />")
    (add-rest-head viewport "<style>a { color: black; }</style>")
    (jqAppend root-widget
      (whc [:div]
        (html
         [:h3 "Lars Rune Nøstdal"]
         [:ul
          [:li "Email: " [:a {:href "mailto:larsnostdal@gmail.com"} "larsnostdal@gmail.com"] "."]
          [:li "Phone: Sometimes; email me first."]
          [:li "Source code: " [:a {:href "https://github.com/lnostdal/"} "GitHub"] "."]
          [:li "Facebook: No."]
          [:li "Twitter: No."]
          [:li "Google+: No."]
          [:li "Skype: No."]
          [:li "Mumble: Sometimes; email me first."]
          [:li "Politics: " [:a {:href "https://en.wikipedia.org/wiki/Non-politics"} "No"] "."]
          [:li "Religion: " [:a {:href "https://en.wikipedia.org/wiki/Antireligion"} "No"] "."]
          [:li "Personality: " [:a {:href "http://tvtropes.org/pmwiki/pmwiki.php/Main/ChaoticNeutral"} "Chaotic Neutral"] "."]
          ]

         [:hr]
         [:p {:style "float: right; font-family: monospace;"}
          "This server runs " [:a {:href "https://github.com/lnostdal/SymbolicWeb"} "SymbolicWeb"] ": data &#8596; code."]
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
