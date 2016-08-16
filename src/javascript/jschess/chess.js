goog.provide("jschess.chess");

var chess = jschess.chess;

////////////////////////////////////
// Random numbers, for Zobrist keys
///////////////////////////////////

var randInt = function () {
    return Math.floor(Math.random() * 0xFFFFFFFF);
};

var randIntArray = function (size) {
    return (function () {
        var result = [];
        for (var i = 0; i < size; i++) {
            result.push(randInt());
        }
        return result;
    }());
};

///////////////////////////////////
// Colors, piece types and pieces
///////////////////////////////////

var COLOR_WHITE = 0, COLOR_BLACK = 1, COLOR_NONE = -1;
var PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;
var WP = 1, WN = 2, WB = 3, WR = 4, WQ = 5, WK = 6;
var BP = 9, BN = 10, BB = 11, BR = 12, BQ = 13, BK = 14;
var EMPTY = 16, OUTSIDE = 24;

// Replace a color with its opposite.
var oppositeColor = function (color) {
    return color == COLOR_WHITE ? COLOR_BLACK : COLOR_WHITE;
};

// Create a piece value with the given color and type.
var pieceMake = function (color, type) {
    return color * 8 + type;
};

chess.pieceMake = pieceMake;

// Get the color of a piece.
var pieceColor = function (piece) {
    return Math.floor(piece / 8);
};

chess.pieceColor = pieceColor;

// Get the type of a piece.
var pieceType = function (piece) {
    return piece % 8;
};

chess.pieceType = pieceType;

// Test whether a piece is a slider, i.e. a bishop, a rook or a wueen.
var isSlider = function (piece) {
    return pieceType(piece) >= BISHOP && pieceType(piece) <= QUEEN;
};

// Convert a character ('w' or 'b') to a piece color. Useful when parsing
// chess positions in Forsyth-Edwards notation.
var colorFromString = function (string) {
    return "wb".indexOf(string[0]);
};

// Convert a color value to a string ('w' or 'b'). Useful when exporting chess
// positions to Forsyth-Edwards notation.
var colorToString = function (color) {
    return "wb"[color];
};

var pieceLetters = "?PNBRQK??pnbrqk";

// Covert a piece to an English piece character. White pieces give uppercase
// letters, black pieces lowercase letters.
var pieceToString = function (piece) {
    return pieceLetters[piece];
};

// Convert the first character in a string to a piece value, using English
// piece letters. Uppercase letters give white pieces, lowercase letters give
// black pieces.
var pieceFromString = function (string) {
    return pieceLetters.indexOf(string[0]);
};

// Test whether an integer is a valid color value, for debugging.
var colorIsOK = function (color) {
    return color == COLOR_WHITE || color == COLOR_BLACK;
};

// Test whether an integer is a valid piece type value, for debugging.
var pieceTypeIsOK = function (type) {
    return type >= PAWN && type <= KING;
};

// Test whether an integer is a valid piece value, for debugging.
var pieceIsOK = function (piece) {
    return colorIsOK(pieceColor(piece)) && pieceTypeIsOK(pieceType(piece));
};


///////////////////////////////////
// Files, ranks and squares
///////////////////////////////////

var FILE_MIN = 4, FILE_MAX = 11, FILE_NONE = -1;
var RANK_MIN = 4, RANK_MAX = 11, RANK_NONE = -1;
var SQUARE_NONE = -1;

// Square deltas
var DELTA_N = 16, DELTA_S = -DELTA_N, DELTA_E = 1, DELTA_W = -DELTA_E;
var DELTA_NW = DELTA_N + DELTA_W, DELTA_NE = DELTA_N + DELTA_E;
var DELTA_SW = DELTA_S + DELTA_W, DELTA_SE = DELTA_S + DELTA_E;
var DELTA_NNW = 2*DELTA_N + DELTA_W, DELTA_NNE = 2*DELTA_N + DELTA_E;
var DELTA_NWW = DELTA_N + 2*DELTA_W, DELTA_NEE = DELTA_N + 2*DELTA_E;
var DELTA_SSW = 2*DELTA_S + DELTA_W, DELTA_SSE = 2*DELTA_S + DELTA_E;
var DELTA_SWW = DELTA_S + 2*DELTA_W, DELTA_SEE = DELTA_S + 2*DELTA_E;


// Create a square with the given file and rank value.
var squareMake = function (file, rank) {
    return rank * 16 + file;
};

// Get the file of a square.
var squareFile = function(square) {
    return square % 16;
};

// Get the rank of a square.
var squareRank = function(square) {
    return Math.floor(square / 16);
};

// The distance in X direction between two squares.
var fileDistance = function (square1, square2) {
    return Math.abs(squareFile(square1) - squareFile(square2));
};

// The distance in Y direction between two squares.
var rankDistance = function (square1, square2) {
    return Math.abs(squareRank(square1) - squareRank(square2));
};

// The distance between two squares, measured as the maximum of the distances
// in the X and Y directions, or the number of king moves required to get from
// one square to the other on an empty board.
var squareDistance = function (square1, square2) {
    return Math.max(
        fileDistance(square1, square2), rankDistance(square1, square2));
};

// A square delta representing a pawn push for the given color.
var pawnPush = function (color) {
    return color === COLOR_WHITE ? DELTA_N : DELTA_S;
};

// The pawn rank of a square seen from the given color's perspective.
var pawnRank = function (color, square) {
    return color === COLOR_WHITE ? squareRank(square) : 7 - squareRank(square);
};

// Convert a file to a string in algebraic notation.
var fileToString = function (file) {
    return String.fromCharCode("a".charCodeAt(0) + file - FILE_MIN);
};

// Convert a rank to a string in algebraic notation.
var rankToString = function (rank) {
    return String.fromCharCode("1".charCodeAt(0) + rank - RANK_MIN);
};

// Convert a square to a string in algebraic notation.
var squareToString = function (square) {
    return fileToString(squareFile(square)) + rankToString(squareRank(square));
};

chess.squareToString = squareToString;

// Convert a string in algebraic notation to a file.
var fileFromString = function (string) {
    var result = string.charCodeAt(0) - "a".charCodeAt(0) + FILE_MIN;
    return fileIsOK(result) ? result : FILE_NONE;
};

// Convert a string in algebraic notation to a rank.
var rankFromString = function (string) {
    var result = string.charCodeAt(0) - "1".charCodeAt(0) + RANK_MIN;
    return rankIsOK(result) ? result : RANK_NONE;
};

// Convert a string in algebraic notation to a square.
var squareFromString = function (string) {
    var file = fileFromString(string[0]);
    var rank = rankFromString(string[1]);
    return fileIsOK(file) && rankIsOK(rank) ? squareMake(file, rank) : SQUARE_NONE;
};

chess.squareFromString = squareFromString;

