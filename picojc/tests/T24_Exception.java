public class T24_Exception {
    public static void main(String[] args) {
        // Basic try-catch
        try {
            Native.putchar(1);
            throw new RuntimeException();
        } catch (RuntimeException e) {
            Native.putchar(2);
        }

        // Try without exception
        try {
            Native.putchar(3);
        } catch (RuntimeException e) {
            Native.putchar(99);  // should not execute
        }

        Native.putchar(4);
        Native.putchar(10);
        Native.halt();
    }
}
