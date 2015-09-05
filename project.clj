(defproject clj-chess "0.3.0-SNAPSHOT"
  :description "A library of utilities for writing chess related applications."
  :url "http://github.com/romstad/clj-chess"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :resource-paths ["src/javascript"]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.48"]
                 [instaparse "1.4.1"]
                 [com.lucasbradstreet/instaparse-cljs "1.4.1.0"]
                 [me.raynes/conch "0.8.0"]
                 [org.apache.commons/commons-math3 "3.5"]
                 [org.apache.commons/commons-lang3 "3.4"]])
