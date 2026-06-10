// continue inside a switch targets the enclosing loop (it used to act as
// break), and nested switches must not clobber each other's case tables.
public class T75_SwitchControl {
    public static void main(String[] args) {
        int n = 0;
        for (int i = 0; i < 3; i++) {
            switch (i) {
                case 0: continue;
                default: break;
            }
            n++;
        }
        Native.putchar('0' + n);

        int r = 0;
        for (int a = 0; a < 3; a++) {
            switch (a) {
                case 0:
                    switch (a + 10) {
                        case 10: r += 1; break;
                        case 11: r += 100; break;
                    }
                    break;
                case 1: r += 20; break;
                case 2: r += 300; break;
            }
        }
        Native.putchar('0' + r / 100);
        Native.putchar('0' + (r / 10) % 10);
        Native.putchar('0' + r % 10);
        Native.putchar(10);
        Native.halt();
    }
}
