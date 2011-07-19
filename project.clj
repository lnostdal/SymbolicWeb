(defproject symbolicweb "1.0.0-SNAPSHOT"
  :description "SymbolicWeb: Yet another web server thingy."
  :dependencies [[org.clojure/clojure "1.3.0-master-SNAPSHOT"]
                 [hiccup "0.3.6"]
                 [enlive "1.2.0-alpha1"]
                 [ring "0.3.11"]
                 [org.clojure/java.jdbc "0.0.5-SNAPSHOT"]]

  :main symbolicweb.core)
