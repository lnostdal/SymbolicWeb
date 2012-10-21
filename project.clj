(defproject symbolicweb "1.0.0-SNAPSHOT"
  :description "SymbolicWeb: WUI for Clojure"
  :dependencies [[org.clojure/clojure "1.5.0-master-SNAPSHOT"]

                 [org.clojure/math.numeric-tower "0.0.2-SNAPSHOT"] ;; ROUND etc.

                 [aleph "0.3.0-SNAPSHOT"] ;; Deals with boring HTTP server stuff.

                 [overtone/at-at "1.0.0"] ;; Scheduling.

                 [com.google.guava/guava "13.0.1"] ;; For soft hash cache.

                 [cheshire "4.0.4-SNAPSHOT"] ;; JSON.

                 [hiccup "1.0.1"] ;; HTML generation.
                 [org.jsoup/jsoup "1.7.2-SNAPSHOT"] ;; HTML templating.
                 [ring/ring-core "1.2.0-SNAPSHOT"] ;; HTTP protocol handling.

                 [org.clojure/java.jdbc "0.2.4-SNAPSHOT"] ;; SQL/DB handling.
                 [postgresql/postgresql "9.2-1000.jdbc4"] ;; PostgreSQL JDBC driver.
                 [c3p0/c3p0 "0.9.1.2"] ;; DB connection pooling.

                 [org.clojure/tools.nrepl "0.2.0-SNAPSHOT"]]

  :plugins [[swank-clojure "1.5.0-SNAPSHOT"]
            [lein-swank "1.4.4"]]

  :jvm-opts [;;"-Xdebug" "-Xrunjdwp:transport=dt_socket,server=y,suspend=n" ;; For JSwat.
             "-server" "-XX:+TieredCompilation"
             "-XX:-OmitStackTraceInFastThrow" ;; http://stackoverflow.com/a/2070568/160305
             "-XX:+HeapDumpOnOutOfMemoryError"])
