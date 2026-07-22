public class N65_TryLocalScope {
    public static void main(String[] args) {
        try {
            int inside = 1;
        } catch (RuntimeException error) {
        }
        Native.putchar(inside);
    }
}
