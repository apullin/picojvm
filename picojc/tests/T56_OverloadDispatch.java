class T56Over {
    static int f() { return 1; }
    static int f(int x) { return x + 2; }

    int g() { return 3; }
    int g(int x) { return x + 4; }
}

public class T56_OverloadDispatch {
    public static void main(String[] args) {
        T56Over o = new T56Over();
        Native.putchar(48 + T56Over.f());
        Native.putchar(48 + T56Over.f(5));
        Native.putchar(48 + o.g());
        Native.putchar(48 + o.g(0));
        Native.putchar(10);
        Native.halt();
    }
}
