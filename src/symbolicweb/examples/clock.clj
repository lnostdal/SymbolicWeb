(ns symbolicweb.examples.clock
  (:use symbolicweb.core))



(defn mk-clock-viewport [request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)]
    (jqAppend root-widget
      (mk-b -now-))
    (mk-Viewport request session root-widget :page-title "SW: Clock example")))



(defapp
  [::Clock
   (fn [request]
     (re-find #"clock$" (:uri request)))]


  (fn [id & args]
    (apply mk-Session id :mk-viewport-fn mk-clock-viewport
           args)))
