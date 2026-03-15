class N01A {
    int f() { return 1; }
}

public class N01_InvalidStaticCall {
    public static void main(String[] args) {
        Native.putchar(48 + N01A.f());
        Native.halt();
    }
}
