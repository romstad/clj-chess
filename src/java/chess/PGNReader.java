package chess;

import java.io.IOException;
import java.io.PushbackReader;

public class PGNReader {
    
    PushbackReader pbReader;

    public PGNReader(PushbackReader pr) {
        pbReader = pr;
    }

    static boolean isSymbolStart(char c) {
        return "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".indexOf(c) >= 0;
    }

    static boolean isSymbolCont(char c) {
        return isSymbolStart(c) || "_+#=:-/".indexOf(c) >= 0;
    }

    int peek() throws IOException {
        int result = pbReader.read();
        pbReader.unread(result);
        return result;
    }

    public void skipWhitespace() throws  IOException {
        int c;
        for (c = pbReader.read(); Character.isWhitespace(c); c = pbReader.read()) { }
        pbReader.unread(c);
    }

    public PGNToken readString() throws IOException, PGNException {
        int c = pbReader.read(), pc;
        assert((char)c == '\"');

        StringBuilder result = new StringBuilder(32);

        for (pc = 0, c = pbReader.read(); true; pc = c, c = pbReader.read()) {
            if (c == -1 || c == 0xFFFF) {
                throw new PGNException("Non-terminated string token");
            } else if ((char)c == '\"') {
                if ((char) pc == '\\') {
                    result.append((char)c);
                } else {
                    break;
                }
            } else if ((char)c == '\\') {
                if ((char)pc == '\\') {
                    result.append((char)c);
                }
            } else {
                result.append((char) c);
            }
        }
        return new PGNToken(PGNToken.TokenType.STRING, result.toString());
    }

    public PGNToken readSymbol() throws IOException {
        int c = pbReader.read();
        assert(isSymbolStart((char)c));

        PGNToken.TokenType type = Character.isDigit((char)c) ?
                PGNToken.TokenType.INTEGER : PGNToken.TokenType.SYMBOL;
        StringBuilder result = new StringBuilder();
        result.append((char)c);

        for (c = pbReader.read(); isSymbolCont((char)c); c = pbReader.read()) {
            result.append((char)c);
            if (!Character.isDigit((char)c)) {
                type = PGNToken.TokenType.SYMBOL;
            }
        }
        if (c != -1 && c != 0xFFFF) {
            pbReader.unread(c);
        }
        return new PGNToken(type, result.toString());
    }

    public PGNToken readPeriod() throws IOException {
        int c = pbReader.read();
        assert((char)c == '.');
        return new PGNToken(PGNToken.TokenType.PERIOD, ".");
    }

    public PGNToken readAsterisk() throws IOException {
        int c = pbReader.read();
        assert((char)c == '*');
        return new PGNToken(PGNToken.TokenType.ASTERISK, "*");
    }

    public PGNToken readLeftBracket() throws IOException {
        int c = pbReader.read();
        assert((char)c == '[');
        return new PGNToken(PGNToken.TokenType.LEFT_BRACKET, "[");
    }

    public PGNToken readRightBracket() throws IOException {
        int c = pbReader.read();
        assert((char)c == ']');
        return new PGNToken(PGNToken.TokenType.RIGHT_BRACKET, "]");
    }

    public PGNToken readLeftParen() throws IOException {
        int c = pbReader.read();
        assert((char)c == '(');
        return new PGNToken(PGNToken.TokenType.LEFT_PAREN, "(");
    }

    public PGNToken readRightParen() throws IOException {
        int c = pbReader.read();
        assert((char)c == ')');
        return new PGNToken(PGNToken.TokenType.RIGHT_PAREN, ")");
    }

    public PGNToken readLeftAngle() throws IOException {
        int c = pbReader.read();
        assert((char)c == '<');
        return new PGNToken(PGNToken.TokenType.LEFT_ANGLE, "<");
    }

    public PGNToken readRightAngle() throws IOException {
        int c = pbReader.read();
        assert((char)c == '>');
        return new PGNToken(PGNToken.TokenType.RIGHT_ANGLE, ">");
    }

    public PGNToken readNAG() throws IOException {
        int c = pbReader.read();
        assert((char)c == '$');
        StringBuilder sb = new StringBuilder();
        for (c = pbReader.read(); Character.isDigit((char)c); c = pbReader.read()) {
            if (c == -1 || c == 0xFFFF) {
                break;
            }
            sb.append((char)c);
        }
        if (c != -1 && c != 0xFFFF) {
            pbReader.unread(c);
        }
        return new PGNToken(PGNToken.TokenType.NAG, sb.toString());
    }

    public PGNToken readComment() throws IOException, PGNException {
        int c = pbReader.read();
        assert(c == '{');
        StringBuilder result = new StringBuilder(50);
        for (c = pbReader.read(); (char)c != '}'; c = pbReader.read()) {
            if (c == -1 || c == 0xFFFF) {
                throw new PGNException("Non-terminated comment");
            }
            result.append((char)c);
        }
        return new PGNToken(PGNToken.TokenType.COMMENT, result.toString());
    }

    public PGNToken readLineComment() throws IOException, PGNException {
        int c = pbReader.read();
        assert(c == ';');
        StringBuilder result = new StringBuilder(50);
        for (c = pbReader.read(); (char)c != '\n'; c = pbReader.read()) {
            if (c == -1 || c == 0xFFFF) {
                throw new PGNException("Non-terminated comment");
            }
            result.append((char)c);
        }
        return new PGNToken(PGNToken.TokenType.LINE_COMMENT, result.toString());
    }

    public PGNToken readToken() throws IOException, PGNException {
        skipWhitespace();

        int c = peek();

        if (c == -1 || c == 0xFFFF) {
            return new PGNToken(PGNToken.TokenType.EOF, "");
        } else if ((char)c == '\"') {
            return readString();
        } else if (isSymbolStart((char)c)) {
            return readSymbol();
        } else if ((char)c == '.') {
            return readPeriod();
        } else if ((char)c == '*') {
            return readAsterisk();
        } else if ((char)c == '[') {
            return readLeftBracket();
        } else if ((char)c == ']') {
            return readRightBracket();
        } else if ((char)c == '(') {
            return readLeftParen();
        } else if ((char)c == ')') {
            return readRightParen();
        } else if ((char)c == '<') {
            return readLeftAngle();
        } else if ((char)c == '>') {
            return readRightAngle();
        } else if ((char)c == '{') {
            return readComment();
        } else if ((char)c == ';') {
            return readComment();
        } else if ((char)c == '$') {
            return readNAG();
        } else {
            throw new PGNException(new StringBuilder()
                    .append("Invalid character: ")
                    .append(c)
                    .append(" ")
                    .append((char)c).toString());
        }
    }

    public boolean nextGame() throws IOException, PGNException {
        PGNToken token;
        for (token = readToken(); token.getTokenType() != PGNToken.TokenType.EOF; token = readToken()) {
            if (token.getTokenType() == PGNToken.TokenType.LEFT_BRACKET) {
                pbReader.unread('[');
                return true;
            }
        }
        return false;
    }
}
