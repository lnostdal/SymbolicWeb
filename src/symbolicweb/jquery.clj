(in-ns 'symbolicweb.core)

(declare render-html)
(declare add-branch)
(declare empty-branch)
(declare remove-branch)


(defn ensure-content-str [content]
  (if (widget? content)
    (render-html content)
    content))


(defn jqHTML
  ([widget] (str "$('#" (widget-id-of widget) "').html();"))
  ([widget new-html]
     (add-response-chunk (str "$('#" (widget-id-of widget) "').html(" (url-encode-wrap (str new-html)) ");")
                         widget)))


(defn jqAttr
  ([widget attribute-name]
     (str "$('#" (widget-id-of widget) "').attr('" attribute-name "');"))
  ([widget attribute-name value]
     (add-response-chunk (str "$('#" (widget-id-of widget) "').attr('" attribute-name "'"
                              ", " (url-encode-wrap value) ");")
                         widget)))


(defn jqAppend [parent content]
  "Inside."
  (with1 (add-response-chunk (str "$('#" (widget-id-of parent) "').append(" (url-encode-wrap (ensure-content-str content)) ");")
                             parent))
  (when (and (not *with-js?*) (widget? content))
    (add-branch parent content)))


(defn jqPrepend [parent content]
  "Inside."
  (with1 (add-response-chunk (str "$('#" (widget-id-of parent) "').prepend(" (url-encode-wrap (ensure-content-str content)) ");")
                             parent)
    (when (and (not *with-js?*) (widget? content))
      (add-branch parent content))))


(defn jqAfter [widget content]
  "Outside."
  (with1 (add-response-chunk (str "$('#" (widget-id-of widget) "').after(" (url-encode-wrap (ensure-content-str content)) ");")
                             (:parent @widget))
    (when (and (not *with-js?*) (widget? content))
      (add-branch (:parent @widget) content))))


(defn jqBefore [widget content]
  "Outside."
  (with1 (add-response-chunk (str "$('#" (widget-id-of widget) "').before(" (url-encode-wrap (ensure-content-str content)) ");")
                             (:parent @widget))
    (when (and (not *with-js?*) (widget? content))
      (add-branch (:parent @widget) content))))


(defn jqAddClass [widget class-name]
  (add-response-chunk (str "$('#" (widget-id-of widget) "').addClass(" (url-encode-wrap class-name) ");")
                      widget))


(defn jqRemoveClass [widget class-name]
  (add-response-chunk (str "$('#" (widget-id-of widget) "').removeClass(" (url-encode-wrap class-name) ");")
                      widget))


(defn jqEmpty [widget]
  (with1 (add-response-chunk (str "$('#" (widget-id-of widget) "').empty();")
                             widget)
    (when-not *with-js?*
      (empty-branch widget))))


(defn jqRemove [widget]
  (with1 (add-response-chunk (str "$('#" (widget-id-of widget) "').remove();")
                             widget)
    (when-not *with-js?*
      (remove-branch widget))))


(defn jqCSS
  ([widget property-name]
     (str "$('#" (widget-id-of widget) "').css('" property-name "');"))
  ([widget property-name value]
     (add-response-chunk (str "$('#" (widget-id-of widget) "').css('" property-name "', '" value "');")
                         widget)))

(defn jqAttr
  ([widget attribute-name]
     (str "$('#" (widget-id-of widget) "').attr('" attribute-name "');"))
  ([widget attribute-name value]
     (add-response-chunk (str "$('#" (widget-id-of widget) "').attr('" attribute-name "', '" value "');")
                         widget)))


(defn jqProp
  ([widget attribute-name]
     (str "$('#" (widget-id-of widget) "').prop('" attribute-name "');"))
  ([widget attribute-name value]
     (add-response-chunk (str "$('#" (widget-id-of widget) "').prop('" attribute-name "', " value ");")
                         widget)))


(defn jqVal
  ([widget]
     (str "$('#" (widget-id-of widget) "').val();"))
  ([widget new-value]
     (add-response-chunk (str "$('#" (widget-id-of widget) "').val(" (url-encode-wrap (str new-value)) ");")
                         widget)))
