(ns symbolicweb.examples.chat
  (:use symbolicweb.core)
  (:use hiccup.core)
  (:use hiccup.util))



(let [conversation-area-model (cm)]
  (defn mk-chat-viewport [request session]
    (let [root-widget (mk-bte :id "_body" :root-widget? true)
          viewport (mk-Viewport request session root-widget :page-title "SW: Chat example")
          nickname (with (spget session :nickname)
                     (when-not @it
                       (vm-set it (str "anon-" (Long/toHexString (generate-uid)))))
                     it)
          conversation-area-view
          (mk-ContainerView (whc [:div {:style "overflow: auto;"}])
                            conversation-area-model
                            (fn [container-view node]
                              (let [[nickname-model msg] (cmn-data node)]
                                (whc [:div]
                                  [:b (sw (mk-span nickname-model))] ": " (escape-html msg)
                                  ;; Scroll consversation to most recent message.
                                  [:script "$('#" (.id container-view) "')"
                                   ".scrollTop($('#" (.id container-view) "')"
                                   ".prop('scrollHeight'));"]))))]

      (jqAppend root-widget
        (whc [:div]
          (sw conversation-area-view)
          [:div {:id "chat_input"}
           [:table {:border 0 :style "width: 100%;"}
            [:tr
             [:td {:style "white-space: nowrap;"}
              "Nick: " (sw (with1 (mk-TextInput nickname :change)
                             (jqCSS it "width" "5em")))
              " | "]
             [:td {:style "width: 100%;"}
              (let [chat-input-model (vm "")
                    chat-input-view (with1 (mk-TextInput chat-input-model :enterpress :clear-on-submit? true)
                                      (jqCSS it "width" "100%"))]
                (js-run chat-input-view "$('#" (.id chat-input-view) "').focus();")
                (vm-observe chat-input-model nil false
                            (fn [inner-lifetime old-value new-value]
                              (when new-value
                                (cm-append conversation-area-model (cmn [nickname new-value])))))
                (sw chat-input-view))]]]]

          [:script
           "$(window).resize(function(){"
           "  $('#" (.id conversation-area-view) "').outerHeight($(window).innerHeight() - $('#chat_input').outerHeight() - 30);"
           "});"
           "$(window).trigger('resize');"]

          #_[:hr]
          #_[:pre
           [:a {:href "https://github.com/lnostdal/SymbolicWeb/blob/master/src/symbolicweb/examples/chat.clj"}
            "Source code"]]))

      viewport)))



(defmethod symbolicweb.examples.nostdal-org/mk-nostdal-org-viewport "/chat" [uri request session]
  (mk-chat-viewport request session))
