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
  (let [res (add-response-chunk (str "$('#" (:id @widget) "')"
                                     ".dialog({"
                                     (map-to-js-options js-options)
                                     "close: function(event, ui){"
                                     "  $('#" (:id @widget) "').remove();"
                                     on-close
                                     "}});")
                                widget)]
    (if *with-js?*
      res
      widget)))




;;(with (show-Dialog (whc ["div"] [:p "Hello World"])
;;                   :js-options {:buttons "[{ text: \"Ok\", click: function(){ $(this).dialog(\"close\"); }}]"})
;;  (set-event-handler "dialogclose" it (fn [] (println "dialogclose"))))


(defn show-Dialog [widget & options]
  ;; TODO: Yeah, (ROOT-ELEMENT) doesn't generally mean the same thing anymore when the root can be the entire document.
  (jqAppend (root-element) ;;(:main-content-view @*viewport*)
    (apply make-Dialog widget options)))


(defn show-ModalDialog [widget & options]
  (apply show-Dialog widget
         (alter-options options update-in [:js-options] assoc :modal :true)))


(defn close-Dialog [widget]
  ;; TODO: But this never removes it on the server end; i.e. jqAppend also accumulates on the server end.
  (add-response-chunk (str "$('#" (:id @widget) "').dialog('close');")
                      widget))
