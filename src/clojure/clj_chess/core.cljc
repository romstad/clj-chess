(ns clj-chess.core
  (:require [clj-chess.board :as b]
            [clj-chess.game :as g]
            #?(:cljs [jschess.chess :as jsc]))
  #?(:clj (:import (chess Square Piece))))

(def ^:private color-to-keyword
  "Converts the internal integer representation of piece colors to one of
  the keywords :white, :black or :empty."
  [:white :black :empty])

(def ^:private piece-type-to-keyword
  "Converts the internal integer representation of piece types to keywords of
  the form :pawn, :knight, etc."
  [:none :pawn :knight :bishop :rook :queen :king])

(def ^:private piece-to-keyword
  "Converts the internal integer representation of chess pieces to keywords
  of the form :wp :wn, etc."
  [:? :wp :wn :wb :wr :wq :wk :? :? :bp :bn :bb :br :bq :bk :? :empty])

(defn piece-type
  "The type of a piece, given by a keyword of the form :pawn, :knight, etc."
  [piece]
  (piece-type-to-keyword
    #?(:clj (Piece/type piece)
       :cljs (jsc/pieceType piece))))

(defn piece-color
  "The color of a piece, :white or :black."
  [piece]
  (let [color #?(:clj (Piece/color piece)
                 :cljs (jsc/pieceColor piece))]
    (color-to-keyword color)))

(defn move-from
  "The source square of a move"
  [move]
  (b/move-from move))

(defn move-to
  "The destination square of a move"
  [move]
  (b/move-to move))

(defn move-promotion?
  "Tests whether the move is a pawn promotion"
  [move]
  (b/move-is-promotion? move))

(defn move-promotion
  "The piece type the move promotes into, or :none if the move is not a
  promotion."
  [move]
  (piece-type-to-keyword (b/move-promotion move)))

(defn kingside-castle?
  "Tests whether a move is a kingside castling move."
  [move]
  (b/is-kingside-castle? move))

(defn queenside-castle?
  "Tests whether a move is a queenside castling move."
  [move]
  (b/is-queenside-castle? move))

(defn en-passant-capture?
  "Tests whether a move is an en passant capture."
  [move]
  (b/move-is-ep? move))

(def move-none b/move-none)

(defn make-board
  "Creates a new chess board from a FEN string. If no FEN is supplied, the
  standard initial position is used."
  [& [fen]]
  (b/make-board fen))

(defn to-fen
  "Converts the board to a string in Forsyth-Edwards notation."
  [board]
  (b/to-fen board))

(defn terminal?
  "Tests whether the board position is terminal, i.e. checkmate or an
  immediate draw."
  [board]
  (b/terminal? board))

(defn checkmate?
  "Tests whether the board position is checkmate."
  [board]
  (b/checkmate? board))

(defn immediate-draw?
  "Tests whether the board position is an immediate draw."
  [board]
  (b/immediate-draw? board))

(defn print-board
  "Prints the board to the standard output, for debugging."
  [board]
  (b/print-board board))

(defn board-side-to-move
  "The current side to move, :white or :black."
  [board]
  (b/side-to-move board))

(defn square-from-string
  "Converts a string in standard coordinate notation to a square."
  [sq-str]
  #?(:clj (Square/fromString sq-str)
     :cljs (jsc/squareFromString sq-str)))

(defn square-to-string
  "Converts a square to a string in standard coordinate notation."
  [square]
  #?(:clj (Square/toString square)
     :cljs (jsc/squareToString square)))

(defmulti ^{:doc "The piece on a given square"} piece-on
          (fn [_ square] (class square)))

(defmethod piece-on String
  [board square]
  (piece-to-keyword (b/piece-on board (square-from-string square))))

(defmethod piece-on Long
  [board square]
  (piece-to-keyword (b/piece-on board square)))

(defmethod piece-on Integer
  [board square]
  (piece-to-keyword (b/piece-on board square)))

(defn can-castle-kingside?
  "Tests whether the given side still has the right to castle kingside."
  [board side]
  (b/can-castle-kingside? board side))

