package chess;

////
//// Static class for manipulating values representing piece types.
//// Piece types are represented by plain integers, for efficiency
//// reasons.

public class PieceType {

    /// Constants
    public static final int NONE = 0;
    public static final int PAWN = 1;
    public static final int KNIGHT = 2;
    public static final int BISHOP = 3;
    public static final int ROOK = 4;
    public static final int QUEEN = 5;
    public static final int KING = 6;
    public static final int BLOCKER = 7;

    public static final int MIN = PAWN, MAX = KING;

    private static final String PIECE_CHARS = "?pnbrqk";


    /// Convert a character in English chess notation (PNBRQK) to a chess
    /// piece value. Both uppercase and lowercase letters are accepted.
    /// If the character is not recognized as a piece letter, PieceType.NONE
    /// is returned.
    public static int fromChar(char c) {
        int i = PIECE_CHARS.indexOf(Character.toLowerCase(c));
        return i == -1 ? NONE : i;
    }


    /// Convert a piece type value to a lowercase English piece letter (pnbrqk).
    public static char toChar(int pieceType) {
        return PIECE_CHARS.charAt(pieceType);
    }


    /// Test whether a piece is a slider, i.e. a bishop, a rook or a queen.
    public static boolean isSlider(int pieceType) {
        return pieceType >= BISHOP && pieceType <= QUEEN;
    }


    /// Test whether an integer is a valid piece type value, for debugging.
    public static boolean isOK(int pieceType) {
        return pieceType >= PAWN && pieceType <= KING;
    }

}
