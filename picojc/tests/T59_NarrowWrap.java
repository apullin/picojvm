public class T59_NarrowWrap {
    static byte sb = 127;
    static short ss = 32767;
    static char sc = 65535;

    static byte bumpB(byte x) {
        x++;
        return x;
    }

    static short bumpS(short x) {
        x++;
        return x;
    }

    static char bumpC(char x) {
        x++;
        return x;
    }

    public static void main(String[] args) {
        byte b = 127;
        b++;
        Native.putchar(b < 0 ? 'B' : 'b');

        short s = 32767;
        s++;
        Native.putchar(s < 0 ? 'S' : 's');

        char c = 65535;
        c++;
        Native.putchar(c == 0 ? 'C' : 'c');

        Native.putchar(bumpB((byte) 127) < 0 ? 'D' : 'd');
        Native.putchar(bumpS((short) 32767) < 0 ? 'E' : 'e');
        Native.putchar(bumpC((char) 65535) == 0 ? 'F' : 'f');

        sb++;
        ss++;
        sc++;
        Native.putchar(sb < 0 ? 'G' : 'g');
        Native.putchar(ss < 0 ? 'H' : 'h');
        Native.putchar(sc == 0 ? 'I' : 'i');

        Native.putchar(10);
        Native.halt();
    }
}
