(defproject symbolicweb "1.0.0-SNAPSHOT"
  :description "SymbolicWeb: WUI for Clojure"
  :license "AGPLv3 + CLA"
  :dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]

                 [org.clojure/math.numeric-tower "0.0.3-SNAPSHOT"] ;; ROUND etc.

                 [http-kit/http-kit "2.2.0-SNAPSHOT"] ;; HTTP (server) stuff.
                 [clj-http "0.7.3-SNAPSHOT"] ;; HTTP(S) (client) stuff.

                 [overtone/at-at "1.2.0"] ;; Scheduling; timers etc..

                 [com.google.guava/guava "14.0.1"] ;; For soft hash cache.

                 [cheshire "5.1.3-SNAPSHOT"] ;; JSON.

                 [clj-time/clj-time "0.5.2-SNAPSHOT"]

                 [hiccup/hiccup "1.0.3"] ;; HTML generation.
                 [org.jsoup/jsoup "1.7.3-SNAPSHOT"] ;; HTML templating.

                 ;; HTTP protocol handling.
                 [ring/ring-codec "1.0.0"] ;; ring.util.codec
                 [ring/ring-core "1.2.0-beta3"] ;; ring.middleware.params, ring.middleware.cookies

                 [org.clojure/java.jdbc "0.2.4-SNAPSHOT"] ;; SQL/DB handling.

                 [org.jumpmind.symmetric.jdbc/postgresql "9.2-1002-jdbc4"] ;; http://search.maven.org/#browse%7C-1373495709
                 [com.jolbox/bonecp "0.8.0-rc1"] ;; DB connection pooling.

                 [org.clojure/tools.nrepl "0.2.4-SNAPSHOT"]]

  :warn-on-reflection true

  :jvm-opts [;; General.
             "-server" "-XX:+TieredCompilation"


             ;;; Garbage Collection
             "-XX:+UseG1GC"
             ;;"-verbose:gc"
             ;;"-XX:+PrintGCDetails"


             ;; Debugging.
             "-XX:-OmitStackTraceInFastThrow" ;; http://stackoverflow.com/a/2070568/160305
             "-XX:+HeapDumpOnOutOfMemoryError"
             ;;"-Xdebug" "-Xrunjdwp:transport=dt_socket,server=y,suspend=n" ;; For JSwat.
             ])