(defn can-castle-queenside?
  "Tests whether the given side still has the right to castle queenside."
  [board side]
  (b/can-castle-queenside? board side))

(defn castle-rights
  "Castle rights, a subset of #{:white-oo, :white-ooo, :black-oo, :black-ooo}"
  [board]
  (b/castle-rights board))

(defn parent
  "The parent board of the current board (i.e. the board as it was before the
  last move was played), or nil if we're at the beginning of the game."
  [board]
  (b/parent board))

(defn forebears
  "A sequence of all ancestors of the board, ordered by the root board, up to
  and including the input board."
  [board]
  (b/forebears board))

(defn last-move
  "The last move played to reach this board position, or nil if we're at the
  beginning of the game."
  [board]
  (b/last-move board))

(defn checking-pieces
  "Returns a vector of all squares containing checking pieces (always 0, 1 or
  2 pieces)."
  [board]
  (b/checking-pieces board))

(defn moves
  "Returns a vector of all legal moves for the given board."
  [board]
  (b/moves board))

(defn move-to-uci
  "Translates a move to a string in UCI notation."
  [move]
  (b/move-to-uci move))

(defn move-from-uci
  "Translates a move string in UCI notation to a move from the given board. If
  no matching move exists, returns nil."
  [board uci-move]
  (b/move-from-uci board uci-move))

(defn move-number
  "Current full move number for the given board."
  [board]
  (b/move-number board))

(defn move-to-san
  "Translates a move to a string in short algebraic notation, optionally
  including a preceding move number."
  [board move & {:keys [include-move-number?]}]
  (b/move-to-san board move :include-move-number? include-move-number?))

(defn move-from-san
  "Translate a move string in short algebraic notation to a move from the given
  board. If no matching move is found, returns nil."
  [board san-move]
  (b/move-from-san board san-move))

(defn last-move-to-san
  "Translate the last move played to reach this board position to short
  algebraic notation."
  [board]
  (b/last-move-to-san board))

(defn do-move
  "Do a move (encoded as an integer) from the current board, and returns the
  new board. The move is assumed to be legal."
  [board move]
  (b/do-move board move))

(defn do-uci-move
  "Do the move represented by a UCI string, and return the new board. If no
  matching move exists, returns nil."
  [board uci-move]
  (b/do-uci-move board uci-move))

(defn do-uci-move-sequence
  "Executes a sequence of moves in UCI notation, and returns the new board,
  assuming that all moves in the sequence are valid, legal moves. Returns
  nil if the sequence contains an invalid move."
  [board uci-move-sequence]
  (b/do-uci-move-sequence board uci-move-sequence))

(defn do-san-move
  "Do the move represented by a SAN string, and return the new board. If no
  matching move exists, returns nil."
  [board san-move]
  (b/do-san-move board san-move))

(defn do-san-move-sequence
  "Executes a sequence of moves in short algebraic notation, and returns the
  new board, assuming that all moves in the sequence are valid, legal moves.
  Returns nil if the sequence contains an invalid move."
  [board san-move-sequence]
  (b/do-san-move-sequence board san-move-sequence))

(defn board-to-uci
  "Translates the board state (including as much of the move history as
  necessary) to a format ready to be sent to a UCI chess engine, i.e.
  like 'position fen' followed by a sequence of moves."
  [board]
  (b/board-to-uci board))

(defn variation-to-san
  "Translates a variation from a sequence of UCI moves to a string in
  short algebraic notation. Used to pretty-print UCI analysis output."
  [board uci-moves & {:keys [include-move-numbers]}]
  (b/variation-to-san board uci-moves
                      :include-move-numbers include-move-numbers))

