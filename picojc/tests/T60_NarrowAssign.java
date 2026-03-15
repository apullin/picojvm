public class T60_NarrowAssign {
    static final int CONST_A = 65;
    static byte fb = CONST_A;
    static short fs;

    static byte id(byte x) {
        return x;
    }

    public static void main(String[] args) {
        byte b = 66;
        short s = b;
        char c = 'C';
        byte d = id(b);
        fs = b;

        Native.putchar(fb);
        Native.putchar(b);
        Native.putchar(s);
        Native.putchar(c);
        Native.putchar(d);
        Native.putchar(fs);
        Native.putchar(10);
        Native.halt();
    }
}
