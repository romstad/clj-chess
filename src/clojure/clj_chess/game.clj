(ns clj-chess.game
  "Tree data structure for representing an annotated game with variations.
  Work in progress, use at your own risk."
  (:require [clojure.zip :as zip]
            [clj-chess.board :as board]))

(defonce counter (atom 0))

(defn- generate-id
  "Generates a unique ID for freshly generated nodes."
  []
  (swap! counter inc))

(defn new-game
  "Creates a new game object with the given PGN tags and start position."
  ([white black event site date round result start-fen]
    (let [root-node {:board (board/make-board start-fen)
                     :node-id (generate-id)}]
      {:white white
       :black black
       :event event
       :site site
       :date date
       :round round
       :result result
       :root-node root-node
       :current-node root-node}))
  ([white black event site date round result]
    (new-game white black event site date round result
              "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
  ([] (new-game "?" "?" "?" "?" "?" "?" "*")))

(defn- game-zip
  "Creates a zipper for traversing a game. By default, the zipper
  is initialized to the root node of the game tree. The optional
  second parameter can be used to supply the node id of some internal
  node when that is desired."
  ([game node-id]
    (let [zip (zip/zipper (constantly true)
                          :children
                          #(assoc %1 :children (vec %2))
                          (game :root-node))]
      (loop [z zip]
        (if (= node-id ((zip/node z) :node-id))
          z
          (recur (zip/next z))))))
  ([game] (game-zip game (-> game :root-node :node-id))))

(defn- zip-add-move
  "Takes a zipper, a move function (a function that, given a board
  and some other value, translates that value to a chess move),
  and a value representing a move, and returns a new zipper for
  a modified game tree where the move has been added at the zipper
  location."
  [zipper move-function move]
  (let [board (-> zipper zip/node :board)]
    (zip/append-child zipper
                      {:board (board/do-move board
                                             (move-function board move))
                       :node-id (generate-id)})))

(def zip-add-san-move #(zip-add-move %1 board/move-from-san %2))
(def zip-add-uci-move #(zip-add-move %1 board/move-from-uci %2) )
(def zip-add-plain-move #(zip-add-move %2 (fn [_ m] m) %2))

(defn- zip-add-key-value-pair
  [zipper key value]
  (zip/edit zipper assoc-in [key] value))


(defn add-move
  "Takes a game, a move function (a function that, given a board and
  some other value, translates that value to a chess move), a
  a value representing a move, and an optional node id as input, and
  returns an updated game where the move has been added as the last
  child at the given node. If no node id is supplied, the current node
  of the game is used."
  [game move-function move node-id]
  (let [z (-> (game-zip game node-id)
              (zip-add-move move-function move)
              zip/down
              zip/rightmost)]
    (assoc game :root-node (zip/root z)
                :current-node (zip/node z))))


(defn add-san-move
  "Adds a move in short algebraic notation to a game at a given node
  id. The move is added as the last child. If no node id is supplied,
  the current node of the game is used."
  [game san-move & [node-id]]
  (add-move game board/move-from-san san-move
            (or node-id (-> game :current-node :node-id))))


(defn add-uci-move
  "Adds a move in UCI notation to a game at a given node id. The move is
  added as the last child. If no node id is supplied, the current node of the
  game is used."
  [game uci-move & [node-id]]
  (add-move game board/move-from-uci uci-move
            (or node-id (-> game :current-node :node-id))))

(defn add-key-value-pair
  "Adds a key value pair to the map at the given node id of the game. If no
  node id is supplied, the key value pair is added at the current node."
  [game key value & [node-id]]
  (let [z (-> (game-zip game (or node-id (-> game :current-node :node-id)))
              (zip-add-key-value-pair key value))]
    (assoc game :root-node (zip/root z))))


(defn to-uci
  "Exports the current game state in a format ready to be sent to a UCI
  chess engine, i.e. like 'position fen' followed by a sequence of moves."
  [game]
  (-> game :current-node :board board/board-to-uci))


(defn move-tree
  "Returns a tree of UCI move strings for the given game. Mostly useful for
  inspecting and debugging the tree structure."
  [game]
  (letfn [(tree [node]
            (let [children (node :children)
                  move (-> node :board board/last-move board/move-to-uci)]
              (if-not children
                [move]
                [move (vec (apply concat (map tree children)))])))]
    (vec (apply concat (map tree (-> game :root-node :children))))))


(defn side-to-move
  "The side to move at the current game position, :white or :black."
  [game]
  (-> game :board board/side-to-move))
