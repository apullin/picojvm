// Counter — exercises objects: new, getfield, putfield, invokespecial, invokevirtual.
// Adapted from picoJVM test suite.

public class T33_Counter {
    int count;

    void increment() {
        count++;
    }

    int getCount() {
        return count;
    }

    public static void main(String[] args) {
        T33_Counter c = new T33_Counter();
        c.increment();
        c.increment();
        c.increment();
        Native.putchar(c.getCount());  // 3
        Native.halt();
    }
}
