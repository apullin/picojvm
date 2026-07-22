public class T82_LongString {
    public static void main(String[] args) {
        String value = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        int length = value.length();
        Native.putchar(48 + length / 100);
        Native.putchar(48 + (length / 10) % 10);
        Native.putchar(48 + length % 10);
        Native.putchar(10);
    }
}
