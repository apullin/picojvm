/*
 * Enums backed by the packed java/lang/Enum.java shim (Java tier) instead
 * of the VM's NATIVE_ENUM_* handlers: the pack rule includes Enum.class,
 * so name()/ordinal()/toString() and the super constructor all run as
 * ordinary bytecode. Compare EnumBasicTest, which packs without the shim
 * and exercises the native tier.
 */
public class EnumShimTest {
    enum Color {
        RED, GREEN, BLUE
    }

    public static void main(String[] args) {
        Color[] all = Color.values();
        Native.putchar(all.length);
        Native.putchar(Color.RED.ordinal());
        Native.putchar(Color.BLUE.ordinal());
        Native.print(Color.GREEN.name());
        Native.print(all[2].toString());
        Native.putchar(all[1] == Color.GREEN ? 1 : 0);
        Native.putchar(10);
        Native.halt();
    }
}
