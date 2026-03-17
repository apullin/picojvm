public class T66_Const {
    @Const static final int[] TABLE = { 10, 20, 30, 40, 50, 60, 70, 80 };
    @Const static final byte[] BYTES = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };

    public static void main(String[] args) {
        for (int i = 0; i < TABLE.length; i++) {
            Native.putchar(TABLE[i]);
        }
        for (int i = 0; i < BYTES.length; i++) {
            Native.putchar(BYTES[i]);
        }
        Native.putchar(TABLE.length);
        Native.putchar(BYTES.length);
        Native.halt();
    }
}
