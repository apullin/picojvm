package pj.text;

/*
 * Small decimal and hex parsers.
 */
public class Parse {
    public static int parseInt(String s) {
        int i = 0;
        int sign = 1;
        int v = 0;
        int len = s.length();

        if (len == 0) return 0;
        if (s.charAt(0) == '-') {
            sign = -1;
            i = 1;
        } else if (s.charAt(0) == '+') {
            i = 1;
        }

        while (i < len) {
            v = (v * 10) + (s.charAt(i) - '0');
            i++;
        }
        return sign < 0 ? -v : v;
    }

    public static int parseHex(String s) {
        int i = 0;
        int v = 0;
        int len = s.length();

        if (len >= 2 && s.charAt(0) == '0' && (s.charAt(1) == 'x' || s.charAt(1) == 'X')) i = 2;

        while (i < len) {
            int ch = s.charAt(i++);
            if (ch >= '0' && ch <= '9') v = (v << 4) + (ch - '0');
            else if (ch >= 'a' && ch <= 'f') v = (v << 4) + (ch - 'a' + 10);
            else if (ch >= 'A' && ch <= 'F') v = (v << 4) + (ch - 'A' + 10);
        }
        return v;
    }
}
