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


(defn make-SmallIntInput [model & attributes]
  (with1 (apply make-IntInput model attributes)
    (jqCSS it "width" "30px")))


;; TODO: Parse international date format: http://download.oracle.com/javase/7/docs/api/java/util/Date.html#parse(java.lang.String)
;; TODO: Support for AM / PM. This means using Calendar/HOUR instead of Calendar/HOUR_OF_DAY etc..
;; .. set(int year, int month, int date) ..
(defn make-TimeAtInput [model & date?]
  "Specific point in time related to server clock, think: At xx h xx m xx s.
MODEL must be a ValueModel like created by e.g. (vm (Calendar/getInstance))."
  (assert (= (class (get-value model)) java.util.GregorianCalendar))
  (let [[hours minutes seconds] [(. (get-value model) get Calendar/HOUR)
                                 (. (get-value model) get Calendar/MINUTE)
                                 (. (get-value model) get Calendar/SECOND)]
        hours   (vm hours)
        minutes (vm minutes)
        seconds (vm seconds)
        date-str (vm "")]

    (with1 (whc ["span"]
             (sw (make-SmallIntInput hours
                                     :input-parsing-fn
                                     (fn [input-str]
                                       (with1 (Integer/parseInt input-str)
                                         (alter-value model (fn [old-cal]
                                                              (let [new-cal (. old-cal clone)]
                                                                (. new-cal set Calendar/HOUR_OF_DAY it)
                                                                new-cal)))))))
             "h "
             (sw (make-SmallIntInput minutes
                                     :input-parsing-fn
                                     (fn [input-str]
                                       (with1 (Integer/parseInt input-str)
                                         (alter-value model (fn [old-cal]
                                                              (let [new-cal (. old-cal clone)]
                                                                (. new-cal set Calendar/MINUTE it)
                                                                new-cal)))))))
             "m "
             (sw (make-SmallIntInput seconds
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
                    (sw (with1 (make-TextInput date-str
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
                          (add-response-chunk (str "$('#" (widget-id-of it) "').datepicker();") it))))))

      (make-View model it
                 :handle-model-event-fn
                 (fn [widget _ new-calendar]
                   (set-value hours (. new-calendar get Calendar/HOUR_OF_DAY))
                   (set-value minutes (. new-calendar get Calendar/MINUTE))
                   (set-value seconds (. new-calendar get Calendar/SECOND))
                   (when date?
                     (set-value date-str (str (+ 1 (. new-calendar get Calendar/MONTH)) ;; Yup, it counts from 0.
                                              "/"
                                              (. new-calendar get Calendar/DATE)
                                              "/"
                                              (. new-calendar get Calendar/YEAR)))))))))


(defn make-TimeInInput [model]
  "Future point in time, think: In xx h xx m xx s.
MODEL must be a ValueModel like created by e.g. (vm 60), where 60 is seconds."
  (let [[hours minutes seconds] (seconds-to-hms (get-value model))
        hours   (vm hours)
        minutes (vm minutes)
        seconds (vm seconds)]
    (with1 (whc ["span"]
             (sw (make-SmallIntInput hours
                                     :input-parsing-fn
                                     (fn [input-str]
                                       (with1 (Integer/parseInt input-str)
                                         (set-value model (hms-to-seconds it
                                                                          (get-value minutes)
                                                                          (get-value seconds)))))))
             "h "
             (sw (make-SmallIntInput minutes
                                     :input-parsing-fn
                                     (fn [input-str]
                                       (with1 (Integer/parseInt input-str)
                                         (set-value model (hms-to-seconds (get-value hours)
                                                                          it
                                                                          (get-value seconds)))))))
             "m "
             (sw (make-SmallIntInput seconds
                                     :input-parsing-fn
                                     (fn [input-str]
                                       (with1 (Integer/parseInt input-str)
                                         (set-value model (hms-to-seconds (get-value hours)
                                                                          (get-value minutes)
                                                                          it))))))
             "s")

      (make-View model it
                 :handle-model-event-fn
                 (fn [widget _ new-value]
                   (let [[h m s] (seconds-to-hms new-value)]
                     (set-value hours h)
                     (set-value minutes m)
                     (set-value seconds s)))))))
