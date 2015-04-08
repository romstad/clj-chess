(ns clj-chess.game
  "Tree data structure for representing an annotated game with variations.
  Work in progress, use at your own risk."
  (:require [clojure.zip :as zip]
            [clojure.pprint :refer [cl-format]]
            [clj-chess.board :as board]
            [clj-chess.pgn :as pgn])
  (:import org.apache.commons.lang3.text.WordUtils))

(defonce ^:private counter (atom 0))

(defn- generate-id
  "Generates a unique ID for freshly generated nodes."
  []
  (swap! counter inc))

(defn new-game
  "Creates a new game object with the given PGN tags and start position. The
  seven standard tags that make up the \"Seven Tag Roster\" in the PGN standard
  are supplied by individual keyword parameters, as is the start-fen parameter
  that is used to specify the initial position of the game. If any further PGN
  tags are desired, they should be supplied in a sequence of two-element
  vectors of the form [<tag-name> <tag-value>]."
  [& {:keys [white black event site date round result start-fen other-tags]
      :or {white "?" black "?" event "?" site "?" date "?" round "?"
           result "*" 
           start-fen board/start-fen}}]
  (let [root-node {:board (board/make-board start-fen)
                   :node-id (generate-id)}]
     {:tags (concat [["Event" event]
                     ["Site" site]
                     ["Date" date]
                     ["Round" round]
                     ["White" white]
                     ["Black" black]
                     ["Result" result]]
                    other-tags)
      :root-node root-node
      :current-node root-node}))

(defn tag-value
  "Returns the value of the given PGN tag in the game, or nil if no such
  tag exists."
  [game tag-name]
  (second (first (filter #(= tag-name (first %))
                         (game :tags)))))

(defn remove-tag
  "Returns a new game equal to the input game, but with the given tag removed
  (if it exists)."
  [game tag-name]
  (assoc game :tags (remove #(= tag-name (first %))
                            (game :tags))))

(defn set-tag
  "Returns a new game equal to the input game, but with the given tag set to
  the given value. If the tag does not exist in the input game, it is added
  to the end of the tags list."
  [game name value]
  (if (tag-value game name)
    (assoc game :tags (map #(if (= name (first %)) [name value] %)
                           (game :tags)))
    (assoc game :tags (concat (game :tags) [[name value]]))))

(defn board
  "Returns the current board position of a game."
  [game]
  (-> game :current-node :board))

(defn find-node-matching
  "Finds the first (as found by a depth-first search) game tree node
  matching the given predicate. If no such node is found, returns nil."
  [game predicate & [start-node]]
  (let [start-node (or start-node (game :root-node))]
    (if (predicate start-node)
      start-node
      (first (filter identity
                     (map #(find-node-matching game predicate %)
                          (start-node :children)))))))

