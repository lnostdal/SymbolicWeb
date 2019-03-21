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


  :global-vars {*warn-on-reflection* true
                *unchecked-math* :warn-on-boxed
                *read-eval* false
                *print-length* 300
                *print-level* 6}


  :dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]

                 [org.clojure/math.numeric-tower "LATEST"] ;; ROUND etc.

                 [http-kit/http-kit "LATEST"] ;; HTTP server and client.
                 [clj-http "LATEST"] ;; HTTP(S) client.

                 [com.google.guava/guava "LATEST"] ;; For soft hash cache (database_dao.clj).

                 [lrn-utils "0.1.0-SNAPSHOT"]

                 [metosin/jsonista "LATEST"] ;; JSON parsing and generation.

                 [clj-time/clj-time "LATEST"]

                 ;; HTML/CSS generation/templating etc.
                 [hiccup/hiccup "LATEST"] ;; HTML generation.
                 [org.jsoup/jsoup "LATEST"] ;; HTML templating.
                 [garden "LATEST"] ;; CSS generation.

                 ;; HTTP protocol handling.
                 [ring/ring-codec "LATEST"] ;; ring.util.codec
                 [ring/ring-core "LATEST"] ;; ring.middleware.params, ring.middleware.cookies

                 ;; DB stuff.
                 [sqlingvo "LATEST"] ;; SQL DSL.
                 [com.impossibl.pgjdbc-ng/pgjdbc-ng "LATEST"] ;; JDBC driver.
                 [com.zaxxer/HikariCP "LATEST"] ;; DB connection pooling.

                 [me.raynes/conch "LATEST"] ;; Shell tools (used by gpg.clj).
                 ]

  :jvm-opts ^:replace
  [;;; General
   "-server" "-XX:+TieredCompilation"
   ;;"-XX:+UnlockExperimentalVMOptions" "-XX:+UseJVMCICompiler" ;; Graal JIT compiler.
   ;;"-Xms1000m" "-Xmx6000m"
   ;;"-Xss2M" ;; 2MB stack. The default on x64 seems to be 1MB which is not that much.


   ;;; Garbage collection, performance
   ;;"-XX:+UseG1GC" ;; This should be the default now, but OK.
   "-XX:+UseStringDeduplication" ;; This seems to make things slower ..odd. TODO: Check this again.
   "-XX:+CompactStrings"
   "-XX:+UseCompressedOops" ;; Seems -Xmx must not be mentioned if this is to work? TODO: Check this again.
   ;;"-XX:+AlwaysPreTouch" ;; Pre-allocate all memory in *physical* (not virtual as normal) memory.
   ;;"-XX:MaxGCPauseMillis=15000" ;; We don't care about latency; we want throughput for backtesting.
   "-XX:+AggressiveOpts" "-XX:MaxTrivialSize=12" "-XX:MaxInlineSize=270" "-XX:InlineSmallCode=2000"
   ;;"-XX:CompileThreshold=10000" "-XX:+UseBiasedLocking"
   "-XX:-DontCompileHugeMethods" ;; Compile big Fns.
   "-XX:+PerfDisableSharedMem" ;; http://www.evanjones.ca/jvm-mmap-pause.html [this breaks certain debugging tools like jstat!]


   ;;; Debugging
   "-XX:+PrintFlagsFinal"
   ;;"-Xdebug" ;; NOTE: Enabling -Xdebug will slow down execution. It is needed for things like profiling, breakpoints, etc..
   "-Xverify:none" ;; Suppresses the bytecode verifier (which speeds up classloading).
   ;; I'm not using exceptions for path control, so full stacktraces (slow!) are OK.
   "-XX:-OmitStackTraceInFastThrow" ;; See http://stackoverflow.com/a/2070568/160305 ..and this is also interesting http://blogs.atlassian.com/2011/05/if_you_use_exceptions_for_path_control_dont_fill_in_the_stac/
   "-XX:-HeapDumpOnOutOfMemoryError"
   ;;"-verbose:gc" ;;"-XX:+PrintGCDetails"


   ;;; Needed for VisualVM ref: https://torsten.io/stdout/how-to-profile-clojure-code/
   ;; NOTE: If none of this works, check the networking / proxy settings in VisualVM!
   ;; "-Dcom.sun.management.jmxremote"
   ;; "-Dcom.sun.management.jmxremote.port=43210"
   ;; "-Dcom.sun.management.jmxremote.rmi.port=43210"
   ;; "-Dcom.sun.management.jmxremote.ssl=false"
   ;; "-Dcom.sun.management.jmxremote.authenticate=false"
   ;; "-Dcom.sun.management.jmxremote.local.only=true" ;; Use SSH proxy as mentioned in link above!


   ;;; YourKit
   ;;"-agentpath:/home/lnostdal/Downloads/yjp-2016.06/bin/linux-x86-64/libyjpagent.so"


      ;;; Clojure specific
   ;;"-Dclojure.compiler.direct-linking=true" ;; We want this true when running long simulations / optimizations. It also makes return value type-hints better(!) which helps during certain parts of development.
   ;;"-Dclojure.debug=false" ;; TODO!: What's this thing again? Document!


   ;;; OpenJ9 JVM options ( https://www.eclipse.org/openj9/docs/cmdline_specifying/ )
   "-Xquickstart"
   ;;"-Xgcpolicy:optthruput" ;; is optimized for throughput by disabling the concurrent mark phase, which means that applications will stop for long pauses while garbage collection takes place. You might consider using this policy when high application throughput, rather than short garbage collection pauses, is the main performance goal.
   ;;"-Xjit:optLevel=scorching" ;; optimize later when the JIT has done more profiling.
   ;;"-Xjit:disableIprofilerDataPersistence" ;; TODO: https://github.com/eclipse/openj9/issues/4205
   "-Xshareclasses:name=symbolicweb" ;; TODO: https://github.com/eclipse/openj9/issues/4205
   "-Xscmx1g" ;; https://www.eclipse.org/openj9/docs/xscmx/
   ])
