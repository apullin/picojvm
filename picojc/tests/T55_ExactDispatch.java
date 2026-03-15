class T55A {
    int f() { return 1; }
}

class T55B {
    int f() { return 2; }
}

public class T55_ExactDispatch {
    static void showA(T55A a) {
        Native.putchar(48 + a.f());
    }

    static void showB(T55B b) {
        Native.putchar(48 + b.f());
    }

    public static void main(String[] args) {
        showB(new T55B());
        showA(new T55A());
        Native.putchar(10);
        Native.halt();
    }
}
