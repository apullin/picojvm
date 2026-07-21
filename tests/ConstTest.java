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

    @Const static final char[] CHARS = { 65, 66, 67 };

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

        // ROM arrays must remain valid inputs to byte-oriented natives.
        byte[] copy = new byte[3];
        Native.arraycopy(BYTES, 2, copy, 0, 3);
        Native.putchar(copy[0]);
        Native.putchar(copy[1]);
        Native.putchar(copy[2]);
        Native.putchar(Native.memcmp(BYTES, 2, copy, 0, 3) == 0 ? 1 : 0);
        Native.writeBytes(BYTES, 0, 2);

        String nativeString = Native.stringFromBytes(BYTES, 4, 2);
        Native.putchar(nativeString.charAt(0));
        Native.putchar(nativeString.charAt(1));

        String byteString = new String(BYTES, 6, 2);
        Native.putchar(byteString.charAt(0));
        Native.putchar(byteString.charAt(1));

        String charString = new String(CHARS);
        Native.putchar(charString.length());
        Native.putchar(charString.charAt(1));

        Native.halt();
    }
}
