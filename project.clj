(defproject symbolicweb "1.0.0-SNAPSHOT"
  :description "SymbolicWeb: WUI for Clojure"
  :dependencies [[org.clojure/clojure "1.4.0-master-SNAPSHOT"]

                 [org.clojure/math.numeric-tower "0.0.2-SNAPSHOT"] ;; ROUND etc.

                 [aleph "0.2.1-alpha2-SNAPSHOT"] ;; Yay, EPOLL() based handling of HTTP.
                 [overtone/at-at "0.2.1"] ;; Scheduling.

                 [org.apache.commons/collections "3.2.1"] ;; For soft hash cache.

                 [cheshire "2.1.1-SNAPSHOT"] ;; JSON.

                 [hiccup "0.3.8"] ;; HTML generation.
                 [enlive "1.2.0-alpha1"] ;; HTML templating.
                 [ring/ring-core "1.0.1"] ;; HTTP handling.

                 [org.clojure/java.jdbc "0.1.2-SNAPSHOT"] ;; SQL/DB handling.
                 [postgresql/postgresql "9.1-901.jdbc4"] ;; PostgreSQL JDBC driver.
                 [c3p0/c3p0 "0.9.1.2"]] ;; DB connection pooling.
  :jvm-opts ["-server" "-XX:+TieredCompilation"
             "-XX:-OmitStackTraceInFastThrow"]) ;; http://stackoverflow.com/a/2070568/160305
