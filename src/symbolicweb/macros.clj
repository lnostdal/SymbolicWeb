(in-ns 'symbolicweb.core)

(def -db-timeout-ms- 5000)


;;; Macros and dynamic variables.

;; html_container.clj
(def ^:dynamic *in-html-container?* false)


;; model.clj
(def ^:dynamic *observed-vms-ctx* false)
(def ^:dynamic *observed-vms-active-body-fns* #{})


;; Used by SWSYNC; bound in DO-MTX.
(def ^:dynamic *dyn-ctx* nil)


(defn %with-errors-logged [f]
  (try
    (f)
    (catch Throwable e ;; TODO: Throwable is "wrong", but it also actually works.
      (try
        ;; TODO: Send this stuff to a ValueModel which'll print to STDOUT and present it to the user and what not.
        (clojure.stacktrace/print-stack-trace e 100)
        (catch Throwable e
          (println "%WITH-ERRORS-LOGGED: Dodge überfail... :(")
          (Thread/sleep 1000)))))) ;; Make sure we aren't flooded in case some loop gets stuck.


(defmacro with-errors-logged [& body]
  `(%with-errors-logged (fn [] ~@body)))



(defmacro with-object "Used by OBJ."
  [object & body]
  `(let [~'%with-object ~object]
     ~@body))

(defmacro obj [signature]
  `(~signature ~'%with-object))



(defn check-type [obj type]
  (assert (isa? (class obj) type)
          (str "CHECK-TYPE: Expected " type ", but got: "
               (if obj
                 (str "<" (class obj) ": " obj ">")
                 "NIL"))))


(defmacro swsync "BODY executes within a 2PCTX; a combined MTX and DBTX."
  [& body] `(do-2pctx (fn [] ~@body)))



(defmacro js-run [widget & args]
  `(add-response-chunk (str ~@args "\n") ~widget))
