(ns clj-chess.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [clj-chess.game-test]))

(enable-console-print!)

(doo-tests 'clj-chess.game-test)
