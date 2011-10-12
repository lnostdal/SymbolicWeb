(in-ns 'symbolicweb.core)

(derive ::Dialog ::HTMLElement)
(defn make-Dialog [widget]
  "\"Convert\" WIDGET into a jQuery UI Dialog."
  (with1 widget
    (add-response-chunk (str "$('#" (:id @it) "')"
                             ".dialog({modal: true, close: function(event, ui){"
                             "  $('#" (:id @it) "').remove();"
                             "}});")
                        it)))
