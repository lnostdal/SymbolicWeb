(ns symbolicweb.examples.clock
  (:use symbolicweb.core))



(defonce -clock-vm- (vm (.toString (clj-time.core/now))))



(defn mk-clock-viewport [request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        viewport (mk-Viewport request session root-widget :page-title "SW: Clock example")]
    (add-resource viewport :css "sw/css/common.css")
    (jqAppend root-widget
      (whc [:div]
        [:h2 "SymbolicWeb: Clock example"]
        [:p "This simple example shows &quot;" [:a {:href "https://en.wikipedia.org/wiki/Comet_%28programming%29"}
                                                "server push"] "&quot; in action."]
        [:p (sw (mk-b -clock-vm-))]))
    viewport))



(defonce -clock-thread-
  (future
    (loop []
      (Thread/sleep 1000)
      (swsync (vm-set -clock-vm- (.toString (clj-time.core/now))))
      (recur))))



(defmethod symbolicweb.examples.nostdal-org/mk-nostdal-org-viewport "/clock" [uri request session]
  (mk-clock-viewport request session))
