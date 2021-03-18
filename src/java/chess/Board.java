package chess;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.random.MersenneTwister;

////
//// Class representing a chess position: Not just the board itself, but also
//// the side to move, the castle rights for both sides, the en passant square,
//// a rule 50 counter, and an array of hash keys for previous positions in the
//// game (we need this for detecting repetition draws).
////

public final class Board {

    private static final class MutableBoard {

        Board parent;
        int sideToMove;
        int[] board;
        long[] piecesOfColor;
        long[] piecesOfType;
        int[] kingSquares;
        int epSquare;
        long checkers;
        int castleRights;
        int rule50Counter;
        int gamePly;
        int lastMove;
        long key;
        long blockers;
        int fileCount, rankCount;
        boolean kingIsSpecial;

        // Constructor, creates an empty MutableBoard
        public MutableBoard(int files, int ranks, boolean allowKingCaptures) {
            clear();
            fileCount = files;
            rankCount = ranks;
            int file, rank;
            for (file = Square.FILE_H; file > fileCount - 1; file--) {
                for (rank = Square.RANK_1; rank <= Square.RANK_8; rank++) {
                    putPiece(Piece.BLOCKER, Square.make(file, rank));
                }
            }
            for (rank = Square.RANK_8; rank > rankCount - 1; rank--) {
                for (file = Square.FILE_A; file <= Square.FILE_H; file++) {
                    putPiece(Piece.BLOCKER, Square.make(file, rank));
                }
            }
            kingIsSpecial = !allowKingCaptures;
        }

        public MutableBoard() {
            this(8, 8, false);
        }


        // Constructor, initializes a MutableBoard from a string in Forsyth-Edwards
        // notation.
        public MutableBoard(String fen, boolean allowKingCaptures) {
            clear();
            kingIsSpecial = !allowKingCaptures;

            String[] components = fen.split(" ");
            String s;

            // Board
            s = components[0];

            // Place blockers on top ranks if rank count less than 8:
            rankCount = StringUtils.countMatches(s, "/") + 1;
            for (int rank = Square.RANK_8; rank > rankCount - 1; rank--) {
                for (int file = Square.FILE_A; file <= Square.FILE_H; file++) {
                    putPiece(Piece.BLOCKER, Square.make(file, rank));
                }
            }

            fileCount = 0;
            int rank = rankCount - 1, file = Square.FILE_A;
            for (int i = 0; i < s.length(); i++) {
                if (Character.isDigit(fen.charAt(i))) { // Skip the given number of files
                    file += (int) fen.charAt(i) - (int) '1' + 1;
                } else if (fen.charAt(i) == '/') { // Move to next rank
                    fileCount = Math.max(fileCount, file);
                    file = Square.FILE_A;
                    rank--;
                } else if (fen.charAt(i) == 'x') { // Blocker
                    putPiece(Piece.BLOCKER, Square.make(file, rank));
                    file++;
                } else { // Must be a piece, unless the FEN string is broken
                    int piece = Piece.fromChar(fen.charAt(i));
                    int square = Square.make(file, rank);
                    assert (Piece.isOK(piece));
                    putPiece(piece, square);
                    file++;
                }
            }

            // Place blockers in the rightmost files if file count less than 8:
            for (file = Square.FILE_H; file > fileCount - 1; file--) {
                for (rank = Square.RANK_1; rank <= Square.RANK_8; rank++) {
                    putPiece(Piece.BLOCKER, Square.make(file, rank));
                }
            }

            // Side to move
            if (components.length > 1) {
                s = components[1];
                if (s.charAt(0) == 'b') {
                    sideToMove = PieceColor.BLACK;
                }
            }

            // Castle rights
            if (components.length > 2)
                for (char c : components[2].toCharArray()) {
                    int index = "KkQq".indexOf(c);
                    if (index != -1) {
                        castleRights |= (1 << index);
                    }
                }

            // En passant square
            if (components.length > 3 && !components[3].equals("-")) {
                epSquare = Square.fromString(components[3]);
                if ((SquareSet.pawnAttacks(PieceColor.opposite(sideToMove), epSquare)
                        & pawnsOfColor(sideToMove)) == 0) {
                    epSquare = Square.NONE;
                }
            }

            // Halfmove clock
            if (components.length > 4) {
                rule50Counter = NumberUtils.toInt(components[4]);
            }

            // Move number
            if (components.length > 5) {
                int fullMove = NumberUtils.toInt(components[5]);
                gamePly = (fullMove - 1) * 2;
                if (sideToMove == PieceColor.BLACK) {
                    gamePly += 1;
                }
            }

            initialize();

            assert (isOK());
        }


        public MutableBoard(String fen) {
            this(fen, false);
        }


        // Copy constructor
        public MutableBoard(MutableBoard original) {
            sideToMove = original.sideToMove;
            board = original.board.clone();
            piecesOfColor = original.piecesOfColor.clone();
            piecesOfType = original.piecesOfType.clone();
            kingSquares = original.kingSquares.clone();
            epSquare = original.epSquare;
            castleRights = original.castleRights;
            rule50Counter = original.rule50Counter;
            gamePly = original.gamePly;
            lastMove = original.lastMove;
            key = original.key;
            blockers = original.blockers;
            fileCount = original.fileCount;
            rankCount = original.rankCount;
            kingIsSpecial = original.kingIsSpecial;
        }

        // Reset the board to an empty state with white to move.
        private void clear() {
            sideToMove = PieceColor.WHITE;
            board = new int[64];
            piecesOfColor = new long[2];
            piecesOfType = new long[7];
            kingSquares = new int[2];
            for (int square = Square.MIN; square <= Square.MAX; square++) {
                board[square] = Piece.EMPTY;
            }
            for (int color = PieceColor.MIN; color <= PieceColor.MAX; color++) {
                piecesOfColor[color] = SquareSet.EMPTY;
            }
            for (int type = PieceType.MIN; type <= PieceType.MAX; type++) {
                piecesOfType[type] = SquareSet.EMPTY;
            }
            piecesOfType[0] = SquareSet.EMPTY; // Occupied squares
            castleRights = CASTLE_RIGHTS_NONE;
            epSquare = Square.NONE;
            checkers = SquareSet.EMPTY;
            lastMove = Move.NONE;
            key = 0;
            rule50Counter = 0;
            gamePly = 0;
            blockers = 0;
            fileCount = 8;
            rankCount = 8;
            kingIsSpecial = true;
        }


        public void initialize() {
            // Checking pieces
            checkers = findCheckers();

            // Hash key
            key = computeKey();
        }


        public void setKingIsSpecial(boolean newValue) {
            kingIsSpecial = newValue;
        }


        public boolean getKingIsSpecial() {
            return kingIsSpecial;
        }


        /// Get the current hash key.
        public long getKey() {
            return key;
        }

        /// Parent board (can be nil):
        public Board getParent() {
            return parent;
        }

        /// Grandparent board (can be nil). Used when looking back through the
        /// ancestors to check for repetition draws.
        private Board getGrandparent() {
            return getParent() == null ? null : getParent().getParent();
        }

        /// Maximum ranks and files, for smaller boards.
        public int maxRank() {
            return Square.RANK_MAX - (8 - rankCount);
        }

        public int maxFile() {
            return Square.FILE_MAX - (8 - fileCount);
        }

        /// Getters for rank and file counts
        public int getFileCount() {
            return fileCount;
        }

        public int getRankCount() {
            return rankCount;
        }

        /// Getter method for the current side to move.
        public int getSideToMove() {
            return sideToMove;
        }

        public void setSideToMove(int newValue) {
            sideToMove = newValue;
        }

        /// The last move played to reach this position, or Move.NONE if
        /// we're at the beginning of the game (board has no parent):
        public int getLastMove() {
            return lastMove;
        }

        /// Number of half moves since the last non-reversible move was made
        public int getRule50Counter() {
            return rule50Counter;
        }

        public void setRule50Counter(int newValue) {
            rule50Counter = newValue;
        }

        /// Number of half moves played in the current game before reaching
        /// this position.
        public int getGamePly() {
            return gamePly;
        }

        public void setGamePly(int newValue) {
            gamePly = newValue;
        }

        /// The piece on the given square, or Piece.EMPTY if the square is empty.
        public int pieceOn(int square) {
            return board[square];
        }

        /// Test whether the given square on the board is empty.
        public boolean isEmpty(int square) {
            return pieceOn(square) == Piece.EMPTY;
        }

        /// Getter method for the current en passant square. If no en passant
        /// capture is possible, Square.NONE is returned.
        public int getEpSquare() {
            return epSquare;
        }

        public void setEpSquare(int newValue) {
            epSquare = newValue;
        }

        public void setCastleRights(int newValue) {
            castleRights = newValue;
        }

        /// Square set representing all occupied squares on the board.
        public long occupiedSquares() {
            return piecesOfType[0] | blockers;
        }

        /// Square set representing all empty squares on the board.
        public long emptySquares() {
            return ~occupiedSquares();
        }

        /// Blocked squares, for boards with "holes".
        public long blockedSquares() {
            return blockers;
        }

        /// The set of squares occupied by pieces of the given color.
        public long piecesOfColor(int color) {
            return piecesOfColor[color];
        }

        /// The set of squares occupied by pieces of the given piece type.
        public long piecesOfType(int type) {
            return piecesOfType[type];
        }

        /// The set of squares occupied by pieces of the given color and
        /// piece type.
        public long piecesOfColorAndType(int color, int type) {
            return piecesOfColor(color) & piecesOfType(type);
        }

        /// The set of squares containing pawns of either color.
        public long pawns() {
            return piecesOfType(PieceType.PAWN);
        }

        /// The set of squares containing knights of either color.
        public long knights() {
            return piecesOfType(PieceType.KNIGHT);
        }

        /// The set of squares containing bishops of either color.
        public long bishops() {
            return piecesOfType(PieceType.BISHOP);
        }

        /// The set of squares containing rooks of either color.
        public long rooks() {
            return piecesOfType(PieceType.ROOK);
        }

        /// The set of squares containing queens of either color.
        public long queens() {
            return piecesOfType(PieceType.QUEEN);
        }

        /// The set of squares containing kings of either color.
        public long kings() {
            return piecesOfType(PieceType.KING);
        }

        /// The set of squares containing pawns of the given color.
        public long pawnsOfColor(int color) {
            return pawns() & piecesOfColor(color);
        }

        /// The set of squares containing knights of the given color.
        public long knightsOfColor(int color) {
            return knights() & piecesOfColor(color);
        }

        /// The set of squares containing bishops of the given color.
        public long bishopsOfColor(int color) {
            return bishops() & piecesOfColor(color);
        }

        /// The set of squares containing rooks of the given color.
        public long rooksOfColor(int color) {
            return rooks() & piecesOfColor(color);
        }

        /// The set of squares containing queens of the given color.
        public long queensOfColor(int color) {
            return queens() & piecesOfColor(color);
        }

        /// The set of squares containing kings of the given color. This
        /// set should always be a singleton.
        public long kingsOfColor(int color) {
            return kings() & piecesOfColor(color);
        }

        /// The square of the king for the given color.
        public int kingSquare(int color) {
            return kingSquares[color];
        }

        /// The set of squares that would be attacked by a pawn of the given color
        /// on the given square.
        public long pawnAttacks(int color, int square) {
            return SquareSet.pawnAttacks(color, square) & ~blockedSquares();
        }

        /// The set of squares that would be attacked by a knight of the given
        /// color on the given square.
        public long knightAttacks(int square) {
            return SquareSet.knightAttacks(square) & ~blockedSquares();
        }

        /// The set of squares that would be attacked by a bishop of the given
        /// color on the given square.
        public long bishopAttacks(int square) {
            return SquareSet.bishopAttacks(square, occupiedSquares()) & ~blockedSquares();
        }

        /// The set of squares that would be attacked by a rook of the given color
        /// on the given square.
        public long rookAttacks(int square) {
            return SquareSet.rookAttacks(square, occupiedSquares()) & ~blockedSquares();
        }

        /// The set of squares that would be attacked by a queen of the given color
        /// on the given square.
        public long queenAttacks(int square) {
            return SquareSet.queenAttacks(square, occupiedSquares()) & ~blockedSquares();
        }

        /// The set of squares that would be attacked by a king of the given color
        /// on the given square.
        public long kingAttacks(int square) {
            return SquareSet.kingAttacks(square) & ~blockedSquares();
        }

        /// Tests whether the given square is attacked by the given color.
        public boolean isAttacked(int square, int color) {
            return !SquareSet.isEmpty(pawnAttacks(PieceColor.opposite(color), square) & pawnsOfColor(color)) ||
                    !SquareSet.isEmpty(knightAttacks(square) & knightsOfColor(color)) ||
                    !SquareSet.isEmpty(kingAttacks(square) & kingsOfColor(color)) ||
                    !SquareSet.isEmpty(bishopAttacks(square) & (bishopsOfColor(color) | queensOfColor(color))) ||
                    !SquareSet.isEmpty(rookAttacks(square) & (rooksOfColor(color) | queensOfColor(color)));
        }

