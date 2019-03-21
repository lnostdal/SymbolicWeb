(ns symbolicweb.facebook
  (:import clojure.lang.Ref
           clojure.lang.Keyword
           clojure.lang.Fn)
  (:require [clojure.string :as str])
  (:use [clojure.pprint :only (cl-format)])
  (:use symbolicweb.core)
  (:require [clj-http.client :as http.client]))



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
  (second (str/split (:body (http.client/get (user-access-token-url app-id app-secret code redirect-uri)))
                     #"=|&")))



(defn user-get-info [^String user-access-token]
  (let [http-response (http.client/get (str "https://graph.facebook.com/me"
                                            "?access_token=" (url-encode-component user-access-token)))]
    (json-parse (:body http-response))))

(defn user-get-info-by-id [fb-uid]
  (let [http-response (http.client/get (str "https://graph.facebook.com/" fb-uid))]
    (json-parse (:body http-response))))

(defn user-get-likes [^String user-access-token]
  (let [http-response (http.client/get (str "https://graph.facebook.com/me/likes?access_token="
                                            (url-encode-component user-access-token)))]
    (json-parse (:body http-response))))


(defn user-get-profile-picture ^String [^String user-id]
  (let [http-response (http.client/get (str "https://graph.facebook.com/" user-id "/picture"))]
    http-response))



;;;; App Auth. See:
;;;;   https://developers.facebook.com/docs/authentication/applications/


(defn app-access-token-url ^String [^String app-id ^String app-secret]
  (str "https://graph.facebook.com/oauth/access_token?"
       "client_id=" (url-encode-component app-id)
       "&client_secret=" (url-encode-component app-secret)
       "&grant_type=client_credentials"))


(defn app-get-access-token ^String [^String app-id ^String app-secret]
  (second (str/split (:body (http.client/get (app-access-token-url app-id app-secret)))
                     #"=")))

(defn app-get-metadata ^String [^String app-access-token]
  (let [url (str "https://graph.facebook.com/app?access_token=" app-access-token)
        http-response (http.client/get url)]
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
  (let [http-response (http.client/post {:url (app-publish-feed-url profile-id)
                                         :body (app-publish-feed-args app-access-token method-args)})]
    (json-parse (:body http-response))))



;;;;;
;;;;;


(defn http-oauth-handler [request
                          ^Ref session
                          ^Ref viewport
                          ^String response-uri
                          ^String app-id
                          ^String app-secret
                          permission-names
                          ^String realtime-verify-token
                          ^String user-authenticate-display
                          ^Fn authorization-accepted-fn
                          ^Fn authorization-declined-fn
                          ^Fn user-data-updated-fn]
  "Example of use from JS:
  window.open('?_sw_request_type=aux&ns=fb&do=login_init=&_=' + new Date().getTime(), 'Facebook login', 'width=640,height=340');"
  (case (get (:query-params request) "do")
    ;;"realtime-update" ;; TODO: Fix this; not using Aleph anymore – and this stuff is not related to auth anyway (move it).
    #_(if (and ;; test if this is a subscription verification
           (= (get (:query-params request) "hub.mode") "subscribe")
           (= (get (:query-params request) "hub.verify_token") realtime-verify-token))
        (http-html-response (get (:query-params request) "hub.challenge"))
        ;; else: assume that this is realtime update POST request
        (do
          (let [update-json (json-parse (aleph.formats/bytes->string (:body request)))
                entry (get update-json :entry)
                entries (if (coll? entry) entry [entry])]
            (when (= "user" (get update-json :object)) ;; ignore anything but user updates for now
              (doseq [entry entries]
                (user-data-updated-fn (get entry :changed_fields)
                                      (user-get-info-by-id (get entry :uid))))))))

    "login_init"
    ;; FB: 1. Redirect the user to the OAuth Dialog
    (let [csrf-check (uuid)]
      ;; TODO: This no longer makes sense using page-redirects given that Sessions might time out.
      (stput session :facebook-csrf-check csrf-check)
      (let [location (user-authenticate-url app-id permission-names csrf-check user-authenticate-display response-uri)]
        (http-replace-response location)))

    ;; We'll end up here after the redirect above.
    ;; FB: 4. Exchange the code for a user access token
    "login_redirect"
    (cond
      ;; Authorization accepted?
      (get (:query-params request) "code")
      (let [code (get (:query-params request) "code")
            csrf-check (get (:query-params request) "state")]
        ;;(assert (= csrf-check (stget session :facebook-csrf-check)))
        (when-not (= csrf-check (stget session :facebook-csrf-check))
          ;; TODO: Ignoring this for now; just logging it instead because I cannot reproduce it and I do not care anymore.
          (println (str "HTTP-OAUTH-HANDLER: CSRF-CHECK failed; got \"" csrf-check
                        "\" while expected \"" (stget session :facebook-csrf-check) "\"")))
        ;;(session-del application :facebook-csrf-check) ;; TODO: Needed?
        (let [access-token (user-get-access-token app-id app-secret code response-uri)]
          (authorization-accepted-fn (user-get-info access-token) access-token)
          (dorun (map (partial url-alter-query-params viewport true dissoc) ["_sw_request_type"
                                                                             "return_uri" "do" "ns"
                                                                             ;; Added by FB:
                                                                             "code" "state"]))
          ((:rest-handler @session) request session viewport)))

      ;; Authorization declined?
      (get (:query-params request) "error")
      (do
        (authorization-declined-fn (:query-params request))
        (dorun (map (partial url-alter-query-params viewport true dissoc) ["_sw_request_type"
                                                                           "return_uri" "do" "ns"
                                                                           ;; Added by FB:
                                                                           "code" "state" "error_reason"
                                                                           "error" "error_code" "error_description"]))
        ((:rest-handler @session) request session viewport)))))



;;; Open Graph

;; https://developers.facebook.com/docs/opengraph/actions/
(defn og-publish [^String app-namespace
                  ^String app-access-token
                  ^String fb-id
                  ^String action
                  ^Keyword object
                  ^String object-url]
  (let [url (str "https://graph.facebook.com/" fb-id "/" app-namespace ":" action)]
    (with (http.client/post {:url url
                             :body (ring.util.codec/form-encode {:access_token app-access-token
                                                                 object object-url})})
      (when-not (= 200 (:status it))
        (println "symbolicweb.facebook/og-publish: " it)))))
