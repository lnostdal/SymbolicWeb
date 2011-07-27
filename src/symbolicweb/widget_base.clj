(in-ns 'symbolicweb.core)

(defn ensure-agent [& objects]
  (map #(if (= clojure.lang.Agent (type %))
          %
          (agent %))
       objects))


(defn render-event [widget event-type & {:keys [js-before callback-data js-after]
                                         :or {js-before "return(true);"
                                              callback-data ""
                                              js-after ""}}]
  (str "$('#" (:id widget) "').bind('" event-type "', "
       "function(event){"
       "swMsg('" (:id widget) "', '" event-type "', function(){" js-before "}, '"
       ;; TODO: This is quite horrible.
       (let [res (with-out-str
                   (doall (map (fn [key_val]
                                 (print (str (url-encode (str (key key_val)))
                                             "="
                                             (val key_val)
                                             "&")))
                               callback-data)))]
         (subs res 0 (- (count res) 1)))
       "', function(){" js-after "});"
       "});"))


(defn render-events [widget]
  (with-out-str
    (loop [callbacks (:callbacks widget)]
      (when-first [[event-type [callback-fn callback-data]] callbacks]
        (print (render-event widget event-type :callback-data callback-data))
        (recur (rest callbacks))))))


(defn render [widget]
  (let [widget (if (= (type widget) clojure.lang.Agent)
                 @(await1 widget)
                 widget)]
    (cond
     (= (type widget) clojure.lang.PersistentArrayMap)
     (str ((:render-fn widget) widget)
          (when (pos? (count (:callbacks widget)))
            (str "<script type='text/javascript'>" (render-events widget) "</script>")))

     (= (type widget) java.lang.String)
     (escape-html widget)

     true
     (throw (Exception. (str "Can't render " widget "."))))))


(defn render-children [widget]
  (with-out-str []
    (loop [children (:children widget)]
      (when-first [child children]
        (print (render child))
        (recur (rest children))))))


(defn add-children [root-Container & children]
  (send root-Container #(update-in % [:children] into (apply ensure-agent children))))


(defn set-event-handler [event-type widget callback-fn & {:keys [callback-data]}]
  (send widget #(update-in % [:callbacks] conj [event-type [callback-fn callback-data]])))


(defn update-widget-data [widget new-widget-data]
  {:pre (= clojure.lang.Agent (type widget))}
  (send widget (fn [_] new-widget-data)))


(defn make-ID
  ([] (make-ID {}))
  ([m] (assoc m :id (generate-aid))))


(defn make-WidgetBase []
  (with1 (agent (assoc (make-ID)
                  :type ::WidgetBase
                  :parent nil
                  :callbacks {} ;; event-name -> [handler-fn callback-data]
                  :render-fn #(throw (Exception. (str "No :RENDER-FN defined for this widget (ID: " (:id %) ").")))
                  :parse-callback-data-handler #'default-parse-callback-data-handler))
         ;; There's no way we'll create a Widget in the context of one Viewport and use it or "send" it to another Viewport anyway.
         (send *viewport* #(update-in % [:widgets] conj [(:id @it) it]))))


(defn make-Widget [type html-element-type]
  (send (make-WidgetBase)
        #(assoc % :type type :html-element-type html-element-type)))


(defn make-HTMLElement [html-element-type & children]
  (send (make-Widget ::HTMLElement html-element-type)
        (fn [m] (assoc m
                  :children (if children
                              (into [] (apply ensure-agent children))
                              [])
                  :render-fn #(str "<" (:html-element-type %) " id='" (:id %) "'>"
                                   (render-children %)
                                   "</" (:html-element-type %) ">")))))


(defn make-Button [& children]
  (send (apply make-HTMLElement "button" children)
        #(assoc % :type ::Button)))
