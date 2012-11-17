(in-ns 'symbolicweb.core)

(defn ^Boolean ref? [x]
  (= Ref (type x)))

(def -http-server-string- "SymbolicWeb (nostdal.org)")

;; Long poll timeout every X / 1000 seconds.
(def -comet-timeout- 25000)

;; X / 1000 seconds since -COMET-TIMEOUT- and still no new request (long poll)?
(def -viewport-timeout- (+ -comet-timeout- 10000))

;; X / 1000 seconds since -VIEWPORT-TIMEOUT- and still no new request (long poll) from any Viewport in the session?
(def -application-timeout- (+ -viewport-timeout- 10000))

(def -request-counter-
  "Number of HTTP requests since server started."
  (atom 0))


(def -application-types-
  "name -> {:fit-fn fit-fn
            :application-constructor-fn application-constructor-fn]"
  (atom {}))


(def -num-applications-model-
  (vm 0))

(def -applications-
  "SESSION-COOKIE-VALUE -> APPLICATION"
  (atom {}))

(def -viewports-
  "ID -> VIEWPORT"
  (atom {}))


(defonce -overtone-pool- (overtone.at-at/mk-pool))
