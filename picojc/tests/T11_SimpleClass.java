public class T11_SimpleClass {
    static int x;
    static int y;

    public static void main(String[] args) {
        x = 10;
        y = 20;
        Native.putchar(x);
        Native.putchar(y);
        Native.putchar(x + y);
        Native.putchar(10);
        Native.halt();
    }
}
