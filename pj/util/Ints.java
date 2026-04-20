package pj.util;

/*
 * Integer helpers for picoJSE.
 */
public class Ints {
    public static int abs(int v) {
        return v < 0 ? -v : v;
    }

    public static int min(int a, int b) {
        return a < b ? a : b;
    }

    public static int max(int a, int b) {
        return a > b ? a : b;
    }

    public static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    public static void fill(int[] a, int off, int len, int val) {
        for (int i = 0; i < len; i++) a[off + i] = val;
    }

    public static void clear(int[] a, int off, int len) {
        fill(a, off, len, 0);
    }
}