        /// The set of squares containing pieces of the given color that attacks
        /// the given square.
        public long attacksTo(int square, int color) {
            return (pawnAttacks(PieceColor.opposite(color), square) & pawnsOfColor(color))
                    | (knightAttacks(square) & knightsOfColor(color))
                    | (bishopAttacks(square) & (bishopsOfColor(color) | queensOfColor(color)))
                    | (rookAttacks(square) & (rooksOfColor(color) | queensOfColor(color)))
                    | (kingAttacks(square) & (kingsOfColor(color)));
        }

        /// The set of squares containing pinned pieces of the given color.
        public long pinnedPieces(int color) {
            // If king is not special, there cannot be any pinned pieces.
            if (!kingIsSpecial) {
                return 0;
            }

            long pinned = SquareSet.EMPTY, occ = occupiedSquares();
            long sliders, pinners, blockers;
            int ksq = kingSquare(color);
            int them = PieceColor.opposite(color);

            sliders = queensOfColor(them) | rooksOfColor(them);
            blockers = rookAttacks(ksq) & piecesOfColor(color);
            pinners = SquareSet.rookAttacks(ksq, occ ^ blockers) & sliders;
            while (pinners != SquareSet.EMPTY) {
                int pinner = SquareSet.first(pinners);
                pinners = SquareSet.removeFirst(pinners);
                pinned |= SquareSet.squaresBetween(pinner, ksq) & blockers;
            }

            sliders = queensOfColor(them) | bishopsOfColor(them);
            blockers = bishopAttacks(ksq) & piecesOfColor(color);
            pinners = SquareSet.bishopAttacks(ksq, occ ^ blockers) & sliders;
            while (pinners != SquareSet.EMPTY) {
                int pinner = SquareSet.first(pinners);
                pinners = SquareSet.removeFirst(pinners);
                pinned |= SquareSet.squaresBetween(pinner, ksq) & blockers;
            }

            return pinned;
        }


        /// Test whether the given color still has the right to castle kingside.
        public boolean canCastleKingside(int color) {
            return (castleRights & (1 << color)) != 0;
        }


        /// Test whether the given color still has the right to castle queenside.
        public boolean canCastleQueenside(int color) {
            return (castleRights & (4 << color)) != 0;
        }


        /// Test whether the side to move is in check
        public boolean isCheck() {
            if (!kingIsSpecial) {
                return false;
            }
            return checkers != SquareSet.EMPTY;
        }


        /// Find all checking pieces, return them as an array of squares.
        public List<Integer> checkingPieces() {
            List<Integer> result = new ArrayList<>();
            if (!kingIsSpecial) {
                return result;
            }

            long ss = checkers;
            while (ss != SquareSet.EMPTY) {
                result.add(SquareSet.first(ss));
                ss = SquareSet.removeFirst(ss);
            }
            return result;
        }


        /// Generate a list of all pseudo-legal moves. "Pseudo-legal" here means
        /// that we don't check whether the moves generated leaves the friendly
        /// king in check. For instance, after 1. d4 Nf6 2. c4 e6 3. Nc3 Bb4,
        /// moves like Nc3-b1 would be included. In other words, all legal moves
        /// will always be included in the list, but some illegal moves may end
        /// up there as well.
        public List<Integer> generateMoves() {
            List<Integer> moves = new ArrayList<Integer>();

            if (isCheck()) {
                generateEvasions(moves);
            } else {
                generatePawnMoves(moves);
                generateKnightMoves(moves);
                generateBishopMoves(moves);
                generateRookMoves(moves);
                generateQueenMoves(moves);
                generateKingMoves(moves);
            }
            return moves;
        }


        /// Generate a list of all legal moves.
        public List<Integer> legalMoves() {
            if (!kingIsSpecial) {
                return generateMoves();
            }
            List<Integer> result = new ArrayList<Integer>();
            for (int move : generateMoves()) {
                if (moveIsLegal(move)) {
                    result.add(move);
                }
            }
            return result;
        }


        /// Generate a list of all legal moves from the given square.
        public List<Integer> movesFrom(int square) {
            List<Integer> result = new ArrayList<Integer>();
            for (int move : generateMoves()) {
                if (Move.from(move) == square && moveIsLegal(move)) {
                    result.add(move);
                }
            }
            return result;
        }


        /// Generate a list of all legal moves to the given square.
        public List<Integer> movesTo(int square) {
            List<Integer> result = new ArrayList<>();
            for (int move : generateMoves()) {
                if (Move.to(move) == square && moveIsLegal(move)) {
                    result.add(move);
                }
            }
            return result;
        }


        /// Generate a list of all legal moves of the given piece type to the
        /// square. Useful when parsing moves in short algebraic notation.
        public List<Integer> movesForPieceTypeToSquare(int pieceType, int square) {
            List<Integer> result = new ArrayList<Integer>();
            for (int move : generateMoves()) {
                if (Piece.type(pieceOn(Move.from(move))) == pieceType &&
                        square == Move.to(move) && moveIsLegal(move)) {
                    result.add(move);
                }
            }
            return result;
        }


        /// Test whether a pseudo-legal move (as generated by generateMoves()) is
        /// actually legal.
        public boolean moveIsLegal(int move) {

            // If the king is not special, all pseudo-legal moves are legal.
            if (!kingIsSpecial) {
                return true;
            }

            // Because we use a special move generator that only generates legal
            // moves when we're in check, we don't have to do any extra legality
            // checking in that case:
            if (isCheck()) {
                return true;
            }

            // Castling moves are always checked for legality during move
            // generation.
            if (Move.isCastle(move)) {
                return true;
            }

            int us = sideToMove, them = PieceColor.opposite(us);
            long pinned = pinnedPieces(us);
            int from = Move.from(move), to = Move.to(move), ksq = kingSquare(us);

            // En passant moves are messy, as always.
            if (Move.isEP(move)) {
                int captureSq = Square.make(Square.file(to), Square.rank(from));

                // Simulate the effect of executing the en passant capture, by
                // calculating how the occupied squares would look after the move
                // is made:
                long occ = occupiedSquares();
                occ = SquareSet.remove(occ, from);
                occ = SquareSet.remove(occ, captureSq);
                occ = SquareSet.add(occ, to);

                // Using the computed set of occupied squares, check whether an
                // enemy slider would attack the friendly king:
                if ((SquareSet.bishopAttacks(ksq, occ) & (bishopsOfColor(them) | queensOfColor(them))) != SquareSet.EMPTY) {
                    return false;
                }
                if ((SquareSet.rookAttacks(ksq, occ) & (rooksOfColor(them) | queensOfColor(them))) != SquareSet.EMPTY) {
                    return false;
                }

                // Fine, the en passant capture appears to be legal!
                return true;
            }

            // King moves: Simply check that the destination square is not attacked
            // by the enemy.
            if (from == ksq) {
                return !isAttacked(to, them);
            }

            // If the moving piece is not pinned, the move must be legal.
            if (!SquareSet.contains(pinned, from)) {
                return true;
            }

            // If the moving piece is pinned, the move is legal if and only if
            // the source and destination square are situated along the same
            // rank, file or diagonal from the friendly king.
            return SquareSet.contains(SquareSet.squaresBetween(from, ksq), to)
                    || SquareSet.contains(SquareSet.squaresBetween(to, ksq), from);
        }


        /// Update the board by executing the given move (which is assumed to be
        /// legal: Always call moveIsLegal on each move generated by generateMoves
        /// before calling doMove).
        public void doMove(int move) {
            int us = sideToMove, them = PieceColor.opposite(us);
            int from = Move.from(move), to = Move.to(move);
            int piece = pieceOn(from), capture = pieceOn(to);
            int promotion = Move.promotion(move);

            sideToMove = them;
            epSquare = Square.NONE;
            rule50Counter++;
            gamePly++;
            lastMove = move;

            if (Move.isEP(move)) {
                int captureSquare = to - Square.pawnPush(us);
                removePiece(captureSquare);
                movePiece(from, to);
                rule50Counter = 0;
            } else if (Move.isCastle(move)) {
                int rfrom = (to > from) ? to + 1 : to - 2;
                int rto = (to > from) ? to - 1 : to + 1;
                movePiece(from, to);
                movePiece(rfrom, rto);
            } else {
                if (capture != Piece.EMPTY) {
                    removePiece(to);
                    rule50Counter = 0;
                }
                if (promotion != 0) {
                    removePiece(from);
                    putPiece(Piece.make(us, promotion), to);
                } else {
                    movePiece(from, to);
                }
                if (Piece.type(piece) == PieceType.PAWN) {
                    rule50Counter = 0;
                    if (to - from == 2 * Square.pawnPush(us)
                            && (pawnAttacks(us, (to + from) / 2) & pawnsOfColor(them)) != SquareSet.EMPTY) {
                        epSquare = (to + from) / 2;
                    }
                }
            }

            if (Piece.type(piece) == PieceType.KING) {
                kingSquares[us] = to;
            }

            int a1 = Square.A1, h1 = Square.H1, e1 = Square.E1;
            int a8 = Square.make(Square.FILE_A, rankCount - 1);
            int h8 = Square.make(Square.FILE_H, rankCount - 1);
            int e8 = Square.make(Square.FILE_E, rankCount - 1);

            if (from == a1 || to == a1) {
                castleRights &= ~CASTLE_RIGHTS_WHITE_OOO;
            }
            if (from == h1 || to == h1) {
                castleRights &= ~CASTLE_RIGHTS_WHITE_OO;
            }
            if (from == a8 || to == a8) {
                castleRights &= ~CASTLE_RIGHTS_BLACK_OOO;
            }
            if (from == h8 || to == h8) {
                castleRights &= ~CASTLE_RIGHTS_BLACK_OO;
            }
            if (from == e1) {
                castleRights &= ~(CASTLE_RIGHTS_WHITE_OO | CASTLE_RIGHTS_WHITE_OOO);
            }
            if (from == e8) {
                castleRights &= ~(CASTLE_RIGHTS_BLACK_OO | CASTLE_RIGHTS_BLACK_OOO);
            }

            checkers = findCheckers();
            key = computeKey();
        }


        /// Do a null move. Only possible when the side to move is not in check.
        public void doNullMove() {
            assert (!isCheck());

            sideToMove = PieceColor.opposite(sideToMove);
            epSquare = Square.NONE;
            rule50Counter++;
            gamePly++;
            lastMove = Move.NONE;

            key = computeKey();
        }


        /// Test whether the current position is checkmate.
        public boolean isMate() {
            return isCheck() && generateMoves().size() == 0;
        }


        /// Test whether the current position is drawn by stalemate.
        public boolean isStalemate() {
            if (isCheck()) {
                return false;
            }
            for (int move : generateMoves()) {
                if (moveIsLegal(move)) {
                    return false;
                }
            }
            return true;
        }


        /// Test whether the current position is drawn by lack of mating
        /// material.
        public boolean isMaterialDraw() {
            return (pawns() | rooks() | queens()) == SquareSet.EMPTY
                    && SquareSet.count(knights() | bishops()) <= 1;
        }


        /// Test whether the current position is drawn by the 50 moves rule.
        public boolean isRule50draw() {
            return rule50Counter >= 100;
        }


        /// Test whether the current position is drawn by triple repetition.
        public boolean isRepetitionDraw() {
            int repetitionCount = 1;
            Board b = getGrandparent();
            for (int i = 2; b != null && i < Math.min(gamePly, rule50Counter); i += 2) {
                if (b.getKey() == key) {
                    repetitionCount++;
                }
                b = b.getGrandparent();
            }
            return repetitionCount >= 3;
        }


        /// Test whether the current position is an immediate draw.
        public boolean isDraw() {
            return isRule50draw() || isMaterialDraw() || isRepetitionDraw() || isStalemate();
        }


        /// Test whether the current position is terminal, i.e. whether the game is
        /// over.
        public boolean isTerminal() {
            return isMate() || isDraw();
        }


