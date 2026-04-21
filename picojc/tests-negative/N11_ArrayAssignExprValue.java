public class N11_ArrayAssignExprValue {
    static int[] values = { 1 };

    public static void main(String[] args) {
        int y = values[0] = 2;
        Native.putchar(y);
    }
}
