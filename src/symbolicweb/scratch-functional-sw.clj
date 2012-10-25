(in-ns 'symbolicweb.core)


(def -sw- (agent {:applications {}}))



(defn mk-Application [id]
  {:id id
   :models {} ;; Cross-session models.
   :sessions {}})



(defn add-application [sw application]
  "Add APPLICATION to -SW-."
  ;;(assert (not (get (:applications sw) (:id application))))
  (update-in sw [:applications]
             assoc (:id application) application))



(defn mk-Session [id]
  {:id id
   :models {} ;; Per session models.
   :viewports {}}) ;; Per session views of both per session and cross-session models.



(defn add-session [sw application-id session]
  (assert (not (:application session)))
  (update-in sw [:applications application-id
                 :sessions]
             assoc (:id session) session))



(defn mk-Viewport [id]
  {:id id
   :agent (agent nil)
   :models {}
   :widgets {}})



(defn add-viewport [sw application-id session-id viewport]
  (assert (not (:session viewport)))
  (update-in sw [:applications application-id
                 :sessions session-id
                 :viewports]
             assoc (:id viewport) viewport))



(defn mk-Observable [^clojure.lang.Fn notify-observers-fn]
  {:observers #{}
   :notify-observers-fn notify-observers-fn})



(defn add-observer [sw pointer observer]
  )



(defn remove-observer [sw pointer observer]
  )



(defn observable-notify [sw pointer & args]
  (let [observable (get-in sw pointer)]      ;; Maybe POINTER here.
    (apply (:notify-observers-fn observable) observable args)))



(defn observe [sw observable-pt lifetime-pt ^clojure.lang.Fn callback]
  (if (get sw lifetime-pt)
    ;; TODO: Call to attach-lifetime happens before the add-lifetime.. calls here.
    (let [inner-lifetime (mk-Lifetime)
          [inner-lifetime-pt sw] (attach-lifetime sw lifetime-pt inner-lifetime)
          callback (partial callback inner-lifetime-pt)]
      [(-> sw
           (add-lifetime-activation-fn inner-lifetime-pt (fn [_] (add-observer sw observable-pt callback)))
           (add-lifetime-deactivation-fn inner-lifetime-pt (fn [_] (remove-observer sw observable-pt callback))))
       inner-lifetime-pt])
    [(add-observer sw pointer (partial callback false))
     false]))



(defn mk-ValueModel [initial-value]
  {:value initial-value
   :observable (mk-Observable (fn [observable old-value new-value]
                                (when-not (= old-value new-value) ;; TOOD: = is magic value.
                                  (doseq [^clojure.lang.Fn observer-fn (:observers observable)]
                                    (observer-fn old-value new-value)))))})



(defn vm-set [sw pointer new-value]
  (let [value-model (get-in sw pointer)
        old-value (:value value-model)]
    (-> sw
        (update-in pointer assoc :value new-value)
        (observable-notify (:observable value-model) old-value new-value))))



(defn vm-observe [sw pointer lifetime ^Boolean initial-sync? ^clojure.lang.Fn callback]
  )





(defn test-functional-sw-2 []
  (def -sw- (agent {:applications {}}))
  (let [new-application (mk-Application :app-id)
        new-session (mk-Session :session-id)
        new-viewport (mk-Viewport :viewport-id)]
    (send -sw- #(-> %
                    (add-application new-application)
                    (add-session (:id new-application) new-session)
                    (add-viewport (:id new-application) (:id new-session) new-viewport)))
    (await1 -sw-)
    (clojure.pprint/pprint @-sw-)))







;; TODO: Error handlers for the Agents.
(defn test-functional-sw []
  (letfn [(update-viewport [^clojure.lang.Agent gm
                            ^clojure.lang.PersistentVector pointer
                            ^String js]
            (send-off (get-in @gm (conj pointer :agent)) ;; Grab the Viewport Agent.
                      (fn [_]
                        (Thread/sleep 200)
                        (println "VIEWPORT:" js))))


          (update-value-model [gm pointer v]
            (update-in gm pointer assoc :value v))]


    (let [global-model
          (agent {:applications
                  {:application-1 ;; E.g. :FreeOrDeal

                   ;; Cross-session models.
                   {:models {:some-app-model 0}

                    :sessions
                    {:session-1

                     ;; Per session models.
                     {:models {:some-session-model {:value 0}}

                      ;; Per session views of both cross- and per session models.
                      :viewports
                      {:viewport-1
                       {:agent (agent :viewport-agent)

                        ;; WidgetBase instances.
                        :widgets
                        {"sw-42"
                         {:render-html (fn [w] (str "<div id='" (:id w) "'></div>"))}}}}}}}}})]

      (update-viewport global-model [:applications :application-1
                                     :sessions :session-1
                                     :viewports :viewport-1]
                       "alert('hello world!');")


      (send-off global-model
                (fn [gm]

                  #_(-> gm
                      (update-value-model [:applications :application-1
                                           :sessions :session-1
                                           :models :some-session-model]
                                          42))
                  (update-in gm [:applications :application-1 :sessions :session-1 :models]
                             update-in [:sessions :session-1]
                             update-in [:models :some-session-model]
                             assoc :value 42)))

      (await1 global-model)
      @global-model
      )))
