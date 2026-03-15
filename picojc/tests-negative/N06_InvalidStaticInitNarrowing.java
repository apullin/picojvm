public class N06_InvalidStaticInitNarrowing {
    static byte b = 300;

    public static void main(String[] args) {
        Native.putchar(b);
    }
}
