package java.lang;

public final class Byte {
    private final byte value;

    public Byte(byte value) {
        this.value = value;
    }

    public static Byte valueOf(byte value) {
        return new Byte(value);
    }

    public byte byteValue() {
        return value;
    }

    public int intValue() {
        return value;
    }

    public String toString() {
        return Integer.toString(value);
    }
}
