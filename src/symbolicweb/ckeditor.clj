(in-ns 'symbolicweb.core)


#_(defn mk-CKEditor [model & attributes]
  (apply mk-TextArea model
         :render-aux-js-fn (fn [widget]
                             (let [id (.id widget)]
                               (str "CKEDITOR.replace('" id "');"
                                    "CKEDITOR.instances['" id "'].on('blur', function(e){"
                                    "  if(CKEDITOR.instances['" id "'].checkDirty()){"
                                    "    CKEDITOR.instances['" id "'].updateElement();"
                                    "    $('#" id "').trigger('change');"
                                    "  }"
                                    "  CKEDITOR.instances['" id "'].resetDirty();"
                                    "});")))
         attributes))
