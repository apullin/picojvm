/** Regression coverage for JVM field lookup through a hidden declaration. */
class FieldBase {
    int value;
    static int code;
    static int mixed;
}

class FieldChild extends FieldBase {
    int value;
    static int code;
    int mixed;
}

public class FieldHidingTest {
    public static void main(String[] args) {
        FieldChild child = new FieldChild();
        child.value = 2;
        ((FieldBase) child).value = 1;
        FieldChild.code = 4;
        FieldBase.code = 3;
        child.mixed = 6;
        FieldBase.mixed = 5;

        Native.putchar(child.value);
        Native.putchar(((FieldBase) child).value);
        Native.putchar(FieldChild.code);
        Native.putchar(FieldBase.code);
        Native.putchar(child.mixed);
        Native.putchar(FieldBase.mixed);
        Native.halt();
    }
}
