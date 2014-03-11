(ns symbolicweb.examples.bootstrap
  (:import clojure.lang.Ref
           clojure.lang.Keyword
           clojure.lang.Fn)
  (:use symbolicweb.core))



(defmethod symbolicweb.examples.nostdal-org/mk-nostdal-org-viewport "/bootstrap" [^String uri request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        viewport (mk-Viewport request session root-widget :page-title "SW: Bootstrap")]
    (add-rest-head viewport "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />")
    (add-resource viewport :css "bootstrap/dist/css/bootstrap.min.css")
    (add-resource viewport :css "bootstrap/dist/css/bootstrap-theme.min.css")
    (add-resource viewport :js "bootstrap/dist/js/bootstrap.min.js")

    (jqAppend root-widget
      (whc [:div {:class "container-fluid"}]

        [:nav {:class "navbar navbar-default" :role "navigation"}
         [:div {:class "container-fluid"}

          [:div {:class "navbar-header"}
           [:button {:type "button" :class "navbar-toggle" :data-toggle "collapse" :data-target ".navbar-collapse"}
            [:span {:class "sr-only"} "Toggle navigation"]
            [:span {:class "icon-bar"}] [:span {:class "icon-bar"}] [:span {:class "icon-bar"}]]
           [:a {:class "navbar-brand" :href "#"} "nostdal.org"]]

          [:div {:class "collapse navbar-collapse"}
           [:ul {:class "nav navbar-nav"}
            [:li [:a {:href "#"} "First button"]]
            [:li [:a {:href "#"} "Second button"]]]]]]


         [:h2 "SymbolicWeb"]

         [:h3 "Bootstrap: basic example"]
         [:p "This example shows how to get " [:a {:href "http://getbootstrap.com/"} "Bootstrap"]
          " up and running with SymbolicWeb."]))

    viewport))
