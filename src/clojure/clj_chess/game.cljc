(ns clj-chess.game
  "Tree data structure for representing an annotated game with variations.
  Work in progress, use at your own risk."
  (:require [clojure.string :as str]
            [clojure.zip :as zip]
            #?(:clj [clojure.pprint :refer [cl-format]]
               :cljs [cljs.pprint :refer [cl-format]])
            #?(:clj [clj-time.core :as time])
            #?(:clj [clj-time.format :as time-format])
            [clj-chess.board :as board]
            #?(:clj [clj-chess.ecn :as ecn])
            #?(:cljs [cljs.reader :refer [read-string]])
            [clj-chess.pgn :as pgn])
  #?(:clj (:import org.apache.commons.lang3.text.WordUtils)))

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
                    (when start-fen
                      [["SetUp" "1"]
                       ["FEN" start-fen]])
                    other-tags)
      :root-node root-node
      :current-node root-node}))

(defn tag-value
  "Returns the value of the given PGN tag in the game, or nil if no such
  tag exists."
  [game tag-name]
  (second (first (filter #(= tag-name (first %))
                         (game :tags)))))

(defn event
  "The event of the game, as a string."
  [game]
  (tag-value game "Event"))

(defn date
  "The date the game was played, as a joda.time.DateTime object in Clojure,
  and a string in ClojureScript."
  [game]
  (when-let [d (tag-value game "Date")]
    #?(:cljs d)
    #?(:clj
       (try
         (time-format/parse (time-format/formatter "YYYY.MM.dd") d)
         (catch Exception _)))))

(defn round
  "The tournament round of the game."
  [game]
  (tag-value game "Round"))

(defn site
  "The site of the game."
  [game]
  (tag-value game "Site"))

(defn white-player
  "Name of the white player."
  [game]
  (tag-value game "White"))

(defn black-player
  "Name of the black player."
  [game]
  (tag-value game "Black"))

(defn result
  "The result of the game. Should be one of the strings \"1-0\", \"0-1\",
  \"1/2-1/2\" and \"*\"."
  [game]
  (tag-value game "Result"))

(defn white-elo
  "The Elo of the white player (as an integer), or nil."
  [game]
  (when-let [elo (tag-value game "WhiteElo")]
    (try
      (read-string elo)
      #?(:clj (catch Exception _)
         :cljs (catch js/Error _)))))

(defn black-elo
  "The Elo of the black player (as an integer), or nil."
  [game]
  (when-let [elo (tag-value game "BlackElo")]
    (try
      (read-string elo)
      #?(:clj (catch Exception _)
         :cljs (catch js/Error _)))))

(defn remove-tag
  "Returns a new game equal to the input game, but with the given tag removed
  (if it exists)."
  [game tag-name]
  (assoc game :tags (remove #(= tag-name (first %))
                            (:tags game))))

(defn set-tag
  "Returns a new game equal to the input game, but with the given tag set to
  the given value. If the tag does not exist in the input game, it is added
  to the end of the tags list."
  [game name value]
  (if (tag-value game name)
    (assoc game :tags (map #(if (= name (first %)) [name value] %)
                           (:tags game)))
    (assoc game :tags (concat (:tags game) [[name value]]))))

(defn board
  "Returns the current board position of a game."
  [game]
  (-> game :current-node :board))

(defn current-node-id
  "Returns the node id of the current node."
  [game]
  (-> game :current-node :node-id))

(defn find-node-matching
  "Finds the first (as found by a depth-first search) game tree node
  matching the given predicate. If no such node is found, returns nil."
  [game predicate & [start-node]]
  (let [start-node (or start-node (:root-node game))]
    (if (predicate start-node)
      start-node
      (first (filter identity
                     (map #(find-node-matching game predicate %)
                          (:children start-node)))))))

