(ns symbolicweb.examples.nostdal-org
  (:use symbolicweb.core)
  (:use hiccup.core)
  (:require [garden.core :refer [css style]]))



(defn homepage [request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        viewport (mk-Viewport request session root-widget :page-title "nostdal.org")]
    (add-resource viewport :css "sw/css/common.css")
    (add-rest-head viewport "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />")
    (add-rest-head viewport (html [:style (css [:body {:background-color "rgb(171,191,181)"
                                                       :color 'black
                                                       :font-family "'DejaVu Sans', sans-serif"}]
                                               [:a {:color 'black}]
                                               [:li {:padding-bottom "0.5em"}])]))
    (add-rest-head viewport "<link href='data:image/x-icon;base64,AAABAAEAEBAQAAAAAAAoAQAAFgAAACgAAAAQAAAAIAAAAAEABAAAAAAAgAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAjIyMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAQAAABAQAAAQAAAAAAAAAAAAAAABAAAAAAAAAAAAAQAAAAAAABAAEAAAAAAAAAAAAAAAAAABAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAD//wAA54cAAOOHAADzvwAA878AAPk/AAD5PwAA/H8AAPx/AAD+/wAA/v8AAP7/AAD8/wAA9P8AAPH/AAD//wAA' rel='icon' type='image/x-icon' />")
    (jqAppend root-widget
      (whc [:div]
        [:h2 "Lars Rune Nøstdal"]

        [:p "Hello! I'm a 33 year old freelancer and " [:a {:href "https://encrypted.google.com/search?hl=en&q=telecommuting"} "telecommuter"] " originally from "
         [:a {:href "https://en.wikipedia.org/wiki/Norway"} "Norway"]
         ". My laptop and I travel the world, and I enjoy things like sailing, nature and hiking."]


        [:ul
         [:li "Email: " [:a {:href "mailto:larsnostdal@gmail.com"} "larsnostdal@gmail.com"]]
         [:li "Some source code at " [:a {:href "https://github.com/lnostdal/"} "GitHub"]]]


        [:h3 "Current tech focus"]
        [:ul
         [:li [:a {:href "https://en.wikipedia.org/wiki/Clojure"} "Clojure"]]
         [:li [:a {:href "https://en.wikipedia.org/wiki/PostgreSQL"} "PostgreSQL"]]
         [:li [:a {:href "https://en.wikipedia.org/wiki/Linux"} "Linux"] " (since 1998)"]
         [:li "JavaScript (e.g. jQuery), HTML5, CSS, Nginx"]
         [:li [:a {:href "https://en.wikipedia.org/wiki/JSON"} "JSON"] ", HTTP (e.g. REST), XML"]
         [:li [:a {:href "https://en.wikipedia.org/wiki/Bootstrap_(front-end_framework)"} "Twitter Bootstrap"]
          ", " [:a {:href "https://en.wikipedia.org/wiki/Foundation_(framework)"} "Foundation (ZURB)"]
          " (nice for mobile devices)"]]


        #_[:h3 "Previous tech focus (history)"]
        #_[:ul
         [:li "Basic (Amiga 500!), C, Pascal (Turbo, Delphi), ASP, PHP, Java, C++, Common Lisp"]
         [:li "MySQL"]]


        #_[:h3 "General or other knowledge; &quot;what can you do?&quot;"]
        #_[:ul
         [:li "Object oriented programming (OOP), functional programming, reactive programming, metaprogramming"]
         [:li "I write clean, maintainable code that performs and scales well; I focus on simplicity, reliability and quality"]
         [:li "Source code control for project collaboration; Git"]
         [:li "Project management and leadership; both technical details and people related details (e.g. teaching)"]
         [:li [:a {:href "https://en.wikipedia.org/wiki/Facebook_Platform"} "Facebook API"] " and integration"]
         [:li "Twitter API and integration"]
         [:li "Linux server setup, configuration, optimization and administration from scratch; database, web server, etc."]
         [:li [:a {:href "https://en.wikipedia.org/wiki/FFmpeg"} "FFmpeg"] " for video and audio encoding, transcoding, etc."]
         [:li "Payment systems; Payex etc."]
         [:li "Bitcoin related APIs"]
         [:li "Security, encryption, SSL/TLS, privacy (Tor, I2P), etc."]
         [:li "Email, mailing list etc. type APIs (e.g. " [:a {:href "http://mandrill.com/"} "http://mandrill.com/"] ")"]
         [:li "I understand Computer Science topics well; algorithms and datastructures, programming language theory, computer architecture (hardware), etc."]
         [:li "Integration with mobile phone SMS APIs"]
         [:li "Hardware interfacing and electronics"]]


        [:div {:style (style {:padding "1em"})}
         [:a {:href "/static/other/IMG_5004_cropped.png"}
          [:img {:alt "" :src "/static/other/lars.png"
                 :style (style {:display 'block :width "15em"})}]]
         [:em {:style (style {:font-size 'smaller})} "&quot;Hello!&quot;"]]


        [:p {:style (style {:font-family 'monospace})}
         "pub   4096R/7B281AED 2013-01-24" [:br]
         "Key fingerprint = 5029 6FDD 199C 2A69 898B  40B0 A08A C77A 7B28 1AED" [:br]
         "uid                  Lars Rune Nøstdal (PGP (RSA)) <larsnostdal@gmail.com>" [:br]
         "sub   4096R/F53DFC31 2013-01-24"]


        [:hr]
        [:p {:style (style {:float 'right :font-family 'monospace})}
         "This page was generated by " [:a {:href "https://github.com/lnostdal/SymbolicWeb"} "SymbolicWeb"] ": "
         [:a {:href "https://en.wikipedia.org/wiki/Homoiconicity"} "data &#8596; code"] "."]
        ))
    viewport))



(defmulti mk-nostdal-org-viewport #(first %&))

(defmethod mk-nostdal-org-viewport :default [^String uri request session]
  (mk-Viewport request session (mk-bte :root-widget? true) :page-title "SW: :default"))

(defmethod mk-nostdal-org-viewport "/nostdal.org" [^String uri request session]
  (homepage request session))



(defapp
  [::Nostdal-org
   (fn [request]
     (in (:server-name request)
         "nostdal.org"
         "aafoss.nostdal.org"
         "localhost.nostdal.org"
         "localhost"
         "127.0.0.1"
         "symbolicweb.i2p" "utgf6vb44abxntvqqfcge2rnt5wfzb5kcltglaojam4sqhemsz6q.b32.i2p"))]

  (fn [request session]
    (if (= (:uri request) "/") ;; Can't set a cookie here as it will become global for all paths; redirect instead.
      (alter session assoc
             :rest-handler (fn [request session viewport]
                             (http-redirect-response "/nostdal.org"))
             :one-shot? true)
      (alter session assoc
             :mk-viewport-fn (fn [request session]
                               (mk-nostdal-org-viewport (:uri request) request session))))
    session))