(defn new-game
  "Creates a new game object with the given PGN tags and start position. The
  seven standard tags that make up the \"Seven Tag Roster\" in the PGN standard
  are supplied by individual keyword parameters, as is the start-fen parameter
  that is used to specify the initial position of the game. If any further PGN
  tags are desired, they should be supplied in a sequence of two-element
  vectors of the form [<tag-name> <tag-value>], assigned to the keyword
  parameter 'other-tags'."
  [& {:keys [white black event site date round result start-fen other-tags]}]
  (g/new-game :white white :black black :event event :site site :date date
              :round round :result result :start-fen start-fen
              :other-tags other-tags))

(defn tag-value
  "Returns the value of the given PGN tag in the game, or nil if no such
  tag exists."
  [game tag-name]
  (g/tag-value game tag-name))

(defn event
  "The event of the game, as a string."
  [game]
  (g/event game))

(defn date
  "The date the game was played, as a joda.time.DateTime object in Clojure,
  and a string in ClojureScript."
  [game]
  (g/date game))

(defn round
  "The tournament round of the game."
  [game]
  (g/round game))

(defn site
  "The site of the game."
  [game]
  (g/site game))

(defn white-player
  "Name of the white player."
  [game]
  (g/white-player game))

(defn black-player
  "Name of the black player."
  [game]
  (g/black-player game))

(defn result
  "The result of the game. Should be one of the strings \"1-0\", \"0-1\",
  \"1/2-1/2\" and \"*\"."
  [game]
  (g/result game))

(defn white-elo
  "The Elo of the white player (as an integer), or nil."
  [game]
  (g/white-elo game))

(defn black-elo
  "The Elo of the black player (as an integer), or nil."
  [game]
  (g/black-elo game))

(defn remove-tag
  "Returns a new game equal to the input game, but with the given tag removed
  (if it exists)."
  [game tag-name]
  (g/remove-tag game tag-name))

(defn set-tag
  "Returns a new game equal to the input game, but with the given tag set to
  the given value. If the tag does not exist in the input game, it is added
  to the end of the tags list."
  [game name value]
  (g/set-tag game name value))

(defn board
  "Returns the current board position of a game."
  [game]
  (g/board game))

(defn current-node-id
  "Returns the node id of the current node."
  [game]
  (g/current-node-id game))

(defn find-node-matching
  "Finds the first (as found by a depth-first search) game tree node
  matching the given predicate. If no such node is found, returns nil."
  [game predicate & [start-node]]
  (g/find-node-matching game predicate start-node))

(defn find-node
  "Returns the node with the given node id, or nil if no such node exists."
  [game node-id]
  (g/find-node game node-id))

(defn add-move
  "Takes a game, a move function (a function that, given a board and
  some other value, translates that value to a chess move), a
  a value representing a move, a node id, and a boolean that says whether the
  existing moves at the current nodes should be removed, and returns an
  updated game where the move has been added as the last child at the given
  node. If no node id is supplied, the current node of the game is used."
  [game move-function move node-id remove-existing-moves?]
  (g/add-move game move-function move node-id remove-existing-moves?))

(defn add-san-move
  "Adds a move in short algebraic notation to a game at a given node
  id. The move is added as the last child. If no node id is supplied,
  the current node of the game is used. If remove-existing-moves? is true,
  any previously existing moves at the point of insertion are removed."
  [game san-move & {:keys [node-id remove-existing-moves?]}]
  (g/add-san-move game san-move
                  :node-id node-id
                  :remove-existing-moves? remove-existing-moves?))

(defn add-uci-move
  "Adds a move in UCI notation to a game at a given node id. The move is
  added as the last child. If no node id is supplied, the current node of the
  game is used. If remove-existing-moves? is true, any previously existing
  moves at the point of insertion are removed."
  [game uci-move & {:keys [node-id remove-existing-moves?]}]
  (g/add-uci-move game uci-move
                  :node-id node-id
                  :remove-existing-moves? remove-existing-moves?))

(defn add-move-sequence
  "Like add-move, but adds a sequence of moves rather than a single move. This
  is faster than calling add-move multiple times, because we don't have to
  unzip and zip the tree for each added move. If remove-existing-moves? is
  true, any previously existing moves at the point of insertion are removed."
  [game move-function moves node-id remove-existing-moves?]
  (g/add-move-sequence game move-function moves node-id
                       remove-existing-moves?))