        /// Export the position to a string in Forsyth-Edwards notation.
        public String toFEN() {
            StringBuilder buffer = new StringBuilder(100);

            // Board
            for (int rank = rankCount - 1; rank >= Square.RANK_1; rank--) {
                int emptySquareCount = 0;
                for (int file = Square.FILE_A; file < fileCount; file++) {
                    int square = Square.make(file, rank);
                    int piece = pieceOn(square);
                    if (piece == Piece.EMPTY) {
                        emptySquareCount++;
                    } else {
                        if (emptySquareCount > 0)
                            buffer.append(emptySquareCount);
                        buffer.append(Piece.toChar(piece));
                        emptySquareCount = 0;
                    }
                }
                if (emptySquareCount > 0) {
                    buffer.append(emptySquareCount);
                }
                buffer.append(rank > Square.RANK_1 ? '/' : ' ');
            }

            // Side to move
            buffer.append(PieceColor.toChar(sideToMove));
            buffer.append(' ');

            // Castle rights
            if (castleRights == CASTLE_RIGHTS_NONE) {
                buffer.append("- ");
            } else {
                if (canCastleKingside(PieceColor.WHITE)) {
                    buffer.append('K');
                }
                if (canCastleQueenside(PieceColor.WHITE)) {
                    buffer.append('Q');
                }
                if (canCastleKingside(PieceColor.BLACK)) {
                    buffer.append('k');
                }
                if (canCastleQueenside(PieceColor.BLACK)) {
                    buffer.append('q');
                }
                buffer.append(' ');
            }

            // En passant square
            int mv = getLastMove(), from = Move.from(mv), to = Move.to(mv);
            if (mv != Move.NONE
                    && Piece.type(pieceOn(to)) == PieceType.PAWN
                    && Math.abs(to - from) == 2 * Square.pawnPush(PieceColor.WHITE)) {
                buffer.append(Square.toString((to + from) / 2));
                buffer.append(' ');
            } else {
                buffer.append("- ");
            }

            // Halfmove clock
            buffer.append(rule50Counter);
            buffer.append(' ');

            // Move number
            buffer.append(gamePly / 2 + 1);

            return buffer.toString();
        }


        /// Print the board in ASCII format to the standard output, for debugging.
        public void print() {
            String[] pieceStrings = {
                    "| ? ", "| P ", "| N ", "| B ", "| R ", "| Q ", "| K ", "| ? ",
                    "| ? ", "|=P=", "|=N=", "|=B=", "|=R=", "|=Q=", "|=K="
            };
            for (int rank = Square.RANK_8; rank >= Square.RANK_1; rank--) {
                System.out.println("+---+---+---+---+---+---+---+---+");
                for (int file = Square.FILE_A; file <= Square.FILE_H; file++) {
                    int square = Square.make(file, rank);
                    int piece = pieceOn(square);
                    if (piece == Piece.BLOCKER) {
                        System.out.print("|###");
                    } else if (piece == Piece.EMPTY) {
                        System.out.print((file + rank) % 2 == 0 ? "|   " : "| . ");
                    } else {
                        System.out.print(pieceStrings[piece]);
                    }
                }
                System.out.println("|");
            }
            System.out.println("+---+---+---+---+---+---+---+---+");
            System.out.println(toFEN());
        }

        /// Add a piece to the given square.
        public void putPiece(int piece, int square) {
            assert (Piece.isOK(piece) || piece == Piece.BLOCKER);
            assert (Square.isOK(square));

            board[square] = piece;
            if (piece == Piece.BLOCKER) {
                blockers = SquareSet.add(blockers, square);
                piecesOfType[0] = SquareSet.add(piecesOfType[0], square);
            } else {
                int color = Piece.color(piece), type = Piece.type(piece);
                piecesOfColor[color] = SquareSet.add(piecesOfColor[color], square);
                piecesOfType[type] = SquareSet.add(piecesOfType[type], square);
                piecesOfType[0] = SquareSet.add(piecesOfType[0], square);
                if (type == PieceType.KING) {
                    kingSquares[color] = square;
                }
            }
        }


        /// Remove the piece on the given square.
        private void removePiece(int square) {
            assert (Square.isOK(square));
            assert (!isEmpty(square));

            int piece = board[square], color = Piece.color(piece), type = Piece.type(piece);
            assert (!kingIsSpecial || type != PieceType.KING);

            board[square] = Piece.EMPTY;
            if (piece == Piece.BLOCKER) {
                blockers = SquareSet.remove(blockers, square);
                piecesOfType[0] = SquareSet.remove(piecesOfType[0], square);
            } else {
                piecesOfColor[color] = SquareSet.remove(piecesOfColor[color], square);
                piecesOfType[type] = SquareSet.remove(piecesOfType[type], square);
                piecesOfType[0] = SquareSet.remove(piecesOfType[0], square);
            }
        }


        /// Move the piece on 'from' to 'to'. The 'to' square must be empty. In the
        /// case of captures, removePiece must be called on the 'to' square before
        /// movePiece is called.
        private void movePiece(int from, int to) {
            assert (Square.isOK(from));
            assert (Square.isOK(to));
            assert (!isEmpty(from));
            assert (pieceOn(from) != Piece.BLOCKER);
            assert (isEmpty(to));

            int piece = pieceOn(from), color = Piece.color(piece), type = Piece.type(piece);
            board[from] = Piece.EMPTY;
            board[to] = piece;
            piecesOfColor[color] = SquareSet.move(piecesOfColor[color], from, to);
            piecesOfType[type] = SquareSet.move(piecesOfType[type], from, to);
            piecesOfType[0] = SquareSet.move(piecesOfType[0], from, to);

            if (type == PieceType.KING) {
                kingSquares[color] = to;
            }
        }


        /// Compute the set of squares occupied by checking pieces.
        private long findCheckers() {
            if (!kingIsSpecial) {
                return 0;
            }
            return attacksTo(kingSquare(sideToMove), PieceColor.opposite(sideToMove));
        }


        private static int sq2j(int sq) {
            int f = sq % 8;
            int r = sq / 8;
            return (f << 3) | (7 - r);
        }


        /// Compute the Zobrist hash key for the current position.
        private long computeKey() {
            long result = 0;
            long occ = occupiedSquares() & ~blockedSquares();

            while (occ != SquareSet.EMPTY) {
                int square = SquareSet.first(occ);
                occ = SquareSet.removeFirst(occ);
                result ^= ZOBRIST[sq2j(square) + 64 * (pieceOn(square) - 1)];
                //result ^= zobrist[pieceOn(square) * 64 + square];
            }
            if (epSquare != Square.NONE) {
                result ^= ZOBRIST[sq2j(epSquare) + 64 * 6];
                //result ^= zobEP[epSquare];
            }
            int cr = 0;
            if (canCastleKingside(PieceColor.WHITE)) {
                cr |= 1;
            }
            if (canCastleQueenside(PieceColor.WHITE)) {
                cr |= 2;
            }
            if (canCastleKingside(PieceColor.BLACK)) {
                cr |= 4;
            }
            if (canCastleQueenside(PieceColor.BLACK)) {
                cr |= 8;
            }
            result ^= ZOBRIST[cr + 64 * 7];
            //result ^= zobCastle[castleRights];
            if (sideToMove == PieceColor.BLACK) {
                result ^= ZOBRIST[63 + 64 * 7];
                //result ^= zobSideToMove;
            }
            return result;
        }


        /// Generate all pseudo-legal pawn moves and add them to the supplied list.
        /// Pawn moves, as always, are messy.
        private void generatePawnMoves(List<Integer> moves) {
            int us = sideToMove, them = PieceColor.opposite(us);
            long pawns = pawnsOfColor(us);
            long theirPieces = piecesOfColor(them);
            long target, promotionZone, thirdRank;

            if (sideToMove == PieceColor.WHITE) {
                promotionZone = SquareSet.RANK_8_SQUARES;
                for (int i = 8; i > rankCount; i--) {
                    promotionZone = SquareSet.shiftS(promotionZone);
                }
                thirdRank = SquareSet.RANK_3_SQUARES;

                // Non-promotion captures
                target = SquareSet.shiftNW(pawns) & theirPieces & ~promotionZone;
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    moves.add(Move.make(to - Square.DELTA_NW, to));
                }
                target = SquareSet.shiftNE(pawns) & theirPieces & ~promotionZone;
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    moves.add(Move.make(to - Square.DELTA_NE, to));
                }

