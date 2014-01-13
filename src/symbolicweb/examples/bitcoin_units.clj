(ns symbolicweb.examples.bitcoin-units
  (:import clojure.lang.Ref
           clojure.lang.Keyword
           clojure.lang.Fn
           clojure.lang.MapEntry)

  (:use symbolicweb.core)
  (:import symbolicweb.core.WidgetBase
           symbolicweb.core.ValueModel
           symbolicweb.core.ContainerModel
           symbolicweb.core.ContainerModelNode)
  (:require [symbolicweb.facebook :as swfb])
  (:require [symbolicweb.mail-chimp :as swmail])

  (:use hiccup.core)
  (:use clojure.math.numeric-tower)
  (:use clojure.pprint))



(defn mk-btc-unit-viewport [request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)

        btc-io-vm (vm "0")
        dbtc-io-vm (vm "0")
        cbtc-io-vm (vm "0")
        mbtc-io-vm (vm "0")
        ubtc-io-vm (vm "0")
        satoshi-io-vm (vm "0")

        satoshi-vm (vm 0)]

    (doseq [[input-vm input-unit] [[btc-io-vm :btc]
                                   [mbtc-io-vm :mbtc]
                                   [dbtc-io-vm :dbtc]
                                   [cbtc-io-vm :cbtc]
                                   [mbtc-io-vm :mbtc]
                                   [ubtc-io-vm :ubtc]
                                   [satoshi-io-vm :satoshi]]]
      (vm-observe input-vm (.lifetime root-widget) false
                  #(vm-set satoshi-vm
                           (handle-btc-value (try
                                               (if (= input-unit :satoshi)
                                                 (Long/parseLong %3)
                                                 (rationalize (Double/parseDouble %3)))
                                               (catch Exception e
                                                 -1))
                                             input-unit
                                             :satoshi))))

    (vm-observe satoshi-vm (.lifetime root-widget) true
                #(doseq [[output-vm output-unit] [[btc-io-vm :btc]
                                                  [dbtc-io-vm :dbtc]
                                                  [cbtc-io-vm :cbtc]
                                                  [mbtc-io-vm :mbtc]
                                                  [ubtc-io-vm :ubtc]
                                                  [satoshi-io-vm :satoshi]]]
                   (vm-set output-vm
                           (cl-format false (case output-unit
                                              :btc "~8$"
                                              :dbtc "~7$"
                                              :cbtc "~6$"
                                              :mbtc "~5$"
                                              :ubtc "~4$"
                                              :satoshi "~D")
                                      (handle-btc-value %3 :satoshi output-unit)))))

    (jqAppend root-widget
      (whc [:div]
        (html
         [:h1 "Bitcoin Units"]
         [:p "BTC (Bitcoin): " (sw (mk-TextInput btc-io-vm :change))]
         [:p "dBTC (decibitcoin): " (sw (mk-TextInput dbtc-io-vm :change))]
         [:p "cBTC (centibitcoin): " (sw (mk-TextInput cbtc-io-vm :change))]
         [:p "mBTC (millibitcoin): " (sw (mk-TextInput mbtc-io-vm :change))]
         [:p "μBTC (microbitcoin): " (sw (mk-TextInput ubtc-io-vm :change))]
         [:p "satoshi: " (sw (mk-TextInput satoshi-io-vm :change))])))

    (mk-Viewport request session root-widget :page-title "Bitcoin units")))
