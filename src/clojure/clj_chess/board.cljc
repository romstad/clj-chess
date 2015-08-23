(ns clj-chess.board
  #?(:cljs (:require [jschess.chess :as jsc])
     :clj (:import (chess Board Move PieceColor Piece Square))))

(def color-white #?(:clj PieceColor/WHITE :cljs 0))
(def color-black #?(:clj PieceColor/BLACK :cljs 1))


(defn square-make [file rank]
  #?(:clj (Square/make file rank)
     :cljs (jsc/squareMake file rank)))

(defn square-file [square]
  #?(:clj (Square/file square)
     :cljs (jsc/squareFile square)))

(defn square-rank [square]
  #?(:clj (Square/rank square)
     :cljs (jsc/squareRank square)))

(defn piece-make [color type]
  #?(:clj (Piece/make color type)
     :cljs (jsc/pieceMake color type)))

(defn move-from [move]
  #?(:clj (Move/from move)
     :cljs (jsc/moveFrom move)))

(defn move-to [move]
  #?(:clj (Move/to move)
     :cljs (jsc/moveTo move)))

(defn is-kingside-castle? [move]
  #?(:clj (Move/isKingsideCastle move)
     :cljs (jsc/moveIsKingsideCastle move)))

(defn is-queenside-castle? [move]
  #?(:clj (Move/isQueensideCastle move)
     :cljs (jsc/moveIsQueensideCastle move)))

(defn move-is-ep? [move]
  #?(:clj (Move/isEP move)
     :cljs (jsc/moveIsEP move)))

(defn move-is-promotion? [move]
  #?(:clj (Move/isPromotion move)
     :cljs (jsc/moveIsPromotion move)))

(defn move-promotion [move]
  #?(:clj (Move/promotion move)
     :cljs (jsc/movePromotion move)))

(def move-none #?(:clj Move/NONE :cljs nil))

(def start-fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

(defn make-board
  "Creates a new chess board from a FEN string. If no string is supplied, the
  standard initial position is used."
  [& [fen]]
  #?(:clj (Board/boardFromFen (or fen start-fen))
     :cljs (.fromFEN jsc/Board (or fen start-fen))))

(defn to-fen
  "Converts the board to a string in Forsyth-Edwards notation."
  [board]
  (.toFEN board))

(defn terminal?
  "Tests whether the board position is terminal, i.e. checkmate or an
  immediate draw."
  [board]
  (.isTerminal board))

(defn checkmate?
  "Tests whether the board position is checkmate."
  [board]
  (.isMate board))

(defn immediate-draw?
  "Tests whether the board position is an immediate draw."
  [board]
  (.isDraw board))

(defn print-board
  "Prints a board to the console, for debugging."
  [board]
  (.print board))

(defn side-to-move
  "The current side to move, :white or :black."
  [board]
  (if (= (.getSideToMove board) color-white)
    :white
    :black))

(defn piece-on
  "The piece on a given square."
  [board square]
  (.pieceOn board square))

(defn can-castle-kingside?
  "Tests whether the given side still has the right to castle kingside."
  [board side]
  (.canCastleKingside board (if (= side :white) 0 1)))

(defn can-castle-queenside?
  "Tests whether the given side still has the right to castle kingside."
  [board side]
  (.canCastleQueenside board (if (= side :white) 0 1)))

(defn castle-rights
  "Castle rights, a subset of #{:white-oo :white-ooo :black-oo :black-ooo}"
  [board]
  (into #{}
        (concat
          (for [[c k] [[:white :white-oo] [:black :black-oo]]
                :when (can-castle-kingside? board c)]
            k)
          (for [[c k] [[:white :white-ooo] [:black :black-ooo]]
                :when (can-castle-queenside? board c)]
            k))))

(defn last-move
  "The last move played to reach this board position, or nil if we are at
  the beginning of the game."
  [board]
  (let [move #?(:clj (.getLastMove board)
                :cljs (.-lastMove board))]
    (if (= move move-none)
      nil
      move)))

