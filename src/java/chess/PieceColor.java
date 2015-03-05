package chess;


////
//// A simple static class for manipulating values representing piece colors.
//// Piece colors are represented by integers, with white = 0 and black = 1.
////

public class PieceColor {

    /// Constants
    public static final int WHITE = 0;
    public static final int BLACK = 1;
    public static final int NONE = 2;

    public static final int MIN = 0, MAX = 1;


    /// Replace a color with its opposite, i.e. WHITE -> BLACK
    // and BLACK -> WHITE.
    public static int opposite(int color) {
        return color ^ 1;
    }


    /// Convert a character ('w' or 'b') to a piece color. Useful when
    //  parsing chess positions in Forsyth-Edwards notation.
    public static int fromChar(char c) {
        if (c == 'w') {
            return WHITE;
        } else if (c == 'b') {
            return BLACK;
        } else {
            return NONE;
        }
    }


    /// Convert a piece color value to a character ('w' or 'b'). Useful
    /// when exporting a chess position to a string in Forsyth-Edwards
    /// notation.
    public static char toChar(int color) {
        if (color == WHITE) {
            return 'w';
        } else if (color == BLACK) {
            return 'b';
        } else {
            return '?';
        }
    }


    /// Test whether a piece value is OK, for debugging.
    public static boolean isOK(int color) {
        return color == WHITE || color == BLACK;
    }

}
