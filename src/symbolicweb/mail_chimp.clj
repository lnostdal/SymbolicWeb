(ns symbolicweb.mail-chimp
  (:use symbolicweb.core)
  (:require [clojure.string :as str])
  (:use [cheshire.core :as json])
  (:use [clojure.pprint :only (cl-format)]))


;;; Common stuff.

(defn- http-get-request [url]
  (let [conn (aleph.http.client/http-request
              {:auto-transform true
               :method :get
               :url url})]
    @conn))


(defn- http-post-request [url body]
  (let [conn (aleph.http.client/http-request
              {:auto-transform true
               :method :post
               :url url
               :body body})]
    @conn))


(defn make-MailChimp [api-key]
  (with {:api-key api-key
         :subdomain (second (str/split api-key #"-"))}
    (assoc it
      :url (str "https://" (:subdomain it) ".sts.mailchimp.com/1.0/"))))




;;; Mail Chimp STS API.
;;;   http://apidocs.mailchimp.com/sts/

(defn send-email [context from-name from-email to-name-email-map subject text html & args]
  (let [params (apply assoc {:message {:from_name (mime-encode-rfc-2047 from-name)
                                       :from_email from-email
                                       :to_email (into [] (vals to-name-email-map))
                                       :to_name (mapv mime-encode-rfc-2047 (keys to-name-email-map))
                                       :subject subject
                                       :text text
                                       :html html}}
                      :apikey (:api-key context)
                      args)]
    (http-post-request (str (:url context) "SendEmail?" (subs (http-build-query params) 1))
                       "")))



#_(defn mail-chimp-test []
  (let [to_emails ["larsnostdal@gmail.com"
                   ;;"paalkiil@gmail.com"
                   ]
        to_names ["Lars Rune Nøstdal: ÆØå æøå ☺"
                  ;;"Pål Kiil: ÆØÅ æøå ☺"
                  ]
        message {;;:html "Dette er <b>HTML-koden</b>. Fungerer Norske tegn? ÆØÅ æøå ☺"
                 :text "Dette er *teksten*. Fungerer Norske tegn? ÆØÅ æøå ☺"
                 :subject "Free or Deal er ikke spam: ÆØÅ æøå ☺"
                 :from_name (mime-encode-rfc-2047 "Free or Deal: ÆØÅ æøå ☺")
                 :from_email "larsnostdal@gmail.com"
                 :to_email to_emails
                 :to_name (mapv mime-encode-rfc-2047 to_names)}
        tags ["WelcomeEmail"]
        params {:message message
                :track_opens false ;;true
                :track_clicks false
                :tags tags}]
    ;;(send-email (make-MailChimp nil nil) params)
    (url-decode-component (http-build-query params)))
  )
