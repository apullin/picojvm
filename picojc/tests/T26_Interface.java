interface Printable {
    int getValue();
}

class Num implements Printable {
    int n;
    Num(int v) { n = v; }
    public int getValue() { return n; }
}

class DoubleNum implements Printable {
    int n;
    DoubleNum(int v) { n = v; }
    public int getValue() { return n * 2; }
}

public class T26_Interface {
    static void show(Printable p) {
        Native.putchar(p.getValue());
    }

    public static void main(String[] args) {
        show(new Num(10));       // 10
        show(new DoubleNum(5));  // 10
        show(new Num(50));       // 50
        Native.putchar(10);
        Native.halt();
    }
}
