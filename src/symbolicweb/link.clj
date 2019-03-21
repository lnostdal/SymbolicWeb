(in-ns 'symbolicweb.core)



(defn mk-Link
  "  URL-MAPPERS: {(vm-sync-from-url ..) (vm ..) ...}

  M: :SCROLL-TO-TOP?, :ON-CLICK-FN, :EVENT-STOP-PROPAGATION?"
  (^WidgetBase [url-mappers]
   (mk-Link url-mappers nil (mk-bte)))

  (^WidgetBase [url-mappers widget-or-m]
   (if (= WidgetBase (class widget-or-m))
     (mk-Link url-mappers nil widget-or-m)
     (mk-Link url-mappers widget-or-m (mk-bte))))

  (^WidgetBase [url-mappers m ^WidgetBase widget]
   (with-observed-vms (.lifetime widget)
     (when-let [viewport (viewport-of widget)]
       (jqAttr
        widget "href"
        (str "window.location.pathname + '?' + "
             (url-encode-wrap
              (ring.util.codec/form-encode
               (apply merge @(:query-params @viewport) ;; QUERY-PARAMS is a Sorted Map, and so result of MERGE will be too.
                      (map (fn [[url-mapper ^ValueModel url-mapper-mutator-vm]]
                             {(:name url-mapper) @url-mapper-mutator-vm})
                           url-mappers)))))
        true)))

   (set-event-handler "click" widget
                      (fn [& _]
                        ;; Extract this here before the VM-SET as that might lead to NIL for the VIEWPORT field in WIDGET.
                        (let [viewport (viewport-of widget)]
                          (doseq [[url-mapper ^ValueModel url-mapper-mutator-vm] url-mappers]
                            (vm-set (:model url-mapper) @url-mapper-mutator-vm))
                          (when (or (not (find m :scroll-to-top?))
                                    (:scroll-to-top? m))
                            ;; TODO: scrollLeft?
                            (js-run viewport "$('html, body').scrollTop(0);"))
                          (when-let [f (with (:on-click-fn m)
                                         (when (fn? it) it))]
                            (f widget))))
                      :js-before
                      (str (with (:on-click-fn m)
                             (when (string? it)
                               it))
                           " return(true);")
                      :js-after
                      (str (when (:event-stop-propagation? m)
                             "event.stopPropagation(); ")
                           "event.preventDefault(); return(false);"))

   widget))