// Convert a square from compressed format (a1=0, h8=63) to regular format
// (a1 = 4 + 4*16, h8 = 11 + 11*16)
var squareExpand = function (square) {
    var f = square % 8, r = Math.floor(square / 8);
    return squareMake(f + FILE_MIN, r + RANK_MIN);
};

// Convert a square from regular format (a1 = 4 + 4*16, h8 = 11 + 11*16) to
// compressed format (1=0, h8=63)
var squareCompress = function (square) {
    var f = squareFile(square), r  = squareRank(square);
    return (f - FILE_MIN) + (r - RANK_MIN) * 8;
};

// Test whether an integer is a valid file value, for debugging.
var fileIsOK = function (file) {
    return file >= FILE_MIN && file <= FILE_MAX;
};

// Test whether an integer is a valid rank value, for debugging.
var rankIsOK = function (rank) {
    return rank >= RANK_MIN && rank <= RANK_MAX;
};

// Test whether an integer is a valid square value, for debugging.
var squareIsOK = function (square) {
    return fileIsOK(squareFile(square)) && rankIsOK(squareRank(square));
};

chess.squareFile = function (square) {
    return squareFile(square) - FILE_MIN;
};

chess.squareRank = function (square) {
    return squareRank(square) - RANK_MIN;
};

chess.squareMake = function (file, rank) {
    return squareMake(file + FILE_MIN, rank + RANK_MAX);
};


///////////////////////////////////
// Moves
///////////////////////////////////

var moveMakeInternal = function (from, to, castle, promotion, ep) {
    return { from: from, to: to, castle: castle, promotion: promotion, ep: ep };
};

var moveMake = function (from, to) {
    return moveMakeInternal(from, to, false, false, false);
};

var moveMakeEp = function (from, to) {
    return moveMakeInternal(from, to, false, false, true);
};

var moveMakePromotion = function (from, to, promotion) {
    return moveMakeInternal(from, to, false, promotion, false);
};

var moveMakeCastle = function (from, to) {
    return moveMakeInternal(from, to, true, false, false);
};

var moveFrom = function (move) { return move.from; };

var moveTo = function (move) { return move.to; };

var moveIsCastle = function (move) { return move.castle; };

var moveIsKingsideCastle = function (move) {
    return moveIsCastle(move) && moveTo(move) > moveFrom(move);
};

var moveIsQueensideCastle = function (move) {
    return moveIsCastle(move) && moveTo(move) < moveFrom(move);
};

var moveIsEP = function (move) { return move.ep; };

var movePromotion = function (move) { return move.promotion; };

chess.moveFrom = function (move) {
    return squareCompress(moveFrom(move));
};

chess.moveTo = function (move) {
    return squareCompress(moveTo(move));
};

chess.moveIsCastle = moveIsCastle;
chess.moveIsKingsideCastle = moveIsKingsideCastle;
chess.moveIsQueensideCastle = moveIsQueensideCastle;
chess.moveIsEP = moveIsEP;
chess.movePromotion = function (move) {
    return movePromotion(move) ? pieceType(movePromotion(move)) : false;
};
chess.moveIsPromotion = movePromotion;

var moveToUCI = function (move) {
    return squareToString(moveFrom(move)) + squareToString(moveTo(move)) +
        (movePromotion(move) ? pieceToString(movePromotion(move)).toLowerCase() : "");
};

chess.moveToUCI = moveToUCI;

var moveFromUCI = function (moveString, board) {
    if (moveString.length < 4) {
        return null;
    }

    var from = squareFromString(moveString.substring(0, 2));
    var to = squareFromString(moveString.substring(2, 4));

    if (!squareIsOK(from) || !squareIsOK(to)) {
        return null;
    }

    if (moveString.length >= 5) {
        var promotion = pieceFromString(moveString.charAt(4));
        if (promotion != -1) {
            promotion = pieceMake(board.sideToMove, pieceType(promotion));
            return moveMakePromotion(from, to, promotion);
        }
    }

    if (pieceType(board.board[from]) == KING && squareDistance(from, to) > 1) {
        return moveMakeCastle(from, to);
    }

    if (pieceType(board.board[from]) == PAWN && to == board.epSquare) {
        return moveMakeEp(from, to);
    }

    return moveMake(from, to);
};

chess.moveFromUCI = moveFromUCI;



//////////////////////////////////
// Boards
//////////////////////////////////

// Zobrist key arrays

var zob = randIntArray(64 * (BK + 1));
var zobEp = randIntArray(16);
var zobCastle = randIntArray(16);
var zobWTM = randInt();


// Constructor. Creates an empty board.

chess.Board = function () {
    this.parent = null;
    this.sideToMove = COLOR_WHITE;
    this.epSquare = SQUARE_NONE;
    this.castleRights = [false, false, false, false];
    this.rule50Counter = 0;
    this.gamePly = 0;
    this.lastMove = null;
    this.key = 0;
    this.board = new Array(256);
    for (var s = 0; s < 256; s++) { this.board[s] = OUTSIDE; }
    for (var i = 0; i < 64; i++) { this.board[squareExpand(i)] = EMPTY; }
    this.kingSquares = [SQUARE_NONE, SQUARE_NONE];
    this.pieceCount = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
};

var Board = chess.Board;

// Initialize a new board object from a FEN string.
Board.fromFEN = function (fen) {

    var isDigit = function (c) {
        var r = /^\d$/;
        return r.test(c);
    };

    var b = new Board();

    var components = fen.split(" ");
    var rank, file, s, i;

    // Board
    s = components[0];
    rank = RANK_MAX;
    file = FILE_MIN;
    for (i = 0; i < s.length; i++) {
        if (isDigit(s[i])) { // Skip the given number of files
            file += s.charCodeAt(i) - "1".charCodeAt(0) + 1;
        } else if (s[i] === "/") { // Move to the next rank
            file = FILE_MIN;
            rank--;
        } else { // Must be a piece, unless the FEN string is broken
	          b.putPiece(pieceFromString(s[i]), squareMake(file, rank));
            file++;
        }
    }

    // Side to move
    if (components.length > 1) {
        s = components[1];
        if (s[0] === "b") {
            b.sideToMove = COLOR_BLACK;
        }
    }

    // Castle rights
    if (components.length > 2) {
        s = components[2];
        for (i = 0; i < s.length; i++) {
            var index = "KQkq".indexOf(s[i]);
            if (index != -1) {
                b.castleRights[index] = true;
            }
        }
    }

    // En passant square
    if (components.length > 3 && components[3] !== "-") {
        b.epSquare = squareFromString(components[3]);
    }

    b.findCheckers();

    // Zobrist key
    b.key = b.computeKey();

    return b;
};


// Initialize a new board with the standard starting position
Board.startpos = function () {
    return Board.fromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
};

// The piece on the given square.
Board.prototype.pieceOn = function (square) {
    return this.board[squareExpand(square)];
};

