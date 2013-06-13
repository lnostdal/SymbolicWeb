(in-ns 'symbolicweb.core)



(defn bs-show-dialog [^Ref viewport ^String title ^WidgetBase content ^WidgetBase footer]
  (with (whc [:div {:html-attrs {:style "display: none;"
                                 :class "modal hide fade"
                                 :role "dialog"
                                 :tabindex "-1"}}]
          (html
           [:div {:class "modal-header"}
            [:button {:type "button" :class "close" :data-dismiss "modal"} "&times;"]
            [:h3  title]
            [:div {:class "modal-body"}
             (sw content)]
            (sw footer)]))
    (jqAddClass footer "modal-footer")
    (set-event-handler "hidden" it (fn [& _] (jqRemove it)))
    (jqAppend (:root-element @viewport) it)
    (add-response-chunk (str "$('#" (.id it) "').modal('show');") viewport)))
