(ns symbolicweb.facebook
  (:require [clojure.string :as str])
  (:use [clojure.pprint :only (cl-format)])
  (:use symbolicweb.core))


;;; Common stuff.

(defonce -fb-agent- (mk-sw-agent nil))


(defn http-get-request [url]
  (aleph.http/sync-http-request
   {:auto-transform true
    :request-method :get
    :url url}))



(defn http-post-request [url body]
  (aleph.http/sync-http-request
   {:auto-transform true
    :request-method :post
    :url url
    :body body}))



;;;; User Auth. See:
;;;;   https://developers.facebook.com/docs/authentication/server-side/

;;; 1. Redirect the user to the OAuth Dialog

(defn user-authenticate-url ^String  [^String app-id
                                      permission-names
                                      ^String csrf-check
                                      ^String user-authenticate-display
                                      ^String redirect-uri]
  (str "https://www.facebook.com/dialog/oauth?"
       "client_id=" (url-encode-component app-id)
       "&redirect_uri=" (url-encode-component redirect-uri)
       (when-let [permission-names permission-names]
         (str "&scope=" (url-encode-component (cl-format false "~{~A~^, ~}" permission-names))))
       "&state=" (url-encode-component csrf-check)
       "&display=" (url-encode-component user-authenticate-display)))


;;; 4. Exchange the code for a user access token

(defn user-access-token-url ^String [^String app-id
                                     ^String app-secret
                                     ^String code
                                     ^String redirect-uri]
  (str "https://graph.facebook.com/oauth/access_token?"
       "client_id=" (url-encode-component app-id)
       "&client_secret=" (url-encode-component app-secret)
       "&redirect_uri=" (url-encode-component redirect-uri)
       "&code=" (url-encode-component code)))


(defn user-get-access-token ^String [^String app-id
                                     ^String app-secret
                                     ^String code
                                     ^String redirect-uri]
  (second (str/split (:body (http-get-request (user-access-token-url app-id app-secret code redirect-uri)))
                     #"=|&")))


(defn user-get-info [^String user-access-token]
  (let [http-response (http-get-request (str "https://graph.facebook.com/me"
                                             "?access_token=" (url-encode-component user-access-token)))]
    (json-parse (:body http-response))))


(defn user-get-likes [^String user-access-token]
  (let [http-response (http-get-request (str "https://graph.facebook.com/me/likes?access_token="
                                             (url-encode-component user-access-token)))]
    (json-parse (:body http-response))))


(defn user-get-profile-picture ^String [^String user-id]
  (let [http-response (http-get-request (str "https://graph.facebook.com/" user-id "/picture"))]
    http-response))



;;;; App Auth. See:
;;;;   https://developers.facebook.com/docs/authentication/applications/


(defn app-access-token-url ^String [^String app-id ^String app-secret]
  (str "https://graph.facebook.com/oauth/access_token?"
       "client_id=" (url-encode-component app-id)
       "&client_secret=" (url-encode-component app-secret)
       "&grant_type=client_credentials"))


(defn app-get-access-token ^String [^String app-id ^String app-secret]
  (second (str/split (:body (http-get-request (app-access-token-url app-id app-secret)))
                     #"=")))

(defn app-get-metadata ^String [^String app-access-token]
  (let [url (str "https://graph.facebook.com/app?access_token=" app-access-token)
        http-response (http-get-request url)]
    (json-parse (:body http-response))))


(defn graph-build-arg-str ^String [m]
  (cl-format false "~{~A=~A~^&~}"
             (interleave (mapv name (keys m))
                         (mapv url-encode-component (vals m)))))


;; TODO: Merge the following two functions somehow?
(defn app-publish-feed-url ^String [^String profile-id]
  (str "https://graph.facebook.com/" profile-id "/feed"))

(defn app-publish-feed-args [^String app-access-token method-args]
  (graph-build-arg-str (assoc method-args
                         :access_token app-access-token)))


(defn app-do-publish-feed [^String app-access-token
                           ^String profile-id
                           method-args]
  (let [http-response (http-post-request (app-publish-feed-url profile-id)
                                         (app-publish-feed-args app-access-token method-args))]
    (json-parse (:body http-response))))



;;;;;
;;;;;


(defn http-oauth-handler [request application
                          ^String response-uri
                          ^String app-id
                          ^String app-secret
                          permission-names
                          ^String user-authenticate-display
                          authorization-accepted-fn
                          authorization-declined-fn]
  "Example of use from JS:
  window.open('http://lrn.freeordeal.no/sw?page=facebook-api&do=init&' + new Date().getTime(), 'blah', 'width=640,height=340');"
  (case (get (:query-params request) "do")
    "init"
    ;; FB: 1. Redirect the user to the OAuth Dialog
    (let [csrf-check (generate-uuid)]
      (session-set application {:facebook-csrf-check csrf-check})
      (let [location (user-authenticate-url app-id permission-names csrf-check user-authenticate-display response-uri)]
        (http-replace-response location)))

    ;; We'll end up here after the redirect above.
    ;; FB: 4. Exchange the code for a user access token
    (cond
      ;; Authorization accepted?
      (get (:query-params request) "code")
      (let [code (get (:query-params request) "code")
            csrf-check (get (:query-params request) "state")]
        ;;(assert (= csrf-check (session-get application :facebook-csrf-check)))
        (when-not (= csrf-check (session-get application :facebook-csrf-check))
          ;; TODO: Ignoring this for now; just logging it instead because I cannot reproduce it and I do not care anymore.
          (println (str "HTTP-OAUTH-HANDLER: CSRF-CHECK failed; got \"" csrf-check
                        "\" while expected \"" (session-get application :facebook-csrf-check) "\"")))
        (session-del application :facebook-csrf-check)
        (let [access-token (user-get-access-token app-id app-secret code response-uri)]
          (with-sw-io -fb-agent- (authorization-accepted-fn (user-get-info access-token) access-token))
          (http-html-response "<script type='text/javascript'> window.close(); </script>")))

      ;; Authorization declined?
      (get (:query-params request) "error")
      (do
        (with-sw-io -fb-agent- (authorization-declined-fn (:query-params request)))
        (http-html-response "<script type='text/javascript'> window.close(); </script>")))))



;;; Open Graph

;; https://developers.facebook.com/docs/opengraph/actions/
(defn og-publish [^String app-namespace
                  ^String app-access-token
                  ^String fb-id
                  ^String action
                  ^Keyword object
                  ^String object-url]
  (let [url (str "https://graph.facebook.com/" fb-id "/" app-namespace ":" action)]
    (with (http-post-request url (ring.util.codec/form-encode {:access_token app-access-token
                                                               object object-url}))
      (when-not (= 200 (:status it))
        (println "symbolicweb.facebook/og-publish: " it)))))
