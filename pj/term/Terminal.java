package pj.term;

import pj.Native;

/*
 * Low-level terminal helpers.
 *
 * ANSI escape generation stays in Java so the native side only needs to
 * expose capabilities, key input, and timing.
 */
public class Terminal {
    public static final int INFO_COLS = 0;
    public static final int INFO_ROWS = 1;
    public static final int INFO_CAPS = 2;

    public static final int CAP_ANSI = 1;
    public static final int CAP_RAWKEY = 2;

    public static int cols() {
        int v = Native.termInfo(INFO_COLS);
        return v > 0 ? v : 80;
    }

    public static int rows() {
        int v = Native.termInfo(INFO_ROWS);
        return v > 0 ? v : 24;
    }

    public static int caps() {
        return Native.termInfo(INFO_CAPS);
    }

    public static boolean hasAnsi() {
        return (caps() & CAP_ANSI) != 0;
    }

    public static boolean hasRawKeys() {
        return (caps() & CAP_RAWKEY) != 0;
    }

    public static int keyRead() {
        return Native.keyRead();
    }

    public static int ticks() {
        return Native.ticks();
    }

    public static void writeString(String s) {
        Native.print(s);
    }

    public static void writeByte(int b) {
        Native.putchar(b & 0xFF);
    }

    public static void writeBytes(byte[] buf, int off, int len) {
        Native.writeBytes(buf, off, len);
    }

    private static void esc() {
        Native.putchar(27);
    }

    public static void home() {
        if (!hasAnsi()) return;
        esc();
        Native.print("[H");
    }

    public static void clear() {
        if (!hasAnsi()) return;
        esc();
        Native.print("[2J");
        home();
    }

    public static void move(int x, int y) {
        if (!hasAnsi()) return;
        esc();
        Native.putchar('[');
        printNum(y + 1);
        Native.putchar(';');
        printNum(x + 1);
        Native.putchar('H');
    }

    public static void clearLine() {
        if (!hasAnsi()) return;
        esc();
        Native.print("[2K");
    }

    public static void hideCursor() {
        if (!hasAnsi()) return;
        esc();
        Native.print("[?25l");
    }

    public static void showCursor() {
        if (!hasAnsi()) return;
        esc();
        Native.print("[?25h");
    }

    public static void enterAlt() {
        if (!hasAnsi()) return;
        esc();
        Native.print("[?1049h");
    }

    public static void leaveAlt() {
        if (!hasAnsi()) return;
        esc();
        Native.print("[?1049l");
    }

    public static void printNum(int n) {
        if (n < 0) {
            Native.putchar('-');
            n = -n;
        }
        if (n >= 10) printNum(n / 10);
        Native.putchar('0' + (n % 10));
    }
}
