class T57Base {
    int f() { return 1; }
}

class T57Child extends T57Base {
    int f(int x) { return x + 2; }
}

public class T57_InheritedOverload {
    public static void main(String[] args) {
        T57Child c = new T57Child();
        Native.putchar(48 + c.f());
        Native.putchar(48 + c.f(2));
        Native.putchar(10);
        Native.halt();
    }
}
