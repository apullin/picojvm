public class N50_InvalidArrayIndex {
    public static void main(String[] args) {
        int[] values = new int[1];
        Native.putchar(values[true]);
    }
}
