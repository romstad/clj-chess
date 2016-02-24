(ns clj-chess.book
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [cl-format]]
            [clj-time.core :as time]
            [clj-chess.board :as b]
            [clj-chess.game :as g]))

(defrecord BookEntry
  [^long key
   ^int move
   ^short elo
   ^short opponent-elo
   ^int wins
   ^int draws
   ^int losses
   ^short latest-year
   ^float score])

(def ^:private entry-size 34)
(def ^:private compact-entry-size 16)

(defn ^:private entry-to-bytes [book-entry compact]
  (let [entry-size (if compact compact-entry-size entry-size)
        bb (java.nio.ByteBuffer/allocate entry-size)
        buf (byte-array entry-size)]
    (.putLong bb (:key book-entry))
    (.putInt bb (:move book-entry))
    (.putFloat bb (:score book-entry))
    (when-not compact
      (.putShort bb (:elo book-entry))
      (.putShort bb (:opponent-elo book-entry))
      (.putInt bb (:wins book-entry))
      (.putInt bb (:draws book-entry))
      (.putInt bb (:losses book-entry))
      (.putShort bb (:latest-year book-entry)))
    (.flip bb)
    (.get bb buf)
    buf))

(defn ^:private entry-from-bytes [bytes compact]
  (let [entry-size (if compact compact-entry-size entry-size)
        bb (java.nio.ByteBuffer/allocate entry-size)]
    (.put bb bytes 0 entry-size)
    (.flip bb)
    (let [key (.getLong bb)
          move (.getInt bb)
          score (.getFloat bb)
          elo (when-not compact (.getShort bb))
          opponent-elo (when-not compact (.getShort bb))
          wins (when-not compact (.getInt bb))
          draws (when-not compact (.getInt bb))
          losses (when-not compact (.getInt bb))
          latest-year (when-not compact (.getShort bb))]
      {:key key :move move :elo elo :opponent-elo opponent-elo
       :wins wins :draws draws :losses losses :latest-year latest-year
       :score score})))

(def ^:dynamic ^:private score-white-win 8.0)
(def ^:dynamic ^:private score-white-draw 4.0)
(def ^:dynamic ^:private score-white-loss 1.0)
(def ^:dynamic ^:private score-black-win 8.0)
(def ^:dynamic ^:private score-black-draw 5.0)
(def ^:dynamic ^:private score-black-loss 1.0)
(def ^:dynamic ^:private yearly-decay-factor 0.85)
(def ^:dynamic ^:private high-elo-factor 6.0)
(def ^:dynamic ^:private max-ply 60)
(def ^:dynamic ^:private min-score 0)
(def ^:dynamic ^:private min-game-count 5)

(def ^:private games-added (atom 0))
(def ^:private entries-added (atom 0))

(defn ^:private compute-score [result color elo date]
  (* (cond (and (= result "1-0") (= color :white)) score-white-win
           (and (= result "1/2-1/2") (= color :white)) score-white-draw
           (and (= result "0-1") (= color :white)) score-white-loss
           (and (= result "0-1") (= color :black)) score-black-win
           (and (= result "1/2-1/2") (= color :black)) score-black-draw
           :else score-black-loss)
     (if (< elo 2400)
       1
       (/ (* high-elo-factor (- elo 2300))
          100.0))
     (Math/exp (* (Math/log yearly-decay-factor)
                  (/ (time/in-days (time/interval date (time/now)))
                     365.25)))))

(defn ^:private add-game [book-entries game]
  (if-not game
    book-entries
    (let [result (g/result game)
          w (if (= result "1-0") 1 0)
          d (if (= result "1/2-1/2") 1 0)
          l (if (= result "0-1") 1 0)
          welo (or (g/white-elo game) 0)
          belo (or (g/black-elo game) 0)
          date (or (g/date game) (time/date-time 1900))
          year (time/year date)
          wscore (compute-score result :white welo date)
          bscore (compute-score result :black belo date)]
      (swap! games-added inc)
      (when (zero? (mod @games-added 1000))
        (println @games-added "games added"))
      (reduce (fn [entries [board move]]
                (let [wtm (= :white (b/side-to-move board))]
                  (swap! entries-added inc)
                  (conj! entries
                         (BookEntry. (.getKey board)
                                     move
                                     (if wtm welo belo)
                                     (if wtm belo welo)
                                     (if wtm w l)
                                     d
                                     (if wtm l w)
                                     year
                                     (if wtm wscore bscore)))))
              book-entries
              (take max-ply (g/boards-with-moves game))))))

(defn ^:private add-games [book-entries games]
  (reduce add-game book-entries games))

(defn ^:private add-game-file [book-entries file-name]
  (add-games book-entries (g/games-in-file file-name)))

