(defproject symbolicweb "1.0.0-SNAPSHOT"
  :description "SymbolicWeb: WUI for Clojure"
  :license "AGPLv3 + CLA"
  :dependencies [[org.clojure/clojure "LATEST"]

                 [org.clojure/math.numeric-tower "LATEST"] ;; ROUND etc.

                 [http-kit/http-kit "LATEST"] ;; HTTP (server) stuff.
                 [clj-http "LATEST"] ;; HTTP(S) (client) stuff.

                 [com.google.guava/guava "LATEST"] ;; For soft hash cache.

                 [cheshire "LATEST"] ;; JSON.

                 [clj-time/clj-time "0.6.1-SNAPSHOT"]

                 [hiccup/hiccup "LATEST"] ;; HTML generation.
                 [org.jsoup/jsoup "LATEST"] ;; HTML templating.

                 ;; HTTP protocol handling.
                 [ring/ring-codec "LATEST"] ;; ring.util.codec
                 [ring/ring-core "LATEST"] ;; ring.middleware.params, ring.middleware.cookies

                 ;; TODO: Fix java.jdbc dep. :(
                 [org.clojure/java.jdbc "0.2.4-SNAPSHOT"] ;; SQL/DB handling.

                 [org.postgresql/postgresql "LATEST"]
                 [com.jolbox/bonecp "LATEST"] ;; DB connection pooling.

                 [org.clojure/tools.nrepl "LATEST"]
                 ]

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