// Whether a given square is empty.
Board.prototype.isEmpty = function (square) {
    return this.pieceOn(square) == EMPTY;
};

// The current side to move.
Board.prototype.getSideToMove = function () {
    return this.sideToMove;
};

// Export the position to a string in Forsyth-Edwards notation.
Board.prototype.toFEN = function () {
    var buffer = "";

    // Board
    for (var rank = RANK_MAX; rank >= RANK_MIN; rank--) {
        var emptySquareCount = 0;
        for (var file = FILE_MIN; file <= FILE_MAX; file++) {
            var piece = this.board[squareMake(file, rank)];
            if (piece === EMPTY) {
                emptySquareCount++;
            } else {
                if (emptySquareCount > 0) {
                    buffer += emptySquareCount;
                }
                buffer += pieceToString(piece);
                emptySquareCount = 0;
            }
        }
        if (emptySquareCount > 0) {
            buffer += emptySquareCount;
        }
        buffer += rank > RANK_MIN ? "/" : " ";
    }

    // Side to move
    buffer += colorToString(this.sideToMove) + " ";

    // Castle rights
    if (!this.castleRights[0] && !this.castleRights[1] &&
        !this.castleRights[2] && !this.castleRights[3]) {
        buffer += "- ";
    } else {
        if (this.castleRights[0]) { buffer += "K"; }
        if (this.castleRights[1]) { buffer += "Q"; }
        if (this.castleRights[2]) { buffer += "k"; }
        if (this.castleRights[3]) { buffer += "q"; }
        buffer += " ";
    }

    // En passant square
    if (this.epSquare === SQUARE_NONE) {
        buffer += "- ";
    } else {
        buffer += squareToString(this.epSquare) + " ";
    }

    // Halfmove clock
    buffer += this.rule50Counter + " ";

    // Move number
    buffer += Math.floor(this.gamePly / 2) + 1;

    return buffer;
};


// Print the board in ASCII format to the console, for debugging.
Board.prototype.print = function () {
    var pieceStrings = [
        "| ? ", "| P ", "| N ", "| B ", "| R ", "| Q ", "| K ", "| ? ",
        "| ? ", "|=P=", "|=N=", "|=B=", "|=R=", "|=Q=", "|=K="
    ];
    for (var rank = RANK_MAX; rank >= RANK_MIN; rank--) {
        console.log("+---+---+---+---+---+---+---+---+");
        var line = "";
        for (var file = FILE_MIN; file <= FILE_MAX; file++) {
            var square = squareMake(file, rank);
            var piece = this.board[square];
            if (piece === EMPTY) {
                line += ((file + rank) % 2 === 0 ? "|   " : "| . ");
            } else {
                line += pieceStrings[piece];
            }
        }
        console.log(line + "|");
    }
    console.log("+---+---+---+---+---+---+---+---+");
    console.log(this.toFEN());
};


// Add a piece to the given square.
Board.prototype.putPiece = function (piece, square) {
    this.board[square] = piece;
    this.pieceCount[piece]++;
    if (pieceType(piece) === KING) {
        this.kingSquares[pieceColor(piece)] = square;
    }
};


// Remove the piece on the given square.
Board.prototype.removePiece = function (square) {
    this.pieceCount[this.board[square]]--;
    this.board[square] = EMPTY;
};


// Move the piece on 'from' to 'to'. The 'to' square must be empty. In the case
// of captures, removePiece must be called on the 'to' square before movePiece
// is called.
Board.prototype.movePiece = function (from, to) {
    var piece = this.board[from];
    this.board[to] = piece;
    this.board[from] = EMPTY;
    if (pieceType[piece] === KING) {
        this.kingSquares[pieceColor(piece)] = to;
    }
};


var stepAttackTable = function (deltas) {
    var result = new Array(256);
    var b = new Board();
    for (var i = 0; i < 256; i++) { result[i] = 0; }

    for (var file = FILE_MIN; file <= FILE_MAX; file++) {
        for (var rank = RANK_MIN; rank <= RANK_MAX; rank++) {
            var s0 = squareMake(file, rank);
            for (i = 0; i < deltas.length; i++) {
                var s1 = s0 + deltas[i];
                if (b.board[s1] != OUTSIDE) {
                    result[s1 - s0 + 128] = deltas[i];
                }
            }
        }
    }
    return result;
};


var slideAttackTable = function (deltas) {
    var result = new Array(256);
    var b = new Board();

    for (var i = 0; i < 256; i++) { result[i] = 0; }

    for (var file = FILE_MIN; file <= FILE_MAX; file++) {
        for (var rank = RANK_MIN; rank <= RANK_MAX; rank++) {
            var s0 = squareMake(file, rank);
            for (i = 0; i < deltas.length; i++) {
                var d = deltas[i];
                for (var s1 = s0 + d; b.board[s1] != OUTSIDE; s1 += d) {
                    result[s1 - s0 + 128] = d;
                }
            }
        }
    }
    return result;
};


var KNIGHT_DIRS = [
    DELTA_NNW, DELTA_NNE, DELTA_NWW, DELTA_NEE,
    DELTA_SSW, DELTA_SSE, DELTA_SWW, DELTA_SEE
];
var BISHOP_DIRS = [DELTA_NW, DELTA_NE, DELTA_SW, DELTA_SE];
var ROOK_DIRS = [DELTA_N, DELTA_S, DELTA_W, DELTA_E];
var QUEEN_DIRS = [
    DELTA_N, DELTA_S, DELTA_W, DELTA_E,
    DELTA_NW, DELTA_NE, DELTA_SW, DELTA_SE
];
var knightAttackTable = stepAttackTable(KNIGHT_DIRS);
var bishopAttackTable = slideAttackTable(BISHOP_DIRS);
var rookAttackTable = slideAttackTable(ROOK_DIRS);
var queenAttackTable = slideAttackTable(QUEEN_DIRS);
var kingAttackTable = stepAttackTable(QUEEN_DIRS);
var pawnAttackTable = [
    stepAttackTable([DELTA_NW, DELTA_NE]),
    stepAttackTable([DELTA_SW, DELTA_SE])
];


var colinearSquares = function (s0, s1, s2) {
    var d = queenAttackTable[s1 - s0 + 128];
    return d !== 0 && d == queenAttackTable[s2 - s1 + 128];
};


Board.prototype.pieceAttacksSquare = function (from, to) {
    var piece = this.board[from];
    var type = pieceType(piece);
    var d;

    if (type == PAWN) {
        return pawnAttackTable[pieceColor(piece)][to - from + 128] !== 0;
    } else if (type == KNIGHT) {
        return knightAttackTable[to - from + 128] !== 0;
    } else if (type == BISHOP) {
        d = bishopAttackTable[to - from + 128];
        return (d !== 0 && this.squaresBetweenAreEmpty(from, to, d));
    } else if (type == ROOK) {
        d = rookAttackTable[to - from + 128];
        return (d !== 0 && this.squaresBetweenAreEmpty(from, to, d));
    } else if (type == QUEEN) {
        d = queenAttackTable[to - from + 128];
        return (d !== 0 && this.squaresBetweenAreEmpty(from, to, d));
    } else if (type == KING) {
        return kingAttackTable[to - from + 128] !== 0;
    }
    return false;
};


