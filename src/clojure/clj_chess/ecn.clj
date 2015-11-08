(ns clj-chess.ecn
  "Functions for reading chess games in ECN (extensible chess notation)
  format."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import (java.io PushbackReader)))

(defn reader
  "Convenience function for creating a java PushbackReader for the given
  file name. Why isn't this included in Clojure?"
  [filename]
  (PushbackReader. (io/reader filename)))

(defn edn-seq
  "A lazy sequence of EDN objects in from the provided reader."
  [rdr]
  (when-let [game (edn/read rdr)]
    (cons game (lazy-seq (edn-seq rdr)))))

(defn game-headers
  "Returns a lazy sequence of the game headers of an ECN file."
  [rdr]
  (map (comp rest second) (edn-seq rdr)))

(defn games-in-file [ecn-file]
  (edn-seq (reader ecn-file)))