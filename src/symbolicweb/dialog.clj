(in-ns 'symbolicweb.core)


;; TODO: Handle buttons. See USER-CHECK-EMAIL in fod/user.clj.


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


;;(with (show-Dialog (whc ["div"] [:p "Hello World"])
;;                   :js-options {:buttons "[{ text: \"Ok\", click: function(){ $(this).dialog(\"close\"); }}]"})
;;  (set-event-handler "dialogclose" it (fn [] (println "dialogclose"))))

(defn show-Dialog [widget & options]
  (with1 widget
    (jqAppend (root-element)
      (apply make-Dialog it options))))


(defn show-ModalDialog [widget & options]
  (with1 widget
    (apply show-Dialog it
           (alter-options options update-in [:js-options] assoc :modal :true))))


(defn close-Dialog [widget]
  (add-response-chunk (str "$('#" (:id @widget) "').dialog('close');")
                      widget))