Board.prototype.slowFindCheckers = function () {
    var us = this.sideToMove;
    var them = oppositeColor(us);
    var ksq = this.kingSquares[this.sideToMove];
    var sq, i, d;

    this.checkers = [SQUARE_NONE, SQUARE_NONE];
    this.checkCount = 0;

    if (ksq != SQUARE_NONE) {

        // Pawn checks
        sq = ksq + pawnPush(us) + DELTA_E;
        if (this.board[sq] == pieceMake(them, PAWN)) {
            this.checkers[this.checkCount++] = sq;
        }
        sq = ksq + pawnPush(us) + DELTA_W;
        if (this.board[sq] == pieceMake(them, PAWN)) {
            this.checkers[this.checkCount++] = sq;
        }

        // Knight checks
        for (i = 0; i < 8; i++) {
            d = KNIGHT_DIRS[i];
            sq = ksq + d;
            if (this.board[sq] == pieceMake(them, KNIGHT)) {
                this.checkers[this.checkCount++] = sq;
            }
        }

        // Bishop/queen checks
        for (i = 0; i < 8; i++) {
            d = BISHOP_DIRS[i];
            for (sq = ksq + d; this.board[sq] == EMPTY; sq += d) { }
            if (this.board[sq] == pieceMake(them, BISHOP) ||
                this.board[sq] == pieceMake(them, QUEEN)) {
                this.checkers[this.checkCount++] = sq;
            }
        }

        // Rook/queen checks
        for (i = 0; i < 8; i++) {
            d = ROOK_DIRS[i];
            for (sq = ksq + d; this.board[sq] == EMPTY; sq += d) { }
            if (this.board[sq] == pieceMake(them, ROOK) ||
                this.board[sq] == pieceMake(them, QUEEN)) {
                this.checkers[this.checkCount++] = sq;
            }
        }
    }
};


Board.prototype.findCheckers = function () {

    var move = this.lastMove;

    if (move === undefined || moveIsCastle(move) || moveIsEP(move)) {
        return this.slowFindCheckers();
    }

    var us = this.sideToMove, them = oppositeColor(us);
    var ksq = this.kingSquares[us];
    var to = moveTo(move), from = moveFrom(move);
    var candidateCheckSquare, piece;

    this.checkers = [SQUARE_NONE, SQUARE_NONE];
    this.checkCount = 0;

    if (this.pieceAttacksSquare(to, ksq)) {
        this.checkers[this.checkCount++] = to;
    }
    var d = bishopAttackTable[from - ksq + 128];
    if (d !== 0) {
        if (this.squaresBetweenAreEmpty(ksq, from, d)) {
            candidateCheckSquare = this.scanSquare(from, d);
            piece = this.board[candidateCheckSquare];
            if (piece == pieceMake(them, BISHOP) ||
                piece == pieceMake(them, QUEEN)) {
                this.checkers[this.checkCount++] = candidateCheckSquare;
            }
        }
    } else {
        d = rookAttackTable[from - ksq + 128];
        if (d !== 0) {
            if (this.squaresBetweenAreEmpty(ksq, from, d)) {
                candidateCheckSquare = this.scanSquare(from, d);
                piece = this.board[candidateCheckSquare];
                if (piece == pieceMake(them, ROOK) ||
                    piece == pieceMake(them, QUEEN)) {
                    this.checkers[this.checkCount++] = candidateCheckSquare;
                }
            }
        }
    }
};


Board.prototype.checkingPieces = function () {
    var result = [];
    for (var i = 0; i < this.checkCount; i++) {
        result.push(squareCompress(this.checkers[i]));
    }
    return result;
};


Board.prototype.isCheck = function () {
    return this.checkCount > 0;
};


Board.prototype.doMove = function (move) {
    var result = new Board();

    result.parent = this;
    result.sideToMove = oppositeColor(this.sideToMove);
    result.epSquare = SQUARE_NONE;
    result.castleRights = this.castleRights.slice(0);
    result.rule50Counter = this.rule50Counter + 1;
    result.gamePly = this.gamePly + 1;
    result.lastMove = move;
    result.key = 0;
    result.board = this.board.slice(0);
    result.kingSquares = this.kingSquares.slice(0);
    result.pieceCount = this.pieceCount.slice(0);

    var us = this.sideToMove, them = result.sideToMove;
    var from = moveFrom(move), to = moveTo(move);

    // In case of a capture, remove captured piece and reset rule 50 counter:
    if (result.board[to] != EMPTY) {
        result.removePiece(to);
        result.rule50Counter = 0;
    } else if (moveIsEP(move)) {
        result.removePiece(to - pawnPush(us));
        result.rule50Counter = 0;
    }

    // If this is a pawn move, reset rule 50 counter:
    if (pieceType(this.board[from]) == PAWN) {
        result.rule50Counter = 0;
        if (Math.abs(to - from) == DELTA_N + DELTA_N) {
            if (this.board[to + DELTA_W] == pieceMake(them, PAWN) ||
                this.board[to + DELTA_E] == pieceMake(them, PAWN)) {
                result.epSquare = (to + from) / 2;
            }
        }
    }

    // In case of a promotion, remove the pawn, and add the promotion piece.
    var prom = movePromotion(move);
    if (prom) {
        result.removePiece(from);
        result.putPiece(prom, to);
    } else {
        // For other moves, simply move the piece.
        result.movePiece(from, to);
    }

    // If the moving piece is a king, update king square.
    if (pieceType(this.board[from]) == KING) {
        result.kingSquares[us] = to;
        result.prohibitKingsideCastling(us);
        result.prohibitQueensideCastling(us);
    }

    // For castling moves, also move the rook.
    if (moveIsCastle(move)) {
        if (moveIsKingsideCastle(move)) {
            result.movePiece(
                squareMake(FILE_MAX, RANK_MIN + 7 * us),
                squareMake(FILE_MAX - 2, RANK_MIN + 7 * us));
        } else {
            result.movePiece(
                squareMake(FILE_MIN, RANK_MIN + 7 * us),
                squareMake(FILE_MIN + 3, RANK_MIN + 7 * us));
        }
    }

    // Prohibit castling after moves to/from corner squares.
    if (squareFile(from) == FILE_MIN && squareRank(from) == RANK_MIN) {
        result.prohibitQueensideCastling(COLOR_WHITE);
    }
    if (squareFile(from) == FILE_MAX && squareRank(from) == RANK_MIN) {
        result.prohibitKingsideCastling(COLOR_WHITE);
    }
    if (squareFile(from) == FILE_MIN && squareRank(from) == RANK_MAX) {
        result.prohibitQueensideCastling(COLOR_BLACK);
    }
    if (squareFile(from) == FILE_MAX && squareRank(from) == RANK_MAX) {
        result.prohibitKingsideCastling(COLOR_BLACK);
    }
    if (squareFile(to) == FILE_MIN && squareRank(to) == RANK_MIN) {
        result.prohibitQueensideCastling(COLOR_WHITE);
    }
    if (squareFile(to) == FILE_MAX && squareRank(to) == RANK_MIN) {
        result.prohibitKingsideCastling(COLOR_WHITE);
    }
    if (squareFile(to) == FILE_MIN && squareRank(to) == RANK_MAX) {
        result.prohibitQueensideCastling(COLOR_BLACK);
    }
    if (squareFile(to) == FILE_MAX && squareRank(to) == RANK_MAX) {
        result.prohibitKingsideCastling(COLOR_BLACK);
    }

    result.findCheckers();

    result.key = result.computeKey();

    return result;
};


