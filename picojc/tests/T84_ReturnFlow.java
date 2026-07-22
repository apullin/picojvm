public class T84_ReturnFlow {
    static int choose(boolean first) {
        if (first) {
            return 1;
        } else {
            return 2;
        }
    }

    public static void main(String[] args) {
        Native.putchar(48 + choose(true));
        Native.putchar(48 + choose(false));
        Native.putchar(10);
    }
}
