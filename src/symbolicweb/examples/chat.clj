(ns symbolicweb.examples.chat
  (:use symbolicweb.core)
  (:use hiccup.core))



(let [conversation-area-model (cm)]
  (defn mk-chat-viewport [request session]
    (let [root-widget (mk-bte :id "_body" :root-widget? true)
          nickname (vm (str "anon-" (Long/toHexString (generate-uid))))
          conversation-area-view (mk-ContainerView conversation-area-model
                                                   (fn [container-view node]
                                                     (let [[nickname-model msg] (cmn-data node)]
                                                       (whc [:div]
                                                        (html
                                                         [:b (sw (mk-span nickname-model))] ": " msg)))))

          viewport (mk-Viewport request session root-widget
                                :page-title "SW: Chat example"
                                :genurl-fs-path "resources/web-design/"
                                :genurl-scheme "http"
                                :genurl-domain "static.nostdal.org"
                                :genurl-path "")]

      (jqAppend root-widget
        (whc [:div]
          (html
           [:div (sw conversation-area-view)]
           [:div
            "Nick: " (sw (mk-TextInput nickname :change))
            " | "
            (let [chat-input-model (vm "")
                  chat-input-view (mk-TextInput chat-input-model :enterpress
                                                :clear-on-submit? true
                                                :one-way-sync-client? true)]
              (vm-observe chat-input-model nil false
                          (fn [inner-lifetime old-value new-value]
                            (cm-append conversation-area-model (cmn [nickname new-value]))))
              (sw chat-input-view))])))

      viewport)))



(defapp
  [::Chat
   (fn [request]
     (re-find #"chat$" (:uri request)))]

  (fn [id & args]
    (apply mk-Session id :mk-viewport-fn #'mk-chat-viewport
           args)))
