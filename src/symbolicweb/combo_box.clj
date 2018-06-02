(in-ns 'symbolicweb.core)


;; TODO: This thing is work in progress. Search for "page=blank" in main.clj.
;; TODO: Might need a ContainerModelWithOneActiveNode type thing, like in old-Symbolicweb. (probably not actually)

(defn mk-ComboBoxElt [value-model]
  ;;(vm-sync vm (fn [v o n] n))
  (ref {:id (cl-format false "~36R" (generate-uid))
        :text (vm value-model)}))



(derive ::ComboBox ::ContainerView)
(defn mk-ComboBox [container-model & attributes]
  (with1 (apply mk-ContainerView "select" container-model
                :type ::ComboBox
                :view-from-node-fn (fn [container-view node]
                                     (dosync
                                      (let [model (cmn-data node)]
                                        (mk-HTMLElement "option" (:text @model)
                                                          :static-attributes {:value (:id @model)}))))
                attributes)
    (set-event-handler "change" it
                       (fn [& {:keys [new-value]}]
                         (dbg new-value)
                         ;; Search for Node matching NEW-VALUE.
                         (dosync
                          (loop [node (cm-head-node container-model)]
                            (when node
                              (if (= new-value (:id @(cmn-data node)))
                                (do (println "match found!") (println))
                                (recur (cmn-right-node node)))))))
                       :callback-data {:new-value "' + encodeURIComponent($(this).val()) + '"})))
