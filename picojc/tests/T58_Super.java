class T58_Base {
    int x;

    T58_Base(int v) {
        x = v;
    }

    int add(int y) {
        return x + y;
    }
}

class T58_Child extends T58_Base {
    int x;

    T58_Child(int v) {
        super(v + 1);
        x = v + 2;
    }

    int add(int y) {
        return 99;
    }

    int total() {
        return super.x + x + super.add(3);
    }
}

public class T58_Super {
    public static void main(String[] args) {
        T58_Child c = new T58_Child(4);
        Native.putchar(c.total());
        Native.putchar(c.x);
        Native.halt();
    }
}
