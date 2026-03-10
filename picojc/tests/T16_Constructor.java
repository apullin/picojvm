class Vec {
    int x;
    int y;

    Vec(int a, int b) {
        x = a;
        y = b;
    }

    int sum() {
        return x + y;
    }
}

public class T16_Constructor {
    public static void main(String[] args) {
        Vec v = new Vec(7, 14);
        Native.putchar(v.x);    // 7
        Native.putchar(v.y);    // 14
        Native.putchar(v.sum()); // 21
        Native.putchar(10);
        Native.halt();
    }
}
