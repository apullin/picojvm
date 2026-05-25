package java.lang;

public final class Boolean {
    public static final Boolean TRUE = new Boolean(true);
    public static final Boolean FALSE = new Boolean(false);

    private final boolean value;

    private Boolean(boolean value) {
        this.value = value;
    }

    public static Boolean valueOf(boolean value) {
        return value ? TRUE : FALSE;
    }

    public boolean booleanValue() {
        return value;
    }

    public static String toString(boolean value) {
        return value ? "true" : "false";
    }

    public String toString() {
        return toString(value);
    }
}
