(ns symbolicweb.gpg
  (:use symbolicweb.core)
  (:import clojure.lang.Ref
           clojure.lang.Keyword
           clojure.lang.Fn)
  (:require [clojure.string :as str])
  (:use me.raynes.conch))



(defn ^String gpg-symmetric-encrypt [^String s ^String passphrase]
  "Returns an encrypted String on success (exit code 0), or a Map when there is a problem."
  (with (with-programs [gpg]
          (gpg "--symmetric" "--armor" "--passphrase" passphrase
               {:in s
                :verbose true}))
    (if (zero? @(:exit-code it))
      (:stdout it)
      it)))



(defn ^String gpg-symmetric-decrypt [^String s ^String passphrase]
  "Returns a decrypted String on success (exit code 0), or a Map when there is a problem."
  (with (with-programs [gpg]
          (gpg "--decrypt" "--passphrase" passphrase
               {:in s
                :verbose true}))
    (if (zero? @(:exit-code it))
      (:stdout it)
      it)))



(defn gpg-sign [^String s ^String id]
  )



(defn gpg-verify
  ([^String s]
     )

  ([^String s ^String id]
     ))
