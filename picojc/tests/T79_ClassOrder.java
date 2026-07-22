class T79Child extends T79Base {
    int childValue;
    static int childStatic = T79Base.baseStatic + 1;
}

class T79Base {
    int baseValue;
    static int baseStatic = 3;
}

public class T79_ClassOrder {
    public static void main(String[] args) {
        T79Child first = new T79Child();
        T79Child second = new T79Child();
        first.baseValue = 2;
        first.childValue = 3;
        second.baseValue = 4;
        second.childValue = 1;
        Native.putchar(48 + first.baseValue);
        Native.putchar(48 + first.childValue);
        Native.putchar(48 + second.baseValue);
        Native.putchar(48 + second.childValue);
        Native.putchar(48 + T79Child.childStatic);
        Native.putchar(10);
    }
}
