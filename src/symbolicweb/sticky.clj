(in-ns 'symbolicweb.core)

;; https://github.com/danjenkins/Sticky


(defn show-Notification [message]
  (add-response-chunk (str "$.sticky(" (url-encode-wrap message) ");")))
