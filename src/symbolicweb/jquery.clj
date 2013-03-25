(in-ns 'symbolicweb.core)



(defn jqHTML
  ([^WidgetBase widget]
     (str "$('#" (.id widget) "').html();\n"))

  ([^WidgetBase widget new-html]
     (add-response-chunk (str "$('#" (.id widget) "').html(" (url-encode-wrap (.toString new-html)) ");\n")
                         widget)))



(defn jqAppend [^WidgetBase parent ^WidgetBase new-widget]
  "Inside."
  (with1 (add-response-chunk (str "$('#" (.id parent) "').append(" (url-encode-wrap (render-html new-widget)) ");\n")
                             parent)
    (when-not *with-js?*
      (attach-branch parent new-widget))))



(defn jqPrepend [^WidgetBase parent ^WidgetBase new-widget]
  "Inside."
  (with1 (add-response-chunk (str "$('#" (.id parent) "').prepend(" (url-encode-wrap (render-html new-widget)) ");\n")
                             parent)
    (when-not *with-js?*
      (attach-branch parent new-widget))))



(defn jqAfter [^WidgetBase widget ^WidgetBase new-widget]
  "Outside."
  (let [parent (parent-of widget)]
    (assert (or parent *with-js?*))
    (with1 (add-response-chunk (str "$('#" (.id widget) "').after(" (url-encode-wrap (render-html new-widget)) ");\n")
                               parent)
      (when-not *with-js?*
        (attach-branch parent new-widget)))))



(defn jqBefore [^WidgetBase widget ^WidgetBase new-widget]
  "Outside."
  (let [parent (parent-of widget)]
    (assert (or parent *with-js?*))
    (with1 (add-response-chunk (str "$('#" (.id widget) "').before(" (url-encode-wrap (render-html new-widget)) ");\n")
                               parent)
      (when-not *with-js?*
        (attach-branch parent new-widget)))))



(defn jqReplaceWith [^WidgetBase widget ^WidgetBase new-widget]
  (when-not *with-js?* (detach-branch widget))
  (with1 (add-response-chunk (str "$('#" (.id widget) "').replaceWith(" (url-encode-wrap (render-html new-widget) ");\n")
                                  widget))
    (when-not *with-js?*
      (attach-branch widget new-widget))))



(defn jqAddClass [^WidgetBase widget ^String class-name]
  (add-response-chunk (str "$('#" (.id widget) "').addClass('" class-name "');\n")
                      widget))



(defn jqRemoveClass [^WidgetBase widget ^String class-name]
  (add-response-chunk (str "$('#" (.id widget) "').removeClass('" class-name "');\n")
                      widget))



(defn jqEmpty [^WidgetBase widget]
  (with1 (add-response-chunk (str "$('#" (.id widget) "').empty();\n")
                             widget)
    (when-not *with-js?*
      (empty-branch widget))))



(defn jqRemove [^WidgetBase widget]
  (with1 (add-response-chunk (str "$('#" (.id widget) "').remove();\n")
                             widget)
    (when-not *with-js?*
      (detach-branch widget))))



(defn jqCSS
  ([^WidgetBase widget ^String property-name]
     (str "$('#" (.id widget) "').css('" property-name "');\n"))
  ([^WidgetBase widget ^String property-name ^String js-value]
     (add-response-chunk (str "$('#" (.id widget) "').css('" property-name "', " js-value ");\n")
                         widget)))



(defn jqAttr
  ([^WidgetBase widget ^String attribute-name]
     (str "$('#" (.id widget) "').attr('" attribute-name "');\n"))
  ([^WidgetBase widget ^String attribute-name ^String js-value]
     (add-response-chunk (str "$('#" (.id widget) "').attr('" attribute-name "', " js-value ");\n")
                         widget)))



(defn jqProp
  ([^WidgetBase widget ^String attribute-name]
     (str "$('#" (.id widget) "').prop('" attribute-name "');\n"))
  ([^WidgetBase widget ^String attribute-name ^String js-value]
     (add-response-chunk (str "$('#" (.id widget) "').prop('" attribute-name "', " js-value ");\n")
                         widget)))



(defn jqVal
  ([^WidgetBase widget]
     (str "$('#" (.id widget) "').val();\n"))
  ([^WidgetBase widget new-value]
     (add-response-chunk (str "$('#" (.id widget) "').val(" (url-encode-wrap (.toString new-value)) ");\n")
                         widget)))



(defn jqFocus [^WidgetBase widget]
  (add-response-chunk (str "$('#" (.id widget) "').focus();\n")
                      widget))
