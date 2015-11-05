package chess;


public class PGNException extends Exception {

    public String getText() {
        return text;
    }

    String text;

    public PGNException(String txt) {
        text = txt;
    }

}
