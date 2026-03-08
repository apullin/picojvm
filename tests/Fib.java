// Fibonacci sequence — first JAVA/85 test program.
// Outputs the first 10 Fibonacci numbers as ASCII characters to verify
// the interpreter handles arithmetic, locals, branching, and method calls.

public class Fib {
    static int fib(int n) {
        if (n <= 1) return n;
        int a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            int t = a + b;
            a = b;
            b = t;
        }
        return b;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            int f = fib(i);
            // Output low byte as raw value (for memory dump verification)
            Native.putchar(f);
        }
        Native.halt();
    }
}
