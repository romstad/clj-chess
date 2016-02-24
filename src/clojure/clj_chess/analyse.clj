(ns clj-chess.analyse
  (:require [clojure.core.async :refer [>! <! >!! <!! go chan thread]]
            [clojure.string :as str :refer [starts-with?]]
            [clj-chess.core :as chess]
            [clj-chess.uci :as uci]))

(defn ^:private go-command [depth time nodes finish-iteration? search-moves]
  (let [search-moves
        (if-not search-moves
          ""
          (apply str " searchmoves "
                 (interpose " " (map chess/move-to-uci search-moves))))]
    (if finish-iteration?
      (str "go infinite" search-moves)
      (str "go "
           (cond depth (str "depth " depth)
                 time (str "movetime " time)
                 nodes (str "nodes " nodes))))))

(defn analyse-board [engine board & {depth :depth time :time nodes :nodes
                                     finish-iteration? :finish-iteration?
                                     search-moves :search-moves}]
  (if-not (= 1 (count (keep identity [depth time nodes])))
    (throw (Exception. "Exactly one if :depth, :time and :nodes must be supplied"))
    (let [command (go-command depth time nodes finish-iteration? search-moves)
          engine-output (do
                          (uci/send-command engine (chess/board-to-uci board))
                          (uci/think-async engine command))]
      (loop [line (<!! engine-output)
             pv nil
             final-iteration? false]
        (if (starts-with? line "bestmove")
          pv
          (let [parsed-line (when (starts-with? line "info")
                              (second (uci/parse-uci-output line)))
                pv? (and parsed-line
                         (:pv parsed-line)
                         (= 2 (count (:score parsed-line))))]
            (when (and final-iteration?
                       (:depth parsed-line)
                       pv
                       (> (:depth parsed-line)
                          (:depth pv)))
              (uci/send-command engine "stop"))
            (when parsed-line (prn parsed-line))
            (recur (<!! engine-output)
                   (if pv?
                     parsed-line
                     pv)
                   (or final-iteration?
                       (and depth
                            (:depth parsed-line)
                            (>= (:depth parsed-line) depth))
                       (and time
                            (:time parsed-line)
                            (>= (:time parsed-line) time))
                       (and nodes
                            (:nodes parsed-line)
                            (>= (:nodes parsed-line) nodes))))))))))

(defn score> [score-1 score-2]
  (let [[score-type-1 score-value-1] score-1
        [score-type-2 score-value-2] score-2]
    (cond
      (and (= score-type-1 :mate) (= score-type-2 :mate)
           (> score-value-1 0) (> score-value-2 0))
      (< score-value-1 score-value-2)

      (and (= score-type-1 :mate) (= score-type-2 :mate)
           (> score-value-1 0) (< score-value-2 0))
      true

      (and (= score-type-1 :mate) (= score-type-2 :mate)
           (< score-value-1 0) (> score-value-2 0))
      false

      (and (= score-type-1 :mate) (= score-type-2 :mate)
           (< score-value-1 0) (< score-value-2 0))
      (< score-value-1 score-value-2)

      (and (= score-type-1 :cp) (= score-type-2 :cp))
      (> score-value-1 score-value-2)

      (= score-type-1 :mate)
      (> score-value-1 0)

      :else
      (< score-value-2 0))))

(defn ^:private remove-noise [pv-info]
  (dissoc pv-info :multipv :tbhits :time :seldepth :nps))

(defn analyse-move [engine board move & {depth :depth time :time nodes :nodes}]
  (if-not (= 1 (count (keep identity [depth time nodes])))
    (throw (Exception. "Exactly one of :depth, :time and :nodes must be supplied."))
    (let [computer-pv (remove-noise
                        (analyse-board engine board
                                       :depth depth :time time :nodes nodes
                                       :finish-iteration? true))]
      (if (= (chess/move-from-uci board (first (:pv computer-pv))) move)
        {:computer-move-pv computer-pv
         :game-move-pv computer-pv}
        (let [user-pv (remove-noise (analyse-board engine board
                                                   :depth (:depth computer-pv)
                                                   :time nil :nodes nil
                                                   :finish-iteration? true
                                                   :search-moves [move]))]
          {:computer-move-pv (if (score> (:score computer-pv)
                                         (:score user-pv))
                               computer-pv
                               user-pv)
           :game-move-pv user-pv})))))

(defn ^:private build-game-from-analysis [headers analysis]
  `[:game
    [:headers ~@headers]
    [:moves
     ~@(reduce concat
               (map (fn [{move :move cpv :computer-move-pv gpv :game-move-pv}]
                      (filterv
                        identity
                        [move
                         [:comment (format "Score: %s" (str (:score gpv)))]
                         (when-not (= (first (:pv gpv)) (first (:pv cpv)))
                           `[:variation
                             [:pre-comment ~(format "Better (%s) was"
                                                    (:score cpv))]
                             ~@(:pv cpv)])]))
                    analysis))]])

(defn analyse-game [engine game & {depth :depth time :time nodes :nodes}]
  (->> (reverse
         (map (fn [[b m]]
                (merge
                  {:move (chess/move-to-uci m)}
                  (analyse-move engine b m :depth depth :time time :nodes nodes)))
              (reverse (chess/boards-with-moves game))))
       (build-game-from-analysis (:tags game))))