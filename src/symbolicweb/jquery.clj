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
  (let [content (ensure-content-str content)]
    (add-response-chunk (str "$('#" (widget-id-of parent) "').append(" (url-encode-wrap content) ");")
                        parent))
  (when (widget? content)
    (add-branch parent content)))


(defn jqPrepend [parent content]
  "Inside."
  (let [content (ensure-content-str content)]
    (add-response-chunk (str "$('#" (widget-id-of parent) "').prepend(" (url-encode-wrap content) ");")
                        parent))
  (when (widget? content)
    (add-branch parent content)))


(defn jqAfter [widget content]
  "Outside."
  (let [content (ensure-content-str content)]
    (add-response-chunk (str "$('#" (widget-id-of widget) "').after(" (url-encode-wrap content) ");")
                        (:parent @widget)))
  (when (widget? content)
    (add-branch (:parent @widget) content)))


(defn jqBefore [widget content]
  "Outside."
  (let [content (ensure-content-str content)]
    (add-response-chunk (str "$('#" (widget-id-of widget) "').before(" (url-encode-wrap content) ");")
                        (:parent @widget)))
  (when (widget? content)
    (add-branch (:parent @widget) content)))


(defn jqAddClass [widget class-name]
  (add-response-chunk (str "$('#" (widget-id-of widget) "').addClass(" (url-encode-wrap class-name) ");")
                      widget))


(defn jqRemoveClass [widget class-name]
  (add-response-chunk (str "$('#" (widget-id-of widget) "').removeClass(" (url-encode-wrap class-name) ");")
                      widget))


(defn jqEmpty [widget]
  (add-response-chunk (str "$('#" (widget-id-of widget) "').empty();")
                      widget)
  (empty-branch widget))


(defn jqRemove [widget]
  (add-response-chunk (str "$('#" (widget-id-of widget) "').remove();")
                      widget)
  (remove-branch widget))


(defn jqCSS
  ([widget property-name]
     (str "$('#" (widget-id-of widget) "').css('" property-name "');"))
  ([widget property-name value]
     (add-response-chunk (str "$('#" (widget-id-of widget) "').css('" property-name "', '" value "');")
                         widget)))


(defn jqVal
  ([widget]
     (str "$('#" (widget-id-of widget) "').val();"))
  ([widget new-value]
     (add-response-chunk (str "$('#" (widget-id-of widget) "').val(" (url-encode-wrap new-value) ");")
                         widget)))
