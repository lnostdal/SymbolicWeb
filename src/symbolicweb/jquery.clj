(in-ns 'symbolicweb.core)



(defn js-handle-value ^String [value ^Boolean as-js?]
  (if as-js?
    value
    (url-encode-wrap (.toString value))))



(defn jqHTML
  ([^WidgetBase widget]
     (str "$('#" (.id widget) "').html();\n"))

  ([^WidgetBase widget ^String value]
     (jqHTML widget value false))

  ([^WidgetBase widget ^String value ^Boolean as-js?]
     (add-response-chunk (str "$('#" (.id widget) "').html(" (js-handle-value value as-js?) ");\n")
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

  ([^WidgetBase widget ^String property-name ^String value]
     (jqCSS widget property-name value false))

  ([^WidgetBase widget ^String property-name ^String value ^Boolean as-js?]
     (add-response-chunk (str "$('#" (.id widget) "').css('" property-name "', " (js-handle-value value as-js?) ");\n")
                         widget)))



(defn jqAttr
  ([^WidgetBase widget ^String attribute-name]
     (str "$('#" (.id widget) "').attr('" attribute-name "');\n"))

  ([^WidgetBase widget ^String attribute-name ^String value]
     (jqAttr widget attribute-name value false))

  ([^WidgetBase widget ^String attribute-name ^String value ^Boolean as-js?]
     (add-response-chunk (str "$('#" (.id widget) "').attr('" attribute-name "', " (js-handle-value value as-js?) ");\n")
                         widget)))



(defn jqAttrRemove [^WidgetBase widget ^String attribute-name]
  (add-response-chunk (str "$('#" (.id widget) "').removeAttr('" attribute-name "');\n")
                      widget))



(defn jqProp
  ([^WidgetBase widget ^String attribute-name]
     (str "$('#" (.id widget) "').prop('" attribute-name "');\n"))

  ([^WidgetBase widget ^String attribute-name ^String value]
     (jqProp widget attribute-name value false))

  ([^WidgetBase widget ^String attribute-name ^String value ^Boolean as-js?]
     (add-response-chunk (str "$('#" (.id widget) "').prop('" attribute-name "', " (js-handle-value value as-js?) ");\n")
                         widget)))



(defn jqVal
  ([^WidgetBase widget]
     (str "$('#" (.id widget) "').val();\n"))

  ([^WidgetBase widget ^String value]
     (jqVal widget value false))

  ([^WidgetBase widget ^String value ^Boolean as-js?]
     (add-response-chunk (str "$('#" (.id widget) "').val(" (js-handle-value value as-js?) ");\n")
                         widget)))



(defn jqFocus [^WidgetBase widget]
  (add-response-chunk (str "$('#" (.id widget) "').focus();\n")
                      widget))



(defn jqTooltipShow
  ([^WidgetBase widget ^String value]
     (jqTooltipShow widget value false))

  ([^WidgetBase widget ^String value ^Boolean as-js?]
     (add-response-chunk (str "$('#" (.id widget) "').attr('title', " (js-handle-value value as-js?) ");\n"
                              "$('#" (.id widget) "').tooltip({track: true}).tooltip('open');\n")
                         widget)))
