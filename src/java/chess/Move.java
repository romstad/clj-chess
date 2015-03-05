package chess;

////
//// Static class for manipulating values representing chess moves. Moves are
//// represented by plain integers, for efficiency reasons. The individual
//// bits of the ints have the following interpretation:
////
////   Bits 0-5: Source square.
////   Bits 6-11: Destination square.
////   Bits 12-14: Promotion piece type.
////   Bit 15: Non-zero for en passant captures.
////   Bit 16: Non-zero for castling moves.
////

public class Move {

    public static final int NONE = 0;

    /// Create a "plain" move (i.e. a move which is neither a promotion, an
    /// an passant capture or a castle) with the given from and to squares.
    public static int make(int from, int to) {
        return to | (from << 6);
    }

    /// Create an en passant capture with the given from and to squares.
    public static int makeEP(int from, int to) {
        return make(from, to) | (1 << 15);
    }

    /// Create a promotion move with the given from and to squares, and the
    /// given promotion piece type. Note that we must supply a PieceType value,
    /// not a Piece value, for the promotion argument.
    public static int makePromotion(int from, int to, int promotion) {
        return make(from, to) | (promotion << 12);
    }

    /// Create a castling move with the given from and to squares.
    public static int makeCastle(int from, int to) {
        return make(from, to) | (1 << 16);
    }

    /// The source square of a move.
    public static int from(int move) {
        return (move >>> 6) & 63;
    }

    /// The destination square of a move.
    public static int to(int move) {
        return move & 63;
    }

    /// The promotion piece type of a move, or 0 for non-promotions.
    public static int promotion(int move) {
        return (move >> 12) & 7;
    }

    /// Test whether a move is a promotion move.
    public static boolean isPromotion(int move) {
        return promotion(move) != 0;
    }

    /// Test whether a move is an en passant capture.
    public static boolean isEP(int move) {
        return (move & (1 << 15)) != 0;
    }

    /// Test whether a move is a castle.
    public static boolean isCastle(int move) {
        return (move & (1 << 16)) != 0;
    }

    /// Test whether a move ia a kingside castle.
    public static boolean isKingsideCastle(int move) {
        return isCastle(move) && Square.file(to(move)) == Square.FILE_G;
    }

    /// Test whether a move ia a queenide castle.
    public static boolean isQueensideCastle(int move) {
        return isCastle(move) && Square.file(to(move)) == Square.FILE_C;
    }

    /// Convert a move to a string in the format used by the Universal Chess
    /// Interface protocol, i.e. "e2e4", "b2b1q", etc.
    public static String toUCI(int move) {
        StringBuilder buffer = new StringBuilder();
        buffer.append(Square.toString(from(move)));
        buffer.append(Square.toString(to(move)));
        if (isPromotion(move)) {
            buffer.append(PieceType.toChar(promotion(move)));
        }
        return buffer.toString();
    }

    /// Convert a move string in Universal Chess Interface format to a move
    /// value. We need a Board object in addition to the string in order to
    /// correctly interpret the move, because the move string by itself is
    /// not sufficient to decide whether the move is a castle or en passant
    /// capture.
    public static int fromUCI(String moveString, Board board) {

        if (moveString.length() < 4) {
            return NONE;
        }

        int from = Square.fromString(moveString.substring(0, 2));
        int to = Square.fromString(moveString.substring(2, 4));

        if (!Square.isOK(from) || !Square.isOK(to)) {
            return NONE;
        }

        if (moveString.length() >= 5) {
            int promotion = PieceType.fromChar(moveString.charAt(4));
            if (promotion != PieceType.NONE) {
                return makePromotion(from, to, promotion);
            }
        }

        if (Piece.type(board.pieceOn(from)) == PieceType.KING && Math.abs(from - to) == 2) {
            return makeCastle(from, to);
        }

        if (Piece.type(board.pieceOn(from)) == PieceType.PAWN && to == board.getEpSquare()) {
            return makeEP(from, to);
        }

        return make(from, to);
    }

    /// Test whether an int looks like a valid move value, for debugging. This
    /// function is very crude, and happily accepts moves that are not
    /// possible in any chess position (e.g. a1h6q).
    public static boolean isOK(int move) {
        if (!Square.isOK(from(move))) {
            return false;
        }
        if (!Square.isOK(to(move))) {
            return false;
        }
        if (promotion(move) == PieceType.PAWN || promotion(move) == PieceType.KING || promotion(move) == 7) {
            return false;
        }
        if (isPromotion(move) && (isCastle(move) || isEP(move))) {
            return false;
        }
        if (isCastle(move) && isEP(move)) {
            return false;
        }
        return true;
    }

}