(defn checking-pieces
  "Returns a vector of all squares containing checking pieces (0, 1 or 2)."
  [board]
  (vec (.checkingPieces board)))

(defn moves
  "Returns the legal moves for the current board."
  [board]
  (vec (.moves board)))

(defn move-to-uci
  "Translates a move to a string in UCI notation."
  [move]
  #?(:clj (Move/toUCI move)
     :cljs (jsc/moveToUCI move)))

(defn move-from-uci
  [board uci-move]
  "Translates a move string in UCI notation to a move from the given board. If
  no matching move is found, returns nil."
  (first (filter #(= uci-move (move-to-uci %))
                 (moves board))))

(defn move-number
  "Current full move number."
  [board]
  (+ 1 (quot #?(:clj (.getGamePly board)
                :cljs (.-gamePly board))
             2)))
 
(defn move-to-san
  "Translates a move to a string in short algebraic notation, optionally
  including a preceding move number."
  [board move & {:keys [include-move-number?]}]
  (str (cond (not include-move-number?) ""
             (= :white (side-to-move board)) (str (move-number board) ". ")
             :else (str (move-number board) "... "))
       (.moveToSAN board move)))

(defn move-from-san
  "Translate a move string in short algebraic notation to a move from the given
  board. If no matching move is found, returns nil."
  [board san-move]
  (let [move (.moveFromSAN board san-move)]
    (if (= move move-none)
      nil
      move)))

(defn last-move-to-san
  "Translate the last move played to reach this board position to short
  algebraic notation."
  [board]
  #?(:clj (.lastMoveToSAN board)
     :cljs (move-to-san (.-parent board) (last-move board))))

(defn do-move
  "Do a move (encoded as an integer) from the current board, and returns the
  new board. The move is assumed to be legal."
  [board move]
  (.doMove board move))

(defn do-uci-move
  "Do the move represented by a UCI string, and return the new board. If no
  matching move exists, returns nil."
  [board uci-move]
  (when-let [move (move-from-uci board uci-move)]
    (do-move board move)))

(defn do-uci-move-sequence
  "Executes a sequence of moves in UCI notation, and returns the new board,
  assuming that all moves in the sequence are valid, legal moves. Returns
  nil if the sequence contains an invalid move."
  [board uci-move-sequence]
  (if (empty? uci-move-sequence)
    board
    (when-let [new-board (do-uci-move board (first uci-move-sequence))]
      (do-uci-move-sequence new-board (rest uci-move-sequence)))))

(defn do-san-move
  "Do the move represented by a SAN string, and return the new board. If no
  matching move exists, returns nil."
  [board san-move]
  (when-let [move (move-from-san board san-move)]
    (do-move board move)))

(defn do-san-move-sequence
  "Executes a sequence of moves in short algebraic notation, and returns the
  new board, assuming that all moves in the sequence are valid, legal moves.
  Returns nil if the sequence contains an invalid move."
  [board san-move-sequence]
  (if (empty? san-move-sequence)
    board
    (when-let [new-board (do-san-move board (first san-move-sequence))]
      (do-san-move-sequence new-board (rest san-move-sequence)))))


(defn- piece-to-keyword
  "Translates a piece to a keyword like :wp, :wn, etc."
  [piece]
  (case piece
    1 :wp, 2 :wn, 3 :wb, 4 :wr, 5 :wq, 6 :wk
    9 :bp, 10 :bn, 11 :bb, 12 :br, 13 :bq, 14 :bk))

(defn move-from-map
  "Translate a move in map notation to a move from the given board. If no
  maching move is found, returns nil."
  [board move-map]
  (first
    (filter #(and (= (move-from %) (move-map :from))
                  (= (move-to %) (move-map :to))
                  (or (not (move-map :promotion))
                      (let [pr (piece-make (side-to-move board)
                                           (move-promotion %))]
                        (= (piece-to-keyword pr)
                           (move-map :promotion)))))
            (moves board))))

(defn do-map-move
  "Execute the move given by the provided move map. For the format of this
  map, see the documentation of board-to-map."
  [board move-map]
  (when-let [move (move-from-map board move-map)]
    (do-move board move)))

