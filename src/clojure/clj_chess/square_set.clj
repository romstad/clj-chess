(ns clj-chess.square-set
  (:refer-clojure :exclude [complement contains? count empty? first remove])
  (:import (chess SquareSet)))

(defn square-set
  "Creates a square-set with the given squares as elements."
  [& squares]
  (reduce (fn [result s] (bit-or (bit-shift-left 1 s) result))
          0
          squares))

(defn empty?
  "True if the set has no elements."
  [set]
  (zero? set))

(defn first
  [set]
  (SquareSet/first set))

(defn remove-first
  [set]
  (SquareSet/removeFirst set))

(defn squares
  "A vector containing the members of a square set."
  [set]
  (loop [result []
         set set]
    (if (empty? set)
      result
      (recur (conj result (first set))
             (remove-first set)))))

(defn intersection
  "The intersection of one or more square sets, i.e. the set of all squares
  that are members of all the input sets."
  [& sets]
  (apply bit-and sets))

(defn union
  "The union of one or more square sets, i.e. the set of squares that are
  members of at least one of the input sets."
  [& sets]
  (apply bit-or sets))

(defn complement
  "The complement of a square set, i.e all squares that are *not* members
  of the input set. The optional 'board-mask' parameter can be used to
  restrict the universe of squares in the case of boards of sizes smaller
  than 8x8."
  ([set] (bit-not set))
  ([set board-mask] (intersection (complement set)
                                  board-mask)))

(defn difference
  "The set of squares that are members of the first set, but not members
  of any of the remaining sets."
  ([set] set)
  ([set-1 set-2]
    (intersection set-1 (complement set-2)))
  ([set-1 set-2 & sets]
    (reduce difference (difference set-1 set-2) sets)))

(defn subset?
  "True if all members of set-1 are also members of set-2."
  [set-1 set-2]
  (= set-1
     (intersection set-1 set-2)))

(defn superset?
  "True if all members of set-2 are also members of set-1."
  [set-1 set-2]
  (= set-1
     (union set-1 set-2)))

(defn contains?
  "Tests whether the square set contains the given square."
  [set square]
  (subset? (square-set square) set))

(defn add
  "Adds a new square to a square set."
  [set square]
  (union set (square-set square)))

(defn remove
  "Removes a square from a square set, if the square is already there.
  If the square is not present in the square set, the set is returned
  unchanged."
  [set square]
  (intersection set (complement (square-set square))))

(defn count
  "The number of elements of the square set."
  [set]
  (SquareSet/count set))

(defn pprint
  "Pretty-print a square set to the standard output, for debugging."
  [set]
  (SquareSet/print set))