Board.prototype.squaresBetweenAreEmpty = function (s0, s1, d) {
    for (var s2 = s0 + d; this.board[s2] == EMPTY && s2 != s1; s2 += d) { }
    return s2 == s1;
};


Board.prototype.scan = function (s0, d) {
    for (var s1 = s0 + d; this.board[s1] == EMPTY; s1 += d) { }
    return this.board[s1];
};


Board.prototype.scanSquare = function (s0, d) {
    for (var s1 = s0 + d; this.board[s1] == EMPTY; s1 += d) { }
    return s1;
};


Board.prototype.squareIsAttacked = function (square, color) {
    if (this.board[square-pawnPush(color)+DELTA_E] == pieceMake(color, PAWN)) {
        return true;
    }
    if (this.board[square-pawnPush(color)+DELTA_W] == pieceMake(color, PAWN)) {
        return true;
    }

    var i;

    for (i = 0; i < 8; i++) {
        if (this.board[square + KNIGHT_DIRS[i]] == pieceMake(color, KNIGHT)) {
            return true;
        }
    }

    for (i = 0; i < 8; i++) {
        if (this.board[square + QUEEN_DIRS[i]] == pieceMake(color, KING)) {
            return true;
        }
    }

    var bishop = pieceMake(color, BISHOP);
    var rook = pieceMake(color, ROOK);
    var queen = pieceMake(color, QUEEN);

    for (i = 0; i < 4; i++) {
        var candidate = this.scan(square, BISHOP_DIRS[i]);
        if (candidate == bishop || candidate == queen) {
            return true;
        }
        candidate = this.scan(square, ROOK_DIRS[i]);
        if (candidate == rook || candidate == queen) {
            return true;
        }
    }
    return false;
};


Board.prototype.isPinned = function (square) {
    var us = pieceColor(this.board[square]);
    var them = oppositeColor(us);
    var bishop = pieceMake(them, BISHOP);
    var rook = pieceMake(them, ROOK);
    var queen = pieceMake(them, QUEEN);
    var ksq = this.kingSquares[us];
    var candidatePinner;

    var d = bishopAttackTable[square - ksq + 128];
    if (d !== 0) {
        if (this.squaresBetweenAreEmpty(ksq, square, d)) {
            candidatePinner = this.scan(square, d);
            if (candidatePinner == bishop || candidatePinner == queen) {
                return d;
            }
        }
    } else {
        d = rookAttackTable[square - ksq + 128];
        if (d !== 0) {
            if (this.squaresBetweenAreEmpty(ksq, square, d)) {
                candidatePinner = this.scan(square, d);
                if (candidatePinner == rook || candidatePinner == queen) {
                    return d;
                }
            }
        }
    }
    return false;
};


Board.prototype.generateMovesFrom = function (square) {
    var us = this.sideToMove;
    var piece = this.board[square];

    if (pieceColor(piece) != us) {
        return [];
    } else if (pieceType(piece) == PAWN) {
        return this.generatePawnMovesFrom(square);
    } else if (pieceType(piece) == KNIGHT) {
        return this.generateKnightMovesFrom(square);
    } else if (pieceType(piece) == BISHOP) {
        return this.generateBishopMovesFrom(square);
    } else if (pieceType(piece) == ROOK) {
        return this.generateRookMovesFrom(square);
    } else if (pieceType(piece) == QUEEN) {
        return this.generateQueenMovesFrom(square);
    } else {
        return this.generateKingMovesFrom(square);
    }
};


Board.prototype.generatePawnMovesFrom = function (square) {
    var pin = Math.abs(this.isPinned(square));

    if (pin == DELTA_E) {
        return [];
    } else {
        var us = this.sideToMove;
        var them = oppositeColor(us);
        var rank = squareRank(square);
        var pawnRank = us == COLOR_WHITE ? rank : RANK_MAX + RANK_MIN - rank;
        var push = pawnPush(us);
        var result = [];
        var d, p;

        if (!pin || pin == DELTA_N) {
            if (pawnRank == RANK_MIN + 1) {
                if (this.board[square + push] == EMPTY) {
                    result.push(moveMake(square, square + push));
                    if (this.board[square + 2 * push] == EMPTY) {
                        result.push(moveMake(square, square + 2 * push));
                    }
                }
            } else if (pawnRank == RANK_MAX - 1) {
                if (this.board[square + push] == EMPTY) {
                    for (p = pieceMake(us, QUEEN); p >= pieceMake(us, KNIGHT); p--) {
                        result.push(moveMakePromotion(square, square + push, p));
                    }
                }
            } else {
                if (this.board[square + push] == EMPTY) {
                    result.push(moveMake(square, square + push));
                }
            }
        }

        d = push + DELTA_E;
        if (!pin || Math.abs(d) == pin) {
            if (pieceColor(this.board[square + d]) == them) {
                if (pawnRank == RANK_MAX - 1) {
                    for (p = pieceMake(us, QUEEN); p >= pieceMake(us, KNIGHT); p--) {
                        result.push(moveMakePromotion(square, square + d, p));
                    }
                } else {
                    result.push(moveMake(square, square + d, p));
                }
            }
        }
        d = push + DELTA_W;
        if (!pin || Math.abs(d) == pin) {
            if (pieceColor(this.board[square + d]) == them) {
                if (pawnRank == RANK_MAX - 1) {
                    for (p = pieceMake(us, QUEEN); p >= pieceMake(us, KNIGHT); p--) {
                        result.push(moveMakePromotion(square, square + d, p));
                    }
                } else {
                    result.push(moveMake(square, square + d, p));
                }
            }
        }

        if (this.epSquare != SQUARE_NONE) {
            if (this.pieceAttacksSquare(square, this.epSquare)) {
                var tmp1 = this.board[square];
                var capsq = squareMake(squareFile(this.epSquare), squareRank(square));
                var tmp2 = this.board[capsq];
                this.board[square] = EMPTY;
                this.board[capsq] = EMPTY;
                this.board[this.epSquare] = tmp1;
                if (!this.squareIsAttacked(this.kingSquares[us], them)) {
                    result.push(moveMakeEp(square, this.epSquare));
                }
                this.board[this.epSquare] = EMPTY;
                this.board[capsq] = tmp2;
                this.board[square] = tmp1;
            }
        }

        return result;
    }
};

