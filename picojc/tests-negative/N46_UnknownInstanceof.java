public class N46_UnknownInstanceof {
    public static void main(String[] args) {
        Object value = null;
        if (value instanceof MissingType) {
            Native.putchar('N');
        }
    }
}
