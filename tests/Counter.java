// Counter — exercises objects: new, getfield, putfield, invokespecial, invokevirtual.

public class Counter {
    int count;

    void increment() {
        count++;
    }

    int getCount() {
        return count;
    }

    public static void main(String[] args) {
        Counter c = new Counter();
        c.increment();
        c.increment();
        c.increment();
        Native.putchar(c.getCount());  // should output 3
        Native.halt();
    }
}
