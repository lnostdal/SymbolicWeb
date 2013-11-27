(ns symbolicweb.examples.nostdal-org
  (:use symbolicweb.core)
  (:use hiccup.core))



(defn homepage [request session]
  (let [root-widget (with1 (mk-bte :id "_body" :root-widget? true)
                      (jqCSS it "background-color" "rgb(171,191,181)")
                      (jqCSS it "color" "black")
                      (jqCSS it "font-family" "sans-serif"))

        viewport (mk-Viewport request session root-widget :page-title "nostdal.org")]
    (add-resource viewport :css "sw/css/common.css")
    (add-rest-head viewport "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0' />")
    (add-rest-head viewport "<style>a { color: black; } li { padding-bottom: 0.5em; }</style>")
    (add-rest-head viewport "<link href='data:image/x-icon;base64,AAABAAEAEBAQAAAAAAAoAQAAFgAAACgAAAAQAAAAIAAAAAEABAAAAAAAgAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAjIyMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAQAAABAQAAAQAAAAAAAAAAAAAAABAAAAAAAAAAAAAQAAAAAAABAAEAAAAAAAAAAAAAAAAAABAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAD//wAA54cAAOOHAADzvwAA878AAPk/AAD5PwAA/H8AAPx/AAD+/wAA/v8AAP7/AAD8/wAA9P8AAPH/AAD//wAA' rel='icon' type='image/x-icon' />")
    (jqAppend root-widget
      (whc [:div]
        (html
         [:h2 "Lars Rune Nøstdal"]
         [:p [:em [:b "Web developer: Clojure, PostgreSQL, JavaScript, nginx, Linux etc.. Send me an email for details!"]]]

         [:ul {:style "display: inline-block; vertical-align: top; margin-top: 0; line-height: 1.4em;"}
          [:li "Email: " [:a {:target "_blank" :href "mailto:larsnostdal@gmail.com"} "larsnostdal@gmail.com"] " (public key is at the bottom of this page)"]
          [:li "Phone, Mumble, XMPP (e.g. "
           [:a {:target "_blank" :href "https://en.wikipedia.org/wiki/Off-the-Record_Messaging"} "OTR"]
           " chat): Sometimes; email me first."]
          [:li "Source code: " [:a {:target "_blank" :href "https://github.com/lnostdal/"} "GitHub"] "."]
          [:li "Facebook, Twitter, LinkedIn, Google+, Skype, etc.: No."]
          [:li "Location: The " [:a {:target "_blank" :href "https://nostdal.org/static/other/uten_fast_bosted.png"} "Internet"] "; I " [:a {:target "_blank" :href "https://en.wikipedia.org/wiki/Telecommuting"} "telecommute"]
           " and travel a lot. I also very much like sailing and sometimes I "
           [:a {:href "http://i.imgur.com/ZdJKhh9.jpg"} "live on/in an old sailboat"] ". I don't pretend to own anything; I just rent e.g. " [:a {:href "https://www.airbnb.com/users/show/3977190"} "https://www.airbnb.com/users/show/3977190"] "."]
          [:li
           "Currency: " [:a {:target "_blank" :href "https://en.bitcoin.it/wiki/Main_Page"} "Bitcoin"]
           "; the " [:a {:target "_blank" :href "https://www.youtube.com/results?search_query=honey+badger+vs"} "honey badger"] " of money." [:br]
           [:a {:target "_blank" :href "https://en.bitcoin.it/wiki/Main_Page"}
            [:img { :alt "" :style "padding: 0.5em;"
                   :src "https://en.bitcoin.it/w/images/en/f/fd/BC_nBG_64px.png"}]]]
          [:li "Politics: " [:a {:target "_blank" :href "https://en.wikipedia.org/wiki/Non-politics"} "No"]
           ". Freedom is the " [:a {:target "_blank" :href "https://www.goodreads.com/quotes/297074-but-i-don-t-want-comfort-i-want-god-i-want"} "right to be unhappy"] "." [:br]
           [:a {:target "_blank" :href "https://en.wikipedia.org/wiki/Anarcho-capitalism"}
            [:img {:alt "" :style "padding: 0.5em; width: 10em;"
                   :src "https://nostdal.org/static/other/bcanarcy-500x500.png?_42"}]]]
          ;;[:li "Cattle serial number: 24068039394. Used by the Norwegian mafia who pretend and tell me this is &quot;my number&quot;, but I never agreed to that (still don't) when I was born.."]
          [:li "Religion: " [:a {:target "_blank" :href "https://en.wikipedia.org/wiki/Atheism"} "No"] ". The meaning of life is to create meaning."]
          [:li "Music: " [:a {:target "_blank" :href "https://en.wikipedia.org/wiki/Music_and_mathematics"} "Is math"] "."]
          [:li "Personality: " [:a {:target "_blank" :href "http://tvtropes.org/pmwiki/pmwiki.php/Main/ChaoticNeutral"} "Chaotic Neutral"] "."]
          ]

         [:div {:style "padding: 1em;"}
          [:a {:href "/static/other/IMG_5004_cropped.png"}
           [:img {:alt "" :src "/static/other/lars.png"
                  :style "display: block; width: 15em; "}]]
          [:em {:style "font-size: smaller;"} "&quot;My face..&quot;"]]

         [:br]
         [:pre
          "pub   4096R/7B281AED 2013-01-24
      Key fingerprint = 5029 6FDD 199C 2A69 898B  40B0 A08A C77A 7B28 1AED
uid                  Lars Rune Nøstdal (PGP (RSA)) <larsnostdal@gmail.com>
sub   4096R/F53DFC31 2013-01-24
"]

         [:br]
         [:pre
          "-----BEGIN PGP PUBLIC KEY BLOCK-----
Version: GnuPG v2.0.20 (GNU/Linux)

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
eJlxkPnxbDLIMdmx9aZcxFPb+Y41
=LKmf
-----END PGP PUBLIC KEY BLOCK-----
"]

         [:blockquote
          [:em "Great spirits have always encountered violent opposition from mediocre minds. The mediocre mind is incapable of understanding the man who refuses to bow blindly to conventional prejudices and chooses instead to express his opinions courageously and honestly."] [:br]
          "-- A. E., 1940"]

         [:hr]
         [:p {:style "float: right; font-family: monospace;"}
          "This page was generated by " [:a {:target "_blank" :href "https://github.com/lnostdal/SymbolicWeb"} "SymbolicWeb"] ": "
          [:a {:target "_blank" :href "https://en.wikipedia.org/wiki/Homoiconicity"} "data &#8596; code"] "."]
         )))
    viewport))



(defn mk-nostdal-org-viewport [request session]
  (case (:uri request)
    "/history"
    (symbolicweb.examples.history/mk-history-viewport request session)

    "/bitcoin"
    (symbolicweb.examples.bitcoin/mk-bitcoin-viewport request session)

    ;; E.g. "/nostdal.org"
    (homepage request session)))



(defapp
  [::Nostdal-org
   (fn [request]
     (= (:server-name request) "nostdal.org"))]

  (fn [request session]
    (case (:uri request)
      "/" ;; Can't set a cookie here as it will become global for all paths; redirect instead.
      (alter session assoc
             :rest-handler (fn [request session viewport]
                             (http-redirect-response "/nostdal.org"))
             :one-shot? true)

      (alter session assoc
             :mk-viewport-fn #'mk-nostdal-org-viewport))
    session))
