// Native method stubs for JAVA/85.
// These are recognized by the JVM interpreter and dispatched to
// hardware-specific handlers on the 8085.

public class Native {
    public static native void putchar(int c);
    public static native int  in(int port);
    public static native void out(int port, int val);
    public static native int  peek(int addr);
    public static native void poke(int addr, int val);
    public static native void halt();
    public static native void print(String s);
}
