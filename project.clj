(defproject clj-chess "0.5.0-SNAPSHOT"
  :description "A library of utilities for writing chess related applications."
  :url "http://github.com/romstad/clj-chess"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :resource-paths ["src/javascript"]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.439"]
                 [org.clojure/core.async "0.4.490"]
                 [instaparse "1.4.9"]
                 [com.lucasbradstreet/instaparse-cljs "1.4.1.2"]
                 [me.raynes/conch "0.8.0"]
                 [clj-time "0.15.1"]
                 [org.apache.commons/commons-math3 "3.6.1"]
                 [org.apache.commons/commons-lang3 "3.8.1"]
                 [funcool/clojure.jdbc "0.9.0"]
                 [honeysql "0.9.4"]
                 [com.taoensso/nippy "2.14.0"]
                 [org.xerial/sqlite-jdbc "3.27.2.1"]]

  :profiles {:dev
             {:dependencies [[lein-doo "0.1.11"]
                             [cider/piggieback "0.4.0"]]

              :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}

              :plugins      [[lein-figwheel "0.5.18"]
                             [lein-doo "0.1.7"]]}}

  :plugins [[lein-cljsbuild "1.1.5"]]

  :cljsbuild
  {:builds [{:id "test"
             :source-paths ["src/clojure" "test" "target/classes"]
             :compiler {:output-to "target/js/testable.js"
                        :output-dir "target/js/out"
                        :main clj-chess.test-runner
                        :optimizations :none}}
            {:id "node-test"
             :source-paths ["src/clojure" "test" "target/classes"]
             :compiler {:output-to "target/nodejs/testable.js"
                        :output-dir "target/nodejs/out"
                        :main clj-chess.test-runner
                        :optimizations :none
                        :target :nodejs}}]})
