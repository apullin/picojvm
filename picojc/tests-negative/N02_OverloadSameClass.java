class N02A {
    static int f() { return 1; }
    static int f(int x) { return x; }
}

public class N02_OverloadSameClass {
    public static void main(String[] args) {
        Native.putchar(48 + N02A.f(1, 2));
        Native.halt();
    }
}
