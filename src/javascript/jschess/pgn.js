goog.provide("jschess.pgn")

var pgn = jschess.pgn

pgn.TokenTypeString = 0
pgn.TokenTypeInteger = 1
pgn.TokenTypePeriod = 2
pgn.TokenTypeAsterisk = 3
pgn.TokenTypeLeftBracket = 4
pgn.TokenTypeRightBracket = 5
pgn.TokenTypeLeftParen = 6
pgn.TokenTypeRightParen = 7
pgn.TokenTypeLeftAngle = 8
pgn.TokenTypeRightAngle = 9
pgn.TokenTypeNAG = 10
pgn.TokenTypeSymbol = 11
pgn.TokenTypeComment = 12
pgn.TokenTypeLineComment = 13
pgn.TokenTypeEOF = 14

pgn.PGNToken = function (tokenType, tokenValue) {
    this.type = tokenType
    this.value = tokenValue
}

var PGNToken = pgn.PGNToken

PGNToken.prototype.terminatesGame = function () {
    if (this.type == pgn.TokenTypeAsterisk || this.type == pgn.TokenTypeEOF) {
        return true
    } else if (this.type = pgn.TokenTypeSymbol) {
        return this.value === "1-0" || this.value === "0-1" || this.value === "1/2-1/2"
    } else {
        return false
    }
}

var StringReader = function (string) {
    this.string = string
    this.index = 0
}

StringReader.prototype.peek = function () {
    return this.string[this.index]
}

StringReader.prototype.read = function () {
    return this.string[this.index++]
}

StringReader.prototype.unread = function () {
    this.index--;
}

var isDigit = function (c) {
    return "0123456789".indexOf(c) >= 0
}

var isSymbolStart = function (c) {
    return "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".indexOf(c) >= 0
}

var isSymbolCont = function (c) {
    return isSymbolStart(c) || "_+#=:-/".indexOf(c) >= 0
}

var isWhitespace = function (c) {
    return /\s/.test(c)
}

pgn.PGNReader = function (string) {
    this.stringReader = new StringReader(string)
}

var PGNReader = pgn.PGNReader

PGNReader.prototype.skipWhitespace = function () {
    for (var c = this.stringReader.read();
         isWhitespace(c);
         c = this.stringReader.read()) { }
    this.stringReader.unread()
}

PGNReader.prototype.readString = function () {
    var c = this.stringReader.read(), pc
    var result = ""

    for (pc = "", c = this.stringReader.read();
         true;
         pc = c, c = this.stringReader.read()) {
        if (c === undefined) {
            throw "Non-terminated string token"
        } else if (c === "\"") {
            if (pc === "\\") {
                result += c
            } else {
                break
            }
        } else if (c === "\\") {
            if (pc === "\\") {
                result += c
            }
        } else {
            result += c
        }
    }
    return new PGNToken(pgn.TokenTypeString, result)
}

PGNReader.prototype.readSymbol = function () {
    var c = this.stringReader.read()
    var type =  isDigit(c) ? pgn.TokenTypeInteger : pgn.TokenTypeSymbol
    var result = c

    for (c = this.stringReader.read();
         isSymbolCont(c);
         c = this.stringReader.read()) {
        result += c
        if (!isDigit(c)) {
            type = pgn.TokenTypeSymbol
        }
    }
    if (c !== undefined) {
        this.stringReader.unread(c)
    }
    return new PGNToken(type, result)
}

PGNReader.prototype.readPeriod = function () {
    var c = this.stringReader.read()
    return new PGNToken(pgn.TokenTypePeriod, c)
}

PGNReader.prototype.readAsterisk = function () {
    var c = this.stringReader.read()
    return new PGNToken(pgn.TokenTypeAsterisk, c)
}

PGNReader.prototype.readLeftBracket = function () {
    var c = this.stringReader.read()
    return new PGNToken(pgn.TokenTypeLeftBracket, c)
}

PGNReader.prototype.readRightBracket = function () {
    var c = this.stringReader.read()
    return new PGNToken(pgn.TokenTypeRightBracket, c)
}

PGNReader.prototype.readLeftParen = function () {
    var c = this.stringReader.read()
    return new PGNToken(pgn.TokenTypeLeftParen, c)
}

PGNReader.prototype.readRightParen = function () {
    var c = this.stringReader.read()
    return new PGNToken(pgn.TokenTypeRightParen, c)
}

PGNReader.prototype.readLeftAngle = function () {
    var c = this.stringReader.read()
    return new PGNToken(pgn.TokenTypeLeftAngle, c)
}

PGNReader.prototype.readRightAngle = function () {
    var c = this.stringReader.read()
    return new PGNToken(pgn.TokenTypeRightAngle, c)
}

PGNReader.prototype.readNAG = function () {
    var c = this.stringReader.read()
    var result = ""

    for (c = this.stringReader.read(); isDigit(c); c = this.stringReader.read()) {
        if (c === undefined) {
            break
        }
        result += c
    }
    if (c !== undefined) {
        this.stringReader.unread()
    }
    return new PGNToken(pgn.TokenTypeNAG, result)
}

PGNReader.prototype.readComment = function () {
    var c = this.stringReader.read()
    var result = ""

    for (c = this.stringReader.read(); c !== "}"; c = this.stringReader.read()) {
        if (c === undefined) {
            throw "Non-terminated comment"
        }
        result += c
    }
    return new PGNToken(pgn.TokenTypeComment, result)
}

PGNReader.prototype.readLineComment = function () {
    var c = this.stringReader.read()
    var result = ""

    for (c = this.stringReader.read(); c !== "\n"; c = this.stringReader.read()) {
        if (c === undefined) {
            throw "Non-terminated comment"
        }
        result += c
    }
    return new PGNToken(pgn.TokenTypeLineComment, result)
}

PGNReader.prototype.readToken = function () {
    this.skipWhitespace()
    var c = this.stringReader.peek()

    if (c === undefined) {
        return new PGNToken(pgn.TokenTypeEOF, "")
    } else if (c === "\"") {
        return this.readString()
    } else if (isSymbolStart(c)) {
        return this.readSymbol()
    } else if (c === ".") {
        return this.readPeriod()
    } else if (c === "*") {
        return this.readAsterisk()
    } else if (c === "[") {
        return this.readLeftBracket()
    } else if (c === "]") {
        return this.readRightBracket()
    } else if (c === "(") {
        return this.readLeftParen()
    } else if (c === ")") {
        return this.readRightParen()
    } else if (c === "<") {
        return this.readLeftAngle()
    } else if (c === ">") {
        return this.readRightAngle()
    } else if (c === "{") {
        return this.readComment()
    } else if (c === ";") {
        return this.readLineComment()
    } else if (c === "$") {
        return this.readNAG()
    } else {
        throw "Invalid character: " + c
    }
}

PGNReader.prototype.nextGame = function () {
    for (var token = this.readToken();
         token.type !== pgn.TokenTypeEOF;
         token = this.readToken()) {
        if (token.type === pgn.TokenTypeLeftBracket) {
            this.stringReader.unread()
            return true
        }
    }
    return false
}
