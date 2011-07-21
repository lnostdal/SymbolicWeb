(in-ns 'symbolicweb.core)

(declare make-Application)


(defmacro defapp [name fit-fn session-constructor-fn]
  `(swap! -application-types-
          #(assoc % '~name {:fit-fn ~fit-fn
                            :application-constructor-fn ~session-constructor-fn})))


(defn hello-world-handler []
  {:status  200
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Connection" "keep-alive"
             "Expires" "Mon, 26 Jul 1997 05:00:00 GMT"
             "Cache-Control" "no-store, no-cache, must-revalidate, post-check=0, pre-check=0"
             "Pragma" "no-cache"}
   :body
   (html
    (doctype :xhtml-strict)
    (xhtml-tag
     "en"
     [:head
      [:title "SymbolicWeb"]
      [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
      (script-src "https://ajax.googleapis.com/ajax/libs/jquery/1.6.1/jquery.min.js")
      (sw-js-bootstrap)]

     [:body
      [:h1 "SymbolicWeb - bringing life to the web!"]
      [:p "Hello, this is SymbolicWeb running on Clojure " (clojure-version)]
      [:p "Norwegian characters: æøå."]
      [:ul (for [i (range 10)]
             [:li [:b "This is nr. " i "."]])]

      [:p "Here is the Clojure source code for this page:"
       [:pre (slurp "src/symbolicweb/core.clj")]]

      [:p (link-to "http://validator.w3.org/check?uri=referer"
                   [:img {:src "http://www.w3.org/Icons/valid-xhtml10"
                          :alt "Valid XHTML 1.0 Strict"
                          :height 31
                          :width  88}])]]))})


(defapp hello-world
  (fn []
    (is-url? "/sw/hello-world"))
  (fn []
    (make-Application hello-world-handler)))


(defn clear-session-page-handler []
  "Clears the session; removes client side cookies and reloads the page."
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Connection" "keep-alive"}
   :body
   (html
    (doctype :xhtml-strict)
    (xhtml-tag
     "en"
     [:head
      [:title "[SymbolicWeb] Reloading page.."]
      [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]
      [:script {:type "text/javascript"}
       ;; Clear session cookie and reload page.
       (set-document-cookie :name "sw" :value nil)
       "window.location.reload();"]]
     [:body
      [:p "Reloading page..."]]))})


(defn not-found-page-handler []
  "This doesn't set a cookie on the client end."
  {:status 404
   :headers {"Content-Type" "text/html; charset=UTF-8"
             "Connection" "keep-alive"}
   :body
   (html
    (doctype :xhtml-strict)
    (xhtml-tag
     "en"
     [:head
      [:title "[SymbolicWeb] HTTP 404: Not Found"]
      [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"}]]

     [:body
      [:h1 "HTTP 404: Not Found"]
      [:p "Going " [:a {:href "javascript:history.go(-1);"} "back"] " might help."]]))})
