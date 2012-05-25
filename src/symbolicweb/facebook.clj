(ns symbolicweb.facebook
  (:use [ring.util.codec :only (url-encode)])
  (:require [clojure.string :as str])
  (:use [clojure.pprint :only (cl-format)])
  (:use symbolicweb.core))


;;; Common stuff.

(defonce -fb-agent- (mk-sw-agent nil))


(defn http-get-request [url]
  (let [conn (aleph.http.client/http-request
              {:auto-transform true
               :method :get
               :url url})]
    @conn))


(defn http-post-request [url body]
  (let [conn (aleph.http.client/http-request
              {:auto-transform true
               :method :post
               :url url
               :body body})]
    @conn))


(defn mk-context [app-id app-secret & args]
  (ref (apply assoc
              {:app-id app-id
               :app-secret app-secret

               :user-access-token nil
               :app-access-token nil

               ;; https://developers.facebook.com/docs/authentication/permissions/
               :permission-names ["publish_stream" "email" "user_birthday"]

               :csrf-check (generate-uuid)

               :user-authenticate-display "popup" ;; page, popup or touch (mobile)
               }
              args)))



;;;; User Auth. See:
;;;;   https://developers.facebook.com/docs/authentication/server-side/

;;; 1. Redirect the user to the OAuth Dialog

(defn user-authenticate-url [context redirect-uri]
  (str "https://www.facebook.com/dialog/oauth?"
       "client_id=" (url-encode (:app-id @context))
       "&redirect_uri=" (url-encode redirect-uri)
       (when-let [permission-names (:permission-names @context)]
         (str "&scope=" (url-encode (cl-format false "~{~A~^, ~}" permission-names))))
       "&state=" (url-encode (:csrf-check @context))
       "&display=" (url-encode (:user-authenticate-display @context))))


;;; 4. Exchange the code for a user access token

(defn user-access-token-url [context code redirect-uri]
  (str "https://graph.facebook.com/oauth/access_token?"
       "client_id=" (url-encode (:app-id @context))
       "&redirect_uri=" (url-encode redirect-uri)
       "&client_secret=" (url-encode (:app-secret @context))
       "&code=" (url-encode code)))


(defn user-get-access-token [context code redirect-uri]
  (second (str/split (:body (http-get-request (user-access-token-url context code redirect-uri)))
                     #"=|&")))


(defn user-get-info [context]
  (let [http-response (http-get-request (str "https://graph.facebook.com/me?access_token="
                                             (url-encode (:user-access-token @context))))]
    (json-parse (:body http-response))))


(defn user-get-likes [context]
  (let [http-response (http-get-request (str "https://graph.facebook.com/me/likes?access_token="
                                             (url-encode (:user-access-token @context))))]
    (json-parse (:body http-response))))



;;;; App Auth. See:
;;;;   https://developers.facebook.com/docs/authentication/applications/


(defn app-access-token-url [context]
  (str "https://graph.facebook.com/oauth/access_token?"
       "client_id="  (url-encode (:app-id @context))
       "&client_secret=" (url-encode (:app-secret @context))
       "&grant_type=client_credentials"))


(defn app-get-access-token [context]
  (second (str/split (:body (http-get-request (app-access-token-url context)))
                     #"=")))

(defn app-get-metadata [context]
  (let [url (str "https://graph.facebook.com/app?access_token=" (:app-access-token @context))
        http-response (http-get-request url)]
    (json-parse (:body http-response))))


(defn graph-build-arg-str [m]
  (cl-format false "~{~A=~A~^&~}"
             (interleave (mapv name (keys m))
                         (mapv url-encode (vals m)))))


;; TODO: Merge the following two functions somehow?
(defn app-publish-feed-url [profile-id]
  (str "https://graph.facebook.com/" profile-id "/feed"))

(defn app-publish-feed-args [context method-args]
  (graph-build-arg-str (assoc method-args
                         :access_token (:app-access-token @context))))


(defn app-do-publish-feed [context profile-id method-args]
  (let [http-response (http-post-request (app-publish-feed-url profile-id)
                                         (app-publish-feed-args context method-args))]
    (json-parse (:body http-response))))



;;;;;
;;;;;


(defn http-oauth-handler [request application response-uri
                          fb-context
                          authorization-accepted-fn
                          authorization-declined-fn]
  "Example of use from JS:
  window.open('http://lrn.freeordeal.no/sw?page=facebook-api&do=init&' + new Date().getTime(), 'blah', 'width=640,height=340');"
  (case (get (:query-params request) "do")
    "init"
    ;; FB: 1. Redirect the user to the OAuth Dialog
    (let [location (user-authenticate-url fb-context response-uri)]
      (http-replace-response location))

    ;; We'll end up here after the redirect above.
    ;; FB: 4. Exchange the code for a user access token
    (cond
      ;; Authorization accepted?
      (get (:query-params request) "code")
      (let [code (get (:query-params request) "code")
            csrf-check (get (:query-params request) "state")]
        (assert (= csrf-check (:csrf-check @fb-context)))
        (let [access-token (user-get-access-token fb-context code response-uri)]
          (dosync (alter fb-context assoc :user-access-token access-token))
          (with-sw-io -fb-agent- (authorization-accepted-fn (user-get-info fb-context)))
          (http-html-response "<script type='text/javascript'> window.close(); </script>")))

      ;; Authorization declined?
      (get (:query-params request) "error")
      (do
        (with-sw-io -fb-agent- (authorization-declined-fn (:query-params request)))
        (http-html-response "<script type='text/javascript'> window.close(); </script>")))))


(defn app-test-do-publish-feed []
  (app-do-publish-feed (with (mk-context nil nil)
                         (alter it assoc :app-access-token (app-get-access-token it)))
                       "688375812"
                       {:message "uhm, hi?"}))