(defn find-node
  "Returns the node with the given node id, or nil if no such node exists."
  [game node-id]
  (find-node-matching game #(= node-id (:node-id %))))

(defn- game-zip
  "Creates a zipper for traversing a game. By default, the zipper
  is initialized to the root node of the game tree. The optional
  second parameter can be used to supply the node id of some internal
  node when that is desired."
  ([game node-id]
    (let [zip (zip/zipper (constantly true)
                          :children
                          #(assoc %1 :children (vec %2))
                          (:root-node game))]
      (loop [z zip]
        (if (= node-id (:node-id (zip/node z)))
          z
          (recur (zip/next z))))))
  ([game] (game-zip game (-> game :root-node :node-id))))


(defn- zip-up-to
  "Takes a game zipper, and returns a zipper obtained by traversing the zipper
  up until the given node id is reached. Returns nil if this node ID does not
  occur along the path to the root."
  [zipper node-id]
  (cond
    (nil? zipper) nil
    (= node-id (:node-id (zip/node zipper))) zipper
    true (zip-up-to (zip/up zipper) node-id)))


(defn- zip-remove-children
  "Takes a game tree zipper and returns a zipper for a modified game tree
  where all the child nodes at the current location are removed."
  [zipper]
  (zip/edit zipper #(dissoc % :children)))


(defn- zip-add-move
  "Takes a zipper, a move function (a function that, given a board
  and some other value, translates that value to a chess move),
  a value representing a move, and a boolean that says whether any previous
  moves at the current location should be removed, and returns a new zipper
  for a modified game tree where the move has been added at the zipper
  location."
  [zipper move-function move remove-existing-moves?]
  (let [board (-> zipper zip/node :board)]
    (zip/append-child (if remove-existing-moves?
                        (zip-remove-children zipper)
                        zipper)
                      {:board (board/do-move board
                                             (move-function board move))
                       :node-id (generate-id)})))

(def ^:private zip-add-san-move #(zip-add-move %1 board/move-from-san %2 false))
(def ^:private zip-add-uci-move #(zip-add-move %1 board/move-from-uci %2 false))
(def ^:private zip-add-plain-move #(zip-add-move %1 (fn [_ m] m) %2 false))

(defn- zip-add-move-sequence
  "Takes a zipper, a move function (a function that, given a board and some
  other value, translates that value to a chess move), a sequence of
  values representing chess moves, and a boolean that says whether any
  previous moves at the current node should be removed, and returns a new
  zipper for a modified game tree where the moves have been added at the
  zipper location."
  [zipper move-function moves remove-existing-moves?]
  (reduce (fn [z m]
            (-> (zip-add-move z move-function m remove-existing-moves?)
                (zip/down)
                (zip/rightmost)))
          zipper
          moves))

(def ^:private zip-add-san-move-sequence
  #(zip-add-move-sequence %1 board/move-from-san %2 false))

(def ^:private zip-add-uci-move-sequence
  #(zip-add-move-sequence %1 board/move-from-uci %2 false))

(def ^:private zip-add-byte-move-sequence
  #(zip-add-move-sequence %1 board/move-from-byte %2 false))

(defn- zip-add-key-value-pair
  [zipper key value]
  (zip/edit zipper assoc-in [key] value))


(defn- zip-add-ecn-data
  "Takes a move function (a function that takes a board and a string and
  returns the move represented by the string, typically board/move-from-san
  or board/move-from-uci), a game zipper and an ECN data object, and returns
  a zipper for a modified game where the ECN data is added. The ECN data is
  either a string representing a move in the notation compatible with the
  move function (e.g. \"g1f3\" for board/move-from-uci or \"Nf3\" for
  board/move-from-san) or a vector where the first element is a keyword
  representing the data type (currently, :moves, :variation, :comment,
  :pre-comment and :nag are the supported data types) and the remaining
  elements are the contents."
  [move-function zipper data]
  (cond
    (string? data)
    (-> zipper
        (zip-add-move move-function data false)
        (zip/down)
        (zip/rightmost))

    (vector? data)
    (let [[key & values] data
          zip-add-fragments (fn [zip fs]
                              (reduce (partial zip-add-ecn-data move-function)
                                      zip fs))
          add-pre-comment (fn [zip pre]
                            (if-not pre
                              zip
                              (-> zip
                                  zip/down
                                  zip/rightmost
                                  (zip-add-key-value-pair :pre-comment pre)
                                  zip/up)))]
      (case key
        :comment (zip-add-key-value-pair zipper :comment (first values))
        :pre-comment (zip-add-key-value-pair zipper :pre-comment (first values))
        :nag (zip-add-key-value-pair zipper :nag (first values))
        :moves (-> zipper
                   (zip-add-fragments values))
        :variation (let [node-id (:node-id (zip/node (zip/up zipper)))
                         pre-comment (when (and (vector? (first values))
                                                (= (first (first values))
                                                   :pre-comment))
                                       (second (first values)))]
                     (-> zipper
                         zip/up
                         (zip-add-fragments (if pre-comment
                                              (rest values)
                                              values))
                         (zip-up-to node-id)
                         (add-pre-comment pre-comment)
                         zip/down
                         zip/leftmost))
        zipper))))


(defn zip-promote-node
  "Takes a game tree zipper and returns a zipper for a modified game tree
  where the current node is promoted one rank among its sibling. If the
  current node is already the oldest child, returns the original zipper."
  [zipper]
  (if-not (zip/left zipper)
    zipper
    (-> zipper zip/remove
        (zip/insert-left (zip/node zipper))
        zip/left)))

(defn zip-promote-node-to-front
  "Takes a game tree zipper and returns a zipper for a modified game tree
  where the current node is promoted to the oldest among its siblings (i.e.
  made the main line). If the current node is already the oldest child,
  returns the original zipper."
  [zipper]
  (if-not (zip/left zipper)
    zipper
    (-> zipper zip/remove zip/leftmost
        (zip/insert-left (zip/node zipper))
        zip/left)))


(defn add-move
  "Takes a game, a move function (a function that, given a board and
  some other value, translates that value to a chess move), a
  a value representing a move, a node id, and a boolean that says whether the
  existing moves at the current nodes should be removed, and returns an
  updated game where the move has been added as the last child at the given
  node. If no node id is supplied, the current node of the game is used."
  [game move-function move node-id remove-existing-moves?]
  (let [z (-> (game-zip game node-id)
              (zip-add-move move-function move remove-existing-moves?)
              zip/down
              zip/rightmost)]
    (assoc game :root-node (zip/root z)
                :current-node (zip/node z))))


(defn add-san-move
  "Adds a move in short algebraic notation to a game at a given node
  id. The move is added as the last child. If no node id is supplied,
  the current node of the game is used. If remove-existing-moves? is true,
  any previously existing moves at the point of insertion are removed."
  [game san-move & {:keys [node-id remove-existing-moves?]}]
  (let [node-id (or node-id (current-node-id game))]
    (add-move game board/move-from-san san-move
              node-id
              remove-existing-moves?)))


(defn add-uci-move
  "Adds a move in UCI notation to a game at a given node id. The move is
  added as the last child. If no node id is supplied, the current node of the
  game is used. If remove-existing-moves? is true, any previously existing
  moves at the point of insertion are removed."
  [game uci-move & {:keys [node-id remove-existing-moves?]}]
  (let [node-id (or node-id (current-node-id game))]
    (add-move game board/move-from-uci uci-move
              node-id
              remove-existing-moves?)))


(defn add-byte-move
  "Adds a move in byte notation to the game at the given node id. The move is
  added as the last child. If no node id is supplied, the current node of the
  game is used. If remove-existing-moves? is true, any previously existing
  moves at the point of insertion are removed."
  [game byte-move & {:keys [node-id remove-existing-moves?]}]
  (let [node-id (or node-id (current-node-id game))]
    (add-move game board/move-from-byte byte-move
              node-id remove-existing-moves?)))


(defn add-map-move
  "Adds a map move (see documentation for clj-chess.board/board-to-map) to a
  game at a given node id. The move is added as the last child. If no node id
  is supplied, the current node of the game is used. If remove-existing-moves?
  is true, any previously existing moves at the point of insertion are removed."
  [game map-move & {:keys [node-id remove-existing-moves?]}]
  (let [node-id (or node-id (current-node-id game))]
    (add-move game board/move-from-map map-move
              node-id
              remove-existing-moves?)))


(defn add-move-sequence
  "Like add-move, but adds a sequence of moves rather than a single move. This
  is faster than calling add-move multiple times, because we don't have to
  unzip and zip the tree for each added move. If remove-existing-moves? is
  true, any previously existing moves at the point of insertion are removed."
  [game move-function moves node-id remove-existing-moves?]
  (let [z (-> (game-zip game node-id)
              (zip-add-move-sequence move-function moves
                                     remove-existing-moves?))]
    (assoc game :root-node (zip/root z)
                :current-node (zip/node z))))


(defn add-san-move-sequence
  "Like add-san-move, but adds a sequence of moves rather than a single move.
  This is faster than calling add-san-move multiple times, because we don't
  have to unzip and zip the tree for each added move. If
  remove-existing-moves? is true, any previously existing moves at the point
  of insertion are removed."
  [game san-moves & {:keys [node-id remove-existing-moves?]}]
  (let [node-id (or node-id (current-node-id game))]
    (add-move-sequence game board/move-from-san san-moves
                       node-id
                       remove-existing-moves?)))


(defn add-uci-move-sequence
  "Like add-uci-move, but adds a sequence of moves rather than a single move.
  This is faster than calling add-uci-move multiple times, because we don't
  have to unzip and zip the tree for each added move. If
  remove-existing-moves? is true, any previously existing moves at the point
  of insertion are removed."
  [game uci-moves & {:keys [node-id remove-existing-moves?]}]
  (let [node-id (or node-id (current-node-id game))]
    (add-move-sequence game board/move-from-uci uci-moves
                       node-id
                       remove-existing-moves?)))


(defn add-byte-move-sequence
  "Like add-byte-move, but adds a sequence of moves rather than a single move.
  This is faster than calling add-byte-move multiple times, because we don't
  have to unzip and zip the tree for each added move. If
  remove-existing-moves? is true, any previously existing moves at the point
  of insertion are removed."
  [game byte-moves & {:keys [node-id remove-existing-moves?]}]
  (let [node-id (or node-id (current-node-id game))]
    (add-move-sequence game board/move-from-byte byte-moves
                       node-id
                       remove-existing-moves?)))


(defn add-key-value-pair
  "Adds a key value pair to the map at the given node id of the game. If no
  node id is supplied, the key value pair is added at the current node."
  [game key value & {:keys [node-id]}]
  (let [node-id (or node-id (current-node-id game))
        z (-> (game-zip game node-id)
              (zip-add-key-value-pair key value))
        g (assoc game :root-node (zip/root z))]
    (assoc g :current-node (find-node g (current-node-id game)))))


(defn add-comment
  "Adds a comment to the move leading to the node with the given node id.
  Uses current node if no node id is supplied. Adding a comment at the root
  of the game has no effect; if you want to add a comment before the first
  move of the game, use add-pre-comment instead."
  [game cmt & {:keys [node-id]}]
  (add-key-value-pair game :comment cmt node-id))


(defn add-pre-comment
  "Adds a pre-comment to the node with the given node-id. Uses the current
  node if no node id is supplied. When exporting as PGN, the pre-comment is
  displayed *before* the move rather than after. It is probably only useful
  for the first move of the game or the first move of a recursive annotation
  variation."
  [game cmt & {:keys [node-id]}]
  (add-key-value-pair game :pre-comment cmt node-id))


(defn remove-node
  "Removes a node (by default, the current node) from the game, and returns
  a modified game with the current node set to the parent of the deleted
  node."
  [game & {:keys [node-id]}]
  (let [node-id (or node-id (current-node-id game))
        z (game-zip game node-id)
        zr (zip/remove z)]
    (assoc game :root-node (zip/root zr)
                :current-node (if (= (zip/up z) (zip/prev z))
                                (zip/node zr)
                                (zip/node (zip/up zr))))))


(defn remove-children
  "Removes all child nodes of a node (by default, the current node), and
  returns a modified game with the current node set to the node whose
  children we just removed."
  [game & {:keys [node-id]}]
  (let [node-id (or node-id (current-node-id game))
        z (-> (game-zip game node-id) zip-remove-children)]
    (assoc game
      :root-node (zip/root z)
      :current-node (zip/node z))))


(defn promote-node
  "Returns a modified game where the node with the given node id (if no node
  id is supplied, the current node is used) is moved one place to the left
  among its siblings. If the node is already the leftmost (oldest) child of
  its parent node, the game is returned unchanged."
  [game & {:keys [node-id]}]
  (let [node-id (or node-id (current-node-id game))
        z (-> (game-zip game node-id) zip-promote-node)]
    (assoc game :root-node (zip/root z)
                :current-node (zip/node z))))


(defn promote-node-to-main-line
  "Returns a modified game where the node with the given node id (if no node
  id is supplied, the current node is used) is made the main line among its
  siblings, i.e. moved to the front of the list of child nodes. If the node
  is already the leftmost (oldest) child of its parent node, the game is
  returned unchanged."
  [game & {:keys [node-id]}]
  (let [node-id (or node-id (current-node-id game))
        z (-> (game-zip game node-id) zip-promote-node-to-front)]
    (assoc game :root-node (zip/root z)
                :current-node (zip/node z))))


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
  (goto-node-matching game #(= node-id (:node-id %))))


(defn at-beginning?
  "Tests whether we are currently at the beginning of the game, i.e. that the
  current node equals the root node."
  [game]
  (= (:current-node game) (:root-node game)))


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
    (goto-node-matching game #(some #{(:current-node game)} (:children %)))))


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
  (loop [n (:current-node game)
         g (step-back game)]
    (if (or (at-beginning? g)
            (not= n (-> g :current-node :children first)))
      g
      (recur (:current-node g) (step-back g)))))

(defn to-beginning
  "Returns a game identical to the input game, except that current-node is
  set to the root node."
  [game]
  (assoc game :current-node (:root-node game)))


(defn to-end-of-variation
  "Returns a game identical to the input game, except that current node
  is set to the leaf node obtained by following the current variation to
  its end, i.e. by following the sequence of first children from the
  current node until a leaf node is reached."
  [game]
  (loop [n (:current-node game)]
    (if (empty? (:children n))
      (assoc game :current-node n)
      (recur (first (:children n))))))


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


(defn main-line
  "Returns a vector of all nodes along the main line of the game, beginning
  with the root and selecting the oldest child until a leaf is reached."
  [game]
  (loop [node (:root-node game)
         result []]
    (let [child (first (:children node))]
      (if-not child
        (conj result node)
        (recur child (conj result node))))))


(defn boards
  "A sequence of all the board positions along the main line of the game,
  beginning at the root node."
  [game]
  (-> game to-end board board/ancestors))


(defn moves
  "A sequence of all moves along the main line of the game, beginning at the
  root."
  [game]
  (map board/last-move (rest (boards game))))


(defn boards-with-moves
  "A sequence of two element vectors, one for each board position along the
  main line, beginning at the root node. Each two-element vector consists of
  a board along with the move made from that board."
  [game]
  (map vector (boards game) (moves game)))


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
            (let [children (:children node)
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
            (and (empty? (:children node))
                 (or (not include-comments?)
                     (not (:comment node)))))
          (node-to-string [node]
            (let [board (:board node)
                  children (:children node)]
              (str
                (when-not (empty? children)
                  (str
                    ;; Pre-comment for first child move:
                    (when include-comments?
                      (when-let [c (:pre-comment (first children))]
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
                    ;; NAG
                    (when include-comments?
                      (when-let [nag (:nag (first children))]
                        (str "$" nag " ")))
                    ;; Comment for first child move:
                    (when include-comments?
                      (when-let [c (:comment (first children))]
                        (str "{" c "} ")))
                    ;; Recursive annotation variations for younger children:
                    (when include-variations?
                      (apply str
                             (map #(let [m (-> % :board board/last-move)]
                                     (str "("
                                          (when include-comments?
                                            (when-let [c (:pre-comment %)]
                                              (str "{" c "} ")))
                                          (board/move-to-san
                                            board m :include-move-number? true)
                                          (when-not (terminal? %)
                                            " ")
                                          (when include-comments?
                                            (when-let [c (:comment %)]
                                              (str "{" c "} ")))
                                          (node-to-string %)
                                          ") "))
                                  (rest children))))
                    ;; Game continuation after first child move:
                    (node-to-string (first children)))))))]
    (node-to-string (:root-node game))))

(defn- ecn-move-text [game & {:keys [include-comments? include-variations?
                                     binary?]}]
  (let [include-comments? (or include-comments? true)
        include-variations? (or include-variations? true)]
    (if binary?
      [:byte-moves #?(:clj  (byte-array (map (fn [[b m]] (board/move-to-byte b m))
                                             (boards-with-moves game)))
                      :cljs (throw
                              (js/Error
                                "Binary move encoding is not supported in cljs")))]
      (letfn [(node-to-ecn [node]
                (let [board (:board node)
                      children (:children node)]
                  (when-not (empty? children)
                    (filterv
                      identity
                      `[; Pre-comment for first child move
                        ~(when include-comments?
                           (when-let [c (:pre-comment (first children))]
                             [:pre-comment c]))
                        ; String representation of first child move (main variation):
                        ~(-> (first children) :board board/last-move
                             board/move-to-uci)
                        ; NAG
                        ~(when include-comments?
                           (when-let [nag (:nag (first children))]
                             [:nag nag]))
                        ; Comment for first child move:
                        ~(when include-comments?
                           (when-let [c (:comment (first children))]
                             [:comment c]))
                        ; Recursive annotation variations for younger children:
                        ~@(when include-variations?
                            (mapv (fn [child]
                                    (let [m (-> child :board board/last-move)]
                                      (filterv
                                        identity
                                        `[:variation
                                          ~(when include-comments?
                                             (when-let [c (:pre-comment child)]
                                               [:pre-comment c]))
                                          ~(board/move-to-uci m)
                                          ~(when include-comments?
                                             (when-let [c (:comment child)]
                                               [:comment c]))
                                          ~@(node-to-ecn child)])))
                                  (rest children)))
                        ; Game continuation after first child move:
                        ~@(node-to-ecn (first children))]))))]
        `[:moves ~@(node-to-ecn (:root-node game))]))))


(defn- wrap [text column]
  #?(:clj (WordUtils/wrap text column)
     :cljs text))

(defn to-pgn
  "Creates a PGN string from a game, optionally including comments and
  variations."
  [game & {:keys [include-comments? include-variations?]
           :or {include-comments? true include-variations? true}}]
  (str (apply str (map #(cl-format nil "[~a ~s]\n" (first %) (second %))
                       (:tags game)))
       "\n"
       (wrap (move-text game
                        :include-comments? include-comments?
                        :include-variations? include-variations?)
             79)
       " "
       (tag-value game "Result")))

(defn to-ecn
  "Exports the game as ECN data, optionally including comments and
  variations."
  [game & {:keys [include-comments? include-variations? binary?]
           :or {include-comments? true include-variations? true}}]
  `[:game
    [:headers ~@(:tags game)]
    ~(ecn-move-text game
                    :include-comments? include-comments?
                    :include-variations? include-variations?
                    :binary? binary?)])

