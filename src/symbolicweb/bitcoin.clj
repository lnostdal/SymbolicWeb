(in-ns 'symbolicweb.core)



(defn handle-btc-value [value ^Keyword from-unit ^Keyword to-unit]
  (with (case from-unit
          :btc  (* value 100000000)
          :dbtc (* value 10000000)
          :cbtc (* value 1000000)
          :mbtc (* value 100000)
          :ubtc (* value 10000)
          :nbtc (* value 10)
          :satoshi value)
    (case to-unit
      :btc  (/ it 100000000)
      :dbtc (/ it 10000000)
      :cbtc (/ it 1000000)
      :mbtc (/ it 100000)
      :ubtc (/ it 10000)
      :nbtc (/ it 10)
      :satoshi it)))
