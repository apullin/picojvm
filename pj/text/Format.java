package pj.text;

import pj.Native;

/*
 * Small formatting helpers.
 *
 * These are deliberately low-level: direct print routines and byte-buffer
 * writers, not a StringBuilder-style abstraction.
 */
public class Format {
    public static int intLength(int v) {
        int n = 1;
        if (v < 0) {
            if (v == -2147483648) return 11;
            n++;
            v = -v;
        }
        while (v >= 10) {
            v /= 10;
            n++;
        }
        return n;
    }

    private static int writeUnsigned(byte[] buf, int off, int v) {
        if (v >= 10) off = writeUnsigned(buf, off, v / 10);
        buf[off] = (byte)('0' + (v % 10));
        return off + 1;
    }

    public static int writeInt(byte[] buf, int off, int v) {
        if (v == -2147483648) {
            return writeString(buf, off, "-2147483648");
        }
        if (v < 0) {
            buf[off] = (byte)'-';
            return writeUnsigned(buf, off + 1, -v);
        }
        return writeUnsigned(buf, off, v);
    }

    public static int writeHex(byte[] buf, int off, int v, int digits) {
        for (int i = digits - 1; i >= 0; i--) {
            int nib = (v >> (i * 4)) & 0xF;
            buf[off++] = (byte)(nib < 10 ? ('0' + nib) : ('A' + nib - 10));
        }
        return off;
    }

    public static int writeString(byte[] buf, int off, String s) {
        int len = s.length();
        for (int i = 0; i < len; i++) buf[off++] = (byte)s.charAt(i);
        return off;
    }

    public static void printInt(int v) {
        if (v == -2147483648) {
            Native.print("-2147483648");
            return;
        }
        if (v < 0) {
            Native.putchar('-');
            v = -v;
        }
        if (v >= 10) printInt(v / 10);
        Native.putchar('0' + (v % 10));
    }

    public static void printlnInt(int v) {
        printInt(v);
        Native.putchar('\n');
    }

    public static void printHex(int v, int digits) {
        for (int i = digits - 1; i >= 0; i--) {
            int nib = (v >> (i * 4)) & 0xF;
            Native.putchar(nib < 10 ? ('0' + nib) : ('A' + nib - 10));
        }
    }
}
