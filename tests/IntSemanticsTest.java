/** Regression coverage for Java's defined 32-bit wrap and shift semantics. */
public class IntSemanticsTest {
    static int multiply(int a, int b) { return a * b; }
    static int shiftLeft(int a, int b) { return a << b; }
    static int shiftRight(int a, int b) { return a >> b; }
    static int shiftRightUnsigned(int a, int b) { return a >>> b; }

    static void result(boolean ok) {
        Native.putchar(ok ? 1 : 0);
    }

    public static void main(String[] args) {
        result(multiply(0x40000000, 4) == 0);
        result(multiply(0x80000000, -1) == 0x80000000);
        result(shiftLeft(-1, 1) == -2);
        result(shiftLeft(0x40000000, 2) == 0);
        result(shiftRight(-2, 1) == -1);
        result(shiftRight(0x80000000, 31) == -1);
        result(shiftRightUnsigned(-1, 31) == 1);
        Native.halt();
    }
}
