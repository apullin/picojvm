public class T14_Ternary {
    public static void main(String[] args) {
        int a = 10;
        int b = 20;
        int max = a > b ? a : b;
        Native.putchar(max);  // 20
        int min = a < b ? a : b;
        Native.putchar(min);  // 10
        Native.putchar(10);
        Native.halt();
    }
}
