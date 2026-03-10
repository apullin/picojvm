public class T06_ForLoop {
    public static void main(String[] args) {
        // Factorial of 5 using for loop
        int result = 1;
        for (int i = 1; i <= 5; i++) {
            result = result * i;
        }
        // 120 = 'x'
        Native.putchar(result);
        Native.putchar(10);
        Native.halt();
    }
}
