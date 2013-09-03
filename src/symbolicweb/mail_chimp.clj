(ns symbolicweb.mail-chimp
  (:use symbolicweb.core)
  (:require [clojure.string :as str])
  (:use [cheshire.core :as json])
  (:use [clojure.pprint :only (cl-format)])
  (:require [clj-http.client :as http.client]))



;;; Common stuff.

(defn- http-post-request [^String url body]
  (http.client/post url {:body body}))



;;;; Mail Chimp: Base

(defn mk-MailChimp [^String api-key]
  {:url (str "https://" (second (.split api-key "-")) ".api.mailchimp.com/2.0/")
   :api-key api-key})



(defn mailchimp-call [context ^String cmd m]
  )



(defn mailchimp-batch-subscribe [context ^String list-id m]
  (http-post-request (str (:url context) "lists/batch-subscribe.json")
                     (json-generate (merge {:apikey (:api-key context)
                                            :id list-id
                                            :double_optin false
                                            :update_existing true}
                                           m))))


(defn mailchimp-batch-unsubscribe [context ^String list-id m]
  (http-post-request (str (:url context) "lists/batch-unsubscribe.json")
                     (json-generate (merge {:apikey (:api-key context)
                                            :id list-id
                                            :send_goodbye false
                                            :send_notify false}
                                           m))))


(defn mailchimp-subscribe [context ^String list-id m]
  (http-post-request (str (:url context) "lists/subscribe.json")
                     (json-generate (merge {:apikey (:api-key context)
                                            :id list-id
                                            :double_optin false
                                            :update_existing true}
                                           m))))

#_(swsync
 (swmail/mailchimp-subscribe (swmail/mk-MailChimp "4d58a3cb5dd97dd3940787f1b63a85f2-us5")
                             "333317793c"
                             {:email {:email "larsnostdal@gmail.com" :email_type "html"}}))


(defn mailchimp-unsubscribe [context ^String list-id m]
  (http-post-request (str (:url context) "lists/unsubscribe.json")
                     (json-generate (merge {:apikey (:api-key context)
                                            :id list-id
                                            :send_goodbye false
                                            :send_notify false}
                                           m))))





;;;; Mail Chimp: Mandrill
;;;; http://mandrillapp.com/api/docs/index.html


(defn mk-Mandrill [^String api-key]
  {:api-key api-key
   :url "https://mandrillapp.com/api/1.0/"})




;;; Messages Calls

(defn mandrill-messages-send [context message]
  (http-post-request (str (:url context) "messages/send.json")
                     (json-generate {:key (:api-key context)
                                     :message message})))
