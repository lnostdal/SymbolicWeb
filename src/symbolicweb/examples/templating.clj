(ns symbolicweb.examples.templating
  (:use symbolicweb.core)
  (:use hiccup.core)
  (:import [org.jsoup Jsoup]))



(defn mk-templating-viewport [request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        viewport (mk-Viewport request session root-widget :page-title "SymbolicWeb: templates")
        template (html
                  [:div
                   [:div {:class 'sw-text} "dummy text"]
                   [:div {:class 'sw-html} "dummy content"]
                   [:div {:class 'sw-attr} "color"]
                   [:div {:class 'sw-widget} "dummy content"]])
        jsoup-template (Jsoup/parseBodyFragment template)]
    (add-resource viewport :css "sw/css/common.css")

    (jqAppend root-widget
      (whc [:div]
        [:h2 "mk-HTMLCTemplate: Client side templating"]
        [:p "The template for both of these are sent to the client once, then 'instantiated' (cloned) there twice then 'templated' (filled in) using jQuery selectors."]
        [:p (sw (mk-HTMLCTemplate
                 template
                 (fn [template-widget]
                   [".sw-text" "<b>some text</b>"
                    ".sw-html" [:html "<b>some html</b>"]
                    ".sw-attr" [:attr :style "color: red;"]
                    ".sw-widget" (mk-te (vm "some widget"))])))]
        [:p (sw (mk-HTMLCTemplate
                 template
                 (fn [template-widget]
                   [".sw-text" "<b>some other text</b>"
                    ".sw-html" [:html "<b>some other html</b>"]
                    ".sw-attr" [:attr :style "color: blue;"]
                    ".sw-widget" (mk-te (vm "some other widget"))])))]


        [:h2 "mk-HTMLTemplate: Server side templating"]
        [:p "The template for these are 'templated' (filled in) on the server side using jsoup then sent to the client."]
        [:p (sw (mk-HTMLTemplate
                 jsoup-template
                 (fn [template-widget]
                   [".sw-text" "<b>some text</b>"
                    ".sw-html" [:html "<b>some html</b>"]
                    ".sw-attr" [:attr :style "color: red;"]
                    ".sw-widget" (mk-te (vm "some widget"))])))]
        [:p (sw (mk-HTMLTemplate
                 jsoup-template
                 (fn [template-widget]
                   [".sw-text" "<b>some other text</b>"
                    ".sw-html" [:html "<b>some other html</b>"]
                    ".sw-attr" [:attr :style "color: blue;"]
                    ".sw-widget" (mk-te (vm "some other widget"))])))]
        ))
    viewport))



(defmethod symbolicweb.examples.nostdal-org/mk-nostdal-org-viewport "/templating" [uri request session]
  (mk-templating-viewport request session))
