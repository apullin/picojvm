public class N53_InvalidForeachElement {
    public static void main(String[] args) {
        int[] values = new int[1];
        for (String value : values) {
            Native.putchar(value.length());
        }
    }
}