                // Promotion captures
                target = SquareSet.shiftNW(pawns) & theirPieces & promotionZone;
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    for (int prom = PieceType.QUEEN; prom >= PieceType.KNIGHT; prom--) {
                        moves.add(Move.makePromotion(to - Square.DELTA_NW, to, prom));
                    }
                }
                target = SquareSet.shiftNE(pawns) & theirPieces & promotionZone;
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    for (int prom = PieceType.QUEEN; prom >= PieceType.KNIGHT; prom--) {
                        moves.add(Move.makePromotion(to - Square.DELTA_NE, to, prom));
                    }
                }

                // Non-promotion pawn pushes
                target = SquareSet.shiftN(pawns) & emptySquares() & ~promotionZone;
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    moves.add(Move.make(to - Square.DELTA_N, to));
                }

                // Double pawn pushes
                target = SquareSet.shiftN(SquareSet.shiftN(pawns) & emptySquares() & thirdRank) & emptySquares();
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    moves.add(Move.make(to - 2 * Square.DELTA_N, to));
                }

                // Promotion pawn pushes
                target = SquareSet.shiftN(pawns) & emptySquares() & promotionZone;
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    for (int prom = PieceType.QUEEN; prom >= PieceType.KNIGHT; prom--) {
                        moves.add(Move.makePromotion(to - Square.DELTA_N, to, prom));
                    }
                }
            } else { // sideToMove == pieceColor.BLACK
                promotionZone = SquareSet.RANK_1_SQUARES;
                thirdRank = SquareSet.RANK_6_SQUARES;
                for (int i = 8; i > rankCount; i--) {
                    thirdRank = SquareSet.shiftS(thirdRank);
                }

                // Non-promotion captures
                target = SquareSet.shiftSW(pawns) & theirPieces & ~promotionZone;
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    moves.add(Move.make(to - Square.DELTA_SW, to));
                }
                target = SquareSet.shiftSE(pawns) & theirPieces & ~promotionZone;
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    moves.add(Move.make(to - Square.DELTA_SE, to));
                }

                // Promotion captures
                target = SquareSet.shiftSW(pawns) & theirPieces & promotionZone;
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    for (int prom = PieceType.QUEEN; prom >= PieceType.KNIGHT; prom--) {
                        moves.add(Move.makePromotion(to - Square.DELTA_SW, to, prom));
                    }
                }
                target = SquareSet.shiftSE(pawns) & theirPieces & promotionZone;
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    for (int prom = PieceType.QUEEN; prom >= PieceType.KNIGHT; prom--) {
                        moves.add(Move.makePromotion(to - Square.DELTA_SE, to, prom));
                    }
                }

                // Non-promotion pawn pushes
                target = SquareSet.shiftS(pawns) & emptySquares() & ~promotionZone;
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    moves.add(Move.make(to - Square.DELTA_S, to));
                }

                // Double pawn pushes
                target = SquareSet.shiftS(SquareSet.shiftS(pawns) & emptySquares() & thirdRank) & emptySquares();
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    moves.add(Move.make(to - 2 * Square.DELTA_S, to));
                }

                // Promotion pawn pushes
                target = SquareSet.shiftS(pawns) & emptySquares() & promotionZone;
                while (target != SquareSet.EMPTY) {
                    int to = SquareSet.first(target);
                    target = SquareSet.removeFirst(target);
                    for (int prom = PieceType.QUEEN; prom >= PieceType.KNIGHT; prom--) {
                        moves.add(Move.makePromotion(to - Square.DELTA_S, to, prom));
                    }
                }
            }

            // En passant captures
            if (epSquare != Square.NONE) {
                long source = pawnAttacks(them, epSquare) & pawns;
                while (source != SquareSet.EMPTY) {
                    int from = SquareSet.first(source);
                    source = SquareSet.removeFirst(source);
                    moves.add(Move.makeEP(from, epSquare));
                }
            }

        }


        /// Generate all pseudo-legal knight moves, and add them to the supplied
        /// list.
        private void generateKnightMoves(List<Integer> moves) {
            long source = knightsOfColor(sideToMove);
            long target = ~piecesOfColor(sideToMove) & ~blockedSquares();
            long ss;

            while (source != SquareSet.EMPTY) {
                int from = SquareSet.first(source);
                source = SquareSet.removeFirst(source);
                ss = target & knightAttacks(from);
                while (ss != SquareSet.EMPTY) {
                    int to = SquareSet.first(ss);
                    ss = SquareSet.removeFirst(ss);
                    moves.add(Move.make(from, to));
                }
            }
        }


        /// Generate all pseudo-legal bishop moves, and add them to the supplied
        /// list.
        private void generateBishopMoves(List<Integer> moves) {
            long source = bishopsOfColor(sideToMove);
            long target = ~piecesOfColor(sideToMove) & ~blockedSquares();
            long ss;

            while (source != SquareSet.EMPTY) {
                int from = SquareSet.first(source);
                source = SquareSet.removeFirst(source);
                ss = target & bishopAttacks(from);
                while (ss != SquareSet.EMPTY) {
                    int to = SquareSet.first(ss);
                    ss = SquareSet.removeFirst(ss);
                    moves.add(Move.make(from, to));
                }
            }
        }


        /// Generate all pseudo-legal rook moves, and add them to the supplied
        /// list.
        private void generateRookMoves(List<Integer> moves) {
            long source = rooksOfColor(sideToMove);
            long target = ~piecesOfColor(sideToMove) & ~blockedSquares();
            long ss;

            while (source != SquareSet.EMPTY) {
                int from = SquareSet.first(source);
                source = SquareSet.removeFirst(source);
                ss = target & rookAttacks(from);
                while (ss != SquareSet.EMPTY) {
                    int to = SquareSet.first(ss);
                    ss = SquareSet.removeFirst(ss);
                    moves.add(Move.make(from, to));
                }
            }
        }


        /// Generate all pseudo-legal queen moves, and add them to the supplied
        /// list.
        private void generateQueenMoves(List<Integer> moves) {
            long source = queensOfColor(sideToMove);
            long target = ~piecesOfColor(sideToMove) & ~blockedSquares();
            long ss;

            while (source != SquareSet.EMPTY) {
                int from = SquareSet.first(source);
                source = SquareSet.removeFirst(source);
                ss = target & queenAttacks(from);
                while (ss != SquareSet.EMPTY) {
                    int to = SquareSet.first(ss);
                    ss = SquareSet.removeFirst(ss);
                    moves.add(Move.make(from, to));
                }
            }
        }


        /// Generate all pseudo-legal king moves, and add them to the supplied
        /// list.
        private void generateKingMoves(List<Integer> moves) {
            if (kingIsSpecial) {
                int us = sideToMove, them = PieceColor.opposite(us);
                int from = kingSquare(us);
                long target = ~piecesOfColor(us) & ~blockedSquares();
                long ss;

                ss = kingAttacks(from) & target;
                while (ss != SquareSet.EMPTY) {
                    int to = SquareSet.first(ss);
                    ss = SquareSet.removeFirst(ss);
                    moves.add(Move.make(from, to));
                }

                // Castling
                if (canCastleKingside(us) && isEmpty(from + 1) && isEmpty(from + 2)
                        && !isAttacked(from, them) && !isAttacked(from + 1, them) && !isAttacked(from + 2, them)) {
                    moves.add(Move.makeCastle(from, from + 2));
                }

                if (canCastleQueenside(us) && isEmpty(from - 1) && isEmpty(from - 2) && isEmpty(from - 3)
                        && !isAttacked(from, them) && !isAttacked(from - 1, them) && !isAttacked(from - 2, them)) {
                    moves.add(Move.makeCastle(from, from - 2));
                }
            } else {
                long source = kingsOfColor(sideToMove);
                long target = ~piecesOfColor(sideToMove) & ~blockedSquares();
                long ss;

                while (source != SquareSet.EMPTY) {
                    int from = SquareSet.first(source);
                    source = SquareSet.removeFirst(source);
                    ss = target & kingAttacks(from);
                    while (ss != SquareSet.EMPTY) {
                        int to = SquareSet.first(ss);
                        ss = SquareSet.removeFirst(ss);
                        moves.add(Move.make(from, to));
                    }
                }
            }
        }


        /// Generate all legal check evasions, and add them to the supplied list.
        /// This function is used to generate moves when the side to move is in
        /// check. Unlike the other move generation functions, generateEvasions
        /// is guaranteed to only return legal moves.
        private void generateEvasions(List<Integer> moves) {

            assert (kingIsSpecial);

            int us = sideToMove, them = PieceColor.opposite(us), ksq = kingSquare(us);
            long ss1, ss2;

            // Generate king evasions. It is not enough to check that the
            // destination square is not attacked by the enemy: There may be
            // an enemy "X-ray attack" through the moving king. For instance,
            // if a black rook on a1 is checking the white king on e1, white
            // can't move his king to f1, even if black doesn't directly attack
            // that square. We therefore simulate the effect of making the king
            // move by first removing the current king square from the set of
            // occupied squares, and testing whether an enemy slider would
            // attack the destination square with this new set of occupied squares.
            ss1 = kingAttacks(ksq) & ~piecesOfColor(us);
            ss2 = occupiedSquares();
            ss2 = SquareSet.remove(ss2, ksq);

            while (ss1 != SquareSet.EMPTY) {
                int to = SquareSet.first(ss1);
                ss1 = SquareSet.removeFirst(ss1);
                if ((pawnAttacks(us, to) & pawnsOfColor(them)) == SquareSet.EMPTY
                        && (knightAttacks(to) & knightsOfColor(them)) == SquareSet.EMPTY
                        && (kingAttacks(to) & kingsOfColor(them)) == SquareSet.EMPTY
                        && (SquareSet.bishopAttacks(to, ss2) & (bishopsOfColor(them) | queensOfColor(them))) == SquareSet.EMPTY
                        && (SquareSet.rookAttacks(to, ss2) & (rooksOfColor(them) | queensOfColor(them))) == SquareSet.EMPTY)
                    moves.add(Move.make(ksq, to));
            }

            // Generate non-king evasions only if not double check.
            if (SquareSet.isSingleton(checkers)) {
                long pinned = pinnedPieces(us);
                int checksq = SquareSet.first(checkers);

                // Generate moves that capture the checking piece.

                // Pawn captures:
                ss1 = pawnAttacks(them, checksq) & pawnsOfColor(us) & ~pinned;
                while (ss1 != SquareSet.EMPTY) {
                    int from = SquareSet.first(ss1);
                    ss1 = SquareSet.removeFirst(ss1);
                    if (Square.rank(checksq) == 0 || Square.rank(checksq) == rankCount - 1) {
                        for (int prom = PieceType.QUEEN; prom >= PieceType.KNIGHT; prom--) {
                            moves.add(Move.makePromotion(from, checksq, prom));
                        }
                    } else {
                        moves.add(Move.make(from, checksq));
                    }
                }

                // Knight captures
                ss1 = knightAttacks(checksq) & knightsOfColor(us) & ~pinned;
                while (ss1 != SquareSet.EMPTY) {
                    int from = SquareSet.first(ss1);
                    ss1 = SquareSet.removeFirst(ss1);
                    moves.add(Move.make(from, checksq));
                }

                // Bishop and queen captures
                ss1 = bishopAttacks(checksq) & (bishopsOfColor(us) | queensOfColor(us)) & ~pinned;
                while (ss1 != SquareSet.EMPTY) {
                    int from = SquareSet.first(ss1);
                    ss1 = SquareSet.removeFirst(ss1);
                    moves.add(Move.make(from, checksq));
                }

                // Rook and queen captures
                ss1 = rookAttacks(checksq) & (rooksOfColor(us) | queensOfColor(us)) & ~pinned;
                while (ss1 != SquareSet.EMPTY) {
                    int from = SquareSet.first(ss1);
                    ss1 = SquareSet.removeFirst(ss1);
                    moves.add(Move.make(from, checksq));
                }

                // Generate blocking evasions. Those are only possible if the checking piece
                // is a slider.
                if (Piece.isSlider(pieceOn(checksq))) {
                    long blockSquares = SquareSet.squaresBetween(checksq, ksq);

                    // Pawn moves. A blocking check evasion can never be a capture, so we only
                    // generate pawn pushes here.
                    ss1 = pawnsOfColor(us) & ~pinned;
                    if (us == PieceColor.WHITE) {
                        int push = Square.DELTA_N;

                        // Single pawn pushes
                        ss2 = SquareSet.shiftN(ss1) & blockSquares;
                        while (ss2 != SquareSet.EMPTY) {
                            int to = SquareSet.first(ss2);
                            ss2 = SquareSet.removeFirst(ss2);
                            if (Square.rank(to) == rankCount - 1) {
                                for (int prom = PieceType.QUEEN; prom >= PieceType.KNIGHT; prom--) {
                                    moves.add(Move.makePromotion(to - push, to, prom));
                                }
                            } else {
                                moves.add(Move.make(to - push, to));
                            }
                        }

                        // Double pawn pushes
                        long r3ss = SquareSet.RANK_3_SQUARES;
                        ss2 = SquareSet.shiftN(SquareSet.shiftN(ss1) & emptySquares() & r3ss) & blockSquares;
                        while (ss2 != SquareSet.EMPTY) {
                            int to = SquareSet.first(ss2);
                            ss2 = SquareSet.removeFirst(ss2);
                            moves.add(Move.make(to - 2 * push, to));
                        }
                    } else {
                        int push = Square.DELTA_S;

                        // Single pawn pushes
                        ss2 = SquareSet.shiftS(ss1) & blockSquares;
                        while (ss2 != SquareSet.EMPTY) {
                            int to = SquareSet.first(ss2);
                            ss2 = SquareSet.removeFirst(ss2);
                            if (Square.rank(to) == Square.RANK_1) {
                                for (int prom = PieceType.QUEEN; prom >= PieceType.KNIGHT; prom--) {
                                    moves.add(Move.makePromotion(to - push, to, prom));
                                }
                            } else {
                                moves.add(Move.make(to - push, to));
                            }
                        }

                        // Double pawn pushes
                        long r6ss = SquareSet.RANK_6_SQUARES;
                        for (int i = 8; i > rankCount; i--) {
                            r6ss = SquareSet.shiftS(r6ss);
                        }
                        ss2 = SquareSet.shiftS(SquareSet.shiftS(ss1) & emptySquares() & r6ss) & blockSquares;
                        while (ss2 != SquareSet.EMPTY) {
                            int to = SquareSet.first(ss2);
                            ss2 = SquareSet.removeFirst(ss2);
                            moves.add(Move.make(to - 2 * push, to));
                        }
                    }

                    // Knight moves
                    ss1 = knightsOfColor(us) & ~pinned;
                    while (ss1 != SquareSet.EMPTY) {
                        int from = SquareSet.first(ss1);
                        ss1 = SquareSet.removeFirst(ss1);
                        ss2 = knightAttacks(from) & blockSquares;
                        while (ss2 != SquareSet.EMPTY) {
                            int to = SquareSet.first(ss2);
                            ss2 = SquareSet.removeFirst(ss2);
                            moves.add(Move.make(from, to));
                        }
                    }

                    // Bishop moves
                    ss1 = bishopsOfColor(us) & ~pinned;
                    while (ss1 != SquareSet.EMPTY) {
                        int from = SquareSet.first(ss1);
                        ss1 = SquareSet.removeFirst(ss1);
                        ss2 = bishopAttacks(from) & blockSquares;
                        while (ss2 != SquareSet.EMPTY) {
                            int to = SquareSet.first(ss2);
                            ss2 = SquareSet.removeFirst(ss2);
                            moves.add(Move.make(from, to));
                        }
                    }

                    // Rook moves
                    ss1 = rooksOfColor(us) & ~pinned;
                    while (ss1 != SquareSet.EMPTY) {
                        int from = SquareSet.first(ss1);
                        ss1 = SquareSet.removeFirst(ss1);
                        ss2 = rookAttacks(from) & blockSquares;
                        while (ss2 != SquareSet.EMPTY) {
                            int to = SquareSet.first(ss2);
                            ss2 = SquareSet.removeFirst(ss2);
                            moves.add(Move.make(from, to));
                        }
                    }

                    // Queen moves
                    ss1 = queensOfColor(us) & ~pinned;
                    while (ss1 != SquareSet.EMPTY) {
                        int from = SquareSet.first(ss1);
                        ss1 = SquareSet.removeFirst(ss1);
                        ss2 = queenAttacks(from) & blockSquares;
                        while (ss2 != SquareSet.EMPTY) {
                            int to = SquareSet.first(ss2);
                            ss2 = SquareSet.removeFirst(ss2);
                            moves.add(Move.make(from, to));
                        }
                    }
                }

                // Finally, the ugly special case of en passant captures.
                if (epSquare != Square.NONE && (checkers & pawnsOfColor(them)) != SquareSet.EMPTY) {
                    int to = epSquare;
                    ss1 = pawnAttacks(them, to) & pawnsOfColor(us) & ~pinned;
                    while (ss1 != SquareSet.EMPTY) {
                        int from = SquareSet.first(ss1);
                        ss1 = SquareSet.removeFirst(ss1);
                        ss2 = occupiedSquares();
                        ss2 = SquareSet.remove(ss2, from);
                        ss2 ^= checkers;
                        if ((SquareSet.bishopAttacks(ksq, ss2) & (bishopsOfColor(them) | queensOfColor(them))) == SquareSet.EMPTY
                                && ((SquareSet.rookAttacks(ksq, ss2) & (rooksOfColor(them) | queensOfColor(them))) == SquareSet.EMPTY)) {
                            moves.add(Move.makeEP(from, to));
                        }
                    }
                }
            }
        }


        /// Test whether the Board object is internally consistent, for debugging. Doesn't yet
        /// work for boards with non-standard file or rank counts, boards with blockers, or
        /// boards where kings are not special.
        public boolean isOK() {

            // King squares and king counts OK:
            for (int color = PieceColor.MIN; color <= PieceColor.MAX; color++) {
                if (pieceOn(kingSquares[color]) != Piece.kingOfColor(color)) {
                    return false;
                }
                if (SquareSet.count(kingsOfColor(color)) != 1) {
                    return false;
                }
            }

            // No invalid pieces on the board:
            for (int square = Square.MIN; square <= Square.MAX; square++) {
                if (board[square] != Piece.EMPTY && !Piece.isOK(board[square])) {
                    return false;
                }
            }

            // Square sets OK:
            for (int square = Square.MIN; square <= Square.MAX; square++) {
                if (pieceOn(square) == Piece.EMPTY) {
                    for (int color = PieceColor.MIN; color <= PieceColor.MAX; color++)
                        if (SquareSet.contains(piecesOfColor[color], square)) {
                            return false;
                        }
                    for (int type = PieceType.MIN; type <= PieceType.MAX; type++)
                        if (SquareSet.contains(piecesOfType[type], square)) {
                            return false;
                        }
                    if (SquareSet.contains(piecesOfType[0], square)) {
                        return false;
                    }
                } else {
                    for (int color = PieceColor.MIN; color <= PieceColor.MAX; color++) {
                        if (color == Piece.color(pieceOn(square))) {
                            if (!SquareSet.contains(piecesOfColor[color], square)) {
                                return false;
                            }
                        } else {
                            if (SquareSet.contains(piecesOfColor[color], square)) {
                                return false;
                            }
                        }
                    }
                    for (int type = PieceType.MIN; type <= PieceType.MAX; type++) {
                        if (type == Piece.type(pieceOn(square))) {
                            if (!SquareSet.contains(piecesOfType[type], square)) {
                                return false;
                            }
                        } else {
                            if (SquareSet.contains(piecesOfType[type], square)) {
                                return false;
                            }
                        }
                    }
                    if (!SquareSet.contains(piecesOfType[0], square)) {
                        return false;
                    }
                }
            }

            // No pawns on 1st or 8th ranks:
            if ((pawns() & (SquareSet.RANK_1_SQUARES | SquareSet.RANK_8_SQUARES)) != SquareSet.EMPTY) {
                return false;
            }

            // Side to move:
            if (!PieceColor.isOK(sideToMove)) {
                return false;
            }

            // Side not to move not in check:
            if (isAttacked(kingSquare(PieceColor.opposite(sideToMove)), sideToMove)) {
                return false;
            }

            // Checkers OK:
            if (checkers != findCheckers()) {
                return false;
            }

            // Hash key OK:
            if (key != computeKey()) {
                return false;
            }

            return true;
        }

    }

    private final MutableBoard state;

    /// Constructor
    private Board(MutableBoard boardState) {
        assert (boardState != null);
        state = boardState;
    }

    /// Factory methods for generating new boards from a FEN:
    static public Board boardFromFen(String fen, boolean allowKingCaptures) {
        return new Board(new MutableBoard(fen, allowKingCaptures));
    }

    static public Board boardFromFen(String fen) {
        return new Board(new MutableBoard(fen, false));
    }

    /// Factory methods for generating new boards with a file and a rank
    /// count:
    static public Board boardWithSize(int fileCount, int rankCount, boolean allowKingCaptures) {
        return new Board(new MutableBoard(fileCount, rankCount, allowKingCaptures));
    }

    static public Board boardWithSize(int fileCount, int rankCount) {
        return new Board(new MutableBoard(fileCount, rankCount, false));
    }

    public Board setKingIsSpecial(boolean newValue) {
        MutableBoard newState = new MutableBoard(state);
        newState.setKingIsSpecial(newValue);
        return new Board(newState);
    }


    public boolean getKingIsSpecial() {
        return state.getKingIsSpecial();
    }


    /// Put a piece or a blocker on a square, and return the new board:
    public Board putPiece(int piece, int square) {
        MutableBoard newState = new MutableBoard(state);
        newState.putPiece(piece, square);
        newState.initialize();
        return new Board(newState);
    }

    /// Remove the piece or blocker on the given square, and return the new
    /// board:
    public Board removePiece(int square) {
        MutableBoard newState = new MutableBoard(state);
        newState.removePiece(square);
        newState.initialize();
        return new Board(newState);
    }

    /// Do a move, and return the new board:
    public Board doMove(int move) {
        MutableBoard newState = new MutableBoard(state);
        newState.doMove(move);
        newState.parent = this;
        return new Board(newState);
    }

    /// Do a null move, and return the new board:
    public Board doNullMove() {
        MutableBoard newState = new MutableBoard(state);
        newState.doNullMove();
        newState.parent = this;
        return new Board(newState);
    }

    /// File and rank counts:
    public int getFileCount() {
        return state.getFileCount();
    }

    public int getRankCount() {
        return state.getRankCount();
    }

    /// Hash key:
    public long getKey() {
        return state.getKey();
    }

    /// Parent board (can be nil):
    public Board getParent() {
        return state.getParent();
    }

    /// Last move played to reach this position:
    public int getLastMove() {
        return state.getLastMove();
    }

    /// Short algebraic notation for the last move, or null if there is no
    /// last move:
    public String lastMoveToSAN() {
        Board b = getParent();
        return b == null ? null : getParent().moveToSAN(getLastMove());
    }

    /// Grandparent board (can be nil). Used when looking back through the
    /// ancestors to check for repetition draws.
    private Board getGrandparent() {
        return getParent() == null ? null : getParent().getParent();
    }

    /// Getter method for the current side to move.
    public int getSideToMove() {
        return state.getSideToMove();
    }

    /// Number of half moves since the last non-reversible move was made
    public int getRule50Counter() {
        return state.getRule50Counter();
    }

    /// Number of half moves played in the current game before reaching
    /// this position.
    public int getGamePly() {
        return state.getGamePly();
    }

    /// The piece on the given square, or Piece.EMPTY if the square is empty.
    public int pieceOn(int square) {
        return state.pieceOn(square);
    }

    /// Test whether the given square on the board is empty.
    public boolean isEmpty(int square) {
        return state.isEmpty(square);
    }

    /// Getter method for the current en passant square. If no en passant
    /// capture is possible, Square.NONE is returned.
    public int getEpSquare() {
        return state.getEpSquare();
    }

    /// Square set representing all occupied squares on the board.
    public long occupiedSquares() {
        return state.occupiedSquares();
    }

    /// Square set repersenting all blocked or outside squares on the board.
    public long blockedSquares() {
        return state.blockedSquares();
    }

    /// Square set representing all empty squares on the board.
    public long emptySquares() {
        return state.emptySquares();
    }

    /// The set of squares occupied by pieces of the given color.
    public long piecesOfColor(int color) {
        return state.piecesOfColor(color);
    }

    /// The set of squares occupied by pieces of the given piece type.
    public long piecesOfType(int type) {
        return state.piecesOfType(type);
    }

    /// The set of squares occupied by pieces of the given color and
    /// piece type.
    public long piecesOfColorAndType(int color, int type) {
        return state.piecesOfColorAndType(color, type);
    }

    /// Number of pieces
    public int pieceCount(int piece) {
        return SquareSet.count(piecesOfColorAndType(Piece.color(piece), Piece.type(piece)));
    }

    public int pieceCount(int color, int type) {
        return SquareSet.count(piecesOfColorAndType(color, type));
    }

    /// The set of squares containing pawns of either color.
    public long pawns() {
        return state.pawns();
    }

    /// The set of squares containing knights of either color.
    public long knights() {
        return state.knights();
    }

    /// The set of squares containing bishops of either color.
    public long bishops() {
        return state.bishops();
    }

    /// The set of squares containing rooks of either color.
    public long rooks() {
        return state.rooks();
    }

    /// The set of squares containing queens of either color.
    public long queens() {
        return state.queens();
    }

    /// The set of squares containing kings of either color.
    public long kings() {
        return state.kings();
    }

    /// The set of squares containing pawns of the given color.
    public long pawnsOfColor(int color) {
        return state.pawnsOfColor(color);
    }

    /// The set of squares containing knights of the given color.
    public long knightsOfColor(int color) {
        return state.knightsOfColor(color);
    }

    /// The set of squares containing bishops of the given color.
    public long bishopsOfColor(int color) {
        return state.bishopsOfColor(color);
    }

    /// The set of squares containing rooks of the given color.
    public long rooksOfColor(int color) {
        return state.rooksOfColor(color);
    }

    /// The set of squares containing queens of the given color.
    public long queensOfColor(int color) {
        return state.queensOfColor(color);
    }

    /// The set of squares containing kings of the given color. This
    /// set should always be a singleton.
    public long kingsOfColor(int color) {
        return state.kingsOfColor(color);
    }

    /// The square of the king for the given color.
    public int kingSquare(int color) {
        return state.kingSquare(color);
    }

    /// The set of squares that would be attacked by a pawn of the given color
    /// on the given square.
    public long pawnAttacks(int color, int square) {
        return state.pawnAttacks(color, square);
    }

    /// The set of squares that would be attacked by a knight of the given
    /// color on the given square.
    public long knightAttacks(int square) {
        return state.knightAttacks(square);
    }

    /// The set of squares that would be attacked by a bishop of the given
    /// color on the given square.
    public long bishopAttacks(int square) {
        return state.bishopAttacks(square);
    }

    /// The set of squares that would be attacked by a rook of the given color
    /// on the given square.
    public long rookAttacks(int square) {
        return state.rookAttacks(square);
    }

    /// The set of squares that would be attacked by a queen of the given color
    /// on the given square.
    public long queenAttacks(int square) {
        return state.queenAttacks(square);
    }

    /// The set of squares that would be attacked by a king of the given color
    /// on the given square.
    public long kingAttacks(int square) {
        return state.kingAttacks(square);
    }

    /// Tests whether the given square is attacked by the given color.
    public boolean isAttacked(int square, int color) {
        return state.isAttacked(square, color);
    }

    /// The set of squares containing pieces of the given color that attacks
    /// the given square.
    public long attacksTo(int square, int color) {
        return state.attacksTo(square, color);
    }

    /// The set of squares attacked by the piece on the given square.
    public long attacksFrom(int square) {
        int pieceType = Piece.type(pieceOn(square));
        if (pieceType == PieceType.PAWN) {
            return pawnAttacks(Piece.color(pieceOn(square)), square);
        } else if (pieceType == PieceType.KNIGHT) {
            return knightAttacks(square);
        } else if (pieceType == PieceType.BISHOP) {
            return bishopAttacks(square);
        } else if (pieceType == PieceType.ROOK) {
            return rookAttacks(square);
        } else if (pieceType == PieceType.QUEEN) {
            return queenAttacks(square);
        } else if (pieceType == PieceType.KING) {
            return kingAttacks(square);
        } else {
            return 0;
        }
    }

    /// The set of squares containing pinned pieces of the given color.
    public long pinnedPieces(int color) {
        return state.pinnedPieces(color);
    }

    /// Test whether the given color still has the right to castle kingside.
    public boolean canCastleKingside(int color) {
        return state.canCastleKingside(color);
    }


    /// Test whether the given color still has the right to castle queenside.
    public boolean canCastleQueenside(int color) {
        return state.canCastleQueenside(color);
    }


    /// Test whether the side to move is in check
    public boolean isCheck() {
        return state.isCheck();
    }


    /// Return a list of all squares containing checking pieces.
    public List<Integer> checkingPieces() {
        return state.checkingPieces();
    }


    /// Generate a list of all legal moves.
    public List<Integer> moves() {
        return state.legalMoves();
    }


    /// Generate a list of all legal moves from the given square.
    public List<Integer> movesFrom(int square) {
        return state.movesFrom(square);
    }


    /// Generate a list of all legal moves to the given square.
    public List<Integer> movesTo(int square) {
        return state.movesTo(square);
    }


    /// Generate a list of all legal moves of the given piece type to the
    /// square. Useful when parsing moves in short algebraic notation.
    public List<Integer> movesForPieceTypeToSquare(int pieceType, int square) {
        return state.movesForPieceTypeToSquare(pieceType, square);
    }


    /// Test whether the current position is checkmate.
    public boolean isMate() {
        return state.isMate();
    }


    /// Test whether the current position is drawn by stalemate.
    public boolean isStalemate() {
        return state.isStalemate();
    }


    /// Test whether the current position is drawn by lack of mating
    /// material.
    public boolean isMaterialDraw() {
        return state.isMaterialDraw();
    }


    /// Test whether the current position is drawn by the 50 moves rule.
    public boolean isRule50draw() {
        return state.isRule50draw();
    }


    /// Test whether the current position is drawn by triple repetition.
    /*
    public boolean isRepetitionDraw() {
        int repetitionCount = 1;
        for (int i = 2; i < Math.min(gamePly, rule50Counter); i += 2) {
            if (historicKeys[gamePly - i] == key) {
                repetitionCount++;
            }
        }
        return repetitionCount >= 3;
    }
    */


    /// Test whether the current position is an immediate draw.
    public boolean isDraw() {
        return state.isDraw();
    }


    /// Test whether the current position is terminal, i.e. whether the game is
    /// over.
    public boolean isTerminal() {
        return state.isTerminal();
    }


    /// Export the position to a string in Forsyth-Edwards notation.
    public String toFEN() {
        return state.toFEN();
    }


    /// Print the board in ASCII format to the standard output, for debugging.
    public void print() {
        state.print();
    }


    /// Test whether the Board object is internally consistent, for debugging.
    public boolean isOK() {
        return state.isOK();
    }

    /// Convert a move to a string in short algebraic notation.
    public String moveToSAN(int move) {
        int from = Move.from(move), to = Move.to(move);
        int promotion = Move.promotion(move);
        int piece = pieceOn(from);
        boolean isShortCastle = Move.isCastle(move) && Square.file(to) == Square.FILE_G;
        boolean isLongCastle = Move.isCastle(move) && Square.file(to) == Square.FILE_C;
        Board newBoard = this.doMove(move);
        boolean isCheck = newBoard.isCheck();
        boolean isMate = isCheck && newBoard.isMate();
        StringBuilder s = new StringBuilder(10);

        if (isLongCastle) {
            s.append("O-O-O");
        } else if (isShortCastle) {
            s.append("O-O");
        } else {
            if (Piece.type(piece) == PieceType.PAWN) {
                if (Square.file(from) != Square.file(to)) {
                    // Capture
                    s.append(Square.fileToChar(Square.file(from)));
                    s.append('x');
                }
                s.append(Square.toString(to));
                if (Move.isPromotion(move)) {
                    s.append('=');
                    s.append(Character.toUpperCase(PieceType.toChar(promotion)));
                }
            } else {
                s.append(Character.toUpperCase(Piece.toChar(piece)));
                List<Integer> moves = this.movesForPieceTypeToSquare(Piece.type(piece), to);
                if (moves.size() > 1) {
                    // Several moves, need disambiguation character(s).
                    int file = Square.file(from), rank = Square.rank(from);
                    int sameFileCount = 0, sameRankCount = 0;
                    for (int m : moves) {
                        if (Square.file(Move.from(m)) == Square.file(from)) {
                            sameFileCount++;
                        }
                        if (Square.rank(Move.from(m)) == Square.rank(from)) {
                            sameRankCount++;
                        }
                    }
                    if (sameFileCount == 1) {
                        s.append(Square.fileToChar(Square.file(from)));
                    } else if (sameRankCount == 1) {
                        s.append(Square.rankToChar(Square.rank(from)));
                    } else {
                        s.append(Square.toString(from));
                    }
                }
                if (this.pieceOn(to) != Piece.EMPTY) {
                    s.append('x');
                }
                s.append(Square.toString(to));
            }
        }
        if (isMate) {
            s.append('#');
        } else if (isCheck) {
            s.append('+');
        }
        return s.toString();
    }


    /// Converts a string in short algebraic notation to a move. If no matching move is
    /// found, or if the move is ambiguous, returns Move.NONE.
    public int moveFromSAN(String str) {
        List<Integer> moves = this.moves();

        // ChessBase uses '--' to encode null moves
        if (str.length() == 2 && str.substring(0, 2).equals("--")) {
            return Move.NONE;
        }

        // Castling moves
        if (str.length() >= 5 && str.substring(0, 5).equals("O-O-O")) {
            for (int move : moves) {
                if (Move.isQueensideCastle(move)) {
                    return move;
                }
            }
            return Move.NONE;
        } else if (str.length() >= 3 && str.substring(0, 3).equals("O-O")) {
            for (int move : moves) {
                if (Move.isKingsideCastle(move)) {
                    return move;
                }
            }
            return Move.NONE;
        }

        // Normal moves

        // Remove undesired characters.
        String s = str.replace("x", "").replace("+", "").replace("#", "").replace("=", "").replace("-", "");

        int left = 0, right = s.length() - 1;
        int pt = PieceType.NONE, promotion = PieceType.NONE;
        int fromFile = Square.FILE_NONE, fromRank = Square.RANK_NONE;
        int from, to;

        // Promotion?
        if ("NBRQ".contains(s.substring(right).toUpperCase())) {
            promotion = "NBRQ".indexOf(s.substring(right)) + 2;
            right--;
        }

        // Find the moving piece
        if (left < right) {
            if ("NBRQK".contains(s.substring(left, left + 1))) {
                pt = "NBRQK".indexOf(s.substring(left, left + 1)) + 2;
                left++;
            } else {
                pt = PieceType.PAWN;
            }
        }

        // Find the destination square:
        if (left < right) {
            char c0 = s.charAt(right - 1), c1 = s.charAt(right);
            if (c0 < 'a' || c0 > 'h' || c1 < '1' || c1 > '8') {
                return Move.NONE;
            }
            to = Square.fromString(s.substring(right - 1, right + 1));
            right -= 2;
        } else {
            return Move.NONE;
        }

        // Find the file and/or rank of the from square:
        if (left <= right) {
            if ("abcdefgh".contains(s.substring(left, left + 1))) {
                fromFile = Square.fileFromChar(s.charAt(left));
                left++;
            }
            if ("12345678".contains(s.substring(left, left + 1))) {
                fromRank = Square.rankFromChar(s.charAt(left));
            }
        }

        // Look for a matching move:
        int move = Move.NONE;
        int matches = 0;
        for (int m : moves) {
            boolean match = true;
            if (Piece.type(pieceOn(Move.from(m))) != pt) {
                match = false;
            } else if (Move.to(m) != to) {
                match = false;
            } else if (Move.promotion(m) != promotion) {
                match = false;
            } else if (fromFile != Square.FILE_NONE && fromFile != Square.file(Move.from(m))) {
                match = false;
            } else if (fromRank != Square.RANK_NONE && fromRank != Square.rank(Move.from(m))) {
                match = false;
            }
            if (match) {
                move = m;
                matches++;
            }
        }

        return matches == 1 ? move : Move.NONE;
    }

    static final int[] materialValues = {0, 1, 3, 3, 5, 9, 100, 0, 0, 1, 3, 3, 5, 9, 100, 0, 0, 0};


    // see() is a static exchange evaluator: A function that given a from and
    // to square tries to estimate the material win or loss obtained by moving
    // the piece on 'from' to 'to'. The return value is an integer representing
    // the number of pawns worth of material win or loss.
    public int see(int from, int to) {
        int us = getSideToMove(), them = PieceColor.opposite(us);
        int piece = pieceOn(from), capture = pieceOn(to);

        // Find all attackers to the destination square, with the moving piece
        // removed, but with possibly an X-ray attacker added behind it:
        long occ = SquareSet.remove(occupiedSquares(), from);
        long attackers =
                (SquareSet.rookAttacks(to, occ) & (rooks() | queens())) |
                        (SquareSet.bishopAttacks(to, occ) & (bishops() | queens())) |
                        (knightAttacks(to) & knights()) |
                        (pawnAttacks(us, to) & pawnsOfColor(them)) |
                        (pawnAttacks(them, to) & pawnsOfColor(us)) |
                        (kingAttacks(to) & kings());
        attackers &= occ;

        // If the opponent has no attackers, we are finished:
        if ((attackers & piecesOfColor(them)) == SquareSet.EMPTY) {
            return materialValues[capture];
        }

        // The destination square is defended. We proceed by building up a
        // "swap list" containing the material gain or loss at each stop in a
        // sequence of captures to the destination square, where the sides
        // alternatingly capture, and always capture with the least valuable
        // piece. After each capture, we look for new X-ray attacks from
        // behind the capturing piece.
        int lastCapturingPieceValue = materialValues[piece];
        int[] swapList = new int[32];
        int n = 1;
        int c = them;
        int pt;

        swapList[0] = materialValues[capture];

        do {
            // Locate the least valuable attacker for the side to move
            for (pt = PieceType.PAWN;
                 (attackers & piecesOfColorAndType(c, pt)) == SquareSet.EMPTY;
                 pt++) {
            }

            // Remove the attacker we just found from the "attackers" square set,
            // and scan for new X-ray attacks behind the attacker:
            long b = attackers & piecesOfColorAndType(c, pt);
            occ ^= (b & -b);
            attackers |=
                    (SquareSet.rookAttacks(to, occ) & (rooks() | queens())) |
                            (SquareSet.bishopAttacks(to, occ) & (bishops() | queens()));
            attackers &= occ;

            // Add the new entry to the swap list:
            swapList[n] = -swapList[n - 1] + lastCapturingPieceValue;
            n++;

            // Remember the value of the capturing piece, and change the side
            // to move before beginning the next iteration:
            lastCapturingPieceValue = materialValues[pt];
            c = PieceColor.opposite(c);

            // Stop after a king capture:
            if (pt == PieceType.KING && (attackers & piecesOfColor(c)) != SquareSet.EMPTY) {
                swapList[n++] = 100;
                break;
            }
        } while ((attackers & piecesOfColor(c)) != SquareSet.EMPTY);

        // Having built the swap list, we negamax through it to find the best
        // achievable score from the point of view of the side to move:
        while (--n != 0) {
            swapList[n - 1] = Math.min(-swapList[n], swapList[n - 1]);
        }

        return swapList[0];
    }


    // see() is a static exchange evaluator: A function that given a move tries
    // to estimate the material win or loss obtained by doing the move. The
    // return value is an integer representing the number of pawns worth of
    // material win or loss.
    public int see(int move) {
        return see(Move.from(move), Move.to(move));
    }


    // toUCI() translates the board state (including as much of the move history
    // as necessary) to a format ready to be sent to a UCI chess engine, i.e. like
    // 'position fen' followed by a sequence of moves.
    public String toUCI() {
        StringBuilder result = new StringBuilder(500);
        List<Integer> moveList = new ArrayList<Integer>();

        result.append("position fen ");

        Board b = this;
        for (int i = 0; i < Math.min(getGamePly(), getRule50Counter()); i++) {
            moveList.add(b.getLastMove());
            b = b.getParent();
        }
        result.append(b.toFEN());

        if (moveList.size() > 0) {
            result.append(" moves");
            ListIterator<Integer> it = moveList.listIterator(moveList.size());
            while (it.hasPrevious()) {
                result.append(" ");
                result.append(Move.toUCI(it.previous()));
            }
        }
        return result.toString();
    }


    // flip() returns a flipped copy of the board: The white and black sides, the
    // side to move, the castle rights and the en passant squares are all switched.
    public Board flip() {
        MutableBoard st = new MutableBoard();

        for (int s = Square.A1; s <= Square.H8; s++) {
            int p = pieceOn(s);
            if (p != Piece.EMPTY) {
                int s2 = s ^ 070;
                st.putPiece(Piece.make(PieceColor.opposite(Piece.color(p)), Piece.type(p)), s2);
            }
        }

        st.sideToMove = PieceColor.opposite(getSideToMove());

        int cr = 0;
        if (canCastleKingside(PieceColor.BLACK)) {
            cr |= 1;
        }
        if (canCastleKingside(PieceColor.WHITE)) {
            cr |= 2;
        }
        if (canCastleKingside(PieceColor.BLACK)) {
            cr |= 4;
        }
        if (canCastleKingside(PieceColor.WHITE)) {
            cr |= 8;
        }
        st.setCastleRights(cr);

        if (getEpSquare() != Square.NONE) {
            st.setEpSquare(getEpSquare() ^ 070);
        }

        st.setRule50Counter(getRule50Counter());
        st.setGamePly(getGamePly());

        st.initialize();

        return new Board(st);
    }


    public long patternKey(int fileMin, int fileMax, int rankMin, int rankMax,
                           int color) {
        long result = 0;
        long occ = occupiedSquares() & ~blockedSquares();

        while (occ != SquareSet.EMPTY) {
            int square = SquareSet.first(occ);
            int file = Square.file(square), rank = Square.rank(square);
            occ = SquareSet.removeFirst(occ);
            if (file >= fileMin && file <= fileMax
                    && rank >= rankMin && rank <= rankMax) {
                if (color == PieceColor.NONE || color == Piece.color(pieceOn(square))) {
                    result ^= zobrist[pieceOn(square) * 64 + square];
                }
            }
        }
        return result;
    }


    public long patternKey(int fileMin, int fileMax, int rankMin, int rankMax) {
        return patternKey(fileMin, fileMax, rankMin, rankMax, PieceColor.NONE);
    }

    static long[] zobrist = new long[2 * 8 * 64];
    static long[] zobEP = new long[64];
    static long[] zobCastle = new long[16];
    static long zobSideToMove;

    private final static int CASTLE_RIGHTS_WHITE_OO = 1, CASTLE_RIGHTS_BLACK_OO = 2;
    private final static int CASTLE_RIGHTS_WHITE_OOO = 4, CASTLE_RIGHTS_BLACK_OOO = 8;
    private final static int CASTLE_RIGHTS_NONE = 0;

    private static final long[] ZOBRIST = {
            3252192330058186162L,
            -9034248777455946116L,
            6916454210424914578L,
            -6510828362487830702L,
            6706573003305027624L,
            -2444478562357441229L,
            789927139174287947L,
            -2707799958812417836L,
            -3066701483822123834L,
            3711537688335189811L,
            2297314625937158060L,
            955484697480965328L,
            -3979159656963423329L,
            -2328126241540161170L,
            -6611685519947586635L,
            1229227506343542103L,
            6841009544082308348L,
            -5246491977682275075L,
            -82165049231052706L,
            -5233793744653463262L,
            3605106625030128295L,
            -4974023596716194640L,
            -8193122049415387334L,
            4133554543176567513L,
            2611056858306744889L,
            -1043333611448612357L,
            -1330159329698013389L,
            -6379559919648461357L,
            8971043418004073634L,
            655401472447530398L,
            1982859612041475647L,
            -3965900286061109530L,
            -8071690721635881954L,
            -6587862605753500278L,
            4877465231039349702L,
            -2773710780068120327L,
            -3230623639667303820L,
            9006695321599673469L,
            4210256238308219882L,
            131754386806139657L,
            3432979079144655521L,
            -5608751175761602561L,
            -3991238442637673580L,
            8694172487471061517L,
            -2341536619974303673L,
            3677267527686240499L,
            -2523891629676287488L,
            -4889798723956301756L,
            -323659948779293351L,
            -5320343170485699684L,
            2204140368572293020L,
            244639717637965481L,
            5841644567553824089L,
            6618459341278247625L,
            -5214633465933742181L,
            2276303820230219687L,
            -4215161371571514197L,
            -7913181944320004330L,
            -8286745260658169113L,
            3818158163926264746L,
            1394151040867610472L,
            -3676471411440241527L,
            5456110512393257675L,
            6772796365049477895L,
            -2837774851121110035L,
            1325623203823551646L,
            -7087858862252627600L,
            -566612675618094844L,
            8412976592762110036L,
            5630886482204262588L,
            -2597762313134919003L,
            -8591364066068702162L,
            -7664185045984413881L,
            6113989138161991950L,
            -7258518776445197034L,
            -1664023489971582130L,
            3518154545121705819L,
            544623070005733819L,
            3271769234195262925L,
            1783783822465309509L,
            694610600797948253L,
            -7760544873363743812L,
            4026619333785688562L,
            1058799883875076682L,
            -7488554510541247473L,
            -3127000987815763469L,
            -5015765559932034533L,
            -3968262337655296914L,
            1914262037860706309L,
            -8861133745988950130L,
            -1438729572853442096L,
            -8267286253786607078L,
            -3969382463502970232L,
            6288522399547158577L,
            -4455000236368618860L,
            6373491138610930517L,
            146078604315498159L,
            6453754954096771050L,
            -9153679500059027440L,
            -3396627056321768824L,
            -7358635236512308027L,
            -8833365806357456234L,
            5962644003129725684L,
            -4897501241338868452L,
            -2638303200512864968L,
            3456629389907970719L,
            -7472517653171322739L,
            5695851907477385571L,
            7635297129754683501L,
            -4190493474613966657L,
            4339484208520963540L,
            -389802629776054238L,
            -5710220560133784364L,
            -725206767609583311L,
            -7265241781987344168L,
            7943925360965047929L,
            400774320787404609L,
            9122069403519150731L,
            -82770721425762995L,
            6898036734842168605L,
            -1086312136503294683L,
            -4292520698919499442L,
            -379835973538882134L,
            329520282175850225L,
            4749670229436756604L,
            7238117245992620579L,
            3710796019324131538L,
            2976445123563052880L,
            315506722911888832L,
            -2066857814980340657L,
            -1674818877247740059L,
            -2183139257396730012L,
            2350441235233726078L,
            -6762748681690510927L,
            7666658483110858850L,
            -2928361240488982558L,
            -1982897452463515539L,
            3865754978855109768L,
            1937279430421269736L,
            7695653064548301357L,
            2142856550387275413L,
            610901853934506024L,
            -436236697938097250L,
            8072379414394476389L,
            -1709348342290604349L,
            611468765304646789L,
            4740912284580156257L,
            8857225115372453867L,
            2983285554172001155L,
            -2509398676720652707L,
            -8716288337779954091L,
            2771558445358822628L,
            -3406752159045152614L,
            -6534978485611325173L,
            -2322792753980625476L,
            -7841307880184588646L,
            3428956465038353180L,
            -5506453407365494858L,
            -1153896813498784413L,
            1763811944938582833L,
            4719559781949511166L,
            -1807329164768673714L,
            -8360165081456267813L,
            -2263175198988992801L,
            4089563646671914336L,
            2084316796171302254L,
            -2777218660098429950L,
            1980305341379660313L,
            -2752291093198045052L,
            3988430114941529842L,
            -7276472233062394206L,
            -5569565171726037508L,
            -4155911425474276602L,
            -8529640179254434802L,
            -8690383490768387686L,
            6098532204889681921L,
            4364997769319165116L,
            -3595285931290556942L,
            -6034243405538108852L,
            6580565237836971388L,
            -8791567586360722814L,
            -4608701026441544583L,
            3627469133560934173L,
            2653504252662341520L,
            -2150555394646658485L,
            -8781579461127441608L,
            8559369259980166559L,
            -6998902488458226441L,
            -6587135992210327728L,
            -7348238893110288193L,
            1790987305725526786L,
            -5104276647925221041L,
            2337565106372091737L,
            -4460364914272049262L,
            1808856590751723746L,
            -6984431019433844919L,
            5453395751276496214L,
            -6980032194530335135L,
            8233489930335089007L,
            -8688864348753784963L,
            3295139668089645317L,
            -4951566886642789701L,
            -1536504083940100332L,
            1003680568490618796L,
            -7424351531518970549L,
            -3127458889170791093L,
            4688846735838064329L,
            5842176101819368174L,
            7839816647226703655L,
            -4058484884916884019L,
            -2803234302355487756L,
            -5615963268941906934L,
            4035294661405111444L,
            -9002446416813021273L,
            5343174208448327614L,
            7810622483069379383L,
            -6551358349771206391L,
            5456009187444409652L,
            4343769899211208995L,
            -6648147234033005207L,
            -4987040300360192115L,
            -483607010813963019L,
            7410842395697427099L,
            -4963157224775654030L,
            4830630007472440291L,
            7642695120476836739L,
            7421841816403691527L,
            -6699536909161362437L,
            -7821963570521955174L,
            -3261699222337383677L,
            559153623533814018L,
            2392666186959835551L,
            -4001958067261993483L,
            -9115885145002187710L,
            -127899497194906225L,
            2535130483657219766L,
            -8592637006748159767L,
            -2685754266353219777L,
            6534244362207158234L,
            -7829711460224042719L,
            -7826043738997274889L,
            4585504249793697980L,
            4094886860670467493L,
            -3054916591385106295L,
            8051406556802857261L,
            4754425687647014681L,
            -7510106399400525643L,
            -6815915673545402481L,
            6487032787412754735L,
            -2913284591154789810L,
            -6771775432314628890L,
            -731060230562125342L,
            -8188753677396250263L,
            -6583677431557852912L,
            7181111674900939662L,
            7648346906093623954L,
            6793073042583608641L,
            -3252359735914695423L,
            -1731094812074584512L,
            -2001636041823915702L,
            -6290743498204563797L,
            7840292909343712160L,
            -7756906406892236628L,
            4212294287416170062L,
            85621858176537057L,
            -2037574730396864264L,
            3045446887881304102L,
            8246715330264727122L,
            3344929108047198010L,
            1602773796371347114L,
            -7011713539281362008L,
            3907607933174367427L,
            -2484007923896939035L,
            2283817517404351639L,
            2052165016381660256L,
            -4332918501346687199L,
            -3932142972263419310L,
            -1984350435243722029L,
            485547155897695197L,
            -8501636444380606863L,
            1858411290557041415L,
            8319766385607022313L,
            3781107868205369761L,
            -206947935967558477L,
            3150338067396584325L,
            4922810655252807128L,
            -1734789899153961416L,
            2098327555232881245L,
            -6472881307119086979L,
            4425173811110705104L,
            -5700068824460248547L,
            6359935563589896322L,
            7986430208344184041L,
            7662145071718117727L,
            -165796001298011689L,
            -7643856621858604596L,
            -2341512025632374242L,
            282588839648040862L,
            -6233273981103530272L,
            -7156593229328788694L,
            -9044523841608058880L,
            -5891928617673161017L,
            5790035218881325843L,
            6207002420652336593L,
            3841685043645900746L,
            4296543666616067020L,
            8667504471805724909L,
            -1445176941853811628L,
            -7859074748249353010L,
            -7511257479181037003L,
            -1685184198277396344L,
            -7878877924358963422L,
            8917954419073497871L,
            -244466246080620224L,
            -7798031873229271584L,
            8065723576868426770L,
            -1242714743193833356L,
            -8568320009882445968L,
            -3920351108082150796L,
            2836462751619927436L,
            -1830058321618513855L,
            -8235905558748645397L,
            7206655484768886442L,
            -2652189969299495855L,
            -79182257610305996L,
            4444151436979811755L,
            -6387364188139921013L,
            1968956797054691909L,
            4442722036538673401L,
            7217760068879762755L,
            -6322253894191356863L,
            -1987059481400453714L,
            -4338985170438429315L,
            3746361066662234434L,
            5997437696507935987L,
            -6706066851288879349L,
            -7686023667628776636L,
            -8651574164410625608L,
            -5230723238808183218L,
            -6790591612381520088L,
            5367092403065299243L,
            2695213461235447113L,
            8839446928915969715L,
            -2305308690427581649L,
            9106454406653140568L,
            -3633244764335163113L,
            -5251339920888597009L,
            -8112234877383835866L,
            3506660371741214157L,
            5568635906726956136L,
            6191297004545966581L,
            7805217000558432307L,
            7191791810834644434L,
            3578777057150418526L,
            -5246755174877434324L,
            -8256471159440202029L,
            5143897816638063834L,
            8124700263156861250L,
            8652269455660831459L,
            3011579340994441921L,
            -65417202551214125L,
            -4425197788901332387L,
            3530007382304219841L,
            80731847952383501L,
            3168641261883532588L,
            -4994955579578462183L,
            -7226422866257300983L,
            -3770780678938839911L,
            -2675916166827729844L,
            7826988691333132627L,
            -9213467597083557034L,
            7657866593365990229L,
            -7962295874850460850L,
            1616031425994892904L,
            8110423805343559731L,
            -1513867046475914416L,
            892649239093300519L,
            188842170145995894L,
            -8273118591597884166L,
            3365451290807029664L,
            -7710712706210487791L,
            3377846657945342093L,
            1945522851293301957L,
            3116892706329963949L,
            8118294489573654107L,
            5596571998756523562L,
            5462352728446677488L,
            5442720042133593732L,
            715051055224678867L,
            -6190329383844420200L,
            4778387255123695052L,
            3571432693238523026L,
            2946455149567819581L,
            8687970849004249558L,
            -2593017595635346629L,
            2706921891841520467L,
            8453884880017654540L,
            -6585512672668093042L,
            -5674216375946052666L,
            9019185360443139547L,
            -6184310481946595068L,
            -680455931877385064L,
            -558524343711605805L,
            9167142523219095314L,
            4426842701359686743L,
            -184654567145669L,
            7865085470733546265L,
            -6619104776103055538L,
            8546774045634989631L,
            -878153574205745318L,
            -8355648893086403086L,
            611982034819158637L,
            -8506231099694934932L,
            4368167081588368477L,
            -4327065509863808666L,
            -3741549675284809760L,
            7747695213257260356L,
            -6466361336575766533L,
            -1859150868975822380L,
            -5240843774331645180L,
            -1118111293361125468L,
            -2948712862138696619L,
            -6878237772318399226L,
            -3241956233454135868L,
            -7166777121980560226L,
            7853721295341083422L,
            -1317340090278243866L,
            -7595689804086182954L,
            -756919264061539247L,
            -71455765595195291L,
            1380611012022096346L,
            6449572952913120440L,
            7698802825845272955L,
            -8845702519146536857L,
            4083714714327244468L,
            -1928911484610451065L,
            8202363280404092350L,
            -5733428328418515790L,
            262247059802779922L,
            231681152749517245L,
            -4910214025891400502L,
            -3751957689593932209L,
            -6231200562869785907L,
            9132676662958190249L,
            -5159162527633831814L,
            -8089578770712167343L,
            3601773898293748994L,
            -1474680553577707407L,
            -7925963982372002822L,
            372799597480388283L,
            -4317840456884161084L,
            -4355929153572934204L,
            8439404646356449851L,
            -1298968951169862480L,
            -4649311224180102489L,
            -2983188274843217677L,
            -8368479491129065746L,
            8918070931501260791L,
            6066815604132603674L,
            -7765856227928094819L,
            -4585664969739121377L,
            4034620884698773274L,
            -7378881676330945212L,
            3342494526178503577L,
            -1011508419718767571L,
            349156193242140089L,
            -2781377464920517787L,
            -8041766441765444959L,
            5132449936153282401L,
            -6512604974073938176L,
            330957325232386136L,
            4297692841997930732L,
            1221556478102391967L,
            485443890950783057L,
            -7298778558455415372L,
            1219444115809753525L,
            656363518839598301L,
            4986687526913911106L,
            -928946504910812501L,
            -7370034544906212936L,
            5089268232381618784L,
            1065757543533036640L,
            -4941575177199488730L,
            6558147971543925127L,
            6941566261347718565L,
            1928997946998800372L,
            4491703794766443885L,
            -301508187654320437L,
            -3021226267053467908L,
            8492138663133745326L,
            -8269351355872928288L,
            1915513688061430946L,
            4221224843511499753L,
            2984739752170898765L,
            -7625306046016260385L,
            6338721825916344238L,
            2538902613284663905L,
            8288200053612031604L,
            1511759810873255956L,
            3404723566686916167L,
            -3605885191215143963L,
            -3184141919496552884L,
            8401630361398157172L,
            -6060435070468365439L,
            -3076011996482233432L,
            -6538305632890864820L,
            5904516531910483224L,
            -7602679083426904787L,
            -2438621698887323762L,
            1451930224231455125L,
            2754333039825204441L,
            -4963050712273943468L,
            2187679055390531733L,
            -8347956098346891330L,
            4979844960687242085L,
            6619062494177547283L,
            2902886735581972202L,
            2699359624356725890L,
            5872601447013654993L,
            5262895687126200895L,
            -6889353627741172870L,
            -1673912230203351700L,
            1727282945158512019L,
            1969797704654387937L,
            -9026610819201911899L,
            -5056215837316901826L,
            3128707908329799688L,
            -6088651757771689576L,
            -5602730164254018152L,
            2848171269454459058L,
            588862899637183498L,
            -283427458047590344L,
            -4226482602060537908L,
            8911198347279337310L,
            1606862927032461179L,
            -6907279678451865162L,
            2398748417776843227L,
            -6687237745520104256L,
            -4790001646232490691L,
            330264527153572509L,
            -2394073870217425505L,
            -4754708258764387469L,
            1847636911975405906L,
            7155352048605433896L,
            -7147724359698836600L,
            -4420744190134917287L,
            -8913963586169307966L,
            5336663946119530632L,
            -2205811742032687777L,
            218430195148666815L,
            -5326209459855960928L,
            3131240757476733847L,
            -8320435178351401403L,
            -4515556146827646780L,
            7513466737201434038L,
            4350228402429709222L,
            -8447489471140472190L,
            -4531151208884321794L,
            -8708905866884219162L,
            -7156819500596769657L,
            4260222896548328749L,
            -5200863350504395742L,
            -2886768388684919180L,
            -9193025413141782264L,
            2186258009852502726L,
            -8813517828337095456L,
            -4139374543423342697L,
            -1592248008755343146L,
            -794306345210558882L,
            -5845736125968491230L,
            -2321778142887135250L,
            -7695985133806666330L,
            -2396300844094994268L,
            -1301155171879169652L,
            7724223021299490292L,
            3712313437437506721L,
            -6404791766142385020L,
            4107262971283502904L,
            9035979976208656239L,
            -5034673596055954479L,
            -8515468970530713006L,
            555924267959131029L,
            -4139560608395528093L,
            7353306131377235488L,
            705519195904062899L,
            -1104676417125763545L,
            -6359298113897739553L,
            -8950587677268131827L,
            4612370652340720832L,
            -2203753554472661362L,
            379423213269844102L,
            -94039175950848917L,
            505071283048471535L,
            -7452244930485245427L,
            7087706157959844520L,
            -3729402580740837485L,
            -4385046213391621182L,
            6901491378341951379L,
            4893575116741791831L,
            -5209724625426717400L,
            1642530440947489693L,
            2482548708982984943L,
            -6469921283066180926L,
            -1933413876683861793L,
            -3768444261626193879L,
            4270008153095336158L,
            -2575461525061117088L,
            -6856981345253445842L,
            -7254221765490933971L,
            7063109810695802268L,
            4730468424017764548L,
            -7276025462701804617L,
            2143504424263217552L,
            -9121251659217047815L,
            8746582728072975968L,
            -7454920119973074251L,
            -3384937146274629719L,
            -4008586010973814836L,
            849310961142403229L,
            -6874198374521564159L,
            3418611877785415227L,
            -8107089829188922772L,
            2615933750115171121L,
            -641975348484969186L,
            1684172170989254651L,
            1442756128523047635L,
            -6190650941861490745L,
            7765816733551996072L,
            4639224320180742489L,
            9063942284774547007L,
            -2148428664384098804L,
            1526643304809735127L,
            -2234348165487405513L,
            8680848830156185446L,
            -1777184255915487011L,
            -4697708120973132357L,
            -1746713223682046130L,
            -7793049937908259119L,
            2348916016139269331L,
            -2132299200707188227L,
            4106628939525024597L,
            5327826245832879533L,
            -3123420785788039240L,
            1966799786892198585L,
            -1614772793393483358L,
            -2565926473347053009L,
            -4843674097490828474L,
            7807495994594450475L,
            -776823931011335565L,
            3227265207364011055L,
            3951760153178854729L,
            -6124445564026066004L,
            8558628624245849432L,
            -3584559298980973859L,
            1897651526113818279L,
            -4257636177681967875L,
            1260854264523285115L,
            -3756329138120916734L,
            -1684684268097225915L,
            -1656639052655700776L,
            -4590300544528083884L,
            -1289637126171385550L,
            4787407447721089162L,
            -384008541640094426L,
            6753330342799971721L,
            7594664659975659679L,
            -5993168421106359232L,
            1175851410610350221L,
            -8310418976938866488L,
            -7170392758975997490L,
            -8095541518719441546L,
            8425242416975363604L,
            8348640866947500362L,
            7772325586861606373L,
            -8959144100541066261L,
            8789801817400416298L,
            -6523758718448298392L,
            7868102641511955463L,
            4882423929041837038L,
            6810787602603004470L,
            6624532800434954653L,
            -8389757540132851159L,
            -6698500384286041586L,
            2472496931386372942L,
            7789378395482927205L,
            -75870390091937617L,
            3442199943721069941L,
            7989301957898846311L,
            -7543151715022550143L,
            -2699347701447452607L,
            -109766556955476465L,
            7563841513061918070L,
            -4661628008249233681L,
            4668187482461501498L,
            -2434386242569608197L,
            5099261607025398043L,
            121650697908552057L,
            1711127861083379183L,
            -7591859008502125064L,
            324969431200290093L,
            6843504998903085557L,
            -3409184725339739133L,
            -2687253000933050686L,
            -2752978936697978211L,
            8453377660372228794L,
            8571828592641490917L,
            8996784377634903539L,
            5392648071872734243L,
            2547365108335708015L,
            -5318849540114975107L,
            -7873115087830277721L,
            4191950720951546682L,
            -256331041467918366L,
            -449248964399054196L,
            -61988939238320041L,
            -1095745476210843890L,
            -8909792847695755324L,
            4210564924146009061L,
            -3773313278637163330L,
            557299320686623896L,
            -2336696800787785617L,
            8773388841640589449L,
            224933315278278718L,
            -2531377169767941553L,
            -5927208260470243651L,
            4817082945707549027L,
            479785148181308017L,
            -1732103293736639631L,
            -4174375947818426568L,
            -8558770988130304577L,
            6698502427554018663L,
            -6901831830110614335L,
            4610269300398599479L,
            -2496347322521550610L,
            4738082734348277036L,
            -2812063473410212986L,
            5850158866369020253L,
            -7757621159039756205L,
            -5891429781655799572L,
            -7529342908090264416L,
            7931821083513799848L,
            -5233473364704603081L,
            -231520214047889675L,
            3462613388791530655L,
            -6675986619732266262L,
            -2059836850689737234L,
            8639645501994222699L,
            -911035067569336063L,
            7314992599907771572L,
            5408185635629822435L,
            763607425661870888L,
            8910983507284806459L,
            968175617610777503L,
            -857000518409680193L,
            4152109326457268211L,
            -3388641329602917465L,
            6181284783883977573L,
            8811452965966380640L,
            -7234662594380196781L,
            -206730407904661193L,
            -6420214400267469276L,
            -1742485966622625349L,
            -8415386021873687381L,
            -5805426162388059094L,
            -7909490813268594715L,
            8676736093249820487L,
            3959472934644990600L,
            -6082901852044608663L,
            8461555771278295943L,
            8839980479811332140L,
            -2341426139554821197L,
            -3930183473063725737L,
            8203492604112491210L,
            -1004842966614391373L,
            -1942769662704661457L,
            -4348392463262243268L,
            8274023104310774248L,
            4216330189856213023L,
            -8905856176019268667L,
            7586606119544224463L,
            5107301170952247754L,
            -7460819033690354647L,
            -6284619411642136174L,
            8302999389325444076L,
            -5423863241074256008L,
            1734339069746150002L,
            5143199403786398731L,
            2492131968511749849L,
            -464628663101641138L,
            -3239071261313179812L,
            7078714127438795470L,
            -2732878923765622691L,
            119029875131324946L,
            -8563723602524896250L,
            -6789317625782784188L,
            -8572286537743339270L,
            6157309624800008148L,
            7076308282627331518L,
            -4230526642425767113L,
            486588163254940234L,
            -1429722432976711475L,
            -8233356779824268060L,
            6173056299466315201L,
            -2717522003583541049L,
            1058063557728448008L,
            1168808719067840496L,
            3428625493582617718L,
            -560796433022244541L,
            -1006174065304474559L,
            -7737802183216524910L,
            3021821673675374005L,
            -6971813855969081857L,
            -5128412016702165171L,
            6461291275237017488L,
            142435710418880831L,
            914527305811238386L,
            -7577031552679264825L,
            -1519860640169184945L,
            -8519399334373517827L,
            1878503442582821124L,
            -8430358751268075613L,
            6938163120341860492L,
            -4421399726874218341L,
            -3580393889211895179L,
            8688623054611823410L,
            -5757230847659464229L,
            -8647365605801942979L,
            -1707618634644634490L,
            -4821575620226319963L,
            4809268585644922242L,
            7929921403165048397L,
            -1624959289518105685L,
            9187991019246063443L,
            -3812186991645976008L,
            6744727960284553058L,
            -332940271173483604L,
            717170190830259207L,
            682018938214404986L,
            -7635282391508096624L,
            -4200635422454932840L,
            8076859048199272947L,
            -4320754577940852010L,
            8393945018200718617L,
            -3326468798232522534L,
            -8371287963019658463L,
            -570255727507788225L,
            -3278394906334110405L,
            2027460916907139729L,
            7983638815697605980L,
            1945649799221045229L,
            7929594356295574148L,
            -8160783996995031817L,
            377556204030792278L,
            -4408524223451411858L,
            -2846048393244859313L,
            8610520518551420993L,
            1550810749380141073L,
            9069023614864255833L,
            2408570047389368614L,
            -8503603355434386227L,
            5800605826289593739L,
            -1398352253478710255L,
            -8331932131959813445L,
            -6821292947844765727L,
            -9027714647524103689L,
            556756821270821654L,
            -3959510983674868742L,
            -897220027741594737L,
            5635614350272255807L,
            -2034854464965770422L,
            -8361011448600037630L,
            -917880095845922342L,
            5294919124943559983L,
            -4035999331027287463L,
            -9110883075565312421L,
            -1802977748394861637L,
            -6701911505202888662L,
            4551114282274555085L,
            -2034819291986999524L,
            8135138257711629800L,
            -1843075056966227263L,
            -5384044607249732425L,
            2136438468154957238L,
            -5569696382951371387L,
            -239240309191315586L,
            270647027728682940L,
            -2802031820775125466L,
            -2057500849028881793L,
            -1251927403378448359L,
            6151550785570874990L,
            -6815388330683093837L,
            -5029633744684870164L,
            6933699261160974997L,
            4362097957347045514L,
            -3323562135402219708L,
            1084474956001996878L,
    };

    /// Static initialization. We initialize the various arrays used to compute
    /// Zobrist hash keys by filling them with pseudo-random numbers.
    static {
        MersenneTwister random = new MersenneTwister(897790353);

        for (int i = 0; i < 2 * 8 * 64; i++) {
            zobrist[i] = random.nextLong();
        }
        for (int i = 0; i < 64; i++) {
            zobEP[i] = random.nextLong();
        }
        for (int i = 0; i < 16; i++) {
            zobCastle[i] = random.nextLong();
        }
        zobSideToMove = random.nextLong();
    }

}
