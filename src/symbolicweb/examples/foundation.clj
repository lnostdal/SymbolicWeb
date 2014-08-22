(ns symbolicweb.examples.foundation
  (:import clojure.lang.Ref
           clojure.lang.Keyword
           clojure.lang.Fn)

  (:use symbolicweb.core))



(defmethod symbolicweb.examples.nostdal-org/mk-nostdal-org-viewport "/foundation" [^String uri request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        viewport (mk-Viewport request session root-widget :page-title "SW: Foundation")]
    (add-rest-head viewport "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />")
    (add-resource viewport :css "foundation/dist/assets/css/normalize.css")
    (add-resource viewport :css "foundation/dist/assets/css/foundation.css")
    (add-resource viewport :js "Modernizr/dist/modernizr-build.min.js")
    (add-resource viewport :js "foundation/dist/assets/js/foundation.min.js")

    (jqAppend root-widget
      (whc [:div]
        [:nav {:class "top-bar" :data-topbar "" :data-options "is_hover: false"}

         [:ul {:class "title-area"}
          [:li {:class "name"}
           [:h1 [:a "nostdal.org"]]]
          [:li {:class "toggle-topbar menu-icon"}
           [:a "Menu"]]]

         [:section {:class "top-bar-section"}
          [:ul {:class "left"}
           [:li [:a "Left Nav Button"]]]

          [:ul {:class "right"}
           [:li [:a "Right Nav Button"]]]]]


        [:h2 "SymbolicWeb"]
        [:h3 "Foundation: basic example"]
        [:p "This example shows how to get " [:a {:href "http://foundation.zurb.com/"} "Foundation"]
         " up and running with SymbolicWeb."]


        [:script "$(document).foundation();"]))

    viewport))
