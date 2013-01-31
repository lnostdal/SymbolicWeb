(defproject symbolicweb "1.0.0-SNAPSHOT"
  :description "SymbolicWeb: WUI for Clojure"
  :dependencies [[org.clojure/clojure "1.5.0-master-SNAPSHOT"]

                 [org.clojure/math.numeric-tower "0.0.3-SNAPSHOT"] ;; ROUND etc.

                 [http-kit/http-kit "2.0-SNAPSHOT"] ;; HTTP stuff.

                 [overtone/at-at "1.1.1"] ;; Scheduling; timers etc..

                 [com.google.guava/guava "14.0-rc2"] ;; For soft hash cache.

                 [cheshire "5.0.2-SNAPSHOT"] ;; JSON.

                 [clj-time/clj-time "0.4.5-SNAPSHOT"]

                 [hiccup/hiccup "1.0.2"] ;; HTML generation.
                 [org.jsoup/jsoup "1.7.2-SNAPSHOT"] ;; HTML templating.
                 [ring/ring-core "1.2.0-SNAPSHOT"] ;; HTTP protocol handling.

                 [org.clojure/java.jdbc "0.2.4-SNAPSHOT"] ;; SQL/DB handling.
                 [postgresql/postgresql "9.2-1002.jdbc4"] ;; PostgreSQL JDBC driver.
                 [c3p0/c3p0 "0.9.1.2"] ;; DB connection pooling.

                 [org.clojure/tools.nrepl "0.2.2-SNAPSHOT"]]

  :warn-on-reflection true

  :jvm-opts [;;"-Xdebug" "-Xrunjdwp:transport=dt_socket,server=y,suspend=n" ;; For JSwat.
             "-server" "-XX:+TieredCompilation"

             ;;; Garbage Collection
             "-XX:+UseG1GC"
             ;;"-verbose:gc"
             ;;"-XX:+PrintGCDetails"

             "-XX:-OmitStackTraceInFastThrow" ;; http://stackoverflow.com/a/2070568/160305
             "-XX:+HeapDumpOnOutOfMemoryError"])
