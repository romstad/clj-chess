package chess;

////
//// Static class for manipulating valued representing square sets. Square sets
//// are represented by 64-bit integers, where the non-zero bits correspond to
//// the members of the set.
////

public class SquareSet {

    /// Add a new square to a set of squares.
    public static long add(long squareSet, int square) {
        return squareSet | (1L << square);
    }

    /// Remove a square from a set of squares.
    public static long remove(long squareSet, int square) {
        return squareSet & ~(1L << square);
    }

    /// Move an element of a square set from one square to another.
    /// move(set, from, to) will have exactly the same effect as
    /// remove(set, from) followed by add(set, to).
    public static long move(long squareSet, int from, int to) {
        return (squareSet | (1L << to)) & ~(1L << from);
    }

    /// Create a square set value with the given squares.
    public static long setWithSquares(int... squares) {
        long result = 0L;
        for (int square : squares) {
            result = add(result, square);
        }
        return result;
    }

    /// Test whether a square set contains the given square.
    public static boolean contains(long squareSet, int square) {
        return (squareSet & (1L << square)) != 0;
    }

    /// Test whether a square set is empty.
    public static boolean isEmpty(long squareSet) {
        return squareSet == 0;
    }

    /// The number of members in a square set.
    public static int count(long squareSet) {
        return Long.bitCount(squareSet);
    }

    /// Test whether a square set contains exactly one square. isSingleton(set)
    /// is equivalent to count(set) == 1, except that isSingleton should be a
    /// little faster.
    public static boolean isSingleton(long squareSet) {
        return squareSet != EMPTY && removeFirst(squareSet) == EMPTY;
    }

    /// Get the first member of a square set.
    public static int first(long squareSet) {
        return Long.numberOfTrailingZeros(squareSet);
    }

    /// Remove the first member of a square set.
    public static long removeFirst(long squareSet) {
        return squareSet & squareSet - 1;
    }

    /// The set of squares a pawn of the given color on the given square would
    /// attack.
    public static long pawnAttacks(int color, int square) {
        return pawnAttacks[color][square];
    }

    /// The set of squares a knight on the given square would attack.
    public static long knightAttacks(int square) {
        return knightAttacks[square];
    }

    /// The set of squares a bishop on the given square would attack, assuming
    /// that the squares in blockedSquares are occupied.
    public static long bishopAttacks(int square, long blockedSquares) {
        long ss = blockedSquares & bishopMask[square];
        return bishopAttacks[bishopAttackIndex[square] + (int)((ss * BISHOP_MAGIC[square]) >>> BISHOP_SHIFT[square])];
    }

    /// The set of squares a rook on the given square would attack, assuming
    /// that the squares in blockedSquares are occupied.
    public static long rookAttacks(int square, long blockedSquares) {
        long ss = blockedSquares & rookMask[square];
        return rookAttacks[rookAttackIndex[square] + (int)((ss * ROOK_MAGIC[square]) >>> ROOK_SHIFT[square])];
    }

    /// The set of squares a queen on the given square would attack, assuming
    /// that the squares in blockedSquares are occupied.
    public static long queenAttacks(int square, long blockedSquares) {
        return bishopAttacks(square, blockedSquares) | rookAttacks(square, blockedSquares);
    }

    /// The set of squares a king on the given square would attack.
    public static long kingAttacks(int square) {
        return kingAttacks[square];
    }

    /// Print a square set to the standard output, for debugging.
    public static void print(long squareSet) {
        for (int rank = 7; rank >= 0; rank--) {
            for (int file = 0; file <= 7; file++) {
                System.out.print(contains(squareSet, file + rank * 8) ? "X " : "- ");
            }
            System.out.println();
        }
    }

    /// Shift a square set one square up the board. Useful when generating
    /// white pawn pushes.
    public static long shiftN(long squareSet) {
        return squareSet << 8;
    }