Board.prototype.generateKnightMovesFrom = function (square) {
    if (this.isPinned(square)) {
        return [];
    } else {
        var result = [];
        var them = oppositeColor(this.sideToMove);

        for (var i = 0; i < 8; i++) {
            var d = KNIGHT_DIRS[i];
            if (this.board[square + d] == EMPTY ||
                pieceColor(this.board[square + d]) == them) {
                result.push(moveMake(square, square + d));
            }
        }
        return result;
    }
};


Board.prototype.generateBishopMovesFrom = function (square) {
    var pin = Math.abs(this.isPinned(square));

    if (pin == DELTA_N || pin == DELTA_E) {
        return [];
    } else {
        var result = [];
        var them = oppositeColor(this.sideToMove);
        for (var i = 0; i < 4; i++) {
            var d = BISHOP_DIRS[i];
            if (!pin || pin == Math.abs(d)) {
                var s;
                for (s = square + d; this.board[s] == EMPTY; s += d) {
                    result.push(moveMake(square, s));
                }
                if (pieceColor(this.board[s]) == them) {
                    result.push(moveMake(square, s));
                }
            }
        }
        return result;
    }
};


Board.prototype.generateRookMovesFrom = function (square) {
    var pin = Math.abs(this.isPinned(square));

    if (pin == DELTA_NE || pin == DELTA_NW) {
        return [];
    } else {
        var result = [];
        var them = oppositeColor(this.sideToMove);
        for (var i = 0; i < 4; i++) {
            var d = ROOK_DIRS[i];
            if (!pin || pin == Math.abs(d)) {
                for (var s = square + d; this.board[s] == EMPTY; s += d) {
                    result.push(moveMake(square, s));
                }
                if (pieceColor(this.board[s]) == them) {
                    result.push(moveMake(square, s));
                }
            }
        }
        return result;
    }
};


Board.prototype.generateQueenMovesFrom = function (square) {
    var pin = Math.abs(this.isPinned(square));
    var result = [];
    var them = oppositeColor(this.sideToMove);
    for (var i = 0; i < 8; i++) {
        var d = QUEEN_DIRS[i];
        if (!pin || pin == Math.abs(d)) {
            for (var s = square + d; this.board[s] == EMPTY; s += d) {
                result.push(moveMake(square, s));
            }
            if (pieceColor(this.board[s]) == them) {
                result.push(moveMake(square, s));
            }
        }
    }
    return result;
};


Board.prototype.canCastleKingside = function (color) {
    return this.castleRights[color * 2];
};


Board.prototype.canCastleQueenside = function (color) {
    return this.castleRights[1 + color * 2];
};


Board.prototype.prohibitKingsideCastling = function (color) {
    this.castleRights[color * 2] = false;
};


Board.prototype.prohibitQueensideCastling = function (color) {
    this.castleRights[1 + color * 2] = false;
};


Board.prototype.generateKingMovesFrom = function (square) {
    var result = [];
    var them = oppositeColor(this.sideToMove);

    for (var i = 0; i < 8; i++) {
        var d = QUEEN_DIRS[i];
        if ((this.board[square + d] == EMPTY ||
             pieceColor(this.board[square + d]) == them) &&
            !this.squareIsAttacked(square + d, them))   {
            result.push(moveMake(square, square + d));
        }
    }

    if (this.canCastleKingside(this.sideToMove)) {
        if (this.board[square + 1] == EMPTY && this.board[square + 2] == EMPTY &&
            !this.squareIsAttacked(square + 1, them) &&
            !this.squareIsAttacked(square + 2, them)) {
            result.push(moveMakeCastle(square, square + 2));
        }
    }
    if (this.canCastleQueenside(this.sideToMove)) {
        if (this.board[square - 1] == EMPTY && this.board[square - 2] == EMPTY &&
            this.board[square - 3] == EMPTY &&
            !this.squareIsAttacked(square - 1, them) &&
            !this.squareIsAttacked(square - 2, them)) {
            result.push(moveMakeCastle(square, square - 2));
        }
    }
    return result;
};


Board.prototype.generateEvasionsFrom = function (square) {
    var us = this.sideToMove;
    var piece = this.board[square];

    if (pieceColor(piece) != us) {
        return [];
    } else if (pieceType(piece) == PAWN) {
        return this.generatePawnEvasionsFrom(square);
    } else if (pieceType(piece) == KNIGHT) {
        return this.generateKnightEvasionsFrom(square);
    } else if (pieceType(piece) == BISHOP) {
        return this.generateBishopEvasionsFrom(square);
    } else if (pieceType(piece) == ROOK) {
        return this.generateRookEvasionsFrom(square);
    } else if (pieceType(piece) == QUEEN) {
        return this.generateQueenEvasionsFrom(square);
    } else {
        return this.generateKingEvasionsFrom(square);
    }
};


Board.prototype.generatePawnEvasionsFrom = function (square) {
    if (this.checkCount > 1) {
        return [];
    } else if (this.isPinned(square)) {
        return [];
    } else {
        var us = this.sideToMove;
        var them = oppositeColor(us);
        var rank = squareRank(square);
        var pawnRank = us == COLOR_WHITE ? rank : RANK_MAX + RANK_MIN - rank;
        var push = pawnPush(us);
        var result = [];
        var chsq = this.checkers[0];
        var ksq = this.kingSquares[us];
        var p;

        // Captures can only be legal evasions if they capture the checking piece.
        if (this.pieceAttacksSquare(square, chsq)) {
            if (pawnRank < RANK_MAX - 1) {
                result.push(moveMake(square, chsq));
            } else {
                for (p = pieceMake(us, QUEEN); p >= pieceMake(us, KNIGHT); p--) {
                    result.push(moveMakePromotion(square, chsq, p));
                }
            }
        }

        if (isSlider(this.board[chsq]) && squareDistance(chsq, ksq) > 1) {
            if (this.board[square + push] == EMPTY) {
                if (colinearSquares(chsq, square + push, ksq)) {
                    if (pawnRank < RANK_MAX - 1) {
                        result.push(moveMake(square, square + push));
                    } else {
                        for (p = pieceMake(us, QUEEN); p >= pieceMake(us, KNIGHT); p--) {
                            result.push(moveMakePromotion(square, square + push, p));
                        }
                    }
                }
                if (pawnRank == RANK_MIN + 1 && this.board[square + 2*push] == EMPTY &&
                    colinearSquares(chsq, square + 2 * push, ksq)) {
                    result.push(moveMake(square, square + 2 * push));
                }
            }
        }

        if (this.epSquare != SQUARE_NONE) {
            if (this.pieceAttacksSquare(square, this.epSquare)) {
                var tmp1 = this.board[square];
                var capsq = squareMake(squareFile(this.epSquare), squareRank(square));
                var tmp2 = this.board[capsq];
                this.board[square] = EMPTY;
                this.board[capsq] = EMPTY;
                this.board[this.epSquare] = tmp1;
                if (!this.squareIsAttacked(this.kingSquares[us], them)) {
                    result.push(moveMakeEp(square, this.epSquare));
                }
                this.board[this.epSquare] = EMPTY;
                this.board[capsq] = tmp2;
                this.board[square] = tmp1;
            }
        }

        return result;
    }
};


