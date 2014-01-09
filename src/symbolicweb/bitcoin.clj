(in-ns 'symbolicweb.core)


;;; Bitcoin units to satoshi:

(defn satoshi<-btc ^Long [btc]
  "BTC: bitcoin"
  (* btc  100000000))

(defn satoshi<-dbtc ^Long [dbtc]
  "dBTC: decibitcoin"
  (* dbtc 10000000))

(defn satoshi<-cbtc ^Long [cbtc]
  "cBTC: centibitcoin"
  (* cbtc 1000000))

(defn satoshi<-mbtc ^Long [mbtc]
  "mBTC: millibitcoin"
  (* mbtc 100000))

(defn satoshi<-ubtc ^Long [ubtc]
  "μBTC: microbitcoin"
  (* ubtc 10000))



;;; Satoshi to Bitcoin units:

(defn btc<-satoshi [^Long satoshi]
  "BTC: bitcoin"
  (/ satoshi 100000000))

(defn dbtc<-satoshi [^Long satoshi]
  "dBTC: decibitcoin"
  (/ satoshi 10000000))

(defn cbtc<-satoshi [^Long satoshi]
  "cBTC: centibitcoin"
  (/ satoshi 1000000))

(defn mbtc<-satoshi [^Long satoshi]
  "mBTC: millibitcoin"
  (/ satoshi 100000))

(defn ubtc<-satoshi [^Long satoshi]
  "μBTC: microbitcoin"
  (/ satoshi 10000))



(defn handle-btc-value [value ^Keyword from-unit ^Keyword to-unit]
  (with (case from-unit
          :btc  (satoshi<-btc  value)
          :dbtc (satoshi<-dbtc value)
          :cbtc (satoshi<-cbtc value)
          :mbtc (satoshi<-mbtc value)
          :ubtc (satoshi<-ubtc value)
          :satoshi value)
    (case to-unit
      :btc  (btc<-satoshi  it)
      :dbtc (dbtc<-satoshi it)
      :cbtc (cbtc<-satoshi it)
      :mbtc (mbtc<-satoshi it)
      :ubtc (ubtc<-satoshi it)
      :satoshi it)))
