(in-ns 'symbolicweb.core)



(defn default-aux-handler [request ^Ref session ^Ref viewport]
  (assert false "TODO: DEFAULT-AUX-HANDLER..??"))



(defn handle-out-channel-request [^org.httpkit.server.AsyncChannel channel request ^Ref session ^Ref viewport]
  "Output (hanging AJAX; Comet) channel."
  (letfn [(do-it [^StringBuilder response-str]
            (when (http.server/send! channel
                                     {:status 200
                                      :headers {"Content-Type" "text/javascript; charset=UTF-8"}
                                      :body (do
                                              (.append response-str "_sw_comet_response_p = true;\n")
                                              (.toString response-str))})
              (.setLength response-str 0)))]
    (locking viewport
      (let [viewport-m @viewport
            response-sched-fn ^Atom (:response-sched-fn viewport-m)
            ^StringBuilder response-str (:response-str viewport-m)]
        (if (pos? (.length response-str))
          (do-it response-str)
          (do
            (when @response-sched-fn
              ;;(println "HANDLE-OUT-CHANNEL-REQUEST: Hm, found existing RESPONSE-SCHED-FN for request; removing it.")
              (org.httpkit.timer/cancel @response-sched-fn))
            (reset! response-sched-fn
                    (org.httpkit.timer/schedule-task -comet-timeout-
                                                     (locking viewport
                                                       (when @response-sched-fn
                                                         (reset! response-sched-fn nil)
                                                         (do-it response-str)))))))))))



(defn handle-in-channel-request [request ^Ref session ^Ref viewport]
  "Input (AJAX) channel."
  (case (get (:query-params request) "do")
    "widget-event"
    (let [query-params (:query-params request)
          widget-id (get query-params "_sw_widget-id")
          callback-id (get query-params "_sw_callback-id")
          widget (get (:widgets @viewport) widget-id)
          [^Fn callback-fn callback-data] (when widget
                                            (get (ensure (.callbacks ^WidgetBase widget))
                                                 callback-id))]
      (if (and widget callback-fn)
        (let [parsed-callback-data (default-parse-callback-data-handler request widget callback-data)]
          (assert (= (:sw-token callback-data)
                     (:sw-token (apply hash-map parsed-callback-data)))
                  "CSRF security check failed.")
          (apply callback-fn parsed-callback-data))
        ;; TODO: Would it be sensible to send a reload page JS snippet here?
        (do
          (println "HANDLE-IN-CHANNEL-REQUEST (widget-event): Widget or callback-fn for event" callback-id "not found."))))

    "viewport-event"
    (let [query-params (:query-params request)
          callback-id (get query-params "_sw_callback-id")
          callback-entry (get @(:callbacks @viewport) callback-id)
          [^Fn callback-fn callback-data] callback-entry
          parsed-callback-data (default-parse-callback-data-handler request viewport callback-data)]
      (assert (= (:sw-token callback-data)
                 (:sw-token (apply hash-map parsed-callback-data)))
              "CRSF security check failed.")
      (apply callback-fn parsed-callback-data))

    "unload"
    (gc-viewport viewport)

    "error"
    (log "HANDLE-IN-CHANNEL-REQUEST (JS error):"
         (json-parse (get (:params request) "msg"))))
  (js-run viewport "swCancelSpinner();")
  {:status 200
   :headers {"Content-Type" "text/javascript; charset=UTF-8"}
   :body (do
           ;; For this to work the call to .runTask in ADD-RESPONSE-CHUNK-REF-FN shouldn't happen in context of AJAX requests;
           ;; only Comet requests.
           #_(locking viewport
               (let [^StringBuilder response-str (:response-str @viewport)]
                 (with1 (.toString response-str)
                   (.setLength response-str 0))))
           "")})



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
  (let [request-type (get (:query-params request) "_sw_request_type")]
    (case request-type
      ;; XHR.
      ("ajax" "comet")
      (if-let [^Ref viewport (get (ensure (:viewports @session))
                                  (get (:query-params request) "_sw_viewport_id"))]
        (do
          (touch viewport)
          ((:ajax-handler @session) request session viewport))
        (do
          #_(println "DEFAULT-REQUEST-HANDLER (AJAX): Got session, but not the Viewport ("
                     (get (:query-params request) "_sw_viewport_id") ")."
                     "Refreshing page, but keeping Session (cookie).")
          {:status 200
           :headers {"Content-Type" "text/javascript; charset=UTF-8"}
           ;; A new Session _might_ have been started for this request, so we reset the cookie just in case before reloading.
           ;; TODO: This and SW-JS-BASE-BOOTSTRAP should be unified.
           :body (str (set-session-cookie (:uuid @session) (= "permanent" @(spget session :session-type)))
                      "window.location.href = window.location.href;")}))

      ;; REST.
      (let [viewport ((:mk-viewport-fn @session) request session)]
        (do1 (if (= request-type "aux")
               ((:aux-handler @session) request session viewport)
               ((:rest-handler @session) request session viewport)) ;; E.g. DEFAULT-REST-HANDLER, below.
          (do-lifetime-activation (.lifetime (:root-element @viewport))))))))



