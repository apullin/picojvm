public class T89_LoopCompletion {
    static int whileForever() {
        while (true) { }
    }

    static int forForever() {
        for (;;) { }
    }

    static int doForever() {
        do { } while (true);
    }

    static int doReturn() {
        do {
            return 7;
        } while (false);
    }

    static int doContinue() {
        int n = 0;
        do {
            n++;
            continue;
        } while (n < 2);
        return n;
    }

    static int whileBreak() {
        while (true) {
            break;
        }
        return 3;
    }

    static int switchInForeverLoop(int n) {
        while (true) {
            switch (n) {
                case 0: break;
                default: continue;
            }
        }
    }

    public static void main(String[] args) {
        Native.putchar('0' + doReturn());
        Native.putchar('0' + doContinue());
        Native.putchar('0' + whileBreak());
        Native.putchar('\n');
    }
}
