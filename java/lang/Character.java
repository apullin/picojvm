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

    public static boolean isWhitespace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' ||
               ch == '\f';
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
