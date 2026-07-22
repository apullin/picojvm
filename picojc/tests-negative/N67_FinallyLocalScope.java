public class N67_FinallyLocalScope {
    public static void main(String[] args) {
        try {
            Native.putchar(1);
        } finally {
            int inside = 2;
        }
        Native.putchar(inside);
    }
}
