(ns clj-chess.book
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [cl-format]]
            [clj-time.core :as time]
            [clj-chess.board :as b]
            [clj-chess.db :as db]
            [clj-chess.game :as g])
  (:import (java.io RandomAccessFile)
           (chess Board Move Square)))

(defrecord BookEntry
  [^long key
   ^int move
   ^short elo
   ^short opponent-elo
   ^int wins
   ^int draws
   ^int losses
   ^short first-year
   ^short last-year
   ^float score])

(def ^:private entry-size 36)
(def ^:private compact-entry-size 16)

(defn ^:private put-long [bb x]
  (.putLong bb (Long/reverseBytes x)))

(defn ^:private get-long [bb]
  (Long/reverseBytes (.getLong bb)))

(defn ^:private put-int [bb x]
  (.putInt bb (Integer/reverseBytes x)))

(defn ^:private get-int [bb]
  (Integer/reverseBytes (.getInt bb)))

(defn ^:private put-short [bb x]
  (.putShort bb (Short/reverseBytes x)))

(defn ^:private get-short [bb]
  (Short/reverseBytes (.getShort bb)))

(defn ^:private put-float [bb x]
  (.putInt (Integer/reverseBytes (Float/floatToIntBits x))))

(defn ^:private get-float [bb]
  (Float/intBitsToFloat (Integer/reverseBytes (.getInt bb))))

(defn ^:private entry-to-bytes [book-entry compact]
  (let [entry-size (if compact compact-entry-size entry-size)
        bb (java.nio.ByteBuffer/allocate entry-size)
        buf (byte-array entry-size)]
    (put-long bb (:key book-entry))
    (put-int bb (:move book-entry))
    (put-float bb (:score book-entry))
    (when-not compact
      (put-short bb (:elo book-entry))
      (put-short bb (:opponent-elo book-entry))
      (put-int bb (:wins book-entry))
      (put-int bb (:draws book-entry))
      (put-int bb (:losses book-entry))
      (put-short bb (:first-year book-entry))
      (put-short bb (:last-year book-entry)))
    (.flip bb)
    (.get bb buf)
    buf))

(defn ^:private entry-from-bytes [bytes compact]
  (let [entry-size (if compact compact-entry-size entry-size)
        bb (java.nio.ByteBuffer/allocate entry-size)]
    (.put bb bytes 0 entry-size)
    (.flip bb)
    (let [key (get-long bb)
          move (get-int bb)
          score (get-float bb)
          elo (when-not compact (get-short bb))
          opponent-elo (when-not compact (get-short bb))
          wins (when-not compact (get-int bb))
          draws (when-not compact (get-int bb))
          losses (when-not compact (get-int bb))
          first-year (when-not compact (get-short bb))
          last-year (when-not compact (get-short bb))]
      {:key key :move move :elo elo :opponent-elo opponent-elo
       :wins wins :draws draws :losses losses
       :first-year first-year :last-year last-year
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

(defn ^:private merge-entries [book-entries]
  (BookEntry.
    (:key (first book-entries))
    (:move (first book-entries))
    (reduce max (map :elo book-entries))
    (reduce max (map :opponent-elo book-entries))
    (reduce + (map :wins book-entries))
    (reduce + (map :draws book-entries))
    (reduce + (map :losses book-entries))
    (reduce min (map :first-year book-entries))
    (reduce max (map :last-year book-entries))
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
                                     year
                                     (if wtm wscore bscore)))))
              book-entries
              (take max-ply (g/boards-with-moves game))))))

(defn ^:private add-games [book-entries games]
  (reduce add-game book-entries games))

(defn ^:private add-game-file [book-entries file-name]
  (add-games book-entries (g/games-in-file file-name)))

(defonce  chunks-added (atom 0))

(defn ^:private add-game-chunk [book-entries chunk]
  (swap! chunks-added inc)
  (add-games book-entries chunk))

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
  format, where win/draw/loss statistics, maximum Elos and last played dates
  are not included."
  [book-entries filename & {:keys [purge compact]
                            :or {purge false compact false}}]
  (with-open [out (io/output-stream filename)]
    (.write out (if compact 1 0))
    (doseq [entry book-entries]
      (when (or (not purge)
                (and (> (:score entry) min-score)
                     (> (+ (:wins entry) (:draws entry) (:losses entry))
                        min-game-count)))
        (.write out (entry-to-bytes entry compact))))))

