public class T03_Arithmetic {
    public static void main(String[] args) {
        int a = 3 + 4;    // 7
        int b = 10 - 3;   // 7
        int c = a + b;     // 14
        // Print as digits: '1' then '4'
        Native.putchar(48 + c / 10);
        Native.putchar(48 + c % 10);
        Native.putchar(10);
        Native.halt();
    }
}
