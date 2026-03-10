public class T29_StringTest {
    public static void main(String[] args) {
        String hello = "Hello";
        String world = "World";
        // Print both strings
        Native.print(hello);
        Native.print(world);
        // Test length
        Native.putchar(hello.length());
        // Test charAt
        Native.putchar(hello.charAt(1)); // 'e'
        // Test equals
        String hello2 = "Hello";
        if (hello.equals(hello2)) {
            Native.putchar(1);
        } else {
            Native.putchar(0);
        }
        if (hello.equals(world)) {
            Native.putchar(1);
        } else {
            Native.putchar(0);
        }
        Native.halt();
    }
}
