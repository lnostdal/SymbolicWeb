(in-ns 'symbolicweb.core)

;; https://github.com/danjenkins/Sticky

;; TODO: Leaky DOM.
(defn show-Notification [message viewport]
  (js-run viewport "$.sticky(" (url-encode-wrap message) ");"))
