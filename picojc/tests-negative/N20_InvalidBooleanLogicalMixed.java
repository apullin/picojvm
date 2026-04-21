public class N20_InvalidBooleanLogicalMixed {
    public static void main(String[] args) {
        if (true && 1) {
            Native.putchar('Y');
        }
    }
}
