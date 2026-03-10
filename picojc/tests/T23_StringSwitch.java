public class T23_StringSwitch {
    static int test(String s) {
        switch (s) {
            case "apple": return 1;
            case "banana": return 2;
            case "cherry": return 3;
            default: return 0;
        }
    }

    public static void main(String[] args) {
        Native.putchar(test("apple"));   // 1
        Native.putchar(test("banana"));  // 2
        Native.putchar(test("cherry"));  // 3
        Native.putchar(test("other"));   // 0
        Native.putchar(10);
        Native.halt();
    }
}
