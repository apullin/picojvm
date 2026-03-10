class Config {
    static int val;
    static int count;

    static {
        val = 42;
        count = 3;
    }
}

public class T18_StaticInit {
    public static void main(String[] args) {
        Native.putchar(Config.val);     // 42 = '*'
        Native.putchar(Config.count);   // 3
        Config.val = 100;
        Native.putchar(Config.val);     // 100 = 'd'
        Native.putchar(10);
        Native.halt();
    }
}
