/**
 * Test @Const annotation on String[]: ROM-resident array of ROM string refs.
 */
class ConstStringArrayTest {
    @Const static final String[] COLORS = {
        "red", "green", "blue", null, "red"
    };

    public static void main(String[] args) {
        Native.putchar(COLORS.length);
        Native.print(COLORS[0]);
        Native.putchar(':');
        Native.print(COLORS[1]);
        Native.putchar(':');
        Native.print(COLORS[2]);
        Native.putchar(':');
        Native.putchar(COLORS[3] == null ? 1 : 0);
        Native.putchar(COLORS[0] == COLORS[4] ? 1 : 0);
        Native.putchar(COLORS[0].length());
        Native.putchar(COLORS[1].charAt(2));
        Native.halt();
    }
}
