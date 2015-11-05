package chess;

public class PGNToken {

    public enum TokenType {
        STRING, INTEGER, PERIOD, ASTERISK, LEFT_BRACKET, RIGHT_BRACKET,
        LEFT_PAREN, RIGHT_PAREN, LEFT_ANGLE, RIGHT_ANGLE, NAG, SYMBOL,
        COMMENT, LINE_COMMENT, EOF
    }

    String value;
    TokenType tokenType;

    public PGNToken(TokenType t, String v) {
        tokenType = t;
        value = v;
    }

    public String getValue() {
        return value;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public boolean terminatesGame() {
        if (tokenType == TokenType.ASTERISK || tokenType == TokenType.EOF) {
            return true;
        } else if (tokenType == TokenType.SYMBOL) {
            return value.equals("1-0") || value.equals("0-1") || value.equals("1/2-1/2");
        }
        else {
            return false;
        }
    }

}
