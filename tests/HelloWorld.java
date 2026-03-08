// Hello World — character output via Native.putchar.
// No String objects — our JVM subset is static/int-only.

public class HelloWorld {
    static void print(int[] chars) {
        for (int i = 0; i < chars.length; i++) {
            Native.putchar(chars[i]);
        }
    }

    public static void main(String[] args) {
        int[] msg = { 'H', 'e', 'l', 'l', 'o', ',', ' ',
                       '8', '0', '8', '5', '!', '\n' };
        print(msg);
        Native.halt();
    }
}
