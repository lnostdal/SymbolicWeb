(in-ns 'symbolicweb.core)

;; Macros and dynamic variables.

(def ^:dynamic *request*)
(def ^:dynamic *viewport*)
(def ^:dynamic *application*)


(def ^{:dynamic true
       :doc "This is used by ADD-RESPONSE-CHUNK to determine whether we're currently handling an AJAX-request; probably based on some DOM/client side event."}
  *in-channel-request?*
  false)


(def ^:dynamic *in-html-container?* false)
(def ^:dynamic *html-container-accu-children* [])


;; Doesn't belong here, but can't find a way to bootstrap this crap proper.
(defmacro dbg-prin1 [form]
  `(let [res# ~form]
     (println (str '~form " => " res#))
     res#))


(defmacro with-errors-logged [& body]
  `(try
     (do ~@body)
     (catch Throwable e# ;; TODO: Throwable is "wrong", but it also actually works.
       (clojure.stacktrace/print-stack-trace e# 20))))



(defmacro with [form & body]
  `(let [~'it ~form]
     ~@body))


(defmacro with1 [form & body]
  `(with ~form
     ~@body
     ~'it))


(defmacro with-object [object & body]
  "Used by OBJ."
  `(let [~'%with-object ~object]
     ~@body))

(defmacro obj [signature]
  `(~signature ~'%with-object))
