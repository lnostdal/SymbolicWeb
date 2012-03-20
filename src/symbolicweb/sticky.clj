(in-ns 'symbolicweb.core)

;; https://github.com/danjenkins/Sticky

;; TODO: Leaky DOM.
(defn show-Notification [message viewport]
  (add-response-chunk (str "$.sticky(" (url-encode-wrap message) ");")
                      viewport))
