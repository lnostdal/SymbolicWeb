(ns symbolicweb.examples.bitcoin
  (:import clojure.lang.Ref
           clojure.lang.Keyword
           clojure.lang.Fn
           clojure.lang.MapEntry)
  (:use symbolicweb.core)
  (:import symbolicweb.core.WidgetBase
           symbolicweb.core.ValueModel
           symbolicweb.core.ContainerModel
           symbolicweb.core.ContainerModelNode)
  (:use hiccup.core)
  (:require [clj-time.core :as time])
  ;;(:require clj-http.core)
  (:require clj-http.client)
  )


;;; Some ideas
;;; ----------
;;
;; Scenario: user wants $100 worth of Bitcoin sent to some address.
;; He can then (via query params) supply this app with the address, the amount and the data source for BTC exchange value.
;; ...



(def -bitcoin-bindings- (get-thread-bindings))
(defonce -btc-data-
  (vm {:all (vm nil)}))



(let [last-sync-ts (atom (time/now))]
  (defn do-bitcoin [request ^Ref session]
    (Thread/sleep 1000)
    (let [now (time/now)]
      (when (> (time/in-seconds (time/interval @last-sync-ts now))
               15)
        (reset! last-sync-ts now)
        (swsync
         (vm-set (:all @-btc-data-)
                 (with-out-str
                   (doseq [[k v] (json-parse (:body (clj-http.client/get "https://api.bitcoinaverage.com/ticker/all")))]
                     (println k)
                     (println v) (println) (println)))))))))



(defn mk-bitcoin-viewport [request ^Ref session]
  #_(defonce -bitcoin-thread-
    (future
      (loop []
        (with-bindings -bitcoin-bindings-
          (try
            (do-bitcoin request session)
            (catch Throwable e
              (println "## -DO-BITCOIN- ##")
              (clojure.stacktrace/print-stack-trace e 50)
              (Thread/sleep 1000))))
        (recur))))

  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        viewport (mk-Viewport request session root-widget
                              :page-title "nostdal.org: bitcoin")]
    (jqCSS root-widget "font-family" "sans-serif")
    (add-rest-head viewport "<link href='data:image/x-icon;base64,AAABAAEAEBAQAAAAAAAoAQAAFgAAACgAAAAQAAAAIAAAAAEABAAAAAAAgAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAjIyMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAQAAABAQAAAQAAAAAAAAAAAAAAABAAAAAAAAAAAAAQAAAAAAABAAEAAAAAAAAAAAAAAAAAABAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAD//wAA54cAAOOHAADzvwAA878AAPk/AAD5PwAA/H8AAPx/AAD+/wAA/v8AAP7/AAD8/wAA9P8AAPH/AAD//wAA' rel='icon' type='image/x-icon' />")

    (jqAppend root-widget
      (whc [:div]
        [:h2 [:a {:href "https://nostdal.org/"} "nostdal.org"] ": bitcoin"]
        (sw (mk-pre (:all @-btc-data-)))
        ))

    viewport))



(defmethod symbolicweb.examples.nostdal-org/mk-nostdal-org-viewport "/bitcoin" [^String uri request session]
  (mk-bitcoin-viewport request session))
