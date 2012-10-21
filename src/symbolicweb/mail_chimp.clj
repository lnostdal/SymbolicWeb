(ns symbolicweb.mail-chimp
  (:use symbolicweb.core)
  (:require [clojure.string :as str])
  (:use [cheshire.core :as json])
  (:use [clojure.pprint :only (cl-format)]))


;;; Common stuff.

(defn- http-get-request [url]
  (aleph.http/sync-http-request
   {:auto-transform true
    :request-method :get
    :url url}))


(defn- http-post-request [url body]
  (aleph.http/sync-http-request
   {:auto-transform true
    :request-method :post
    :url url
    :body body}))


(defn mk-MailChimp [api-key]
  {:api-key api-key
   :url "https://mandrillapp.com/api/1.0/"})


;;;; Mail Chimp: Mandrill
;;;; http://mandrillapp.com/api/docs/index.html

;;; Users Calls

(defn users-info [context]
  (http-post-request (str (:url context) "users/info.json")
                     (json-generate {:key (:api-key context)})))

(defn users-ping [context]
  (http-post-request (str (:url context) "users/ping.json")
                     (json-generate {:key (:api-key context)})))

(defn users-senders [context]
  (http-post-request (str (:url context) "users/senders.json")
                     (json-generate {:key (:api-key context)})))


;;; Messages Calls

(defn messages-send [context message]
  (http-post-request (str (:url context) "messages/send.json")
                     (json-generate {:key (:api-key context)
                                     :message message})))



#_(messages-send (mk-MailChimp (:mandrill_key FreeOrDeal.core/-config-data-))
               {:subject "test subject"
                :from_email "larsnostdal@gmail.com"
                :to [{:email "larsnostdal@gmail.com"}]
                :html "blah"})