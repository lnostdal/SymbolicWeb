(in-ns 'symbolicweb.core)


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


(defn jqAppend [widget content]
  "Inside."
  (add-response-chunk (str "$('#" (widget-id-of widget) "').append(" (url-encode-wrap content) ");")
                      widget))


(defn jqPrepend [widget content]
  "Inside."
  (add-response-chunk (str "$('#" (widget-id-of widget) "').prepend(" (url-encode-wrap content) ");")
                      widget))


(defn jqAfter [widget content]
  "Outside."
  (add-response-chunk (str "$('#" (widget-id-of widget) "').after(" (url-encode-wrap content) ");")
                      widget))


(defn jqBefore [widget content]
  "Outside."
  (add-response-chunk (str "$('#" (widget-id-of widget) "').before(" (url-encode-wrap content) ");")
                      widget))


(defn jqRemove [widget]
  (add-response-chunk (str "$('#" (widget-id-of widget) "').remove();")
                      widget))


(defn jqAddClass [widget class-name]
  (add-response-chunk (str "$('#" (widget-id-of widget) "').addClass(" (url-encode-wrap class-name) ");")
                      widget))


(defn jqRemoveClass [widget class-name]
  (add-response-chunk (str "$('#" (widget-id-of widget) "').removeClass(" (url-encode-wrap class-name) ");")
                      widget))


(defn jqEmpty [widget]
  (add-response-chunk (str "$('#" (widget-id-of widget) "').empty();")
                      widget))


(defn jqRemove [widget]
  (add-response-chunk (str "$('#" (widget-id-of widget) "').remove();")
                      widget))
