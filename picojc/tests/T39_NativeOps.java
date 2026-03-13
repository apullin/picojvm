public class T39_NativeOps {
    static void printNum(int n) {
        if (n < 0) { Native.putchar('-'); n = -n; }
        if (n >= 10) printNum(n / 10);
        Native.putchar('0' + (n % 10));
    }

    public static void main(String[] args) {
        // Test arraycopy
        byte[] src = new byte[5];
        src[0] = 72; src[1] = 101; src[2] = 108; src[3] = 108; src[4] = 111;
        byte[] dst = new byte[5];
        Native.arraycopy(src, 0, dst, 0, 5);
        for (int i = 0; i < 5; i++) Native.putchar(dst[i]);
        Native.putchar('\n');

        // Test arraycopy with offset
        byte[] dst2 = new byte[7];
        dst2[0] = 42; dst2[6] = 33;
        Native.arraycopy(src, 1, dst2, 1, 4);
        printNum(dst2[0]); Native.putchar(',');
        printNum(dst2[1]); Native.putchar(',');
        printNum(dst2[4]); Native.putchar(',');
        printNum(dst2[6]); Native.putchar('\n');

        // Test memcmp - equal
        printNum(Native.memcmp(src, 0, dst, 0, 5));
        Native.putchar('\n');

        // Test memcmp - src < dst
        dst[2] = 120;
        int cmp = Native.memcmp(src, 0, dst, 0, 5);
        if (cmp < 0) Native.print("LT");
        else if (cmp > 0) Native.print("GT");
        else Native.print("EQ");
        Native.putchar('\n');

        // Test writeBytes
        byte[] msg = new byte[6];
        msg[0] = 87; msg[1] = 111; msg[2] = 114; msg[3] = 108; msg[4] = 100; msg[5] = 10;
        Native.writeBytes(msg, 0, 6);

        // Test writeBytes with offset
        Native.writeBytes(msg, 1, 4);
        Native.putchar('\n');

        // Test stringFromBytes
        byte[] buf = new byte[3];
        buf[0] = 65; buf[1] = 66; buf[2] = 67;
        String s = Native.stringFromBytes(buf, 0, 3);
        Native.print(s);
        Native.putchar('\n');

        // Test stringFromBytes with offset
        String s2 = Native.stringFromBytes(buf, 1, 2);
        printNum(s2.length()); Native.putchar(',');
        printNum(s2.charAt(0)); Native.putchar(',');
        printNum(s2.charAt(1)); Native.putchar('\n');

        // Test string equality via stringFromBytes
        byte[] buf2 = new byte[3];
        buf2[0] = 65; buf2[1] = 66; buf2[2] = 67;
        String s3 = Native.stringFromBytes(buf2, 0, 3);
        if (s.equals(s3)) Native.print("EQ");
        else Native.print("NE");
        Native.putchar('\n');
    }
}
