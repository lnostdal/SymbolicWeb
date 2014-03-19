(in-ns 'symbolicweb.core)



(defn ^WidgetBase mk-Link
  "  URL-MAPPERS: {(vm-sync-from-url ..) (vm ..) ...}

  M: :SCROLL-TO-TOP?, :ON-CLICK-FN, :EVENT-STOP-PROPAGATION?"
  ([url-mappers]
     (mk-Link url-mappers nil (mk-bte)))

  ([url-mappers widget-or-m]
     (if (= WidgetBase (class widget-or-m))
       (mk-Link url-mappers nil widget-or-m)
       (mk-Link url-mappers widget-or-m (mk-bte))))

  ([url-mappers m ^WidgetBase widget]
     (let [query-str-vm (vm "")
           query-params (vm nil)]

       (vm-observe query-str-vm (.lifetime widget) false
                   #(jqAttr widget "href" (str "window.location.pathname + '?' + " (url-encode-wrap %3)) true))

       (doseq [[url-mapper url-mapper-mutator-vm] url-mappers]
         (with-observed-vms (.lifetime widget)
           (when-let [viewport (viewport-of widget)]
             (when-not @query-params
               (vm-set query-params @(:query-params @viewport)))
             (vm-set query-str-vm (ring.util.codec/form-encode
                                   @(with1 query-params
                                      ;; QUERY-PARAMS is a Sorted Map, and result of MERGE will be too.
                                      (vm-set it (merge @it {(:name url-mapper) @url-mapper-mutator-vm}))))))))

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
                          ;; TODO: Remove this when IE9 is gone.
                          ;; NOTE: Super, mega, hack for IE 9. :(. We clear the page while (before) re-rendering it to avoid some
                          ;; flickering the user isn't used to seeing. MS needs to just go away.
                          :js-before
                          (str (with (:on-click-fn m)
                                 (when (string? it)
                                   it))
                               " if(navigator.userAgent.search('MSIE 9') != -1) { $('#_body').css('display', 'none'); } return(true);")
                          :js-after (str (when (:event-stop-propagation? m) "event.stopPropagation(); ")
                                         "event.preventDefault(); return(false);"))

       widget)))
