/**
 * Console.java -- Formatted output for picoJVM programs.
 *
 * Pure Java, built on Native.putchar. Ships as part of the picoJVM
 * platform package.
 *
 * No method overloading -- picojc resolves methods by name only.
 */
public class Console {
    public static void print(int n) {
        if (n == -2147483648) {
            Native.print("-2147483648");
            return;
        }
        if (n < 0) { Native.putchar('-'); n = -n; }
        if (n >= 10) print(n / 10);
        Native.putchar('0' + (n % 10));
    }

    public static void println(int n) {
        print(n);
        Native.putchar('\n');
    }

    public static void newline() {
        Native.putchar('\n');
    }

    public static void printHex(int v, int digits) {
        for (int i = digits - 1; i >= 0; i--) {
            int nib = (v >> (i * 4)) & 0xF;
            Native.putchar(nib < 10 ? '0' + nib : 'A' + nib - 10);
        }
    }
}