(defn find-node
  "Returns the node with the given node id, or nil if no such node exists."
  [game node-id]
  (find-node-matching game #(= node-id (% :node-id))))

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

(def ^:private zip-add-san-move #(zip-add-move %1 board/move-from-san %2))
(def ^:private zip-add-uci-move #(zip-add-move %1 board/move-from-uci %2))
(def ^:private zip-add-plain-move #(zip-add-move %2 (fn [_ m] m) %2))

(defn- zip-add-move-sequence
  "Takes a zipper, a move function (a function that, given a board and some
  other value, translates that value to a chess move), and a sequence of 
  values representing chess moves, and returns a new zipper for a modified 
  game tree where the moves have been added at the zipper location."
  [zipper move-function moves]
  (reduce (fn [z m] 
            (-> (zip-add-move z move-function m)
                (zip/down)
                (zip/rightmost)))
          zipper
          moves))

(def ^:private zip-add-san-move-sequence 
  #(zip-add-move-sequence %1 board/move-from-san %2))

(def ^:private zip-add-uci-move-sequence
  #(zip-add-move-sequence %2 board/move-from-uci %2))

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


(defn add-move-sequence
  "Like add-move, but adds a sequence of moves rather than a single move. This
  is faster than calling add-move multiple times, because we don't have to
  unzip and zip the tree for each added move."
  [game move-function moves node-id]
  (let [z (-> (game-zip game node-id)
              (zip-add-move-sequence move-function moves))]
    (assoc game :root-node (zip/root z)
                :current-node (zip/node z))))


(defn add-san-move-sequence
  "Like add-san-move, but adds a sequence of moves rather than a single move. 
  This is faster than calling add-san-move multiple times, because we don't
  have to unzip and zip the tree for each added move."
  [game san-moves & [node-id]]
  (add-move-sequence game board/move-from-san san-moves
                     (or node-id (-> game :current-node :node-id))))


(defn add-uci-move-sequence
  "Like add-uci-move, but adds a sequence of moves rather than a single move. 
  This is faster than calling add-uci-move multiple times, because we don't
  have to unzip and zip the tree for each added move."
  [game uci-moves & [node-id]]
  (add-move-sequence game board/move-from-uci uci-moves
                     (or node-id (-> game :current-node :node-id))))


(defn add-key-value-pair
  "Adds a key value pair to the map at the given node id of the game. If no
  node id is supplied, the key value pair is added at the current node."
  [game key value & [node-id]]
  (let [z (-> (game-zip game (or node-id (-> game :current-node :node-id)))
              (zip-add-key-value-pair key value))
        g (assoc game :root-node (zip/root z))]
    (assoc g :current-node (find-node g (-> g :current-node :node-id)))))


(defn add-comment
  "Adds a comment to the move leading to the node with the given node id.
  Uses current node if no node id is supplied. Adding a comment at the root
  of the game has no effect; if you want to add a comment before the first
  move of the game, use add-pre-comment instead."
  [game cmt & [node-id]]
  (add-key-value-pair game :comment cmt node-id))


(defn add-pre-comment
  "Adds a pre-comment to the node with the given node-id. Uses the current
  node if no node id is supplied. When exporting as PGN, the pre-comment is
  displayed *before* the move rather than after. It is probably only useful
  for the first move of the game or the first move of a recursive annotation
  variation."
  [game cmt & [node-id]]
  (add-key-value-pair game :pre-comment cmt node-id))


(defn remove-node
  "Removes a node (by default, the current node) from the game, and returns
  a modified game with the current node set to the parent of the deleted
  node."
  [game & [node-id]]
  (let [node-id (or node-id (-> game :current-node :node-id))
        z (game-zip game node-id)
        zr (zip/remove z)]
    (assoc game :root-node (zip/root zr)
                :current-node (if (= (zip/up z) (zip/prev z))
                                (zip/node zr)
                                (zip/node (zip/up zr))))))

(defn goto-node-matching
  "Returns a game equal to the input game, except that :current-node is set to
  the first node matching the given predicate. If no such node is found,
  returns nil."
  [game predicate]
  (when-let [node (find-node-matching game predicate)]
    (assoc game :current-node node)))


(defn goto-node-id
  "Returns a game equal to the input game, except that :current-node is set to
  the node with the given node id. If no node with the supplied node id exists
  in the game, returns nil."
  [game node-id]
  (goto-node-matching game #(= node-id (% :node-id))))


(defn at-beginning?
  "Tests whether we are currently at the beginning of the game, i.e. that the
  current node equals the root node."
  [game]
  (= (game :current-node) (game :root-node)))


(defn at-end?
  "Tests whether we are currently at the end of the game, i.e. that the
  current node has no children."
  [game]
  (empty? (-> game :current-node :children)))


(defn step-back
  "Steps one move backward in the game (goes back to the parent node), and
  returns the resulting game. The retracted move is not deleted from the game,
  only the :current-node of the game is changed. If we are already at the
  beginning of the game, the original game is returned unchanged."
  [game]
  (if (at-beginning? game)
    game
    (goto-node-matching game #(some #{(game :current-node)} (% :children)))))


(defn step-forward
  "Steps one step forward in the game (moves down to the first child node),
  and returns the resulting game. If we are already at the end of the game,
  the original game is returned unchanged."
  [game]
  (if (at-end? game)
    game
    (assoc game :current-node (-> game :current-node :children first))))


(defn to-beginning-of-variation
  "Returns a game identical to the input game, except that we have stepped
  back until reaching the beginning of the variation containing the previous
  current node. In other words, we step back until we reach a branch point
  where the current variation started, or to the root of the game."
  [game]
  (loop [n (game :current-node)
         g (step-back game)]
    (if (or (at-beginning? g)
            (not= n (-> g :current-node :children first)))
      g
      (recur (g :current-node) (step-back g)))))

(defn to-beginning
  "Returns a game identical to the input game, except that current-node is
  set to the root node."
  [game]
  (assoc game :current-node (game :root-node)))


(defn to-end-of-variation
  "Returns a game identical to the input game, except that current node
  is set to the leaf node obtained by following the current variation to
  its end, i.e. by following the sequence of first children from the
  current node until a leaf node is reached."
  [game]
  (loop [n (game :current-node)]
    (if (empty? (n :children))
      (assoc game :current-node n)
      (recur (first (n :children))))))


(defn to-end
  "Returns a game identical to the input game, except that current-node is
  set to the leaf node obtained by following the main line from the root,
  i.e. by following the sequence of first children from the root node until
  a leaf node is reached."
  [game]
  (to-end-of-variation (to-beginning game)))


(defn take-back
  "Takes back the move that led to the current node, and returns a game tree
  with the corresponding node removed and the current node set to the parent.
  Does nothing if we're already at the beginning of the game."
  [game]
  (when-not (at-beginning? game)
    (remove-node game)))


(defn to-uci
  "Exports the current game state in a format ready to be sent to a UCI
  chess engine, i.e. like 'position fen' followed by a sequence of moves."
  [game]
  (-> game board board/board-to-uci))


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
  (-> game board board/side-to-move))


(defn move-text 
  "The move text of the game in short algebraic notation, optionally including
  comments and variations."
  [game & {:keys [include-comments? include-variations?]
           :or {include-comments? true include-variations? true}}]
  (letfn [(terminal? [node]
            (and (empty? (node :children))
                 (or (not include-comments?)
                     (not (node :comment)))))
          (node-to-string [node]
            (let [board (node :board)
                  children (node :children)]
              (str 
                (when-not (empty? children)
                  (str 
                    ;; Pre-comment for first child move:
                    (when include-comments?
                      (when-let [c ((first children) :pre-comment)]
                        (str "{" c "} ")))
                    ;; SAN of first child move (main variation):
                    (let [m (-> (first children) :board board/last-move)
                          wtm (= :white (board/side-to-move board))]
                      (str (board/move-to-san 
                             board m :include-move-number? wtm)
                           ;; Add a space after the move if it is not followed
                           ;; by further moves, comments or variations:
                           (when-not (and (terminal? (first children))
                                          (or (not include-variations?)
                                              (empty? (rest children))))
                             " ")))
                    ;; Comment for first child move:
                    (when include-comments?
                      (when-let [c ((first children) :comment)]
                        (str "{" c "} ")))
                    ;; Recursive annotation variations for younger children:
                    (when include-variations?
                      (apply str
                             (map #(let [m (-> % :board board/last-move)]
                                     (str "("
                                          (when include-comments?
                                            (when-let [c (% :pre-comment)]
                                              (str "{" c "} ")))
                                          (board/move-to-san 
                                            board m :include-move-number? true) 
                                          (when-not (terminal? %)
                                            " ")
                                          (when include-comments?
                                            (when-let [c (% :comment)]
                                              (str "{" c "} ")))
                                          (node-to-string %)
                                          ") "))
                                  (rest children))))
                    ;; Game continuation after first child move:
                    (node-to-string (first children)))))))]
    (node-to-string (game :root-node))))


(defn to-pgn
  "Creates a PGN string from a game, optionally including comments and
  variations."
  [game & {:keys [include-comments? include-variations?]
           :or {include-comments? true include-variations? true}}]
  (str (apply str (map #(cl-format nil "[~a ~s]\n" (first %) (second %))
                       (game :tags)))
       "\n"
       (WordUtils/wrap (move-text game
                                  :include-comments? include-comments?
                                  :include-variations? include-variations?)
                       79)
       " "
       (tag-value game "Result")))


(defn from-pgn
  "Creates a game from a PGN string. Doesn't yet handle comments or
  variations."
  [pgn-string]
  (let [parsed-pgn (pgn/parse-pgn pgn-string)
        game (reduce #(apply set-tag %1 %2)
                     (new-game)
                     (rest (second parsed-pgn)))]
    (-> game (add-san-move-sequence
               (filter string? (-> parsed-pgn (nth 2) rest))))))