(defn from-ecn
  "Creates a game from ECN data, or from parsed PGN."
  [ecn & {:keys [include-annotations? san]
          :or {include-annotations? true san false}}]
  (let [fen (reduce (fn [fen tag-value-pair]
                      (if-not (= (str/upper-case (first tag-value-pair))
                                 "FEN")
                        fen
                        (second tag-value-pair)))
                    board/start-fen
                    (rest (second ecn)))]
    (try
      (let [game (reduce #(apply set-tag %1 %2)
                         (new-game :start-fen fen)
                         (rest (second ecn)))]
        (case (first (nth ecn 2))
          :byte-moves (add-byte-move-sequence game (vec (second (nth ecn 2))))
          :moves (if-not include-annotations?
                   (-> game ((if san
                               add-san-move-sequence
                               add-uci-move-sequence)
                              (filter string? (-> ecn (nth 2) rest))))
                   (let [z (zip-add-ecn-data (if san
                                               board/move-from-san
                                               board/move-from-uci)
                                             (game-zip game) (nth ecn 2))]
                     (to-beginning
                       (assoc game :root-node (zip/root z)))))))
      (catch #?(:clj Exception :cljs js/Error) _
        (println "Illegal or ambiguous move in game:")
        (prn ecn)))))

(defn from-pgn
  "Creates a game from a PGN string."
  [pgn-string & {:keys [include-annotations?]
                 :or {include-annotations? true}}]
  (from-ecn (pgn/parse-pgn pgn-string)
            :san true
            :include-annotations? include-annotations?))

#?(:clj
   (defn games-in-file
     "Returns a lazy sequence of all games in an ECN or PGN file. The optional
     :format keyword parameter can take the values :ecn or :pgn, and is used
     to specify the file format. If this parameter is ommited, the function
     tries to guess the file type by inspecting the extension. If the extension
     is neither \".pgn\", \".PGN\", \".ecn\" nor \".ECN\", PGN format is
     assumed."
     [file-name & {:keys [format]}]
     (let [format (or format
                      (if (or (.endsWith file-name ".ecn")
                              (.endsWith file-name ".ECN"))
                        :ecn
                        :pgn))]
       (if (= format :pgn)
         (keep #(from-ecn % :san true) (pgn/games-in-file file-name))
         (keep #(from-ecn % :san false) (ecn/games-in-file file-name))))))