Board.prototype.generateKnightEvasionsFrom = function (square) {
    if (this.checkCount > 1) {
        return [];
    } else if (this.isPinned(square)) {
        return [];
    } else {
        var chsq = this.checkers[0];
        var result = [];

        // Capture checking piece, if possible
        if (this.pieceAttacksSquare(square, chsq)) {
            result.push(moveMake(square, chsq));
        }

        var checkerType = pieceType(this.board[chsq]);
        var ksq = this.kingSquares[this.sideToMove];
        if (isSlider(checkerType) && squareDistance(chsq, ksq) > 1) {
            // Generate blocking evasions
            for (var i = 0; i < 8; i++) {
                var d = KNIGHT_DIRS[i];
                if (this.board[square + d] == EMPTY &&
                    colinearSquares(chsq, square + d, ksq)) {
                    result.push(moveMake(square, square + d));
                }
            }
        }
        return result;
    }
};


Board.prototype.generateBishopEvasionsFrom = function (square) {
    if (this.checkCount > 1) {
        return [];
    } else if (this.isPinned(square)) {
        return [];
    } else {
        var chsq = this.checkers[0];
        var result = [];

        // Capture checking piece, if possible
        if (this.pieceAttacksSquare(square, chsq)) {
            result.push(moveMake(square, chsq));
        }

        var checkerType = pieceType(this.board[chsq]);
        var ksq = this.kingSquares[this.sideToMove];
        if (isSlider(checkerType) && squareDistance(chsq, ksq) > 1) {
            // Generate blocking evasions
            for (var i = 0; i < 4; i++) {
                var d = BISHOP_DIRS[i];
                for (var to = square + d; this.board[to] == EMPTY; to += d) {
                    if (colinearSquares(chsq, to, ksq)) {
                        result.push(moveMake(square, to));
                    }
                }
            }
        }
        return result;
    }
};


Board.prototype.generateRookEvasionsFrom = function (square) {
    if (this.checkCount > 1) {
        return [];
    } else if (this.isPinned(square)) {
        return [];
    } else {
        var chsq = this.checkers[0];
        var result = [];

        // Capture checking piece, if possible
        if (this.pieceAttacksSquare(square, chsq)) {
            result.push(moveMake(square, chsq));
        }

        var checkerType = pieceType(this.board[chsq]);
        var ksq = this.kingSquares[this.sideToMove];
        if (isSlider(checkerType) && squareDistance(chsq, ksq) > 1) {
            // Generate blocking evasions
            for (var i = 0; i < 4; i++) {
                var d = ROOK_DIRS[i];
                for (var to = square + d; this.board[to] == EMPTY; to += d) {
                    if (colinearSquares(chsq, to, ksq)) {
                        result.push(moveMake(square, to));
                    }
                }
            }
        }
        return result;
    }
};


Board.prototype.generateQueenEvasionsFrom = function (square) {
    if (this.checkCount > 1) {
        return [];
    } else if (this.isPinned(square)) {
        return [];
    } else {
        var chsq = this.checkers[0];
        var result = [];

        // Capture checking piece, if possible
        if (this.pieceAttacksSquare(square, chsq)) {
            result.push(moveMake(square, chsq));
        }

        var checkerType = pieceType(this.board[chsq]);
        var ksq = this.kingSquares[this.sideToMove];
        if (isSlider(checkerType) && squareDistance(chsq, ksq) > 1) {
            // Generate blocking evasions
            for (var i = 0; i < 8; i++) {
                var d = QUEEN_DIRS[i];
                for (var to = square + d; this.board[to] == EMPTY; to += d) {
                    if (colinearSquares(chsq, to, ksq)) {
                        result.push(moveMake(square, to));
                    }
                }
            }
        }
        return result;
    }
};


Board.prototype.generateKingEvasionsFrom = function (square) {
    // Temporarily remove king from the board, in order to correctly detect
    // X-ray attacks through the king's square:
    this.removePiece(square);

    var us = this.sideToMove, them = oppositeColor(us);
    var result = [];

    for (var i = 0; i < 8; i++) {
        var d = QUEEN_DIRS[i];
        var cap = this.board[square + d];
        if (cap == EMPTY || pieceColor(cap) == them) {
            if (!this.squareIsAttacked(square + d, them)) {
                result.push(moveMake(square, square + d));
            }
        }
    }

    // Place the king back on the board
    this.putPiece(pieceMake(us, KING), square);

    return result;
};


Board.prototype.movesFrom = function (square) {
    if (this.isCheck()) {
        return this.generateEvasionsFrom(square);
    } else {
        return this.generateMovesFrom(square);
    }
};


Board.prototype.moves = function () {
    var result = [];
    for (var f = FILE_MIN; f <= FILE_MAX; f++) {
        for (var r = RANK_MIN; r <= RANK_MAX; r++) {
            result = result.concat(this.movesFrom(squareMake(f, r)));
        }
    }
    return result;
};


Board.prototype.perft = function (depth) {
    if (depth === 0) {
        return 1;
    } else if (depth == 1) {
        return this.moves().length;
    } else {
        var ms = this.moves();
        var result = 0;
        for (var i = 0; i < ms.length; i++) {
            result += this.doMove(ms[i]).perft(depth - 1);
        }
        return result;
    }
};


Board.prototype.divide = function (depth) {
    var ms = this.moves();
    var acc = 0;
    for (var i = 0; i < ms.length; i++) {
        var b = this.doMove(ms[i]);
        var p = b.perft(depth - 1);
        acc += p;
        console.log(moveToUCI(ms[i]) + " " + p + " " + acc);
    }
};


Board.prototype.isMate = function () {
    return this.isCheck && this.moves().length === 0;
};

Board.prototype.isStalemate = function () {
    return !this.isCheck && this.moves().length === 0;
};

Board.prototype.isRule50Draw = function () {
    return this.rule50Counter >= 100;
};

