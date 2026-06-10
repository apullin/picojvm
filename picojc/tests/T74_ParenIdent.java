// Parenthesized identifiers must parse as expressions, not casts:
// "(x) - 1" used to swallow x and evaluate to -1.
public class T74_ParenIdent {
    public static void main(String[] args) {
        int x = 5;
        Native.putchar('0' + ((x) - 1));
        Native.putchar('0' + (x));
        Native.putchar('0' + ((x) + 0));
        Native.putchar(10);
        Native.halt();
    }
}
