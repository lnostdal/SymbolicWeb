(in-ns 'symbolicweb.core)


(defn remove-Tooltip [context-widget]
  "Remove (destroy) Tooltip."
  (add-response-chunk (str "$('#" (:id @context-widget) "').tooltip('destroy');")
                      context-widget))


(defn show-Tooltip [context-widget message & options]
  "Show Tooltip for CONTEXT-WIDGET. MESSAGE is the string to show in Tooltip message."
  (add-response-chunk (str "$('#" (:id @context-widget) "')"
                           ".attr('title', '')"
                           ".tooltip({"
                           "  content: function(){ return " (url-encode-wrap message) "}"
                           "}).tooltip('open');")
                      context-widget))


(defn show-ErrorTooltip [widget & options]
  )