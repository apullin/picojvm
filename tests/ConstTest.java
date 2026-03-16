/**
 * Test @Const annotation: ROM-resident constant array.
 *
 * The TABLE array should be placed in const_data by pjvmpack,
 * eliminating both the <clinit> init code and heap allocation.
 * Lookups go through PROG() instead of r8/r16.
 */
class ConstTest {
    @Const static final int[] TABLE = {
        10, 20, 30, 40, 50, 60, 70, 80
    };

    @Const static final byte[] BYTES = {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10
    };

    public static void main(String[] args) {
        // Test iaload on ROM int array
        for (int i = 0; i < TABLE.length; i++) {
            Native.putchar(TABLE[i]);
        }

        // Test baload on ROM byte array
        for (int i = 0; i < BYTES.length; i++) {
            Native.putchar(BYTES[i]);
        }

        // Test arraylength
        Native.putchar(TABLE.length);  // 8
        Native.putchar(BYTES.length);  // 10

        Native.halt();
    }
}
