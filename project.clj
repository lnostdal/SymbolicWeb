(defproject symbolicweb "1.0.0-SNAPSHOT"
  :description "SymbolicWeb: WUI for Clojure"
  :license "AGPLv3 + CLA"
  :dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]

                 [org.clojure/math.numeric-tower "0.0.3-SNAPSHOT"] ;; ROUND etc.

                 [http-kit/http-kit "2.2.0-SNAPSHOT"] ;; HTTP (server) stuff.
                 [clj-http "0.7.7-SNAPSHOT"] ;; HTTP(S) (client) stuff.

                 [com.google.guava/guava "15.0"] ;; For soft hash cache.

                 [cheshire "5.2.1-SNAPSHOT"] ;; JSON.

                 [clj-time/clj-time "0.6.1-SNAPSHOT"]

                 [hiccup/hiccup "1.0.4"] ;; HTML generation.
                 [org.jsoup/jsoup "1.7.3-SNAPSHOT"] ;; HTML templating.

                 ;; HTTP protocol handling.
                 [ring/ring-codec "1.0.0"] ;; ring.util.codec
                 [ring/ring-core "1.2.0"] ;; ring.middleware.params, ring.middleware.cookies

                 [org.clojure/java.jdbc "0.2.4-SNAPSHOT"] ;; SQL/DB handling.

                 [org.postgresql/postgresql "9.2-1003-jdbc4"]
                 [com.jolbox/bonecp "0.8.0-rc2-SNAPSHOT"] ;; DB connection pooling.
                 ;;[com.mchange/c3p0 "0.9.5-pre3"] ;; DB connection pooling.

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
