package java.lang;

public final class Character {
    private final char value;

    public Character(char value) {
        this.value = value;
    }

    public static Character valueOf(char value) {
        return new Character(value);
    }

    public char charValue() {
        return value;
    }

    public static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    public static boolean isLetter(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    public static boolean isLetterOrDigit(char ch) {
        return isLetter(ch) || isDigit(ch);
    }

    public static boolean isUpperCase(char ch) {
        return ch >= 'A' && ch <= 'Z';
    }

    public static boolean isWhitespace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' ||
               ch == '\f';
    }

    public static int digit(char ch, int radix) {
        int v;
        if (ch >= '0' && ch <= '9') v = ch - '0';
        else if (ch >= 'a' && ch <= 'z') v = ch - 'a' + 10;
        else if (ch >= 'A' && ch <= 'Z') v = ch - 'A' + 10;
        else return -1;
        return v < radix && v >= 0 ? v : -1;
    }

    public static char toLowerCase(char ch) {
        if (ch >= 'A' && ch <= 'Z') return (char)(ch + ('a' - 'A'));
        return ch;
    }

    public static char toUpperCase(char ch) {
        if (ch >= 'a' && ch <= 'z') return (char)(ch - ('a' - 'A'));
        return ch;
    }

    public String toString() {
        byte[] buf = new byte[1];
        buf[0] = (byte)value;
        return pj.Native.stringFromBytes(buf, 0, 1);
    }
}
