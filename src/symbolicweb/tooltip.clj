(in-ns 'symbolicweb.core)


;; TODO: http://view.jqueryui.com/master/demos/tooltip/delegation-mixbag.html "jzaefferer: ..or use the items option"

(defn remove-Tooltip [context-widget]
  "Remove (destroy) Tooltip."
  (js-run context-widget "$('#" (:id @context-widget) "').tooltip('destroy');"))



(defn show-Tooltip
  "Show Tooltip for CONTEXT-WIDGET. MESSAGE is the string to show in Tooltip message."
  ([context-widget message]
     (show-Tooltip context-widget message nil))
  ([context-widget message options]
     (js-run context-widget
       "$('#" (:id @context-widget) "')"
       ".attr('title', '')"
       ".tooltip({"
       (map-to-js-options options)
       "  content: function(){ return " (url-encode-wrap message) "}"
       "}).tooltip('open');")))




(defn show-ErrorTooltip
  "Show Tooltip for CONTEXT-WIDGET. MESSAGE is the string to show in Tooltip message."
  ([context-widget message]
     (show-ErrorTooltip context-widget message {}))
  ([context-widget message options]
     (show-Tooltip context-widget message (conj options [:tooltipClass "'ui-state-error'"]))))
