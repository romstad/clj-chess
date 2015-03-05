package chess;

////
//// Static class for manipulating values representing chess pieces. Chess
//// pieces are represented by plain integers, for efficiency reasons. A piece
//// is composed of a piece color (see the PieceColor class) and a piece type
//// (see the PieceType class) with the piece type encoded in the lower 3 bits
//// of the int, and the color in the 4th bit.
////

public class Piece {

    /// Constants
    public static final int NONE = 0;
    public static final int WHITE_PAWN = Piece.make(PieceColor.WHITE, PieceType.PAWN);
    public static final int WHITE_KNIGHT = Piece.make(PieceColor.WHITE, PieceType.KNIGHT);
    public static final int WHITE_BISHOP = Piece.make(PieceColor.WHITE, PieceType.BISHOP);
    public static final int WHITE_ROOK = Piece.make(PieceColor.WHITE, PieceType.ROOK);
    public static final int WHITE_QUEEN = Piece.make(PieceColor.WHITE, PieceType.QUEEN);
    public static final int WHITE_KING = Piece.make(PieceColor.WHITE, PieceType.KING);
    public static final int BLACK_PAWN = Piece.make(PieceColor.BLACK, PieceType.PAWN);
    public static final int BLACK_KNIGHT = Piece.make(PieceColor.BLACK, PieceType.KNIGHT);
    public static final int BLACK_BISHOP = Piece.make(PieceColor.BLACK, PieceType.BISHOP);
    public static final int BLACK_ROOK = Piece.make(PieceColor.BLACK, PieceType.ROOK);
    public static final int BLACK_QUEEN = Piece.make(PieceColor.BLACK, PieceType.QUEEN);
    public static final int BLACK_KING = Piece.make(PieceColor.BLACK, PieceType.KING);
    public static final int EMPTY = Piece.make(PieceColor.NONE, PieceType.NONE);

    /// Create a piece value with the given color and type:
    public static int make(int color, int type) {
        return (color << 3) | type;
    }

    /// Create a pawn of the given color:
    public static int pawnOfColor(int color) {
        return make(color, PieceType.PAWN);
    }

    /// Create a knight of the given color:
    public static int knightOfColor(int color) {
        return make(color, PieceType.KNIGHT);
    }

    /// Create a bishop of the given color:
    public static int bishopOfColor(int color) {
        return make(color, PieceType.BISHOP);
    }

    /// Create a rook of the given color:
    public static int rookOfColor(int color) {
        return make(color, PieceType.ROOK);
    }

    /// Create a queen of the given color:
    public static int queenOfColor(int color) {
        return make(color, PieceType.QUEEN);
    }

    /// Create a king of the given color:
    public static int kingOfColor(int color) {
        return make(color, PieceType.KING);
    }

    /// Find the piece color given a piece value:
    public static int color(int piece) {
        return piece >>> 3;
    }

    /// Find the piece type of a piece value:
    public static int type(int piece) {
        return piece & 7;
    }

    /// Test whether a piece is a slider (i.e. a bishop, a rook or a queen):
    public static boolean isSlider(int piece) {
        return PieceType.isSlider(type(piece));
    }

    /// Convert an English piece character (PNBRQK) to a piece value. An
    /// upper-case letter gives a white piece, and a lower-case letter a
    /// black piece. This function is used when parsing positions in
    /// Forsyth-Edwards notation.
    public static int fromChar(char c) {
        int type = PieceType.fromChar(c);
        int color = Character.isUpperCase(c) ? PieceColor.WHITE : PieceColor.BLACK;
        return PieceColor.isOK(color) && PieceType.isOK(type) ? make(color, type) : NONE;
    }

    /// Convert a piece value to an English piece letter (PNBRQK). White
    /// pieces give upper-case letters, and black pieces lowercase letters.
    /// This function is used when exporting a chess position in
    /// Forsyth-Edwards notation.
    public static char toChar(int piece) {
        int color = color(piece), type = type(piece);
        char c = PieceType.toChar(type);
        return color == PieceColor.WHITE ? Character.toUpperCase(c) : c;
    }

    /// Test whether an integer is a valid piece value, for debugging.
    public static boolean isOK(int piece) {
        return PieceColor.isOK(color(piece)) && PieceType.isOK(type(piece));
    }
}
