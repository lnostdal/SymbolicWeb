(in-ns 'symbolicweb.core)

(declare render-html)
(declare add-branch)
(declare empty-branch)
(declare remove-branch)


(defn jqHTML
  (^String [^WidgetBase widget]
    (str "$('#" (.id widget) "').html();"))

  (^String [^WidgetBase widget new-html]
     (add-response-chunk (str "$('#" (.id widget) "').html(" (url-encode-wrap (.toString new-html)) ");" \newline)
                         widget)))


(defn jqAppend [^WidgetBase parent ^WidgetBase new-widget]
  "Inside."
  (with1 (add-response-chunk (str "$('#" (.id parent) "').append(" (url-encode-wrap (render-html new-widget)) ");" \newline)
                             parent)
    (when-not *with-js?*
      (add-branch parent new-widget))))


(defn jqPrepend [^WidgetBase parent ^WidgetBase new-widget]
  "Inside."
  (with1 (add-response-chunk (str "$('#" (.id parent) "').prepend(" (url-encode-wrap (render-html new-widget)) ");" \newline)
                             parent)
    (when-not *with-js?*
      (add-branch parent new-widget))))


(defn jqAfter [^WidgetBase widget ^WidgetBase new-widget]
  "Outside."
  (let [parent (parent-of widget)]
    (assert (or parent *with-js?*))
    (with1 (add-response-chunk (str "$('#" (.id widget) "').after(" (url-encode-wrap (render-html new-widget)) ");" \newline)
                               parent)
      (when-not *with-js?*
        (add-branch parent new-widget)))))


(defn jqBefore [^WidgetBase widget ^WidgetBase new-widget]
  "Outside."
  (let [parent (parent-of widget)]
    (assert (or parent *with-js?*))
    (with1 (add-response-chunk (str "$('#" (.id widget) "').before(" (url-encode-wrap (render-html new-widget)) ");" \newline)
                               parent)
      (when-not *with-js?*
        (add-branch parent new-widget)))))


(defn jqReplaceWith [^WidgetBase widget ^WidgetBase new-widget]
  (when-not *with-js?* (remove-branch widget))
  (with1 (add-response-chunk (str "$('#" (.id widget) "').replaceWith(" (url-encode-wrap (render-html new-widget) ");" \newline)
                                  widget))
    (when-not *with-js?*
      (add-branch widget new-widget))))


(defn jqAddClass [^WidgetBase widget ^String class-name]
  (add-response-chunk (str "$('#" (.id widget) "').addClass(" (url-encode-wrap class-name) ");" \newline)
                      widget))


(defn jqRemoveClass [^WidgetBase widget ^String class-name]
  (add-response-chunk (str "$('#" (.id widget) "').removeClass(" (url-encode-wrap class-name) ");" \newline)
                      widget))


(defn jqEmpty [^WidgetBase widget]
  (with1 (add-response-chunk (str "$('#" (.id widget) "').empty();" \newline)
                             widget)
    (when-not *with-js?*
      (empty-branch widget))))


(defn jqRemove [^WidgetBase widget]
  (with1 (add-response-chunk (str "$('#" (.id widget) "').remove();" \newline)
                             widget)
    (when-not *with-js?*
      (remove-branch widget))))


(defn jqCSS
  ([^WidgetBase widget ^String property-name]
     (str "$('#" (.id widget) "').css('" property-name "');" \newline))
  ([^WidgetBase widget ^String property-name value]
     (add-response-chunk (str "$('#" (.id widget) "').css('" property-name "', '" value "');" \newline)
                         widget)))

(defn jqAttr
  ([^WidgetBase widget ^String attribute-name]
     (str "$('#" (.id widget) "').attr('" attribute-name "');" \newline))
  ([^WidgetBase widget ^String attribute-name value]
     (add-response-chunk (str "$('#" (.id widget) "').attr('" attribute-name "', '" value "');" \newline)
                         widget)))


(defn jqProp
  ([^WidgetBase widget ^String attribute-name]
     (str "$('#" (.id widget) "').prop('" attribute-name "');" \newline))
  ([widget attribute-name value]
     (add-response-chunk (str "$('#" (.id widget) "').prop('" attribute-name "', " value ");" \newline)
                         widget)))


(defn jqVal
  ([^WidgetBase widget]
     (str "$('#" (.id widget) "').val();" \newline))
  ([^WidgetBase widget new-value]
     (add-response-chunk (str "$('#" (.id widget) "').val(" (url-encode-wrap (.toString new-value)) ");" \newline)
                         widget)))
