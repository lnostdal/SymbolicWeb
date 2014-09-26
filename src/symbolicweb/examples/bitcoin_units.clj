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

        btc-input-vm (vm 0 (constantly false)) btc-output-vm (vm 0)
        dbtc-input-vm (vm 0 (constantly false)) dbtc-output-vm (vm 0)
        cbtc-input-vm (vm 0 (constantly false)) cbtc-output-vm (vm 0)
        mbtc-input-vm (vm 0 (constantly false)) mbtc-output-vm (vm 0)
        ubtc-input-vm (vm 0 (constantly false)) ubtc-output-vm (vm 0)
        nbtc-input-vm (vm 0 (constantly false)) nbtc-output-vm (vm 0)
        satoshi-input-vm (vm 0 (constantly false)) satoshi-output-vm (vm 0)

        satoshi-vm (vm 0)]

    (doseq [[input-vm input-unit] [[btc-input-vm :btc]
                                   [dbtc-input-vm :dbtc]
                                   [cbtc-input-vm :cbtc]
                                   [mbtc-input-vm :mbtc]
                                   [ubtc-input-vm :ubtc]
                                   [nbtc-input-vm :nbtc]
                                   [satoshi-input-vm :satoshi]]]
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

    (vm-observe satoshi-vm (.lifetime root-widget) false
                #(doseq [[output-vm output-unit] [[btc-output-vm :btc]
                                                  [dbtc-output-vm :dbtc]
                                                  [cbtc-output-vm :cbtc]
                                                  [mbtc-output-vm :mbtc]
                                                  [ubtc-output-vm :ubtc]
                                                  [nbtc-output-vm :nbtc]
                                                  [satoshi-output-vm :satoshi]]]
                   (vm-set output-vm
                           (cl-format false (case output-unit
                                              :btc "~8$"
                                              :dbtc "~7$"
                                              :cbtc "~6$"
                                              :mbtc "~5$"
                                              :ubtc "~4$"
                                              :nbtc "~1$"
                                              :satoshi "~D")
                                      (handle-btc-value %3 :satoshi output-unit)))))

    (vm-sync-from-url {:name "satoshi"
                       :model (with1 (vm-sync satoshi-vm nil #(str %3))
                                (vm-observe it nil true #(vm-set satoshi-input-vm %3)))
                       :context-widget root-widget})

    (jqAppend root-widget
      (whc [:div]
        [:h1 "Bitcoin Units"]
        [:p "BTC (Bitcoin): " (sw (mk-TextInput btc-input-vm :change :output-vm btc-output-vm))]
        [:p "dBTC (decibitcoin): " (sw (mk-TextInput dbtc-input-vm :change :output-vm dbtc-output-vm))]
        [:p "cBTC (centibitcoin): " (sw (mk-TextInput cbtc-input-vm :change :output-vm cbtc-output-vm))]
        [:p "mBTC (millibitcoin): " (sw (mk-TextInput mbtc-input-vm :change :output-vm mbtc-output-vm))]
        [:p "μBTC (microbitcoin): " (sw (mk-TextInput ubtc-input-vm :change :output-vm ubtc-output-vm))]
        [:p "nBTC (nanobitcoin): " (sw (mk-TextInput nbtc-input-vm :change :output-vm nbtc-output-vm))]
        [:p "satoshi: " (sw (mk-TextInput satoshi-input-vm :change :output-vm satoshi-output-vm))]))

    (mk-Viewport request session root-widget :page-title "Bitcoin units")))



(defmethod symbolicweb.examples.nostdal-org/mk-nostdal-org-viewport "/bitcoin-units" [^String uri request session]
  (mk-btc-unit-viewport request session))
