/**
 * StringTest.java — Test String constants and methods.
 *
 * Expected output (as bytes):
 *   'H' 'e' 'l' 'l' 'o'       — Native.print("Hello")
 *   5                           — "Hello".length()
 *   'l'                         — "Hello".charAt(2)
 *   1                           — "Hello".equals("Hello")
 *   0                           — "Hello".equals("World")
 *   1                           — s == s (same ref)
 *   'W' 'o' 'r' 'l' 'd'       — Native.print("World")
 */
public class StringTest {
    public static void main(String[] args) {
        String hello = "Hello";
        String world = "World";

        // Print "Hello" via Native.print
        Native.print(hello);

        // length()
        Native.putchar(hello.length());

        // charAt(2) = 'l'
        Native.putchar(hello.charAt(2));

        // equals — same content
        String hello2 = "Hello";
        Native.putchar(hello.equals(hello2) ? 1 : 0);

        // equals — different content
        Native.putchar(hello.equals(world) ? 1 : 0);

        // Reference equality (interned — same "Hello" constant)
        Native.putchar(hello == hello2 ? 1 : 0);

        // Print "World"
        Native.print(world);

        Native.halt();
    }
}
