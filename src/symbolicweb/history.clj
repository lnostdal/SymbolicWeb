(ns symbolicweb.core)



(defn url-alter-query-params [^Ref viewport ^Boolean replace? f & args]
  "Directly alters query-params of URL for Viewport.

  REPLACE?: If True, a history entry will be added at the client end."
  (apply vm-alter (:query-params @viewport) f args)
  (add-response-chunk (str "window.history." (if replace? "replaceState" "pushState")
                           "(null, '', '?" (ring.util.codec/form-encode @(:query-params @viewport)) "');\n")
                      viewport))



(defn vm-sync-to-url [m]
  "Maps :MODEL to Viewport URL based on :NAME. A one-way sync for the Lifetime of :CONTEXT-WIDGET: Server --> Client.

Returns M."
  ;; Server --> Client; via CONTEXT-WIDGET.
  (with1 m
    (let [name (:name m)
          model (:model m)
          viewport (:viewport m)
          context-widget (:context-widget m)]
      (vm-observe model (.lifetime context-widget) false
                  (fn [_ _ new-value]
                    ;; TODO: Remove key/value pair from URL when NEW-VALUE is NIL?
                    (when (and new-value (not= new-value (get @(:query-params @viewport) name)))
                      (url-alter-query-params viewport false assoc name new-value))))
      ;; Remove URL entry when Lifetime of CONTEXT-WIDGET ends.
      (add-lifetime-deactivation-fn (.lifetime context-widget)
                                    (fn [_] (url-alter-query-params viewport true dissoc name))))))



(defn vm-sync-from-url [m]
  "Maps Viewport URL to :MODEL based on :NAME. A one-way sync for the Lifetime of :VIEWPORT or the Viewport of
:CONTEXT-WIDGET: Client --> Server.

If :CONTEXT-WIDGET is given, VM-SYNC-TO-URL is also called with M as argument.

Returns M."
  (with1 m
    (let [name (:name m)
          model (:model m)
          viewport (:viewport m)
          context-widget (:context-widget m)]
      (assert (string? name))
      (assert (isa? (class model) ValueModel))
      (if-let [viewport (or viewport (and context-widget @(.viewport context-widget)))]
        (do
          ;; Initialization.
          ;; TODO: Make init in this direction optional?
          ;; TODO: Remove if not found?
          (if-let [value (get @(:query-params @viewport) name)]
            ;; Something found in URL already; sync Client --> Server.
            (vm-set model value)
            ;; Nothing found in URL; sync Server --> Client.    TODO: (when-let [value @model] ...) here?
            (url-alter-query-params viewport true assoc name @model))

          ;; Client --> Server; via VIEWPORT – i.e. before CONTEXT-WIDGET is active.
          (vm-observe (:popstate-observer @viewport) (.lifetime (:root-element @viewport)) false
                      (fn [_ _ value]
                        (when value
                          (with-delayed-reactions
                            (doseq [[k v] value]
                              (when (= k name)
                                (vm-alter (:query-params @viewport) assoc name v)
                                (vm-set model v)))))))
          (when context-widget
            (vm-sync-to-url m)))

        ;; No Viewport found anywhere; observe CONTEXT-WIDGET until it has a Viewport set then try again.
        (when context-widget
          (vm-observe (.viewport context-widget) (.lifetime context-widget) true
                      (fn [_ _ viewport]
                        (when viewport
                          (vm-sync-from-url (assoc m :viewport viewport))))))))))
