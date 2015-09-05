(ns clj-chess.ecn
  "Functions for reading chess games in ECN (extensible chess notation)
  format."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clj-chess.game :as game]))

(defn reader
  "Convenience function for creating a java PushbackReader for the given
  file name. Why isn't this included in Clojure?"
  [filename]
  (java.io.PushbackReader. (io/reader filename)))

(defn edn-seq
  "A lazy sequence of EDN objects in from the provided reader."
  [rdr]
  (when-let [game (edn/read rdr)]
    (cons game (lazy-seq (edn-seq rdr)))))

(defn game-headers
  "Returns a lazy sequence of the game headers of an ECN file."
  [rdr]
  (map (comp rest second) (edn-seq rdr)))

(defn games
  "Returns a lazy sequence of all games in an ECN file satisfying an optional
  predicate. If no predicate is supplied, all games in the file are included."
  [rdr & [predicate?]]
  (filter (or predicate? identity)
          (map game/from-ecn (edn-seq rdr))))

(defn read-game [rdr & {:keys [skip include-annotations?]
                        :or {skip 0 include-annotations? true}}]
  (game/from-ecn
    (nth (edn-seq rdr) skip)
    :include-annotations? include-annotations?))