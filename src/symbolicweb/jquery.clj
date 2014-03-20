(in-ns 'symbolicweb.core)


(declare render-html)


(defn js-handle-value ^String [value ^Boolean as-js?]
  (if as-js?
    (or value "")
    (if value
      (url-encode-wrap (.toString value))
      "''")))



(defn jqHTML
  ([^WidgetBase widget]
     (str "$('#" (.id widget) "').html();\n"))

  ([^WidgetBase widget ^String value]
     (jqHTML widget value false))

  ([^WidgetBase widget ^String value ^Boolean as-js?]
     (js-run widget "$('#" (.id widget) "').html(" (js-handle-value value as-js?) ");")
     widget))



(defn jqAppend [^WidgetBase parent ^WidgetBase new-widget]
  "Inside."
  (js-run parent "$('#" (.id parent) "').append(" (url-encode-wrap (render-html new-widget)) ");")
  (attach-branch parent new-widget)
  parent)



(defn jqPrepend [^WidgetBase parent ^WidgetBase new-widget]
  "Inside."
  (js-run parent "$('#" (.id parent) "').prepend(" (url-encode-wrap (render-html new-widget)) ");")
  (attach-branch parent new-widget)
  parent)



(defn jqAfter [^WidgetBase widget ^WidgetBase new-widget]
  "Outside."
  (let [parent (parent-of widget)]
    (assert parent)
    (js-run parent "$('#" (.id widget) "').after(" (url-encode-wrap (render-html new-widget)) ");")
    (attach-branch parent new-widget)
    widget))



(defn jqBefore [^WidgetBase widget ^WidgetBase new-widget]
  "Outside."
  (let [parent (parent-of widget)]
    (assert parent)
    (js-run parent "$('#" (.id widget) "').before(" (url-encode-wrap (render-html new-widget)) ");")
    (attach-branch parent new-widget)
    widget))



(defn jqReplaceWith [^WidgetBase widget ^WidgetBase new-widget]
  (detach-branch widget)
  (js-run widget "$('#" (.id widget) "').replaceWith(" (url-encode-wrap (render-html new-widget)) ");")
  (attach-branch widget new-widget)
  widget)



(defn jqAddClass [^WidgetBase widget ^String class-name]
  (js-run widget "$('#" (.id widget) "').addClass('" class-name "');")
  widget)



(defn jqRemoveClass [^WidgetBase widget ^String class-name]
  (js-run widget "$('#" (.id widget) "').removeClass('" class-name "');")
  widget)



(defn jqEmpty [^WidgetBase widget]
  (js-run widget "$('#" (.id widget) "').empty();")
  (empty-branch widget)
  widget)



(defn jqRemove [^WidgetBase widget]
  (js-run widget "$('#" (.id widget) "').remove();")
  (detach-branch widget)
  widget)



(defn jqCSS
  ([^WidgetBase widget ^String property-name]
     (str "$('#" (.id widget) "').css('" property-name "');\n"))

  ([^WidgetBase widget ^String property-name ^String value]
     (jqCSS widget property-name value false))

  ([^WidgetBase widget ^String property-name ^String value ^Boolean as-js?]
     (js-run widget "$('#" (.id widget) "').css('" property-name "', " (js-handle-value value as-js?) ");")
     widget))



(defn jqAttr
  ([^WidgetBase widget ^String attribute-name]
     (str "$('#" (.id widget) "').attr('" attribute-name "');\n"))

  ([^WidgetBase widget ^String attribute-name ^String value]
     (jqAttr widget attribute-name value false))

  ([^WidgetBase widget ^String attribute-name ^String value ^Boolean as-js?]
     (js-run widget "$('#" (.id widget) "').attr('" attribute-name "', " (js-handle-value value as-js?) ");")
     widget))



(defn jqAttrRemove [^WidgetBase widget ^String attribute-name]
  (js-run widget "$('#" (.id widget) "').removeAttr('" attribute-name "');")
  widget)



(defn jqProp
  ([^WidgetBase widget ^String attribute-name]
     (str "$('#" (.id widget) "').prop('" attribute-name "');\n"))

  ([^WidgetBase widget ^String attribute-name ^String value]
     (jqProp widget attribute-name value false))

  ([^WidgetBase widget ^String attribute-name ^String value ^Boolean as-js?]
     (js-run widget "$('#" (.id widget) "').prop('" attribute-name "', " (js-handle-value value as-js?) ");")
     widget))



(defn jqVal
  ([^WidgetBase widget]
     (str "$('#" (.id widget) "').val();\n"))

  ([^WidgetBase widget ^String value]
     (jqVal widget value false))

  ([^WidgetBase widget ^String value ^Boolean as-js?]
     (js-run widget "$('#" (.id widget) "').val(" (js-handle-value value as-js?) ");")
     widget))



(defn jqFocus [^WidgetBase widget]
  (js-run widget "$('#" (.id widget) "').focus();")
  widget)



(defn jqTooltipShow
  ([^WidgetBase widget ^String value]
     (jqTooltipShow widget value false))

  ([^WidgetBase widget ^String value ^Boolean as-js?]
     (js-run widget
       "$('#" (.id widget) "').attr('title', " (js-handle-value value as-js?) "); "
       "$('#" (.id widget) "').tooltip({track: true}).tooltip('open');")
     widget))
