(defproject clj-chess "0.4.1-SNAPSHOT"
  :description "A library of utilities for writing chess related applications."
  :url "http://github.com/romstad/clj-chess"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :resource-paths ["src/javascript"]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.495"]
                 [org.clojure/core.async "0.3.442"]
                 [instaparse "1.4.5"]
                 [com.lucasbradstreet/instaparse-cljs "1.4.1.2"]
                 [me.raynes/conch "0.8.0"]
                 [clj-time "0.13.0"]
                 [org.apache.commons/commons-math3 "3.6.1"]
                 [org.apache.commons/commons-lang3 "3.5"]]

  :profiles {:dev
             {:dependencies [[lein-doo "0.1.7"]]

              :plugins      [[lein-figwheel "0.5.9"]
                             [lein-doo "0.1.7"]]
              }}

  :plugins [[lein-cljsbuild "1.1.5"]]

  :cljsbuild
  {:builds [{:id "test"
             :source-paths ["src/clojure" "test" "target/classes"]
             :compiler {:output-to "target/js/testable.js"
                        :main clj-chess.test-runner
                        :optimizations :none}}
            {:id "node-test"
             :source-paths ["src/clojure" "test" "target/classes"]
             :compiler {:output-to "target/nodejs/testable.js"
                        :main clj-chess.test-runner
                        :optimizations :none
                        :target :nodejs}}]})
