// A static final with an expression initializer must evaluate the whole
// expression (the constant folder used to keep just the leading literal).
public class T76_ConstFieldInit {
    static final int A = 5 + 3;
    static final int B = 4;
    public static void main(String[] args) {
        Native.putchar('0' + A);
        Native.putchar('0' + B);
        Native.putchar(10);
        Native.halt();
    }
}
