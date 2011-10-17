(in-ns 'symbolicweb.core)


(letfn [(handle-js-options [opts]
          (with-out-str
            (doseq [opt opts]
              (print (str (name (key opt)) ": "
                          (with (val opt)
                            (if (keyword? it)
                              (name it)
                              it))
                          ", ")))))]
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
                               ".dialog({" (handle-js-options js-options)
                               "close: function(event, ui){"
                               "  $('#" (:id @it) "').remove();"
                               on-close
                               "}});")
                          it))))


(defn show-Dialog [widget & options]
  (jqAppend (root-element)
    (apply make-Dialog widget options)))


(defn show-ModalDialog [widget & options]
  (apply show-Dialog widget
         ;; TODO: I seem to need this kind of thing rather often. Does something like this already exist in clj.core or in some of the contribs?
         (with-local-vars [lst (list)]
           (doseq [option (update-in (apply hash-map options) [:js-options] assoc :modal :true)]
             (var-set lst (conj (var-get lst) (val option) (key option))))
           (var-get lst))))