(defn board-to-uci
  "Translates the board state (including as much of the move history as
  necessary) to a format ready to be sent to a UCI chess engine, i.e.
  like 'position fen' followed by a sequence of moves."
  [board]
  (.toUCI board))

(defn variation-to-san
  "Translates a variation from a sequence of UCI moves to a string in
  short algebraic notation."
  [board uci-moves & {:keys [include-move-numbers]}]
  (loop [moves uci-moves
         b board
         result ""]
    (if (empty? moves)
      result
      (let [move (move-from-uci b (first moves))
            wtm (= :white (side-to-move b))
            san-move (move-to-san 
                       b move 
                       :include-move-number? (and include-move-numbers
                                                  (or wtm (empty? result))))]  
        (recur (rest moves)
               (do-move b move)
               (if (empty? result)
                 (str san-move)
                 (str result " " san-move)))))))


(defn move-to-map
  "Translate a move to a map of the format described in the documentation of
  the board-to-map function, e.g.

    {:piece :wp, :from 53, :to 62, :captured-piece :bn, :promote-to :wr}

  for the move fxg8=R."
  [board move]
  (let [from (move-from move)
        to (move-to move)
        map {:piece (piece-to-keyword (.pieceOn board from)) :from from :to to}
        stm (.getSideToMove board)]
    (cond
      (is-kingside-castle? move) (assoc map :rook-from (+ to 1) :rook-to (- to 1))
      (is-queenside-castle? move) (assoc map :rook-from (- to 2) :rook-to (+ to 1))
      (move-is-ep? move) (assoc map :captured-piece (if (= stm color-white) :bp :wp)
                                  :capture-square (square-make (square-file to)
                                                               (square-rank from)))
      (and (move-is-promotion? move) (not (.isEmpty board to)))
      (assoc map :captured-piece (piece-to-keyword (.pieceOn board to))
                 :promote-to (piece-to-keyword
                               (piece-make stm (move-promotion move))))
      (move-is-promotion? move)
      (assoc map :promote-to (piece-to-keyword
                               (piece-make stm (move-promotion move))))
      (not (.isEmpty board to)) (assoc map
                                  :captured-piece (piece-to-keyword (.pieceOn board to)))
      :else map)))


(defn board-to-map
  "Translates a board and its list of legal moves to a map of the following
   format (for the initial position):

     {:pieces [[:br 56] [:bn 57] [:bb 58] [:bq 59] ... [wr 7]]
      :moves [{:piece :white-pawn, :from 8, :to 16} ... ]
      :checks [...]}

  Every move map needs at least the keys :piece, :from and :to. In addition,
  capture moves take a :captured-piece and an optional :capture-square (if
  omitted, assumed to be equal to the :to square). Promotion moves include
  a :promote-to key. Castle moves include the :rook-from and :rook-to keys.
  Some examples:

  The white move fxg8=R, where the captured piece is a knight, would be
  represented by the map

    {:piece :wp, :from 53, :to 62, :captured-piece :bn, :promote-to :wr}

  Black queenside castling would be represented by the map

    {:piece :bk, :from 60, :to 58, :rook-from 56, :rook-to 59}

  An en passant capture exd6 would be represented by the map

    {:piece :wp, :from 36, :to 43, :captured-piece :bp, :capture-square 35}

  No assumptions can be made about the ordering of the lists of pieces
  and legal moves.

  The value attached to the :checks key is a sequence of checking pieces.
  For each checking piece, the sequence contains a map of the form
  {:from <sq-1> :to <sq-2>}, where :from is the square of the checking
  piece, and :to the square of the king."
  [board]
  {:pieces (for [sq (range 64)
                     :when (not (.isEmpty board sq))]
                 [(piece-to-keyword (.pieceOn board sq)) sq])
   :moves (map #(move-to-map board %) (moves board))
   :checks (map (fn [s] {:from s :to (.kingSquare board (.getSideToMove board))})
                (checking-pieces board))})
