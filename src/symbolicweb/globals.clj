(in-ns 'symbolicweb.core)

(defonce ^:const -http-server-string- "SymbolicWeb")

;; Long poll timeout every X / 1000 seconds.
(defonce ^:const -comet-timeout- 25000)

;; X / 1000 seconds since -COMET-TIMEOUT- and still no new request (long poll)?
(defonce ^:const -viewport-timeout- (+ -comet-timeout- 10000))

;; X / 1000 seconds since -VIEWPORT-TIMEOUT- and still no new request (long poll) from any Viewport in the session?
(defonce ^:const -session-timeout- (+ -viewport-timeout- 10000))

(defonce ^:const -request-counter- (atom 0))

(defonce ^:const -session-cookie-name- "_sw")

(defonce ^:const -session-types- (atom {})) ;; name -> {:fit-fn fit-fn, :session-constructor-fn session-constructor-fn]

(defonce ^:const -num-sessions-model- (vm 0))

(defonce ^:const -sessions- (ref {})) ;; SESSION-COOKIE-VALUE -> APPLICATION
