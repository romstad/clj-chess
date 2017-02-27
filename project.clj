(defproject clj-chess "0.4.0-SNAPSHOT"
  :description "A library of utilities for writing chess related applications."
  :url "http://github.com/romstad/clj-chess"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :resource-paths ["src/javascript"]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.494"]
                 [org.clojure/core.async "0.3.441"]
                 [instaparse "1.4.5"]
                 [com.lucasbradstreet/instaparse-cljs "1.4.1.2"]
                 [me.raynes/conch "0.8.0"]
                 [clj-time "0.13.0"]
                 [org.apache.commons/commons-math3 "3.6.1"]
                 [org.apache.commons/commons-lang3 "3.5"]])
