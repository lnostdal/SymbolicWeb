(in-ns 'symbolicweb.core)



(defn default-aux-handler [request ^Ref session ^Ref viewport]
  (assert false "TODO: DEFAULT-AUX-HANDLER..??"))



(defn handle-out-channel-request [channel request ^Ref session ^Ref viewport]
  "Output (hanging AJAX; Comet) channel."
  (letfn [(do-it [^StringBuilder response-str]
            (http.server/send! channel
                               {:status 200
                                :headers {"Content-Type" "text/javascript; charset=UTF-8"}
                                :body (do
                                        (.append response-str "_sw_comet_response = true;")
                                        (with1 (.toString response-str)
                                          (.setLength response-str 0)))}))]
    (locking viewport
      (let [viewport-m @viewport
            response-sched-fn (:response-sched-fn viewport-m)
            ^StringBuilder response-str (:response-str viewport-m)]
        (if (pos? (.length response-str))
          (do-it response-str)
          (if @response-sched-fn
            (do
              ;;(println "HANDLE-OUT-CHANNEL-REQUEST: Hm, found existing RESPONSE-SCHED-FN for request: " request)
              (.runTask ^org.httpkit.timer.CancelableFutureTask @response-sched-fn))
            (reset! response-sched-fn
                    (org.httpkit.timer/schedule-task -comet-timeout-
                                                     (locking viewport
                                                       (when @response-sched-fn
                                                         (reset! response-sched-fn nil)
                                                         (do-it response-str))
                                                       nil)))))))))





(defn handle-in-channel-request [request ^Ref session ^Ref viewport]
  "Input (AJAX) channel."
  (case (get (:query-params request) "do")
    "widget-event"
    (let [query-params (:query-params request)
          widget-id (get query-params "_sw_widget-id")
          callback-id (get query-params "_sw_callback-id")
          widget (get (:widgets @viewport) widget-id)
          [^Fn callback-fn callback-data] (when widget (get @(.callbacks ^WidgetBase widget) callback-id))]
      (if widget
        (apply callback-fn (default-parse-callback-data-handler request widget callback-data))
        ;; TODO: Would it be sensible to send a reload page JS snippet here?
        (println "HANDLE-IN-CHANNEL-REQUEST (widget-event): Widget for event" callback-id "not found.")))


    "viewport-event"
    (let [query-params (:query-params request)
          callback-id (get query-params "_sw_callback-id")
          callback-entry (get @(:callbacks @viewport) callback-id)
          [^Fn callback-fn callback-data] callback-entry]
      (apply callback-fn (default-parse-callback-data-handler request viewport callback-data)))

    "unload"
    (gc-viewport viewport)

    "error"
    (log "HANDLE-IN-CHANNEL-REQUEST (JS error):"
         (json-parse (get (:params request) "msg"))))
  {:status 200
   :headers {"Content-Type" "text/javascript; charset=UTF-8"}
   :body ""})



(defn default-ajax-handler [request ^Ref session ^Ref viewport]
  (let [sw-request-type (get (:query-params request) "_sw_request_type")]
    (case sw-request-type
      "comet"
      (http.server/with-channel request channel
        (handle-out-channel-request channel request session viewport))

      "ajax"
      (handle-in-channel-request request session viewport)

      (throw (Exception. (str "DEFAULT-AJAX-HANDLER: Unknown _sw_request_type \"" sw-request-type "\" given."))))))



(defn default-request-handler [request ^Ref session]
  "Default top-level request handler for both REST and AJAX/Comet type requests."
  (with-once-only-ctx
    (let [request-type (get (:query-params request) "_sw_request_type")]
      (case request-type
        ("ajax" "comet")
        (if-let [^Ref viewport (get (ensure (:viewports @session))
                                    (get (:query-params request) "_sw_viewport_id"))]
          (do
            (touch viewport)
            ((:ajax-handler @session) request session viewport))
          (do
            (println "DEFAULT-REQUEST-HANDLER (AJAX): Got session, but not the Viewport ("
                     (get (:query-params request) "_sw_viewport_id") ")."
                     "Refreshing page, but keeping Session (cookie).")
            {:status 200
             :headers {"Content-Type" "text/javascript; charset=UTF-8"}
             ;; A new Session might have been started for this request.
             ;; TODO: This and SW-JS-BASE-BOOTSTRAP should be unified.
             :body (str (set-session-cookie (:uuid @session) (= "permanent" @(spget session :session-type)))
                        "window.location.href = window.location.href;")}))

        ;; REST.
        (let [viewport ((:mk-viewport-fn @session) request session)]
          ;; TODO: The name here seems counter intuitive since :REST-HANDLER is actually called below. Change or document this?
          (vm-set (:after-rest? @viewport) true)

          (if (= request-type "aux")
            ((:aux-handler @session) request session viewport)
            (with1 ((:rest-handler @session) request session viewport)
              (add-response-chunk "swDoOnLoadFNs();\n" (:root-element @viewport)))))))))



(defn default-rest-handler [request ^Ref session ^Ref viewport]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Cache-Control" "no-cache, no-store"
             "Expires" "-1"}
   :body
   (html
    (hiccup.page/doctype :html5)
    "<!-- λ SymbolicWeb: " (name (:name (:session-type @session))) " | Request #" @-request-counter- " λ -->\n\n"
    [:html
     [:head
      [:meta {:charset "UTF-8"}]
      [:title (:page-title @viewport)]

      (generate-rest-head @(:rest-head-entries @viewport))

      [:link {:rel "icon" :type "image/x-icon"
              :href "data:image/x-icon;base64,AAABAAEAEBAQAAAAAAAoAQAAFgAAACgAAAAQAAAAIAAAAAEABAAAAAAAgAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAjIyMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAQAAABAQAAAQAAAAAAAAAAAAAAABAAAAAAAAAAAAAQAAAAAAABAAEAAAAAAAAAAAAAAAAAABAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAD//wAA54cAAOOHAADzvwAA878AAPk/AAD5PwAA/H8AAPx/AAD+/wAA/v8AAP7/AAD8/wAA9P8AAPH/AAD//wAA"}]

      ;; User defind CSS.
      (generate-rest-css @(:rest-css-entries @viewport))

      ;; jQuery.
      "<!--[if lt IE 9]>"
      [:script {:src (gen-url viewport "sw/js/jquery-1.10.2.min.js")}]
      "<![endif]-->"
      "<!--[if gte IE 9]><!-->"
      [:script {:src (gen-url viewport "sw/js/jquery-2.0.3.min.js")}]
      "<!--<![endif]-->"

      ;; jQuery migrate.
      [:script {:src (gen-url viewport "sw/js/jquery-migrate-1.2.1.min.js")}]

      ;; SW specific.
      [:script (sw-js-base-bootstrap session viewport)]
      [:script {:src (gen-url viewport "sw/deps/TraceKit/tracekit.js")}]
      [:script {:src (gen-url viewport "sw/js/sw-ajax.js")}]

      ;; User defined JS.
      (generate-rest-js @(:rest-js-entries @viewport))]

     [:body {:id "_body"}
      [:noscript "SymbolicWeb: JavaScript needs to be enabled."]
      [:script "$(function(){ swBoot(); });"]]])})



(defn not-found-page-handler [request ^Ref session ^Ref viewport]
  {:status 404
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Cache-Control" "no-cache"}

   :body
   (html
    (hiccup.page/doctype :html5)
    "<!-- λ SymbolicWeb: Request #" @-request-counter- " λ -->\n\n"
    [:html
     [:head
      ;; Already set via HTTP header above, but perhaps useful in case the user wants to save a snapshot of the page.
      [:meta {:charset "UTF-8"}]
      ;; TODO: Extract from VIEWPORT.
      [:meta {:name "viewport" :content "width=device-width,initial-scale=1.0,maximum-scale=1.0"}]
      [:title "SW: 404 Page Not Found"]

     [:body
      [:h1 "SW: HTTP 404: Not Found"]
      [:p "Going " [:a {:href (str "//" (:server-name request) "/")} "home"] " might help."]]]])})



(defn simple-aux-handler [^Fn fn-to-wrap]
  (assert false "SIMPLE-AUX-HANDLER: Does this thing still work?")
  (fn []
    {:status 200
     :headers {"Content-Type" "text/javascript; charset=UTF-8"
               "Cache-Control" "no-cache"}
     :body (fn-to-wrap)}))
