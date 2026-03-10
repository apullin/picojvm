class Counter {
    int count;

    Counter() {
        count = 0;
    }

    void increment() {
        count++;
    }

    void add(int n) {
        count = count + n;
    }

    int get() {
        return count;
    }
}

public class T30_MultiClass {
    public static void main(String[] args) {
        Counter c = new Counter();
        c.increment();
        c.increment();
        c.increment();
        c.add(7);
        Native.putchar(c.get());  // 10
        Native.putchar(10);
        Native.halt();
    }
}