(defn ^:private merge-entries [book-entries]
  (BookEntry.
    (:key (first book-entries))
    (:move (first book-entries))
    (/ (reduce + (map :elo book-entries))
       (max 1 (count (remove #{0} (map :elo book-entries)))))
    (/ (reduce + (map :opponent-elo book-entries))
       (max 1 (count (remove #{0} (map :opponent-elo book-entries)))))
    (reduce + (map :wins book-entries))
    (reduce + (map :draws book-entries))
    (reduce + (map :losses book-entries))
    (reduce max (map :latest-year book-entries))
    (reduce + (map :score book-entries))))

(defn ^:private compress-beginning [book-entries]
  (let [mergeable? (fn [e]
                    (and (= (:key e) (:key (first book-entries)))
                         (= (:move e) (:move (first book-entries)))))]
    [(merge-entries (take-while mergeable? book-entries))
     (drop-while mergeable? book-entries)]))

(defn ^:private compress [book-entries]
  (println "compressing...")
  (loop [[compressed-entry remaining-entries] (compress-beginning book-entries)
         result (transient [])]
    (if (empty? remaining-entries)
      (persistent! (conj! result compressed-entry))
      (recur (compress-beginning remaining-entries)
             (conj! result compressed-entry)))))

(defn ^:private purge [book-entries]
  (println "purging...")
  (filter (fn [entry]
            (and (> (:score entry) min-score)
                 (> (+ (:wins entry) (:draws entry) (:losses entry))
                    min-game-count)))
          book-entries))

(defn write-book
  "Writes book entries to a binary file on disk. The book-entries parameter
  should be the value returned by an earlier call to create-book. If 'purge'
  is true, entries with low book scores or a very low number of games are
  discarded. If 'compact' is true, the book is written in a more compact
  format, where win/draw/loss statistics, average Elos and last played dates
  are not included."
  [book-entries filename & {:keys [purge compact]
                            :or {purge true compact false}}]
  (with-open [out (io/output-stream filename)]
    (.write out (if compact 1 0))
    (doseq [entry book-entries]
      (when (or (not purge)
                (and (> (:score entry) min-score)
                     (> (+ (:wins entry) (:draws entry) (:losses entry))
                        min-game-count)))
        (.write out (entry-to-bytes entry compact))))))

(defn create-book
  "Creates an opening book from the provided input files (in PGN or ECN
  format). The book is stored in memory, use write-book afterwards to save
  the book to a binary file.

  Note that creating books from large game files consumes a huge amount of
  memory, you may have to increase your Java heap size."
  [& filenames]
  (compress
    (sort #(or (< (:key %1) (:key %2))
               (and (= (:key %1) (:key %2))
                    (< (:move %1) (:move %2))))
          (persistent!
            (reduce add-game-file (transient []) filenames)))))

(defn ^:private read-key [file index entry-size]
  (.seek file (+ 1 (* entry-size index)))
  (.readLong file))

(defn ^:private find-key [key file left right entry-size]
  (if (> left right)
    nil
    (let [middle (quot (+ left right) 2)
          mid-key (read-key file middle entry-size)]
      (cond (and (= key mid-key)
                 (not= key (read-key file (dec middle) entry-size)))
            middle

            (< mid-key key)
            (recur key file (inc middle) right entry-size)

            :else
            (recur key file left (dec middle) entry-size)))))

(defn ^:private read-entry [file index compact]
  (let [entry-size (if compact compact-entry-size entry-size)
        buf (byte-array entry-size)]
    (.seek file (+ 1 (* entry-size index)))
    (.read file buf)
    (entry-from-bytes buf compact)))

(defn find-book-entries
  "Returns a list of all book entries in the given book file for the given
  board."
  [book-file-name board]
  (with-open [f (java.io.RandomAccessFile. book-file-name "r")]
    (let [compact (= (.read f) 1)
          entry-size (if compact compact-entry-size entry-size)
          entry-count (quot (.length f) entry-size)]
      (when-let [i (find-key (.getKey board) f 0 entry-count entry-size)]
        (let [entries (sort
                        #(> (:score %1) (:score %2))
                        (loop [i i
                               result [(read-entry f i compact)]]
                          (if (= (inc i) entry-count)
                            result
                            (let [next-entry (read-entry f (inc i) compact)]
                              (if (not= (:key next-entry) (.getKey board))
                                result
                                (recur (inc i)
                                       (conj result next-entry)))))))
              score-sum (reduce + (map :score entries))]
          (map (comp #(dissoc % :score)
                     #(assoc % :probability
                               (/ (:score %) score-sum)))
               entries))))))

(defn pick-book-move
  "Pick a random book move from the board position, based on their
  probabilities. Returns nil if no book move is found."
  [book-file-name board]
  (let [entries (find-book-entries book-file-name board)
        r (rand)]
    (first (first (filter #(> (second %) r)
                          (map (fn [e c] [(:move e) c])
                               entries
                               (reductions + (map :probability entries))))))))

(defn ^:private pprint-entries
  "Pretty-print the book entries for the given board position to the standard
   output, for debugging."
  [book-file board]
  (doseq [entry (find-book-entries book-file board)]
    (if (:wins entry)
      (cl-format true
                 "~a ~v$% (+~a,=~a,-~a) ~a ~a ~a ~v$%~%"
                 (b/move-to-san board (:move entry))
                 1 (* 100 (/ (+ (:wins entry) (* 0.5 (:draws entry)))
                             (+ (:wins entry) (:draws entry) (:losses entry))))
                 (:wins entry) (:draws entry) (:losses entry)
                 (:elo entry) (:opponent-elo entry) (:latest-year entry)
                 1 (* 100 (:probability entry)))
      (cl-format true "~a ~v$%~%"
                 (b/move-to-san board (:move entry))
                 1 (* 100 (:probability entry))))))