    /// Shift a square set one square down the board. Useful when generating
    /// black pawn pushes.
    public static long shiftS(long squareSet) {
        return squareSet >>> 8;
    }

    /// Shift a square set one square up and to the left. Useful when
    /// generating white pawn captures.
    public static long shiftNW(long squareSet) {
        return (squareSet << 7) & ~FILE_H_SQUARES;
    }

    /// Shift a square set one square up and to the right. Useful when
    /// generating white pawn captures.
    public static long shiftNE(long squareSet) {
        return (squareSet << 9) & ~FILE_A_SQUARES;
    }

    /// Shift a square set one square down and to the left. Useful when
    /// generating black pawn captures.
    public static long shiftSW(long squareSet) {
        return (squareSet >>> 9) & ~FILE_H_SQUARES;
    }

    /// Shift a square set one square down and to the right. Useful when
    /// generating black pawn captures.
    public static long shiftSE(long squareSet) {
        return (squareSet >>> 7) & ~FILE_A_SQUARES;
    }

    /// The squares between two squares situated on the same rank, file or
    /// diagonal, or the empty set if the two square don't share a rank, file
    /// or diagonal. For instance, squaresBetween(Square.F1, Square.B5) will
    /// return a square set consisting of the squares e2, d3 and c4, while
    /// squaresBetween(Square.F1, Square.C5) will return the empty set.
    public static long squaresBetween(int square1, int square2) {
        return squaresBetween[square1 * 64 + square2];
    }

    /// Some useful constant square sets.
    public static final long FILE_A_SQUARES = 0x0101010101010101L;
    public static final long FILE_B_SQUARES = 0x0202020202020202L;
    public static final long FILE_C_SQUARES = 0x0404040404040404L;
    public static final long FILE_D_SQUARES = 0x0808080808080808L;
    public static final long FILE_E_SQUARES = 0x1010101010101010L;
    public static final long FILE_F_SQUARES = 0x2020202020202020L;
    public static final long FILE_G_SQUARES = 0x4040404040404040L;
    public static final long FILE_H_SQUARES = 0x8080808080808080L;

    public static final long RANK_1_SQUARES = 0xFFL;
    public static final long RANK_2_SQUARES = 0xFF00L;
    public static final long RANK_3_SQUARES = 0xFF0000L;
    public static final long RANK_4_SQUARES = 0xFF000000L;
    public static final long RANK_5_SQUARES = 0xFF00000000L;
    public static final long RANK_6_SQUARES = 0xFF0000000000L;
    public static final long RANK_7_SQUARES = 0xFF000000000000L;
    public static final long RANK_8_SQUARES = 0xFF00000000000000L;

    public static final long EMPTY = 0;


    /// The rest of this file contains various low-level magic that makes
    /// attack generation for sliding pieces work, and a few initialization
    /// functions.

    private static final long[] ROOK_MAGIC = {
            0xa8002c000108020L, 0x4440200140003000L, 0x8080200010011880L,
            0x380180080141000L, 0x1a00060008211044L, 0x410001000a0c0008L,
            0x9500060004008100L, 0x100024284a20700L, 0x802140008000L,
            0x80c01002a00840L, 0x402004282011020L, 0x9862000820420050L,
            0x1001448011100L, 0x6432800200800400L, 0x40100010002000cL,
            0x2800d0010c080L, 0x90c0008000803042L, 0x4010004000200041L,
            0x3010010200040L, 0xa40828028001000L, 0x123010008000430L,
            0x24008004020080L, 0x60040001104802L, 0x582200028400d1L,
            0x4000802080044000L, 0x408208200420308L, 0x610038080102000L,
            0x3601000900100020L, 0x80080040180L, 0xc2020080040080L,
            0x80084400100102L, 0x4022408200014401L, 0x40052040800082L,
            0xb08200280804000L, 0x8a80a008801000L, 0x4000480080801000L,
            0x911808800801401L, 0x822a003002001894L, 0x401068091400108aL,
            0x4a10a00004cL, 0x2000800640008024L, 0x1486408102020020L,
            0x100a000d50041L, 0x810050020b0020L, 0x204000800808004L,
            0x20048100a000cL, 0x112000831020004L, 0x9000040810002L,
            0x440490200208200L, 0x8910401000200040L, 0x6404200050008480L,
            0x4b824a2010010100L, 0x4080801810c0080L, 0x400802a0080L,
            0x8224080110026400L, 0x40002c4104088200L, 0x1002100104a0282L,
            0x1208400811048021L, 0x3201014a40d02001L, 0x5100019200501L,
            0x101000208001005L, 0x2008450080702L, 0x1002080301d00cL,
            0x410201ce5c030092L
    };

