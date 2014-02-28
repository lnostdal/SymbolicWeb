(in-ns 'symbolicweb.core)


(defn seconds-to-hms [seconds]
  "Returns a vector of three values; hours, minutes and seconds in whole (integer) numbers."
  [(int (/ seconds 3600)) ;; hours
   (int (/ (rem seconds 3600) 60)) ;; minutes
   (rem (rem seconds 3600) 60)]) ;; seconds


(defn seconds-to-hms-str
  "This renders the result of SECONDS-TO-HMS into a string such as: 1h 5m 42s"
  ([seconds] (seconds-to-hms-str seconds \:))

  ([seconds separator]
     (let [[h m s] (seconds-to-hms seconds)]
       (format "%02d%c%02d%c%02d" h separator m separator s))))


(defn hms-to-seconds [hours minutes seconds]
  "HOURS, MINUTES and SECONDS are integers. This returns a single integer value; seconds."
  (+ (* hours 60 60)
     (* minutes 60)
     seconds))


(defn seconds-to-readable-str [total-seconds & {:keys [day-format hour-format minute-format second-format]
                                                :or {day-format #(if (= 1 %) (str % " day") (str % " days"))
                                                     hour-format #(if (= 1 %) (str % " hour") (str % " hours"))
                                                     minute-format #(if (= 1 %) (str % " minute") (str % " minutes"))
                                                     second-format #(if (= 1 %) (str % " second") (str % " seconds"))}}]
  (let [[hours minutes seconds] (seconds-to-hms total-seconds)
        days (quot hours 24)
        hours (rem hours 24)]

    (cond
      (>= total-seconds (hms-to-seconds 24 0 0))
      (str
       (day-format days) \space
       (hour-format hours) \space
       (minute-format minutes))

      (>= total-seconds (hms-to-seconds 0 60 0))
      (str
       (hour-format hours) \space
       (minute-format minutes) \space
       (second-format seconds))

      (>= total-seconds (hms-to-seconds 0 0 60))
      (str
       (minute-format minutes) \space
       (second-format seconds))

      :else
      (second-format seconds))))


#_(defn mk-SmallIntInput [model & attributes]
  (with1 (apply mk-IntInput model attributes)
    (jqCSS it "width" "30px")))


;; TODO: Switch to Joda time (clj-time).
;; TODO: Parse international date format: http://download.oracle.com/javase/7/docs/api/java/util/Date.html#parse(java.lang.String)
;; TODO: Support for AM / PM. This means using Calendar/HOUR instead of Calendar/HOUR_OF_DAY etc..
;; .. set(int year, int month, int date) ..
#_(defn mk-TimeAtInput [model & date?]
    "Specific point in time related to server clock, think: At xx h xx m xx s.
MODEL must be a ValueModel like created by e.g. (vm (Calendar/getInstance))."
    (assert (= (class @model) java.util.GregorianCalendar))
    (let [[hours minutes seconds] [(. @model get Calendar/HOUR)
                                   (. @model get Calendar/MINUTE)
                                   (. @model get Calendar/SECOND)]
          hours   (vm hours)
          minutes (vm minutes)
          seconds (vm seconds)
          date-str (vm "")]

      (with1 (whc ["span"]
               (sw (mk-SmallIntInput hours
                                     :input-parsing-fn
                                     (fn [input-str]
                                       (with1 (Integer/parseInt input-str)
                                         (alter-value model (fn [old-cal]
                                                              (let [new-cal (. old-cal clone)]
                                                                (. new-cal set Calendar/HOUR_OF_DAY it)
                                                                new-cal)))))))
               "h "
               (sw (mk-SmallIntInput minutes
                                     :input-parsing-fn
                                     (fn [input-str]
                                       (with1 (Integer/parseInt input-str)
                                         (alter-value model (fn [old-cal]
                                                              (let [new-cal (. old-cal clone)]
                                                                (. new-cal set Calendar/MINUTE it)
                                                                new-cal)))))))
               "m "
               (sw (mk-SmallIntInput seconds
                                     :input-parsing-fn
                                     (fn [input-str]
                                       (with1 (Integer/parseInt input-str)
                                         (alter-value model (fn [old-cal]
                                                              (let [new-cal (. old-cal clone)]
                                                                (. new-cal set Calendar/SECOND it)
                                                                new-cal)))))))
               "s"
               (when date?
                 (str " Date: "
                      (sw (with1 (mk-TextInput date-str
                                               :input-parsing-fn
                                               (fn [input-str]
                                                 (with1 input-str
                                                   (let [[month day year] (str/split input-str #"/")
                                                         month (- (Integer/parseInt month) 1)
                                                         day (Integer/parseInt day)
                                                         year (Integer/parseInt year)]
                                                     (alter-value model (fn [old-cal]
                                                                          (let [new-cal (. old-cal clone)]
                                                                            (. new-cal set year month day)
                                                                            new-cal)))))))
                            (js-run it "$('#" (widget-id-of it) "').datepicker();"))))))
        (observe model it false
                 (fn [_ new-calendar]
                   (vm-set hours (. new-calendar get Calendar/HOUR_OF_DAY))
                   (vm-set minutes (. new-calendar get Calendar/MINUTE))
                   (vm-set seconds (. new-calendar get Calendar/SECOND))
                   (when date?
                     (vm-set date-str (str (+ 1 (. new-calendar get Calendar/MONTH)) ;; Yup, it counts from 0.
                                           "/"
                                           (. new-calendar get Calendar/DATE)
                                           "/"
                                           (. new-calendar get Calendar/YEAR)))))))))


#_(defn mk-TimeInInput [model]
  "Future point in time, think: In xx h xx m xx s.
MODEL must be a ValueModel like created by e.g. (vm 60), where 60 is seconds."
  (let [[hours minutes seconds] (seconds-to-hms @model)
        hours   (vm hours)
        minutes (vm minutes)
        seconds (vm seconds)]
    (with1 (whc ["span"]
                (sw (mk-SmallIntInput hours
                                      :input-parsing-fn
                                      (fn [input-str]
                                        (with1 (Integer/parseInt input-str)
                                          (vm-set model (hms-to-seconds it
                                                                        @minutes
                                                                        @seconds))))))
                "h "
                (sw (mk-SmallIntInput minutes
                                      :input-parsing-fn
                                      (fn [input-str]
                                        (with1 (Integer/parseInt input-str)
                                          (vm-set model (hms-to-seconds @hours
                                                                        it
                                                                        @seconds))))))
                "m "
                (sw (mk-SmallIntInput seconds
                                      :input-parsing-fn
                                      (fn [input-str]
                                        (with1 (Integer/parseInt input-str)
                                          (vm-set model (hms-to-seconds @hours
                                                                        @minutes
                                                                        it))))))
                "s")

      (observe model it true
               (fn [_ new-value]
                 (let [[h m s] (seconds-to-hms new-value)]
                   (vm-set hours h)
                   (vm-set minutes m)
                   (vm-set seconds s)))))))