(defn add-san-move-sequence
  "Like add-san-move, but adds a sequence of moves rather than a single move.
  This is faster than calling add-san-move multiple times, because we don't
  have to unzip and zip the tree for each added move. If
  remove-existing-moves? is true, any previously existing moves at the point
  of insertion are removed."
  [game san-moves & {:keys [node-id remove-existing-moves?]}]
  (g/add-san-move-sequence game san-moves
                           :node-id node-id
                           :remove-existing-moves? remove-existing-moves?))

(defn add-uci-move-sequence
  "Like add-uci-move, but adds a sequence of moves rather than a single move.
  This is faster than calling add-uci-move multiple times, because we don't
  have to unzip and zip the tree for each added move. If
  remove-existing-moves? is true, any previously existing moves at the point
  of insertion are removed."
  [game uci-moves & {:keys [node-id remove-existing-moves?]}]
  (g/add-uci-move-sequence game uci-moves
                           :node-id node-id
                           :remove-existing-moves? remove-existing-moves?))

(defn add-key-value-pair
  "Adds a key value pair to the map at the given node id of the game. If no
  node id is supplied, the key value pair is added at the current node."
  [game key value & {:keys [node-id]}]
  (g/add-key-value-pair game key value :node-id node-id))

(defn add-comment
  "Adds a comment to the move leading to the node with the given node id.
  Uses current node if no node id is supplied. Adding a comment at the root
  of the game has no effect; if you want to add a comment before the first
  move of the game, use add-pre-comment instead."
  [game cmt & {:keys [node-id]}]
  (g/add-comment game cmt :node-id node-id))

(defn add-pre-comment
  "Adds a pre-comment to the node with the given node-id. Uses the current
  node if no node id is supplied. When exporting as PGN, the pre-comment is
  displayed *before* the move rather than after. It is probably only useful
  for the first move of the game or the first move of a recursive annotation
  variation."
  [game cmt & {:keys [node-id]}]
  (g/add-pre-comment game cmt :node-id node-id))

(defn remove-node
  "Removes a node (by default, the current node) from the game, and returns
  a modified game with the current node set to the parent of the deleted
  node."
  [game & {:keys [node-id]}]
  (g/remove-node game :node-id node-id))

(defn remove-children
  "Removes all child nodes of a node (by default, the current node), and
  returns a modified game with the current node set to the node whose
  children we just removed."
  [game & {:keys [node-id]}]
  (g/remove-children game :node-id node-id))

(defn promote-node
  "Returns a modified game where the node with the given node id (if no node
  id is supplied, the current node is used) is moved one place to the left
  among its siblings. If the node is already the leftmost (oldest) child of
  its parent node, the game is returned unchanged."
  [game & {:keys [node-id]}]
  (g/promote-node game :node-id node-id))

(defn promote-node-to-main-line
  "Returns a modified game where the node with the given node id (if no node
  id is supplied, the current node is used) is made the main line among its
  siblings, i.e. moved to the front of the list of child nodes. If the node
  is already the leftmost (oldest) child of its parent node, the game is
  returned unchanged."
  [game & {:keys [node-id]}]
  (g/promote-node-to-main-line game :node-id node-id))

(defn goto-node-matching
  "Returns a game equal to the input game, except that :current-node is set to
  the first node matching the given predicate. If no such node is found,
  returns nil."
  [game predicate]
  (g/goto-node-matching game predicate))

(defn goto-node-id
  "Returns a game equal to the input game, except that :current-node is set to
  the node with the given node id. If no node with the supplied node id exists
  in the game, returns nil."
  [game node-id]
  (g/goto-node-id game node-id))

(defn at-beginning?
  "Tests whether we are currently at the beginning of the game, i.e. that the
  current node equals the root node."
  [game]
  (g/at-beginning? game))

(defn at-end?
  "Tests whether we are currently at the end of the game, i.e. that the
  current node has no children."
  [game]
  (g/at-end? game))

