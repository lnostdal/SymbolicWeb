(in-ns 'symbolicweb.core)

(def -db-timeout-ms- 5000)

;; Macros and dynamic variables.

(def ^:dynamic *with-sw?* false)
(def ^:dynamic *with-sw-ctx-fn* (fn [f] (f)))

(let [*out* *out*
      *err* *err*]
  (defn %with-sw [f ctx-fn]
    (binding [*with-sw?* true ;; So components can check whether they run within the dynamic context of WITH-SW.
              *print-length* 10
              *print-level* 10
              *out* *out*
              *err* *err*]
      (ctx-fn f))))

(defmacro with-sw [[ctx-fn] & body]
  "Sets up the needed context (dynamic variables) needed by various components in SymbolicWeb."
  `(%with-sw
     (fn [] ~@body)
     ~(if ctx-fn
        ctx-fn
        *with-sw-ctx-fn*)))


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


(defn %with-errors-logged [f]
  (try
    (f)
    (catch Throwable e ;; TODO: Throwable is "wrong", but it also actually works.
      (try
        ;; TODO: Send this stuff to a ValueModel which'll print to STDOUT and present it to the user and what not.
        (clojure.stacktrace/print-stack-trace e 50)
        (catch Throwable e
          (println "%WITH-ERRORS-LOGGED: Dodge überfail... :(")
          (Thread/sleep 1000)))))) ;; Make sure we aren't flooded in case some loop gets stuck.


(defmacro with-errors-logged [& body]
  `(%with-errors-logged (fn [] ~@body)))


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


;; Not really a macro, but screw it; model.clj needs it, and since common.clj needs model.clj it doesn't fit in common.clj where it
;; should.
(defn check-type [obj type]
  (assert (isa? (class obj) type)
          (str "CHECK-TYPE: Expected " type ", but got: "
               (if obj
                 (str "<" (class obj) ": " obj ">")
                 "NIL"))))


(def ^:dynamic *in-sw-db?* false)