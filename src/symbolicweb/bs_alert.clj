(in-ns 'symbolicweb.core)



(defn bs-alert
  ([^WidgetBase container ^WidgetBase msg]
     (bs-alert container msg nil))

  ([^WidgetBase container ^WidgetBase msg delay]
     (let [close-button (whc [:button {:data-dismiss "alert" :class "close"}]
                          "&times;")]
       (with1 (whc [:div {:class "alert fade in"}])
         (jqAppend it close-button)
         (jqAppend it msg)
         (jqPrepend container it)
         (when delay
           (js-run msg "setTimeout(function(){ $('#" (.id msg) "').alert('close') }, " delay ");"))
         ;; Ensure that the alert is visible by scrolling it into view.
         (js-run container "$('#" (.id container) "')[0].scrollIntoView(false);")))))