Board.prototype.isMaterialDraw = function () {
    return this.pieceCount[WP] + this.pieceCount[WR] + this.pieceCount[WQ] +
        this.pieceCount[BP] + this.pieceCount[BR] + this.pieceCount[BQ] === 0 &&
        this.pieceCount[WN] + this.pieceCount[WB] + this.pieceCount[BN] +
        this.pieceCount[BB] <= 1;
};

Board.prototype.grandParent = function () {
    return this.parent === null ? null : this.parent.parent;
};

Board.prototype.isRepetitionDraw = function () {
    var repetitionCount = 1;
    for (var b = this.grandParent(); b !== null; b = b.grandParent()) {
        if (b.key == this.key) {
            repetitionCount++;
        }
    }
    return repetitionCount >= 3;
};

Board.prototype.isDraw = function () {
    return this.isRule50Draw() || this.isMaterialDraw() || this.isStalemate() || this.isRepetitionDraw();
};

Board.prototype.isTerminal = function () {
    return this.isMate() || this.isDraw();
};

Board.prototype.movesForPieceTypeToSquare = function (piece, square) {
    var ms = this.moves();
    var result = [];
    for (var i = 0; i < ms.length; i++) {
        if (moveTo(ms[i]) == square &&
            pieceType(this.board[moveFrom(ms[i])] == pieceType(piece))) {
            result.push(ms[i]);
        }
    }
    return result;
};


Board.prototype.moveToSAN = function (move) {
    var from = moveFrom(move), to = moveTo(move);
    var promotion = movePromotion(move);
    var piece = this.board[from];
    var newBoard = this.doMove(move);
    var isCheck = newBoard.isCheck();
    var isMate = newBoard.isMate();
    var result = "";

    if (moveIsKingsideCastle(move)) {
        result += "O-O";
    } else if (moveIsQueensideCastle(move)) {
        result += "O-O-O";
    } else if (pieceType(piece) == PAWN) {
        if (squareFile(from) != squareFile(to)) { // Capture
            result += fileToString(squareFile(from)) + "x";
        }
        result += squareToString(to);
        if (promotion) {
            result += "=" + pieceToString(promotion).toUpperCase();
        }
    } else {
        result += pieceToString(piece).toUpperCase();
        var moves = this.movesForPieceTypeToSquare(piece, to);
        if (moves.length > 1) {
            // Several matching moves, need disambiguation character(s)
            var file = squareFile(from), rank = squareRank(from);
            var sameFileCount = 0, sameRankCount = 0;
            for (var i = 0; i < moves.length; i++) {
                if (squareFile(moveFrom(moves[i])) == file) {
                    sameFileCount++;
                }
                if (squareRank(moveFrom(moves[i])) == rank) {
                    sameRankCount++;
                }
            }
            if (sameFileCount == 1) {
                result += fileToString(file);
            } else if (sameRankCount == 1) {
                result += rankToString(rank);
            } else {
                result += squareToString(from);
            }
        }
        if (this.board[to] != EMPTY) {
            result += "x";
        }
        result += squareToString(to);
    }
    if (isMate) {
        result += "#";
    } else if (isCheck) {
        result += "+";
    }
    return result;
};


Board.prototype.lastMoveToSAN = function () {
    return this.parent ? this.parent.moveToSAN(this.lastMove) : null;
};

Board.prototype.moveFromSAN = function (str) {
    var moves = this.moves();
    var i;

    // Castling
    if (str.length >= 5 && str.substring(0, 5) == "O-O-O") {
        for (i = 0; i < moves.length; i++) {
            if (moveIsQueensideCastle(moves[i])) {
                return moves[i];
            }
        }
        return null;
    } else if (str.length >= 3 && str.substring(0, 3) == "O-O") {
        for (i = 0; i < moves.length; i++) {
            if (moveIsKingsideCastle(moves[i])) {
                return moves[i];
            }
        }
        return null;
    }

    // Normal moves

    // Remove undesired characters.
    var s = str.replace("x", "").replace("+", "").replace("#", "").replace("=", "").replace("-", "");

    var left = 0, right = s.length - 1;
    var pt = -1, promotion = -1;
    var fromFile = FILE_NONE, fromRank = RANK_NONE;
    var from, to;

    // Promotion?
    promotion = pieceFromString(s[right]);
    if (promotion != -1) {
        promotion = pieceMake(this.sideToMove, promotion);
        right--;
    }

    // Moving piece
    if (left < right) {
        pt = pieceFromString(s[left]); //"NBRKQ".indexOf(s.substring(left, left + 1))
        if (pt != -1 && pt < 8) {
            left++;
        } else {
            pt = PAWN;
        }
    }

    // Destination square
    if (left < right) {
        to = squareFromString(s.substring(right - 1, right + 1));
        if (to == SQUARE_NONE) {
            return null;
        }
        right -= 2;
    } else {
        return null;
    }

    // Find the file and/or rank of the from square
    if (left <= right) {
        fromFile = fileFromString(s[left]);
        if (fromFile != -1) {
            left++;
        }
        if (left <= right) {
            fromRank = rankFromString(s[left]);
        }
    }

    // Look for matching moves
    var move = null;
    var matches = 0;
    for (i = 0; i < moves.length; i++) {
        var m = moves[i];
        var match = true;
        if (pieceType(this.board[moveFrom(m)]) != pt) {
            match = false;
        } else if (moveTo(m) != to) {
            match = false;
        } else if (promotion != -1 && promotion != movePromotion(m)) {
            match = false;
        } else if (fromFile != FILE_NONE && fromFile != squareFile(moveFrom(m))) {
            match = false;
        } else if (fromRank != RANK_NONE && fromRank != squareRank(moveFrom(m))) {
            match = false;
        }
        if (match) {
            move = m;
            matches++;
        }
    }

    return matches == 1 ? move : null;
};


Board.prototype.doSANMove = function (san) {
    var move = this.moveFromSAN(san);
    if (move === null) {
        this.print();
        console.log("Illegal or ambiguous move: " + san);
        return null;
    } else {
        return this.doMove(move);
    }
};


Board.prototype.computeKey = function () {
    var result = 0;

    // Board
    for (var i = 0; i < 64; i++) {
        var p = this.board[squareExpand(i)];
        if (p != EMPTY) {
            result ^= zob[p * 64 + i];
        }
    }

    // Castle rights
    var cf = this.castleRights[0] ? 1 : 0;
    cf |= this.castleRights[1] ? 2 : 0;
    cf |= this.castleRights[2] ? 4 : 0;
    cf |= this.castleRights[3] ? 8 : 0;
    result ^= zobCastle[cf];

    // En passant
    if (this.epSquare != SQUARE_NONE) {
        result ^= zobEp[squareFile(this.epSquare)];
    }

    // Side to move
    if (this.sideToMove == COLOR_WHITE) {
        result ^= zobWTM;
    }

    return result;
};
