public class EnumBasicTest {
    enum Color {
        RED, GREEN, BLUE
    }

    public static void main(String[] args) {
        Color[] all = Color.values();
        Native.putchar(all.length);
        Native.putchar(Color.RED.ordinal());
        Native.putchar(Color.GREEN.ordinal());
        Native.putchar(Color.BLUE.ordinal());
        Native.print(Color.RED.name());
        Native.print(all[2].name());
        Native.putchar(all[0] == Color.RED ? 1 : 0);
        Native.putchar(all == Color.values() ? 0 : 1);
        Native.halt();
    }
}
