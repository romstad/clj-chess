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
                    assert(Piece.isOK(piece));
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

            assert(isOK());
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
        public Board getParent() { return parent; }

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
        public int getLastMove() { return lastMove; }

        /// Number of half moves since the last non-reversible move was made
        public int getRule50Counter() { return rule50Counter; }

        public void setRule50Counter(int newValue) {
            rule50Counter = newValue;
        }

        /// Number of half moves played in the current game before reaching
        /// this position.
        public int getGamePly() { return gamePly; }

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
            List<Integer>result = new ArrayList<Integer>();
            for (int move : generateMoves()) {
                if (moveIsLegal(move)) {
                    result.add(move);
                }
            }
            return result;
        }


        /// Generate a list of all legal moves from the given square.
        public List<Integer> movesFrom(int square) {
            List<Integer>result = new ArrayList<Integer>();
            for (int move : generateMoves()) {
                if (Move.from(move) == square && moveIsLegal(move)) {
                    result.add(move);
                }
            }
            return result;
        }


        /// Generate a list of all legal moves to the given square.
        public List<Integer> movesTo(int square) {
            List<Integer>result = new ArrayList<>();
            for (int move: generateMoves()) {
                if (Move.to(move) == square && moveIsLegal(move)) {
                    result.add(move);
                }
            }
            return result;
        }


        /// Generate a list of all legal moves of the given piece type to the
        /// square. Useful when parsing moves in short algebraic notation.
        public List<Integer> movesForPieceTypeToSquare(int pieceType, int square) {
            List<Integer>result = new ArrayList<Integer>();
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
            assert(!isCheck());

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
            assert(Piece.isOK(piece) || piece == Piece.BLOCKER);
            assert(Square.isOK(square));

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
            assert(Square.isOK(square));
            assert(!isEmpty(square));

            int piece = board[square], color = Piece.color(piece), type = Piece.type(piece);
            assert(!kingIsSpecial || type != PieceType.KING);

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
            assert(Square.isOK(from));
            assert(Square.isOK(to));
            assert(!isEmpty(from));
            assert(pieceOn(from) != Piece.BLOCKER);
            assert(isEmpty(to));

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


        /// Compute the Zobrist hash key for the current position.
        private long computeKey() {
            long result = 0;
            long occ = occupiedSquares() & ~blockedSquares();

            while (occ != SquareSet.EMPTY) {
                int square = SquareSet.first(occ);
                occ = SquareSet.removeFirst(occ);
                result ^= zobrist[pieceOn(square) * 64 + square];
            }
            if (epSquare != Square.NONE) {
                result ^= zobEP[epSquare];
            }
            result ^= zobCastle[castleRights];
            if (sideToMove == PieceColor.BLACK) {
                result ^= zobSideToMove;
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
                    promotionZone  = SquareSet.shiftS(promotionZone);
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

            assert(kingIsSpecial);

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
        assert(boardState != null);
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
    public int getLastMove() { return state.getLastMove(); }

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
    public int getRule50Counter() { return state.getRule50Counter(); }

    /// Number of half moves played in the current game before reaching
    /// this position.
    public int getGamePly() { return state.getGamePly(); }

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
    public List<Integer>movesFrom(int square) {
        return state.movesFrom(square);
    }


    /// Generate a list of all legal moves to the given square.
    public List<Integer>movesTo(int square) {
        return state.movesTo(square);
    }


    /// Generate a list of all legal moves of the given piece type to the
    /// square. Useful when parsing moves in short algebraic notation.
    public List<Integer>movesForPieceTypeToSquare(int pieceType, int square) {
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
                        (pawnAttacks(them, to) & pawnsOfColor(us));
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

        st.sideToMove =  PieceColor.opposite(getSideToMove());

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
