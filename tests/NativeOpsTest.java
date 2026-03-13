/**
 * NativeOpsTest.java — Test new native kernel operations.
 *
 * Expected output (as bytes):
 *   65 66 67 68     — arraycopy result (ABCD)
 *   1               — memcmp equal
 *   0               — memcmp not equal (result != 0, but we test == 0)
 *   72 105          — writeBytes ("Hi")
 *   5               — stringFromBytes length
 *   87 111 114      — stringFromBytes charAt (W, o, r)
 */
public class NativeOpsTest {

    static void testArraycopy() {
        byte[] src = new byte[4];
        src[0] = 65; src[1] = 66; src[2] = 67; src[3] = 68;
        byte[] dst = new byte[6];
        Native.arraycopy(src, 0, dst, 1, 4);
        // dst[1..4] should be ABCD
        Native.putchar(dst[1]);  // 65 'A'
        Native.putchar(dst[2]);  // 66 'B'
        Native.putchar(dst[3]);  // 67 'C'
        Native.putchar(dst[4]);  // 68 'D'
    }

    static void testMemcmp() {
        byte[] a = new byte[3];
        a[0] = 1; a[1] = 2; a[2] = 3;
        byte[] b = new byte[3];
        b[0] = 1; b[1] = 2; b[2] = 3;
        byte[] c = new byte[3];
        c[0] = 1; c[1] = 9; c[2] = 3;
        // a == b
        Native.putchar(Native.memcmp(a, 0, b, 0, 3) == 0 ? 1 : 0);  // 1
        // a != c
        Native.putchar(Native.memcmp(a, 0, c, 0, 3) == 0 ? 1 : 0);  // 0
    }

    static void testWriteBytes() {
        byte[] buf = new byte[5];
        buf[0] = 72;   // 'H'
        buf[1] = 105;  // 'i'
        buf[2] = 33;   // '!'
        buf[3] = 10;
        buf[4] = 0;
        Native.writeBytes(buf, 0, 2);  // outputs 'H', 'i'
    }

    static void testStringFromBytes() {
        byte[] data = new byte[5];
        data[0] = 87;   // 'W'
        data[1] = 111;  // 'o'
        data[2] = 114;  // 'r'
        data[3] = 108;  // 'l'
        data[4] = 100;  // 'd'
        String s = Native.stringFromBytes(data, 0, 5);
        Native.putchar(s.length());   // 5
        Native.putchar(s.charAt(0));  // 87 'W'
        Native.putchar(s.charAt(1));  // 111 'o'
        Native.putchar(s.charAt(2));  // 114 'r'
    }

    public static void main(String[] args) {
        testArraycopy();
        testMemcmp();
        testWriteBytes();
        testStringFromBytes();
        Native.halt();
    }
}
