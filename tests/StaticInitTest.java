/**
 * StaticInitTest.java — Test static initializers (<clinit>).
 *
 * Expected output (as bytes): 2A 32  (42, 50)
 */
public class StaticInitTest {
    static int x = 42;
    static int y;
    static { y = x + 8; }

    public static void main(String[] args) {
        Native.putchar(x);   // 42
        Native.putchar(y);   // 50
        Native.halt();
    }
}
