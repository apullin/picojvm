public class N35_ReturnInFinally {
    static int f() {
        try {
            return 1;
        } finally {
            Native.putchar('F');
        }
    }
    public static void main(String[] args) {
        Native.putchar('0' + f());
        Native.halt();
    }
}
