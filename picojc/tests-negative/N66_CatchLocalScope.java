public class N66_CatchLocalScope {
    public static void main(String[] args) {
        try {
            Native.putchar(1);
        } catch (RuntimeException error) {
        }
        throw error;
    }
}
