public class T10_StringLiteral {
    public static void main(String[] args) {
        String s = "Hello";
        // Print each char
        for (int i = 0; i < s.length(); i++) {
            Native.putchar(s.charAt(i));
        }
        // Print length
        Native.putchar(s.length());
        Native.putchar(10);
        Native.halt();
    }
}
