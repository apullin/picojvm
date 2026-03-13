/**
 * Arrays.java -- Bulk array operations for picoJVM programs.
 *
 * Thin wrappers around native arraycopy/memcmp for readability.
 * Pure Java fallbacks for fill (no native needed).
 *
 * No method overloading -- picojc resolves methods by name only.
 */
public class Arrays {
    public static boolean equals(byte[] a, int aOff, byte[] b, int bOff, int len) {
        return Native.memcmp(a, aOff, b, bOff, len) == 0;
    }

    public static int compare(byte[] a, int aOff, byte[] b, int bOff, int len) {
        return Native.memcmp(a, aOff, b, bOff, len);
    }

    public static void copy(byte[] src, int srcOff, byte[] dst, int dstOff, int len) {
        Native.arraycopy(src, srcOff, dst, dstOff, len);
    }

    public static void fillBytes(byte[] a, int off, int len, byte val) {
        for (int i = 0; i < len; i++) a[off + i] = val;
    }

    public static void fillInts(int[] a, int off, int len, int val) {
        for (int i = 0; i < len; i++) a[off + i] = val;
    }
}
