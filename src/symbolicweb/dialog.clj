(in-ns 'symbolicweb.core)


(defn make-Dialog [widget & {:keys [js-options on-close]}]
  "\"Convert\" WIDGET into a jQuery UI Dialog.
DIALOG-JS-OPTIONS can be e.g. {:width 800 :modal true} etc., see the jQuery UI Dialog docs.

  (dosync
     (jqAppend (root-element)
       (make-Dialog (mk-p (vm \"test\"))
                    :js-options {:modal :true :width 800 :height 600}
                    :on-close (with-js (alert \"Dialog was closed.\")))))"
  (with1 widget
    (add-response-chunk (str "$('#" (:id @it) "')"
                             ".dialog({"
                             (map-to-js-options js-options)
                             "close: function(event, ui){"
                             "  $('#" (:id @it) "').remove();"
                             on-close
                             "}});")
                        it)))


(defn show-Dialog [widget & options]
  (jqAppend (root-element)
    (apply make-Dialog widget options)))


(defn show-ModalDialog [widget & options]
  (apply show-Dialog widget
         (alter-options options update-in [:js-options] assoc :modal :true)))