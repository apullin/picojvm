package pj.text;

/*
 * Small String helpers built on length/charAt.
 */
public class Strings {
    public static int asciiLower(int ch) {
        if (ch >= 'A' && ch <= 'Z') return ch + ('a' - 'A');
        return ch;
    }

    public static int asciiUpper(int ch) {
        if (ch >= 'a' && ch <= 'z') return ch - ('a' - 'A');
        return ch;
    }

    public static boolean regionEquals(String a, int aOff, String b, int bOff, int len) {
        for (int i = 0; i < len; i++) {
            if (a.charAt(aOff + i) != b.charAt(bOff + i)) return false;
        }
        return true;
    }

    public static boolean startsWith(String s, String prefix) {
        int n = prefix.length();
        if (n > s.length()) return false;
        return regionEquals(s, 0, prefix, 0, n);
    }

    public static boolean endsWith(String s, String suffix) {
        int n = suffix.length();
        int m = s.length();
        if (n > m) return false;
        return regionEquals(s, m - n, suffix, 0, n);
    }

    public static int indexOf(String s, int ch) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (s.charAt(i) == ch) return i;
        }
        return -1;
    }

    public static int indexOfFrom(String s, int ch, int from) {
        int len = s.length();
        if (from < 0) from = 0;
        for (int i = from; i < len; i++) {
            if (s.charAt(i) == ch) return i;
        }
        return -1;
    }

    public static boolean equalsIgnoreAsciiCase(String a, String b) {
        int len = a.length();
        if (len != b.length()) return false;
        for (int i = 0; i < len; i++) {
            if (asciiLower(a.charAt(i)) != asciiLower(b.charAt(i))) return false;
        }
        return true;
    }
}
