(ns symbolicweb.examples.chat
  (:use symbolicweb.core)
  (:use hiccup.core)
  (:use hiccup.util))



(let [conversation-area-model (cm)]
  (defn mk-chat-viewport [request session]
    (let [root-widget (mk-bte :id "_body" :root-widget? true)
          nickname (with (session-get session :nickname)
                     (when-not @it
                       (vm-set it (str "anon-" (Long/toHexString (generate-uid)))))
                     it)
          conversation-area-view (mk-ContainerView (mk-WB :div)
                                                   conversation-area-model
                                                   (fn [container-view node]
                                                     (let [[nickname-model msg] (cmn-data node)]
                                                       (whc [:div]
                                                        (html
                                                         [:b (sw (mk-span nickname-model))] ": "
                                                         (escape-html msg))))))
          viewport (mk-Viewport request session root-widget :page-title "SW: Chat example")]

      (jqAppend root-widget
        (whc [:div]
          (html
           [:div (sw conversation-area-view)]
           [:div
            "Nick: " (sw (mk-TextInput nickname :change)) " | "
            (let [chat-input-model (vm "")
                  chat-input-view (with1 (mk-TextInput chat-input-model :enterpress
                                                       :clear-on-submit? true
                                                       :one-way-sync-client? true)
                                    (jqCSS it "width" "700px"))]
              (add-response-chunk (str "$('#" (.id chat-input-view) "').focus();") chat-input-view)
              (vm-observe chat-input-model nil false
                          (fn [inner-lifetime old-value new-value]
                            (cm-append conversation-area-model (cmn [nickname new-value]))))
              (sw chat-input-view))]

           [:hr]
           [:pre
            [:a {:href "https://github.com/lnostdal/SymbolicWeb/blob/master/src/symbolicweb/examples/chat.clj"}
             "Source code"]])))

      viewport)))



(defapp
  [::Chat
   (fn [request]
     (re-find #"chat$" (:uri request)))]

  (fn [session-skeleton]
    (alter session-skeleton assoc
           :mk-viewport-fn #'mk-chat-viewport)
    session-skeleton))
