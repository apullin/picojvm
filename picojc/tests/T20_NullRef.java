class Box {
    int val;
}

public class T20_NullRef {
    public static void main(String[] args) {
        Box b = null;
        if (b == null) {
            Native.putchar(1);  // null check true
        } else {
            Native.putchar(0);
        }
        b = new Box();
        b.val = 42;
        if (b != null) {
            Native.putchar(b.val);  // 42
        }
        Native.putchar(10);
        Native.halt();
    }
}
