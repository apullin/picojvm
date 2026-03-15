class N03A {
    int f() { return 1; }
    int f(int x) { return x; }
}

public class N03_OverloadInherited {
    public static void main(String[] args) {
        N03A a = new N03A();
        Native.putchar(48 + a.f(1, 2));
        Native.halt();
    }
}
