package java.lang;

public final class Short {
    private final short value;

    public Short(short value) {
        this.value = value;
    }

    public static Short valueOf(short value) {
        return new Short(value);
    }

    public short shortValue() {
        return value;
    }

    public int intValue() {
        return value;
    }

    public String toString() {
        return Integer.toString(value);
    }
}
