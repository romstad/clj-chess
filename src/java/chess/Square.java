package chess;

////
//// Static class for manipulating values representing squares, files and
//// ranks. Squares are represented by integers in the range 0 (a1) to
//// 63 (h8), while ranks and files are represented by integers between
//// 0 and 7.
////

public class Square {

    /// Square constants
    public static final int A1 = 0, B1 = 1, C1 = 2, D1 = 3, E1 = 4, F1 = 5, G1 = 6, H1 = 7;
    public static final int A2 = 8, B2 = 9, C2 = 10, D2 = 11, E2 = 12, F2 = 13, G2 = 14, H2 = 15;
    public static final int A3 = 16, B3 = 17, C3 = 18, D3 = 19, E3 = 20, F3 = 21, G3 = 22, H3 = 23;
    public static final int A4 = 24, B4 = 25, C4 = 26, D4 = 27, E4 = 28, F4 = 29, G4 = 30, H4 = 31;
    public static final int A5 = 32, B5 = 33, C5 = 34, D5 = 35, E5 = 36, F5 = 37, G5 = 38, H5 = 39;
    public static final int A6 = 40, B6 = 41, C6 = 42, D6 = 43, E6 = 44, F6 = 45, G6 = 46, H6 = 47;
    public static final int A7 = 48, B7 = 49, C7 = 50, D7 = 51, E7 = 52, F7 = 53, G7 = 54, H7 = 55;
    public static final int A8 = 56, B8 = 57, C8 = 58, D8 = 59, E8 = 60, F8 = 61, G8 = 62, H8 = 63;
    public static final int NONE = 64;

    /// Rank/file constants
    public static final int FILE_A = 0, FILE_B = 1, FILE_C = 2, FILE_D = 3, FILE_E = 4, FILE_F = 5, FILE_G = 6, FILE_H = 7, FILE_NONE = 8;
    public static final int RANK_1 = 0, RANK_2 = 1, RANK_3 = 2, RANK_4 = 3, RANK_5 = 4, RANK_6 = 5, RANK_7 = 6, RANK_8 = 7, RANK_NONE = 9;

    public static final int MIN = 0, MAX = 63, FILE_MIN = 0, FILE_MAX = 7, RANK_MIN = 0, RANK_MAX = 7;

    /// Square deltas
    public static final int DELTA_N = 8, DELTA_S = -8, DELTA_W = -1, DELTA_E = 1;
    public static final int DELTA_NW = DELTA_N + DELTA_W, DELTA_NE = DELTA_N + DELTA_E;
    public static final int DELTA_SW = DELTA_S + DELTA_W, DELTA_SE = DELTA_S + DELTA_E;

    /// Create a square with the given file and rank value. For instance,
    /// Square.make(FILE_F, RANK_6) will return Square.F6.
    static public int make(int file, int rank) {
        return (rank << 3) | file;
    }

    /// Get the file of a square.
    static public int file(int square) {
        return square & 7;
    }

    /// Get the rank of a square.
    static public int rank(int square) {
        return square >>> 3;
    }

    /// The difference in the X direction between two squares.
    static public int fileDistance(int square1, int square2) {
        return Math.abs(file(square1) - file(square2));
    }

    /// The difference in the Y direction between two squares.
    static public int rankDistance(int square1, int square2) {
        return Math.abs(rank(square1) - rank(square2));
    }

    /// The difference between two squares, measured as the maximum of the
    /// X and Y directions, or the number of king moves to get from one square
    /// to the other on an empty board.
    static public int distance(int square1, int square2) {
        return Math.max(fileDistance(square1, square2), rankDistance(square1, square2));
    }

    /// A square delta representing a pawn push for the given color.
    static public int pawnPush(int color) {
        return color == PieceColor.WHITE ? DELTA_N : DELTA_S;
    }

    /// The pawn rank of a square seen from the given color's perspective.
    /// For instance, pawnRank(A3, PieceColor.WHITE) will return RANK_3, while
    /// pawnRank(A3, PieceColor.BLACK) will return RANK_6.
    static public int pawnRank(int color, int square) {
        return rank(square) ^ (color * 7);
    }

    /// Convert a file to a character in standard algebraic notation (a-h).
    static public char fileToChar(int file) {
        return (char)(file + (int)'a');
    }

    /// Convert a character in standard algebraic notation to a file value.
    static public int fileFromChar(char c) {
        return (int)c - (int)'a';
    }

    /// Convert a rank to a character in standard algebraic notation (1-8).
    static public char rankToChar(int rank) {
        return (char)(rank + (int)'1');
    }

    /// Convert a character in standard algebraic notation to a rank value.
    static public int rankFromChar(char c) {
        return (int)c - (int)'1';
    }

    /// Convert a square value to a string in standard algebraic notation.
    /// For instance, toString(E4) will return "e4".
    static public String toString(int square) {
        return "" + fileToChar(file(square)) + rankToChar(rank(square));
    }

    /// Convert a square string in standard algebraic notation to a square
    /// value.
    static public int fromString(String s) {
        return make(fileFromChar(s.charAt(0)), rankFromChar(s.charAt(1)));
    }

    /// Test whether an int is a valid file value, for debugging.
    static public boolean fileIsOK(int file) {
        return file >= FILE_MIN && file <= FILE_MAX;
    }

    /// Test whether an int is a valid rank value, for debugging.
    static public boolean rankIsOK(int rank) {
        return rank >= RANK_MIN && rank <= RANK_MAX;
    }

    /// Test whether an int is a valid square value, for debugging.
    static public boolean isOK(int square) {
        return square >= MIN && square <= MAX;
    }
}
