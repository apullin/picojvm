public class N10_FieldAssignExprValue {
    int value;

    public static void main(String[] args) {
        N10_FieldAssignExprValue x = new N10_FieldAssignExprValue();
        int y = x.value = 2;
        Native.putchar(y);
    }
}
