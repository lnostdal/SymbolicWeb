(in-ns 'symbolicweb.core)


;;; Represents a browser window or tab within a single browser Session
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(declare set-viewport-event-handler)
(defn mk-Viewport [request ^Ref session ^WidgetBase root-widget & args]
  "This will instantiate a new Viewport and also 'register' it as a part of SESSION and the server via -VIEWPORTS-."
  (assert (= :lifetime-root @(.parent (.lifetime root-widget))))
  (let [viewport-id (str "sw-" (generate-uid))
        viewport (ref (apply assoc {}
                             :id viewport-id
                             :last-activity-time (atom (System/currentTimeMillis))

                             ;; DOM events for Viewport (not Widgets).
                             :callbacks (ref {}) ;; Viewport specific callbacks. E.g. window.onpopstate etc..

                             ;; HTML5 History stuff.
                             :query-params (vm (into (sorted-map) (:query-params request)))
                             :popstate-observer (vm {})

                             :scrolled-to-bottom-event (vm 0)

                             :page-title "SW"

                             ;; gen-url
                             :genurl-fs-path "resources/web-design/"
                             :genurl-domain (or (get (:headers request) "x-forwarded-host")
                                                (:server-name request))
                             :genurl-path "static/"

                             ;; Comet.
                             :response-str (StringBuilder.)
                             :response-sched-fn (atom nil)
                             :response-agent (mk-sw-agent {:executor clojure.lang.Agent/pooledExecutor} nil nil)

                             ;; Resources; maps in Clojure (still?) seem to maintain order.
                             :rest-css-entries (ref {})
                             :rest-js-entries (ref {})
                             :rest-head-entries (ref [])
                             :html-templates (ref #{}) ;; mk-HTMLCTemplate

                             :session session
                             :root-element root-widget ;; TODO: Rename...
                             :widgets {(.id root-widget) root-widget} ;; Viewport --> Widget  (DOM events.)
                             args))]

    ;; HTML5 History handler.
    (set-viewport-event-handler "window" "popstate" viewport
                                (fn [& {:keys [query-string]}]
                                  (let [query-params (into (sorted-map) (ring.util.codec/form-decode query-string))]
                                    (vm-set (:query-params @viewport) query-params)
                                    (vm-set (:popstate-observer @viewport) query-params)
                                    (vm-set (:popstate-observer @viewport) nil)))
                                :callback-data {:query-string "' + encodeURIComponent(window.location.search.slice(1)) + '"})

    ;; Viewport scrolled to the bottom
    #_(set-viewport-event-handler "window" "sw_scrollbottom" viewport
                                (fn [& _] (vm-alter (:scrolled-to-bottom-event @viewport) inc)))
    ;; TODO: Reduce spammy event rate: http://underscorejs.org/#throttle
    #_(add-response-chunk
     (str "$(window).scroll(function(){"
          "  if(50 > $(document).height() - $(window).height() - $(window).scrollTop()){"
          "    $(window).trigger('sw_scrollbottom');"
          "  }"
          "});\n")
     viewport)

    ;; Session --> Viewport
    (alter (:viewports @session) assoc viewport-id viewport)
    ;; Widget --> Viewport.
    (vm-set (.viewport root-widget) viewport)
    viewport))



(defn add-rest-css [^Ref viewport rest-css-entry]
  (when-not (get (ensure (:rest-css-entries @viewport)) (:url rest-css-entry))
    (alter (:rest-css-entries @viewport)
           assoc (:url rest-css-entry) rest-css-entry)
    (add-lifetime-activation-fn (.lifetime (:root-element @viewport))
                                (fn [_]
                                  (let [rest-css-entry (get (ensure (:rest-css-entries @viewport)) (:url rest-css-entry))]
                                    ;; Not already added via code in DEFAULT-REST-HANDLER?
                                    (when-not (:already-added? rest-css-entry)
                                      (js-run viewport
                                        "$('<link rel=\"stylesheet\" href=\"" (:url rest-css-entry) "\">').appendTo('head');")
                                      (alter (:rest-css-entries @viewport)
                                             assoc (:url rest-css-entry) (assoc rest-css-entry :already-added? true))))))))