(defn step-back
  "Steps one move backward in the game (goes back to the parent node), and
  returns the resulting game. The retracted move is not deleted from the game,
  only the :current-node of the game is changed. If we are already at the
  beginning of the game, the original game is returned unchanged."
  [game]
  (g/step-back game))

(defn step-forward
  "Steps one step forward in the game (moves down to the first child node),
  and returns the resulting game. If we are already at the end of the game,
  the original game is returned unchanged."
  [game]
  (g/step-forward game))

(defn to-beginning-of-variation
  "Returns a game identical to the input game, except that we have stepped
  back until reaching the beginning of the variation containing the previous
  current node. In other words, we step back until we reach a branch point
  where the current variation started, or to the root of the game."
  [game]
  (g/to-beginning-of-variation game))

(defn to-beginning
  "Returns a game identical to the input game, except that current-node is
  set to the root node."
  [game]
  (g/to-beginning game))

(defn to-end-of-variation
  "Returns a game identical to the input game, except that current node
  is set to the leaf node obtained by following the current variation to
  its end, i.e. by following the sequence of first children from the
  current node until a leaf node is reached."
  [game]
  (g/to-end-of-variation game))

(defn to-end
  "Returns a game identical to the input game, except that current-node is
  set to the leaf node obtained by following the main line from the root,
  i.e. by following the sequence of first children from the root node until
  a leaf node is reached."
  [game]
  (g/to-end game))

(defn take-back
  "Takes back the move that led to the current node, and returns a game tree
  with the corresponding node removed and the current node set to the parent.
  Does nothing if we're already at the beginning of the game."
  [game]
  (g/take-back game))

(defn main-line
  "Returns a vector of all nodes along the main line of the game, beginning
  with the root and selecting the oldest child until a leaf is reached."
  [game]
  (g/main-line game))

(defn boards
  "A sequence of all the board positions along the main line of the game,
  beginning at the root node."
  [game]
  (g/boards game))

(defn moves
  "A sequence of all moves along the main line of the game, beginning at the
  root."
  [game]
  (g/moves game))

(defn boards-with-moves
  "A sequence of two element vectors, one for each board position along the
  main line, beginning at the root node. Each two-element vector consists of
  a board along with the move made from that board."
  [game]
  (g/boards-with-moves game))

(defn game-to-uci
  "Exports the current game state in a format ready to be sent to a UCI
  chess engine, i.e. like 'position fen' followed by a sequence of moves."
  [game]
  (g/to-uci game))

(defn side-to-move
  "The side to move at the current position of the game, :white or :black."
  [game]
  (g/side-to-move game))

(defn move-text
  "The move text of the game in short algebraic notation, optionally including
  comments and variations."
  [game & {:keys [include-comments? include-variations?]}]
  (g/move-text game
               :include-comments? include-comments?
               :include-variations? include-variations?))

(defn game-to-pgn
  "Creates a PGN string from a game, optionally including comments and
  variations."
  [game]
  (g/to-pgn game))

(defn game-to-ecn
  "Exports the game as ECN data, optionally including comments and
  variations."
  [game]
  (g/to-ecn game))

(defn game-from-ecn
  "Creates a game from ECN data."
  [ecn & {:keys [include-annotations?]}]
  (g/from-ecn ecn :include-annotations? include-annotations?))

(defn game-from-pgn
  "Creates a game from a PGN string."
  [pgn & {:keys [include-annotations?]}]
  (g/from-pgn pgn :include-annotations? include-annotations?))

#?(:clj
   (defn games-in-file
     "Returns a lazy sequence of all games in an ECN or PGN file. The optional
     :format keyword parameter can take the values :ecn or :pgn, and is used
     to specify the file format. If this parameter is ommited, the function
     tries to guess the file type by inspecting the extension. If the extension
     is neither \".pgn\", \".PGN\", \".ecn\" nor \".ECN\", PGN format is
     assumed."
     [file-name & {:keys [format]}]
     (g/games-in-file file-name :format format)))