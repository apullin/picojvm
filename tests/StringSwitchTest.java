/**
 * StringSwitchTest.java — Test switch on strings (hashCode + lookupswitch + equals).
 *
 * Expected output (as bytes): 01 02 03 00
 */
public class StringSwitchTest {
    static int classify(String s) {
        switch (s) {
            case "red":   return 1;
            case "green": return 2;
            case "blue":  return 3;
            default:      return 0;
        }
    }

    public static void main(String[] args) {
        Native.putchar(classify("red"));    // 1
        Native.putchar(classify("green"));  // 2
        Native.putchar(classify("blue"));   // 3
        Native.putchar(classify("other"));  // 0
        Native.halt();
    }
}
