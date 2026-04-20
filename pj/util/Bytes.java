package pj.util;

import pj.Native;

/*
 * Byte-array helpers for picoJSE.
 *
 * Kept separate from int-array helpers to avoid overload ambiguity in the
 * smaller compiler/runtime environment.
 */
public class Bytes {
    public static boolean equals(byte[] a, int aOff, byte[] b, int bOff, int len) {
        return Native.memcmp(a, aOff, b, bOff, len) == 0;
    }

    public static int compare(byte[] a, int aOff, byte[] b, int bOff, int len) {
        return Native.memcmp(a, aOff, b, bOff, len);
    }

    public static void copy(byte[] src, int srcOff, byte[] dst, int dstOff, int len) {
        Native.arraycopy(src, srcOff, dst, dstOff, len);
    }

    public static void fill(byte[] a, int off, int len, int val) {
        byte b = (byte)val;
        for (int i = 0; i < len; i++) a[off + i] = b;
    }

    public static void clear(byte[] a, int off, int len) {
        fill(a, off, len, 0);
    }

    public static int indexOf(byte[] a, int off, int len, int val) {
        byte b = (byte)val;
        for (int i = 0; i < len; i++) {
            if (a[off + i] == b) return off + i;
        }
        return -1;
    }
}
