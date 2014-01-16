(in-ns 'symbolicweb.core)



(defn bs-alert
  ([^WidgetBase container ^WidgetBase msg]
     (bs-alert container msg nil))

  ([^WidgetBase container ^WidgetBase msg delay]
     (let [close-button (whc [:button {:html-attrs {:data-dismiss "alert" :class "close"}}]
                          "&times;")]
       (with1 (mk-WB :div {:html-attrs {:class "alert fade in"}})
         (jqAppend it close-button)
         (jqAppend it msg)
         (jqPrepend container it)
         (when delay
           (add-response-chunk (str "setTimeout(function(){ $('#" (.id msg) "').alert('close') }, " delay ");\n") msg))
         ;; Ensure that the alert is visible by scrolling it into view.
         (add-response-chunk (str "$('#" (.id container) "')[0].scrollIntoView(false);\n") container)))))
