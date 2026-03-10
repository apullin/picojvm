public class T07_MethodCall {
    static int add(int a, int b) {
        return a + b;
    }

    static int factorial(int n) {
        if (n <= 1) return 1;
        return n * factorial(n - 1);
    }

    public static void main(String[] args) {
        int sum = add(3, 4);
        // sum = 7 -> '7'
        Native.putchar(48 + sum);

        int fact = factorial(5);
        // fact = 120 -> 'x'
        Native.putchar(fact);
        Native.putchar(10);
        Native.halt();
    }
}