(defn add-rest-js [^Ref viewport rest-js-entry]
  (when-not (get (ensure (:rest-js-entries @viewport)) (:url rest-js-entry))
    (alter (:rest-js-entries @viewport)
           assoc (:url rest-js-entry) rest-js-entry)
    (add-lifetime-activation-fn (.lifetime (:root-element @viewport))
                                (fn [_]
                                  (let [rest-js-entry (get (ensure (:rest-js-entries @viewport)) (:url rest-js-entry))]
                                    ;; Not already added to page via code in DEFAULT-REST-HANDLER?
                                    (when-not (:already-added? rest-js-entry)
                                      (js-run viewport
                                        "$('<script src=\"" (:url rest-js-entry) "\"></script>').appendTo('head');")
                                      (alter (:rest-js-entries @viewport)
                                             assoc (:url rest-js-entry) (assoc rest-js-entry :already-added? true))))))))



(defn add-rest-head [^Ref viewport rest-head-entry]
  (alter (:rest-head-entries @viewport) conj rest-head-entry))



(defn add-resource [^Ref viewport ^Keyword type ^String url]
  (case type
    :css
    (add-rest-css viewport (mk-rest-css-entry (gen-url viewport url)))

    :js
    (add-rest-js viewport (mk-rest-js-entry (gen-url viewport url)))))



(defn add-response-chunk-agent-fn [^Ref viewport viewport-m ^String new-chunk]
  (with-sw-agent (:response-agent viewport-m)
    (locking viewport
      (.append ^StringBuilder (:response-str viewport-m) new-chunk)))
  (swap! *dyn-ctx* assoc [::add-response-chunk-agent-fn viewport]
         (fn []
           (with-sw-agent (:response-agent viewport-m)
             (locking viewport
               (let [response-sched-fn ^Atom (:response-sched-fn viewport-m)]
                 (when @response-sched-fn
                   (.runTask ^org.httpkit.timer.CancelableFutureTask @response-sched-fn))))))))



(defn add-response-chunk [^String new-chunk widget]
  "WIDGET: A WidgetBase or Viewport instance."
  (if (= (class widget) WidgetBase)
    (if-let [^Ref viewport (viewport-of ^WidgetBase widget)] ;; Visible?
      (add-response-chunk-agent-fn viewport @viewport new-chunk)
      (when-not (= :deactivated (lifetime-state-of (.lifetime ^WidgetBase widget)))
        (add-lifetime-activation-fn (.lifetime ^WidgetBase widget)
                                    (fn [_]
                                      (let [^Ref viewport (viewport-of ^WidgetBase widget)]
                                        (add-response-chunk-agent-fn viewport @viewport new-chunk))))))
    (add-response-chunk-agent-fn widget @widget new-chunk))) ;; WIDGET is assumed to be a Viewport.



(defn set-viewport-event-handler [^String selector ^String event-type ^Ref viewport ^Fn callback-fn
                                  & {:keys [js-before callback-data js-after once?]
                                     :or {js-before "return(true);"
                                          callback-data {}
                                          js-after ""}}]
  (let [;; CSRF security check token. Check is done in HANDLE-IN-CHANNEL-REQUEST.
        callback-data (conj callback-data [:sw-token (subs (generate-uuid) 0 8)])
        cb-id (str selector "_" event-type)]
    (alter (:callbacks @viewport) assoc cb-id
           [(if once?
              ;; Unbind event handler on server side before executing it once.
              (comp callback-fn
                    (fn [& args]
                      (alter (:callbacks @viewport) dissoc cb-id)
                      args))
              callback-fn)
            callback-data])
    (js-run viewport
      "$(" selector ").off('" event-type "')"
      (if once? ".one" ".on") "('" event-type"', "
      "function(event){"
      "swViewportEvent('" selector "_" event-type "', function(){" js-before "}, '"
      (apply str (interpose \& (map #(str (url-encode-component (str %1)) "=" %2)
                                    (keys callback-data)
                                    (vals callback-data))))
      "', function(){" js-after "});"
      "});")
    viewport))
