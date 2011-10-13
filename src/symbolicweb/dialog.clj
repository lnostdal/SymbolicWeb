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

  (defn make-Dialog
    "\"Convert\" WIDGET into a jQuery UI Dialog.
DIALOG-JS-OPTIONS can be e.g. {:width 800 :modal true} etc., see the jQuery UI Dialog docs."
    ([widget] (make-Dialog widget {}))
    ([widget dialog-js-options]
       (with1 widget
         (add-response-chunk (str "$('#" (:id @it) "')"
                                  ".dialog({" (handle-js-options dialog-js-options)
                                  "close: function(event, ui){"
                                  "  $('#" (:id @it) "').remove();"
                                  "}});")
                             it)))))