    private static final int[] ROOK_SHIFT = {
            52, 53, 53, 53, 53, 53, 53, 52, 53, 54, 54, 54, 54, 54, 54, 53,
            53, 54, 54, 54, 54, 54, 54, 53, 53, 54, 54, 54, 54, 54, 54, 53,
            53, 54, 54, 54, 54, 54, 54, 53, 53, 54, 54, 54, 54, 54, 54, 53,
            53, 54, 54, 54, 54, 54, 54, 53, 52, 53, 53, 53, 53, 53, 53, 52
    };

    private static long[] rookMask = new long[64];
    private static int[] rookAttackIndex = new int[64];
    private static long[] rookAttacks = new long[0x19000];

    private static final long[] BISHOP_MAGIC = {
            0x440049104032280L, 0x1021023c82008040L, 0x404040082000048L,
            0x48c4440084048090L, 0x2801104026490000L, 0x4100880442040800L,
            0x181011002e06040L, 0x9101004104200e00L, 0x1240848848310401L,
            0x2000142828050024L, 0x1004024d5000L, 0x102044400800200L,
            0x8108108820112000L, 0xa880818210c00046L, 0x4008008801082000L,
            0x60882404049400L, 0x104402004240810L, 0xa002084250200L,
            0x100b0880801100L, 0x4080201220101L, 0x44008080a00000L,
            0x202200842000L, 0x5006004882d00808L, 0x200045080802L,
            0x86100020200601L, 0xa802080a20112c02L, 0x80411218080900L,
            0x200a0880080a0L, 0x9a01010000104000L, 0x28008003100080L,
            0x211021004480417L, 0x401004188220806L, 0x825051400c2006L,
            0x140c0210943000L, 0x242800300080L, 0xc2208120080200L,
            0x2430008200002200L, 0x1010100112008040L, 0x8141050100020842L,
            0x822081014405L, 0x800c049e40400804L, 0x4a0404028a000820L,
            0x22060201041200L, 0x360904200840801L, 0x881a08208800400L,
            0x60202c00400420L, 0x1204440086061400L, 0x8184042804040L,
            0x64040315300400L, 0xc01008801090a00L, 0x808010401140c00L,
            0x4004830c2020040L, 0x80005002020054L, 0x40000c14481a0490L,
            0x10500101042048L, 0x1010100200424000L, 0x640901901040L,
            0xa0201014840L, 0x840082aa011002L, 0x10010840084240aL,
            0x420400810420608L, 0x8d40230408102100L, 0x4a00200612222409L,
            0xa08520292120600L
    };

    private static final int[] BISHOP_SHIFT = {
            58, 59, 59, 59, 59, 59, 59, 58, 59, 59, 59, 59, 59, 59, 59, 59,
            59, 59, 57, 57, 57, 57, 59, 59, 59, 59, 57, 55, 55, 57, 59, 59,
            59, 59, 57, 55, 55, 57, 59, 59, 59, 59, 57, 57, 57, 57, 59, 59,
            59, 59, 59, 59, 59, 59, 59, 59, 58, 59, 59, 59, 59, 59, 59, 58
    };

    private static long[] bishopMask = new long[64];
    private static int[] bishopAttackIndex = new int[64];
    private static long[] bishopAttacks = new long[0x1480];

