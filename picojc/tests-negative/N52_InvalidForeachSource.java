public class N52_InvalidForeachSource {
    public static void main(String[] args) {
        for (int value : 1) {
            Native.putchar(value);
        }
    }
}
