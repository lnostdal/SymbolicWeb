(in-ns 'symbolicweb.core)



(defn bs-show-dialog [^Ref viewport ^String title ^Fn content-fn ^Fn footer-fn]
  (with (whc [:div {:style "display: none;"
                    :class "modal hide"}]
          [:div {:class "modal-header"}
           [:button {:type "button" :class "close" :data-dismiss "modal"} "&times;"]
           [:h3 title]
           [:div {:class "modal-body"}
            (content-fn html-container)]
           [:div {:class "modal-footer"}
            (footer-fn html-container)]])
    (set-event-handler "hidden" it (fn [& _] (jqRemove it)))
    (jqAppend (:root-element @viewport) it)
    (add-response-chunk (str "$('#" (.id it) "').modal('show');\n")
                        viewport)))



(defn bs-hide-dialog [^WidgetBase dialog]
  (add-response-chunk (str "$('#" (.id dialog) "').modal('hide');\n")
                      dialog))
