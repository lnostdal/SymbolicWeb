(defproject symbolicweb "1.0.0-SNAPSHOT"
  :description "SymbolicWeb: WUI for Clojure"
  :license "
-----BEGIN PGP SIGNED MESSAGE-----
Hash: SHA512

AGPLv3 + CLA
-----BEGIN PGP SIGNATURE-----
Version: GnuPG v2.0.20 (GNU/Linux)

iQIcBAEBCgAGBQJSkRjBAAoJEKCKx3p7KBrtum0QAJzbztYaglmwcNFksJF6ZNBd
nxmU/d/ZlHsmBRCflE/vP7HEjKjDLIA96ntBG7W3ufIx0fPJR3F68gsdmPB5HJqO
HSnDuWTX3DOsb8295GQYf/Ludjr4QaFplIJdOSpCeKxy71ufFVcfGd0EeBVF7dhL
V3skBIEje0/DFidjVBQywShwAbrfZ/0QzyMo5NPyIElqQZEn6tBqFgenE+lTCj9T
DZXfOYPg7+/Q89W9NbCXY05xQ/IH7UIphMUt3+5hkntbTCi4ZjhF9PEwvwjy+Pq0
gmKbXVROeh8IRpO2i5TGkyip8z+mYZ9Qv30wZKgvXC5XftbFJEAIeGx/c27GF4nr
vyeH38jQr6lkReQVv2qZSuwRS8+rpSQlbZQFtAmXScEl2c1+QMVzB2r+SgCsopIY
dFw/TNDGJ6tzYumR408hHF9AopIu7QwT3zXlfdblCtzs/jPZiZ8KftYm7t43JkET
IALIJHt0yWZTvcoptKvWS0yFzCjlyXTbJb/s0fZc+9u70aOE/k0rgxQL5zk+bldc
OUAH8oV3ui4qe3gzZz9Wo1q9e47RS9s9A+0nUH2PS2AfuG2W5BqxWY0R+H/vD036
DOltIy+DABz3mcJznUaQ5ikwf7Y0m3iPFB3nBsJGgQJaD9VlY3Wpvm78RQIGRhGI
7P2hj9gOYoKRsXNpAG8r
=gRrr
-----END PGP SIGNATURE-----
"
  :dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]

                 [org.clojure/math.numeric-tower "LATEST"] ;; ROUND etc.

                 [http-kit/http-kit "LATEST"] ;; HTTP (server) stuff.
                 [clj-http "LATEST"] ;; HTTP (client) stuff.

                 [com.google.guava/guava "LATEST"] ;; For soft hash cache.

                 [cheshire "LATEST"] ;; JSON.

                 [clj-time/clj-time "0.6.1-SNAPSHOT"]

                 [hiccup/hiccup "LATEST"] ;; HTML generation.
                 [org.jsoup/jsoup "1.7.4-SNAPSHOT"] ;; HTML templating.
                 ;;[org.jsoup/jsoup "LATEST"] ;; HTML templating.

                 ;; HTTP protocol handling.
                 [ring/ring-codec "LATEST"] ;; ring.util.codec
                 [ring/ring-core "LATEST"] ;; ring.middleware.params, ring.middleware.cookies

                 [sqlingvo "0.5.14-SNAPSHOT"] ;; SQL DSL.

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
