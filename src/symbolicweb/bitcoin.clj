(in-ns 'symbolicweb.core)



(defn handle-btc-value [value ^Keyword from-unit ^Keyword to-unit]
  (with (case from-unit
          :btc  (long (* value 100000000))
          :dbtc (long (* value 10000000))
          :cbtc (long (* value 1000000))
          :mbtc (long (* value 100000))
          :ubtc (long (* value 10000))
          :satoshi value)
    (case to-unit
      :btc  (/ it 100000000)
      :dbtc (/ it 10000000)
      :cbtc (/ it 1000000)
      :mbtc (/ it 100000)
      :ubtc (/ it 10000)
      :satoshi it)))
