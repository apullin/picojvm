/**
 * Recursion — mutual recursion and deep recursion.
 *
 * Expected output (as bytes): 1, 0, 1, 55
 *   isEven(4)=1, isEven(3)=0, isEven(0)=1, fib(10)=55
 */
public class T37_Recursion {
    static int isEven(int n) {
        if (n == 0) return 1;
        return isOdd(n - 1);
    }

    static int isOdd(int n) {
        if (n == 0) return 0;
        return isEven(n - 1);
    }

    static int fib(int n) {
        if (n <= 1) return n;
        return fib(n - 1) + fib(n - 2);
    }

    public static void main(String[] args) {
        Native.putchar(isEven(4));   // 1
        Native.putchar(isEven(3));   // 0
        Native.putchar(isEven(0));   // 1
        Native.putchar(fib(10));     // 55
        Native.halt();
    }
}
