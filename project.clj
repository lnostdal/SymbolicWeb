(defproject symbolicweb "1.0.0-SNAPSHOT"
  :description "SymbolicWeb: WUI for Clojure"
  :dependencies [[org.clojure/clojure "1.4.0-master-SNAPSHOT"]
                 [org.clojure/math.numeric-tower "0.0.2-SNAPSHOT"]

                 ;; Yay, EPOLL() based handling of HTTP.
                 [aleph "0.2.1-SNAPSHOT"]
                 [overtone/at-at "0.2.1"]

                 ;;[clache "0.7.0"] ;; Caching.
                 ;;[unk "0.9.3"]
                 [org.apache.commons/collections "3.2.1"]

                 ;; JSON.
                 [cheshire "2.0.5-SNAPSHOT"]

                 [hiccup "0.3.7"]
                 [enlive "1.2.0-alpha1"]
                 [ring/ring-core "1.0.0-RC4"]

                 [org.clojure/java.jdbc "0.1.2-SNAPSHOT"]
                 [postgresql/postgresql "9.1-901.jdbc4"]
                 [c3p0/c3p0 "0.9.1.2"]] ;; DB connection pooling.
  :jvm-opts ["-server" "-XX:+TieredCompilation"])
