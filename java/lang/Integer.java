package java.lang;

import pj.Native;

public final class Integer {
    private final int value;

    public Integer(int value) {
        this.value = value;
    }

    public static Integer valueOf(int value) {
        return new Integer(value);
    }

    public static int compare(int a, int b) {
        return a < b ? -1 : (a == b ? 0 : 1);
    }

    public int intValue() {
        return value;
    }

    public String toString() {
        return toString(value);
    }

    public static String toString(int value) {
        byte[] buf = new byte[12];
        int off = 0;
        if (value == -2147483648) return "-2147483648";
        if (value < 0) {
            buf[off++] = (byte)'-';
            value = -value;
        }
        int div = 1000000000;
        boolean seen = false;
        while (div > 0) {
            int digit = value / div;
            if (digit != 0 || seen || div == 1) {
                buf[off++] = (byte)('0' + digit);
                seen = true;
            }
            value = value - digit * div;
            div = div / 10;
        }
        return Native.stringFromBytes(buf, 0, off);
    }

    public static String toHexString(int value) {
        byte[] buf = new byte[8];
        int off = 0;
        boolean seen = false;
        for (int shift = 28; shift >= 0; shift -= 4) {
            int nib = (value >> shift) & 0xF;
            if (nib != 0 || seen || shift == 0) {
                buf[off++] = (byte)(nib < 10 ? ('0' + nib) : ('a' + nib - 10));
                seen = true;
            }
        }
        return Native.stringFromBytes(buf, 0, off);
    }
}