    private static long[][] pawnAttacks = new long[2][64];
    private static long[] knightAttacks = new long[64];
    private static long[] kingAttacks = new long[64];

    private static long[] squaresBetween = new long[64 * 64];

    private static long slidingAttacks(int square, long block, int dirs, int[][] deltas,
                                       int fmin, int fmax, int rmin, int rmax) {
        long result = 0;
        int rk = square / 8, fl = square % 8, r, f, i;
        for (i = 0; i < dirs; i++) {
            int dx = deltas[i][0], dy = deltas[i][1];
            for (f = fl + dx, r = rk + dy;
                 (dx == 0 || (f >= fmin && f <= fmax)) && (dy == 0 || (r >= rmin && r <= rmax));
                 f += dx, r += dy) {
                result |= (1L << (f + r * 8));
                if ((block & (1L << (f + r * 8))) != 0) {
                    break;
                }
            }
        }
        return result;
    }

    private static long indexToSquareSet(int index, long mask) {
        int i, j, n = count(mask);
        long result = 0;
        for (i = 0; i < n; i++) {
            j = first(mask);
            mask = removeFirst(mask);
            if ((index & (1L << i)) != 0) {
                result |= (1L << j);
            }
        }
        return result;
    }

    private static void initSlidingAttacks(long[] attacks, int[] attackIndex, long[] mask,
                                           final int[] shift, final long[] magic,
                                           int[][] deltas) {
        int i, j, k, index = 0;
        long ss;

        for (i = 0; i < 64; i++) {
            attackIndex[i] = index;
            mask[i] = slidingAttacks(i, 0, 4, deltas, 1, 6, 1, 6);
            j = (1 << (64 - shift[i]));
            for (k = 0; k < j; k++) {
                ss = indexToSquareSet(k, mask[i]);
                attacks[index + (int)((ss * magic[i]) >>> shift[i])] =
                        slidingAttacks(i, ss, 4, deltas, 0, 7, 0, 7);
            }
            index += j;
        }
    }

    private static void initStepAttacks(long[] attacks, int[] deltas) {
        for (int i = 0; i < 64; i++) {
            attacks[i] = 0;
            for (int delta : deltas) {
                int j = i + delta;
                if (Square.distance(i, j) <= 2 && j >= 0 && j <= 63) {
                    attacks[i] |= (1L << j);
                }
            }
        }
    }

    static {
        int[][] rookDeltas = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        int[][] bishopDeltas = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        initSlidingAttacks(rookAttacks, rookAttackIndex, rookMask, ROOK_SHIFT, ROOK_MAGIC, rookDeltas);
        initSlidingAttacks(bishopAttacks, bishopAttackIndex, bishopMask, BISHOP_SHIFT, BISHOP_MAGIC, bishopDeltas);

        int[][] pawnDeltas = { { 7, 9 }, { -7, -9} };
        int[] kingDeltas = { -9, -8, -7, -1, 1, 7, 8, 9 };
        int[] knightDeltas = { -17, -15, -10, -6, 6, 10, 15, 17 };

        initStepAttacks(pawnAttacks[0], pawnDeltas[0]);
        initStepAttacks(pawnAttacks[1], pawnDeltas[1]);
        initStepAttacks(knightAttacks, knightDeltas);
        initStepAttacks(kingAttacks, kingDeltas);

        for (int i = 0; i < 64 * 64; i++)
            squaresBetween[i] = 0;
        for (int square1 = Square.MIN; square1 < Square.MAX; square1++)
            for (int square2 = square1 + 1; square2 <= Square.MAX; square2++)
                if (contains(queenAttacks(square1, 0), square2))
                    for (int square3 = square1 + 1; square3 < square2; square3++)
                        if (!contains(queenAttacks(square1, 1L << square3), square2)) {
                            squaresBetween[square1 * 64 + square2] |= (1L << square3);
                            squaresBetween[square2 * 64 + square1] |= (1L << square3);
                        }
    }
}