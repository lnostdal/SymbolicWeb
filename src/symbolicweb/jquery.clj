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
     (js-run widget "$('#" (.id widget) "').html(" (js-handle-value value as-js?) ");")))



(defn jqAppend [^WidgetBase parent ^WidgetBase new-widget]
  "Inside."
  (with1 (js-run parent "$('#" (.id parent) "').append(" (url-encode-wrap (render-html new-widget)) ");")
    (when-not *with-js?*
      (attach-branch parent new-widget))))



(defn jqPrepend [^WidgetBase parent ^WidgetBase new-widget]
  "Inside."
  (with1 (js-run parent "$('#" (.id parent) "').prepend(" (url-encode-wrap (render-html new-widget)) ");")
    (when-not *with-js?*
      (attach-branch parent new-widget))))



(defn jqAfter [^WidgetBase widget ^WidgetBase new-widget]
  "Outside."
  (let [parent (parent-of widget)]
    (assert (or parent *with-js?*))
    (with1 (js-run parent "$('#" (.id widget) "').after(" (url-encode-wrap (render-html new-widget)) ");")
      (when-not *with-js?*
        (attach-branch parent new-widget)))))



(defn jqBefore [^WidgetBase widget ^WidgetBase new-widget]
  "Outside."
  (let [parent (parent-of widget)]
    (assert (or parent *with-js?*))
    (with1 (js-run parent "$('#" (.id widget) "').before(" (url-encode-wrap (render-html new-widget)) ");")
      (when-not *with-js?*
        (attach-branch parent new-widget)))))



(defn jqReplaceWith [^WidgetBase widget ^WidgetBase new-widget]
  (when-not *with-js?* (detach-branch widget))
  (with1 (js-run widget "$('#" (.id widget) "').replaceWith(" (url-encode-wrap (render-html new-widget)) ");")
    (when-not *with-js?*
      (attach-branch widget new-widget))))



(defn jqAddClass [^WidgetBase widget ^String class-name]
  (js-run widget "$('#" (.id widget) "').addClass('" class-name "');"))



(defn jqRemoveClass [^WidgetBase widget ^String class-name]
  (js-run widget "$('#" (.id widget) "').removeClass('" class-name "');"))



(defn jqEmpty [^WidgetBase widget]
  (with1 (js-run widget "$('#" (.id widget) "').empty();")
    (when-not *with-js?*
      (empty-branch widget))))



(defn jqRemove [^WidgetBase widget]
  (with1 (js-run widget "$('#" (.id widget) "').remove();")
    (when-not *with-js?*
      (detach-branch widget))))



(defn jqCSS
  ([^WidgetBase widget ^String property-name]
     (str "$('#" (.id widget) "').css('" property-name "');\n"))

  ([^WidgetBase widget ^String property-name ^String value]
     (jqCSS widget property-name value false))

  ([^WidgetBase widget ^String property-name ^String value ^Boolean as-js?]
     (js-run widget "$('#" (.id widget) "').css('" property-name "', " (js-handle-value value as-js?) ");")))



(defn jqAttr
  ([^WidgetBase widget ^String attribute-name]
     (str "$('#" (.id widget) "').attr('" attribute-name "');\n"))

  ([^WidgetBase widget ^String attribute-name ^String value]
     (jqAttr widget attribute-name value false))

  ([^WidgetBase widget ^String attribute-name ^String value ^Boolean as-js?]
     (js-run widget "$('#" (.id widget) "').attr('" attribute-name "', " (js-handle-value value as-js?) ");")))



(defn jqAttrRemove [^WidgetBase widget ^String attribute-name]
  (js-run widget "$('#" (.id widget) "').removeAttr('" attribute-name "');"))



(defn jqProp
  ([^WidgetBase widget ^String attribute-name]
     (str "$('#" (.id widget) "').prop('" attribute-name "');\n"))

  ([^WidgetBase widget ^String attribute-name ^String value]
     (jqProp widget attribute-name value false))

  ([^WidgetBase widget ^String attribute-name ^String value ^Boolean as-js?]
     (js-run widget "$('#" (.id widget) "').prop('" attribute-name "', " (js-handle-value value as-js?) ");")))



(defn jqVal
  ([^WidgetBase widget]
     (str "$('#" (.id widget) "').val();\n"))

  ([^WidgetBase widget ^String value]
     (jqVal widget value false))

  ([^WidgetBase widget ^String value ^Boolean as-js?]
     (js-run widget "$('#" (.id widget) "').val(" (js-handle-value value as-js?) ");")))



(defn jqFocus [^WidgetBase widget]
  (js-run widget "$('#" (.id widget) "').focus();"))



(defn jqTooltipShow
  ([^WidgetBase widget ^String value]
     (jqTooltipShow widget value false))

  ([^WidgetBase widget ^String value ^Boolean as-js?]
     (js-run widget
       "$('#" (.id widget) "').attr('title', " (js-handle-value value as-js?) "); "
       "$('#" (.id widget) "').tooltip({track: true}).tooltip('open');")))
