public class T04_IfElse {
    public static void main(String[] args) {
        int x = 5;
        if (x > 3) {
            Native.putchar(89);  // 'Y'
        } else {
            Native.putchar(78);  // 'N'
        }
        if (x < 3) {
            Native.putchar(89);  // 'Y'
        } else {
            Native.putchar(78);  // 'N'
        }
        Native.putchar(10);
        Native.halt();
    }
}
