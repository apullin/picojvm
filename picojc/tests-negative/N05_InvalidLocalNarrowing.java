public class N05_InvalidLocalNarrowing {
    public static void main(String[] args) {
        byte b = 300;
        Native.putchar(b);
    }
}
