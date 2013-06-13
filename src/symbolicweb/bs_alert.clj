(in-ns 'symbolicweb.core)



(defn bs-alert
  ([^WidgetBase container ^WidgetBase msg]
     (bs-alert container msg nil))

  ([^WidgetBase container ^WidgetBase msg delay]
     (let [close-button (mk-Button "&times;")]
       (jqAttr close-button "data-dismiss" "alert")
       (jqAddClass close-button "close")
       (with1 (mk-WB :div)
         (dorun (map (partial jqAddClass it) ["alert" "fade" "in"]))
         (jqAppend it close-button)
         (jqAppend it msg)
         (jqPrepend container it)
         (when delay
           (add-response-chunk (str "setTimeout(function(){ $('#" (.id msg) "').alert('close') }, " delay ");") msg))
         ))))
