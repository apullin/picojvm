public class N08_InvalidReturnNarrowing {
    static byte bad() {
        return 300;
    }

    public static void main(String[] args) {
        Native.putchar(bad());
    }
}
