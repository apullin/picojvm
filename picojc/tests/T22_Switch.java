public class T22_Switch {
    static int test(int x) {
        switch (x) {
            case 1: return 10;
            case 2: return 20;
            case 3: return 30;
            default: return 0;
        }
    }

    public static void main(String[] args) {
        Native.putchar(test(1));  // 10
        Native.putchar(test(2));  // 20
        Native.putchar(test(3));  // 30
        Native.putchar(test(9));  // 0
        Native.putchar(10);
        Native.halt();
    }
}