(defn default-rest-handler [request ^Ref session ^Ref viewport]
  (js-run (:root-element @viewport) "swDoOnLoadFNs();")
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Cache-Control" "no-cache, no-store, must-revalidate"
             "Expires" "0"}
   :body
   (html
    (hiccup.page/doctype :html5)
    "<!-- ############################################################################### -->\n"
    "<!-- ## Runs on the SymbolicWeb platform: https://github.com/lnostdal/SymbolicWeb ## -->\n"
    "<!-- ############################################################################### -->\n"
    [:html
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"}]
      [:title (:page-title @viewport)]

      (generate-rest-head (ensure (:rest-head-entries @viewport)))

      (with-string-builder sb
        (doseq [[k entry] (reverse (ensure (:rest-css-entries @viewport)))]
          (strb sb "<link rel=\"stylesheet\" href=\"" (:url entry) "\">")
          (alter (:rest-css-entries @viewport)
                 assoc k (assoc entry :already-added? true))))

      ;; jQuery.
      [:script {:src (gen-url viewport "sw/js/jquery-2.1.4.min.js")}]

      ;; SW specific.
      [:script (sw-js-base-bootstrap session viewport)]
      [:script {:src (gen-url viewport "sw/js/sw-ajax.js")}]

      (with-string-builder sb
        (doseq [[k entry] (reverse (ensure (:rest-js-entries @viewport)))]
          (strb sb "<script src=\"" (:url entry) "\"></script>")
          (alter (:rest-js-entries @viewport)
                 assoc k (assoc entry :already-added? true))))]

     [:body {:id "_body"}
      [:div {:id "_sw_htmlctemplates" :style "display: none !important;"}] ;; Used by mk-HTMLCTemplate
      (:initial-html @viewport) ;; add-rest-initial
      [:noscript
       [:h3 "JavaScript needs to be enabled in your browser"]
       [:p [:a {:href "https://encrypted.google.com/search?hl=en&q=how%20to%20enable%20javascript"}
            "Click here"] " to see how you can enable JavaScript in your browser."]]
      (when-not (:initial-html @viewport)
        [:p {:id "sw-page-is-loading-msg" :style "padding: 1em;"} "Loading..."])
      [:script
       (when-not (:initial-html @viewport)
         "swAddOnLoadFN(function(){ $('#sw-page-is-loading-msg').remove(); });")
       "$(function(){ swBoot(); });"]]])})



(defn not-found-page-handler [request ^Ref session ^Ref viewport]
  {:status 404
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Cache-Control" "no-cache, no-store, must-revalidate"
             "Expires" "0"}
   :body
   (html
    (hiccup.page/doctype :html5)
    [:html
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"}]
      [:title "SW: 404 Page Not Found"]

     [:body
      [:h1 "SW: HTTP 404: Not Found"]
      [:p "Going " [:a {:href (str "//" (:server-name request) "/")} "home"] " might help."]]]])})



(defn simple-aux-handler [^Fn fn-to-wrap]
  (assert false "SIMPLE-AUX-HANDLER: Does this thing still work?")
  (fn []
    {:status 200
     :headers {"Content-Type" "text/javascript; charset=UTF-8"
               "Cache-Control" "no-cache, no-store, must-revalidate"
               "Expires" "0"}
     :body (fn-to-wrap)}))
