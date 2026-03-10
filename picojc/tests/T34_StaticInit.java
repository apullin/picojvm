/**
 * StaticInitTest — static initializers (<clinit>).
 * Adapted from picoJVM test suite.
 *
 * Expected output (as bytes): 42, 50
 */
public class T34_StaticInit {
    static int x = 42;
    static int y;
    static { y = x + 8; }

    public static void main(String[] args) {
        Native.putchar(x);   // 42
        Native.putchar(y);   // 50
        Native.halt();
    }
}
