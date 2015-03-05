(defproject clj-chess "0.1.0"
  :description "A library of utilities for writing chess related applications."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [instaparse "1.3.5"]
                 [me.raynes/conch "0.8.0"]
                 [org.apache.commons/commons-math3 "3.4.1"]])
