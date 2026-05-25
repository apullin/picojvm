package java.lang;

public final class System {
    public static java.io.PrintStream err;
    public static java.io.InputStream in;

    private System() {
    }

    public static native void arraycopy(Object src, int srcPos,
                                        Object dst, int dstPos,
                                        int length);

    public static native int identityHashCode(Object value);

    public static String getProperty(String name) {
        return null;
    }

    public static String getProperty(String name, String def) {
        return def;
    }

    public static String getenv(String name) {
        return null;
    }
}
