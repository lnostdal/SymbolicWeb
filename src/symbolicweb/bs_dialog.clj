(in-ns 'symbolicweb.core)



(defn bs-show-dialog [^Ref viewport ^String title ^WidgetBase content ^WidgetBase footer]
  (with (whc [:div {:html-attrs {:style "display: none;"}}]
          (html
           [:div {:class "modal-header"}
            [:button {:type "button" :class "close" :data-dismiss "modal" :aria-hidden "true"} "&times;"]
            [:h3  title]
            [:div {:class "modal-body"}
             (sw content)]
            (sw footer)]))
    (jqAddClass footer "modal-footer")
    (dorun (map (partial jqAddClass it) ["modal" "hide" "fade"]))
    (jqAttr it "role" "dialog") (jqAttr it "tabindex" "-1")
    (set-event-handler "hidden" it (fn [& _] (jqRemove it)))
    (jqAppend (:root-element @viewport) it)
    (add-response-chunk (str "$('#" (.id it) "').modal('show');") viewport)))
