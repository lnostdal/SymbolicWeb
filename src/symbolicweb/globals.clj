(in-ns 'symbolicweb.core)

(defn ^Boolean ref? [x]
  (= Ref (class x)))

(def -http-server-string- "SymbolicWeb (nostdal.org)")

;; Long poll timeout every X / 1000 seconds.
(def -comet-timeout- 25000)

;; X / 1000 seconds since -COMET-TIMEOUT- and still no new request (long poll)?
(def -viewport-timeout- (+ -comet-timeout- 10000))

;; X / 1000 seconds since -VIEWPORT-TIMEOUT- and still no new request (long poll) from any Viewport in the session?
(def -session-timeout- (+ -viewport-timeout- 10000))

(def -request-counter-
  "Number of HTTP requests since server started."
  (atom 0))




(def -session-cookie-name- "_sw")


(def -session-types-
  "name -> {:fit-fn fit-fn
            :session-constructor-fn session-constructor-fn]"
  (atom {}))


(def -num-sessions-model-
  (vm 0))


(def -sessions-
  "SESSION-COOKIE-VALUE -> APPLICATION"
  (ref {}))
