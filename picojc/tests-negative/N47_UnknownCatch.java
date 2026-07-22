public class N47_UnknownCatch {
    public static void main(String[] args) {
        try {
            Native.putchar('T');
        } catch (MissingType error) {
            Native.putchar('N');
        }
    }
}
