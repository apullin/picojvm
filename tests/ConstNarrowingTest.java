/**
 * Test @Const object arrays whose trivial ctor/factory paths use narrowing casts.
 */
final class NarrowPoint {
    final byte b;
    final short s;
    final int i;
    final String label;

    NarrowPoint(int b, int s, int i, String label) {
        this.b = (byte)b;
        this.s = (short)s;
        this.i = i;
        this.label = label;
    }
}

final class ByteCommand {
    final byte key;
    final String name;

    ByteCommand(byte key, String name) {
        this.key = key;
        this.name = name;
    }
}

class ConstNarrowingTest {
    static NarrowPoint pt(int b, int s, int i, String label) {
        return new NarrowPoint(b, s, i, label);
    }

    static ByteCommand command(char key, String name) {
        return new ByteCommand((byte)key, name);
    }

    @Const static final NarrowPoint[] POINTS = {
        new NarrowPoint(-1, 32767, 1000000, "neg-byte"),
        pt(127, -32768, 0, "min-short"),
        null,
    };

    @Const static final ByteCommand[] COMMANDS = {
        command('A', "cmd"),
    };

    public static void main(String[] args) {
        Native.putchar(POINTS.length);
        Native.putchar(POINTS[0].b & 0xff);
        Native.putchar(POINTS[1].s == -32768 ? 1 : 0);
        Native.putchar(POINTS[0].i & 0xff);
        Native.print(POINTS[0].label);
        Native.putchar(POINTS[2] == null ? 1 : 0);
        Native.putchar(COMMANDS[0].key);
        Native.print(COMMANDS[0].name);
        Native.halt();
    }
}
