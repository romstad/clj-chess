(ns clj-chess.db
  "SQLite based chess database functions.

  WARNING: This code is still under construction, and shouldn't be used yet."
  (:require [jdbc.core :as jdbc]
            [honeysql.core :as sql]
            [honeysql.helpers :as h]
            [taoensso.nippy :as nip]
            [clj-chess.board :as b]
            [clj-chess.game :as g]))

(defn create-tables!
  "Given an empty SQLite database, create the database tables."
  [db-spec]
  (with-open [conn (jdbc/connection db-spec)]
    (jdbc/execute
      conn
      "CREATE TABLE IF NOT EXISTS games (id INTEGER PRIMARY KEY, event VARCHAR(100) NOT NULL, site VARCHAR(100) NOT NULL, date VARCHAR(100) NOT NULL, round VARCHAR(100) NOT NULL, white VARCHAR(100) NOT NULL, black VARCHAR(100) NOT NULL, result VARCHAR(100) NOT NULL, startfen VARCHAR(100), white_elo INTEGER, black_elo INTEGER, eco VARCHAR(10), other_tags BLOB, moves BLOB)")))

(defn save-games!
  "Saves a sequence of games to the database."
  [db-spec games]
  (with-open [conn (jdbc/connection db-spec)]
    (jdbc/execute
      conn
      (-> (h/insert-into :games)
          (h/values (map (fn [g]
                           (let [tags (into {} (:tags g))]
                             {:event (tags "Event")
                              :site (tags "Site")
                              :date (tags "Date")
                              :round (tags "Round")
                              :white (tags "White")
                              :black (tags "Black")
                              :result (tags "Result")
                              :startfen (when-let [fen (tags "FEN")]
                                          (when (not= fen
                                                      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
                                            fen))
                              :eco (tags "ECO")
                              :white_elo (when-let [elo (tags "WhiteElo")]
                                           (read-string elo))
                              :black_elo (when-let [elo (tags "BlackElo")]
                                           (read-string elo))
                              :other_tags (nip/freeze
                                            (dissoc tags
                                                    "Event" "Site" "Date" "Round"
                                                    "White" "Black" "Result"
                                                    "WhiteElo" "BlackElo"
                                                    "FEN" "SetUp" "ECO" "Source"
                                                    "SourceDate" "SourceVersion"
                                                    "SourceVersionDate"
                                                    "SourceQuality"))
                              :moves (nip/freeze
                                       (byte-array
                                         (map (fn [[b m]]
                                                (b/move-to-byte b m))
                                              (g/boards-with-moves g))))}))
                         games))
          sql/format))))

(defn games-matching
  "Returns all games matching the given SQL query in the database."
  [db-spec query]
  (with-open [conn (jdbc/connection db-spec)]
    (map (fn [x]
           (g/from-ecn
             [:game
              `[:headers ~@(vec (concat
                                  [["Event" (:event x)]
                                   ["Site" (:site x)]
                                   ["Date" (:date x)]
                                   ["Round" (:round x)]
                                   ["White" (:white x)]
                                   ["Black" (:black x)]
                                   ["Result" (:result x)]
                                   ["WhiteElo" (when-let [elo (:white_elo x)]
                                                 (pr-str elo))]
                                   ["BlackElo" (when-let [elo (:white_elo x)]
                                                 (pr-str elo))]]
                                  (when-let [fen (:startfen x)]
                                    [["SetUp" "1"]
                                     ["FEN" fen]])
                                  (when-let [eco (:eco x)]
                                    [["ECO" eco]])
                                  (nip/thaw (:other_tags x))))]
              [:byte-moves (nip/thaw (:moves x))]]))
         (jdbc/fetch conn query))))

(defn game-chunks
  "Returns a lazy sequence of chunks of games, `chunk-size` at a time,
  contained in the database. Assumes that the games in the database have
  contiguous IDs starting from 1."
  [db-spec & [chunk-size skip]]
  (let [chunk-size (or chunk-size 1000)
        skip (or skip 0)]
    (take-while
      (complement empty?)
      (map (fn [i]
             (println "selecting from "
                      (+ 1 (* i chunk-size))
                      " to "
                      (+ (+ chunk-size 1)
                         (* i chunk-size)))
             (games-matching db-spec
                             ["SELECT * FROM games WHERE id>=(?) and id<(?)"
                              (+ 1 (* i chunk-size))
                              (+ (+ chunk-size 1)
                                 (* i chunk-size))]))
           (drop skip (range))))))
