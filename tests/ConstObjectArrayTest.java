/**
 * Test @Const on arrays of immutable value objects.
 */
final class ConstPoint {
    final int x;
    final int y;
    final String label;

    ConstPoint(int x, int y, String label) {
        this.x = x;
        this.y = y;
        this.label = label;
    }

    int sum() {
        return x + y;
    }

    String name() {
        return label;
    }
}

class ConstObjectArrayTest {
    static ConstPoint make(int x, int y, String label) {
        return new ConstPoint(x, y, label);
    }

    @Const static final ConstPoint[] POINTS = {
        new ConstPoint(1, 2, "origin"),
        null,
        new ConstPoint(10, 3, "edge"),
        new ConstPoint(0, 0, null),
    };

    @Const static final ConstPoint[] FACTORY_POINTS = {
        make(7, 8, "made"),
        make(9, 1, null),
    };

    public static void main(String[] args) {
        ConstPoint p = POINTS[0];
        ConstPoint q = POINTS[2];
        ConstPoint f = FACTORY_POINTS[0];

        Native.putchar(POINTS.length);
        Native.putchar(p.x);
        Native.putchar(p.y);
        Native.print(p.label);
        Native.putchar(p.sum());
        Native.putchar(q.x);
        Native.putchar(q.y);
        Native.putchar(POINTS[1] == null ? 1 : 0);
        Native.putchar(POINTS[0] == p ? 1 : 0);
        Native.putchar(POINTS[0] == POINTS[2] ? 1 : 0);
        Native.putchar(POINTS[3].label == null ? 1 : 0);
        Native.print(q.name());
        Native.putchar(FACTORY_POINTS.length);
        Native.putchar(f.sum());
        Native.print(f.label);
        Native.putchar(FACTORY_POINTS[1].label == null ? 1 : 0);
        Native.halt();
    }
}
