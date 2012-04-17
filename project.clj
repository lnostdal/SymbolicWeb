(defproject symbolicweb "1.0.0-SNAPSHOT"
  :description "SymbolicWeb: WUI for Clojure"
  :dependencies [[org.clojure/clojure "1.5.0-master-SNAPSHOT"]

                 [org.clojure/math.numeric-tower "0.0.2-SNAPSHOT"] ;; ROUND etc.

                 [aleph "0.2.1-beta2"] ;; Deals with boring HTTP server stuff.
                 [overtone/at-at "0.2.1"] ;; Scheduling.

                 [org.apache.commons/collections "3.2.1"] ;; For soft hash cache.

                 [cheshire "3.0.1-SNAPSHOT"] ;; JSON.

                 [hiccup "1.0.0-beta1"] ;; HTML generation.
                 [org.jsoup/jsoup "1.6.1"] ;; HTML templating.
                 [ring/ring-core "1.1.0-SNAPSHOT"] ;; HTTP protocol handling.

                 [org.clojure/java.jdbc "0.1.4-SNAPSHOT"] ;; SQL/DB handling.
                 [postgresql/postgresql "9.1-901.jdbc4"] ;; PostgreSQL JDBC driver.
                 [c3p0/c3p0 "0.9.1.2"]] ;; DB connection pooling.
  :plugins [[swank-clojure "1.5.0-SNAPSHOT"]]
  :jvm-opts [;;"-Xdebug" "-Xrunjdwp:transport=dt_socket,server=y,suspend=n" ;; For JSwat.
             "-server" "-XX:+TieredCompilation"
             "-XX:-OmitStackTraceInFastThrow" ;; http://stackoverflow.com/a/2070568/160305
             "-XX:+HeapDumpOnOutOfMemoryError"])