(defn ^:private entry< [entry-0 entry-1]
  (or (< (:key entry-0) (:key entry-1))
      (and (= (:key entry-0) (:key entry-1))
           (< (:move entry-0) (:move entry-1)))))

(defn ^:private key< [entry-0 entry-1]
  (< (:key entry-0) (:key entry-1)))

(def ^:private sort-entries
  (partial sort entry<))

(defn create-book
  "Creates an opening book from the provided input files (in PGN or ECN
  format). The book is stored in memory, use write-book afterwards to save
  the book to a binary file.

  Note that creating books from large game files consumes a huge amount of
  memory, you may have to increase your Java heap size."
  [& filenames]
  (compress
    (sort-entries
      (persistent!
        (reduce add-game-file (transient []) filenames)))))

(defn create-book-from-db
  "Creates an opening book from the SQLite games database provided in the
  parameter. The parameter should be a jdbc.core db-spec, i.e. a map of the
  form `{:subprotocol \"sqlite\", :subname \"/path/to/gamesdb.db\"}."
  [db-spec & [drop-chunks take-chunks]]
  (reset! chunks-added 0)
  (compress
    (sort-entries
      (persistent!
        (reduce add-game-chunk
                (transient [])
                (take take-chunks
                      (db/game-chunks
                        db-spec 1000 (* take-chunks drop-chunks))))))))

(defn ^:private read-key [file index entry-size]
  (.seek file (+ 1 (* entry-size index)))
  (Long/reverseBytes (.readLong file)))

(defn ^:private find-key [key file left right entry-size]
  (if (> left right)
    nil
    (let [middle (quot (+ left right) 2)
          mid-key (read-key file middle entry-size)]
      (cond (and (= key mid-key)
                 (or (= middle 0)
                     (not= key (read-key file (dec middle)
                                         entry-size))))
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

(defn ^:private first-entry-bigger-than [entry file-name]
  (with-open [file (RandomAccessFile. file-name "r")]
    (let [compact (= (.read file) 1)
          entry-size (if compact compact-entry-size entry-size)]
      (loop [left 0
             right (quot (.length file) entry-size)]
        (if (> left right)
          nil
          (let [middle (quot (+ left right) 2)
                mid-entry (read-entry file middle compact)]
            (cond (and (entry< entry mid-entry)
                       (not (entry< entry (read-entry
                                            file (dec middle) compact))))
                  mid-entry

                  (not (entry< entry mid-entry))
                  (recur (inc middle) right)

                  :else
                  (recur left (dec middle)))))))))

(defn ^:private first-entry-with-key-bigger-than [entry file-name]
  (with-open [file (RandomAccessFile. file-name "r")]
    (let [compact (= (.read file) 1)
          entry-size (if compact compact-entry-size entry-size)]
      (loop [left 0
             right (quot (.length file) entry-size)]
        (if (> left right)
          nil
          (let [middle (quot (+ left right) 2)
                mid-entry (read-entry file middle compact)]
            (cond (and (key< entry mid-entry)
                       (or (= middle 0)
                           (not (key< entry (read-entry
                                              file (dec middle)
                                              compact)))))
                  mid-entry

                  (not (key< entry mid-entry))
                  (recur (inc middle) right)

                  :else
                  (recur left (dec middle)))))))))

(defn ^:private first-entry [book-file-name]
  (with-open [f (RandomAccessFile. book-file-name "r")]
    (let [compact (= (.read f) 1)]
      (read-entry f 0 compact))))

(defn ^:private read-book-file
  "Reads an entire book file into a vector of book entries."
  [book-file-name]
  (with-open [f (RandomAccessFile. book-file-name "r")]
    (let [compact (= (.read f) 1)
          entry-size (if compact compact-entry-size entry-size)
          entry-count (quot (.length f) entry-size)]
      (loop [i 0
             result [(read-entry f i compact)]]
        (if (= (inc i) entry-count)
          result
          (recur (inc i)
                 (conj result (read-entry f (inc i) compact))))))))

(defn merge-books
  "Merge a number of opening books to a single large book. Doesn't work
  for compact books, for now."
  [output-file & input-files]
  (write-book
    (reduce (fn [acc next]
              (println "merging" next)
              (println "Entries so far:" (count acc))
              (->> (read-book-file next)
                   (concat acc)
                   sort-entries
                   compress))
            []
            input-files)
    output-file
    :purge false
    :compact false))

(defn ^:private merge-book-entry-lists
  "Merges lists of lists of book entries from the same position to a single
  list, by combining entries corresponding to the same move. Used when
  looking up a move in multiple book files at once. Does not support
  compact books."
  [list-of-list-of-entries]
  (let [moves (-> (mapcat #(map :move %) list-of-list-of-entries)
                  distinct
                  sort)
        mergeable (map (fn [m]
                         (keep (fn [es]
                                 (first (filter #(= (:move %) m) es)))
                               list-of-list-of-entries))
                       moves)]
    (map #(into {} (merge-entries %)) mergeable)))

(defn ^:private find-book-entries-internal [key book-file-name]
  (with-open [f (RandomAccessFile. book-file-name "r")]
    (let [compact (= (.read f) 1)
          entry-size (if compact compact-entry-size entry-size)
          entry-count (quot (.length f) entry-size)]
      (when-let [i (find-key key f 0 (dec entry-count) entry-size)]
        (sort
          #(> (:score %1) (:score %2))
          (loop [i i
                 result [(read-entry f i compact)]]
            (if (= (inc i) entry-count)
              result
              (let [next-entry (read-entry f (inc i) compact)]
                (if (not= (:key next-entry) key)
                  result
                  (recur (inc i)
                         (conj result next-entry)))))))))))

(defn merge-book-files
  "Merge a number of opening books to a single large book. Doesn't work
  for compact books, for now. This function is functionally almost identical
  to merge-books, except that merge-book-files is memory friendly, but
  also much slower."
  [output-file-name & file-names]
  (with-open [out (io/output-stream output-file-name)]
    (.write out 0)
    (loop [entry (apply min-key :key (map first-entry file-names))]
      (when entry
        (let [key (:key entry)
              merged-entries (merge-book-entry-lists
                               (map #(find-book-entries-internal
                                       key %)
                                    file-names))]
          (doseq [e merged-entries]
            (.write out (entry-to-bytes e false)))
          (recur (apply min-key :key
                        (keep #(first-entry-with-key-bigger-than entry %)
                              file-names))))))))

(defn ^:private clojurify-move [board entry]
  (let [m (:move entry)
        jf (bit-and (bit-shift-right m 6) 63)
        jt (bit-and m 63)
        p (bit-and (bit-shift-right m 12) 7)
        ff (quot jf 8)
        fr (- 7 (mod jf 8))
        tf (quot jt 8)
        tr (- 7 (mod jt 8))
        f (Square/make ff fr)
        t (Square/make tf tr)]
    (assoc entry :move
           (if (= p 0)
             (Move/make f t)
             (Move/makePromotion f t p)))))

(defn find-book-entries
  "Returns a list of all book entries for the given input board, read from
  the supplied book files."
  [board & book-file-names]
  (let [entries (merge-book-entry-lists
                  (map #(find-book-entries-internal (.getKey board) %)
                       book-file-names))
        score-sum (reduce + (map :score entries))]
    (map (comp (partial clojurify-move board)
               (fn [e] (dissoc e :score))
               (fn [e] (prn e) (prn score-sum) (assoc e :probability (/ (:score e) score-sum))))
         entries)))

(defn pick-book-move
  "Pick a random book move from the board position, based on their
  probabilities. Returns nil if no book move is found."
  [board & book-file-names]
  (let [entries (apply find-book-entries board book-file-names)
        r (rand)]
    (first (first (filter #(> (second %) r)
                          (map (fn [e c] [(:move e) c])
                               entries
                               (reductions + (map :probability entries))))))))

(defn ^:private pprint-entries
  "Pretty-print the book entries for the given board position to the standard
   output, for debugging."
  [board & book-file-names]
  (doseq [entry (sort-by (comp - :probability)
                         (apply find-book-entries board book-file-names))]
    (if (:wins entry)
      (cl-format true
                 "~a ~v$% (+~a,=~a,-~a) ~a ~a ~a ~a ~v$%~%"
                 (b/move-to-san board (:move entry))
                 1 (* 100 (/ (+ (:wins entry) (* 0.5 (:draws entry)))
                             (+ (:wins entry) (:draws entry) (:losses entry))))
                 (:wins entry) (:draws entry) (:losses entry)
                 (:elo entry) (:opponent-elo entry)
                 (:first-year entry) (:last-year entry)
                 1 (* 100 (:probability entry)))
      (cl-format true "~a ~v$%~%"
                 (b/move-to-san board (:move entry))
                 1 (* 100 (:probability entry))))))
