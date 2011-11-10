(defproject symbolicweb "1.0.0-SNAPSHOT"
  :description "SymbolicWeb: WUI for Clojure"
  :dependencies [[org.clojure/clojure "1.4.0-master-SNAPSHOT"]

                 ;; Yay, EPOLL() based handling of HTTP.
                 [aleph "0.2.1-SNAPSHOT"]
                 [overtone/at-at "0.2.1"]

                 [hiccup "0.3.7"]
                 [enlive "1.2.0-alpha1"]
                 [org.clojure/java.jdbc "0.1.2-SNAPSHOT"]
                 [ring "1.0.0-RC1"]
                 [postgresql/postgresql "9.1-901.jdbc4"]]
  :jvm-opts ["-server" "-XX:+TieredCompilation"])
