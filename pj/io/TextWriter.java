package pj.io;

import pj.Native;

/*
 * Routed text output for picoJSE.
 *
 * stdout stays easy for host-side tools, but target code can route text to a
 * specific port-backed device without changing call sites all over the app.
 */
public class TextWriter {
    public static final int DEST_STDOUT = 0;
    public static final int DEST_PORT = 1;

    private int dest;
    private int port;

    public TextWriter(int dest, int port) {
        this.dest = dest;
        this.port = port;
    }

    public static TextWriter stdout() {
        return new TextWriter(DEST_STDOUT, 0);
    }

    public static TextWriter port(int port) {
        return new TextWriter(DEST_PORT, port);
    }

    public int destination() {
        return dest;
    }

    public int portNumber() {
        return port;
    }

    public void writeByte(int b) {
        if (dest == DEST_PORT) {
            Native.out(port, b & 0xFF);
        } else {
            Native.putchar(b & 0xFF);
        }
    }

    public void writeBytes(byte[] buf, int off, int len) {
        if (dest == DEST_PORT) {
            for (int i = 0; i < len; i++) writeByte(buf[off + i] & 0xFF);
        } else {
            Native.writeBytes(buf, off, len);
        }
    }

    public void print(String s) {
        int len;
        if (dest == DEST_STDOUT) {
            Native.print(s);
            return;
        }
        len = s.length();
        for (int i = 0; i < len; i++) writeByte(s.charAt(i));
    }

    public void printChar(int c) {
        writeByte(c);
    }

    public void println() {
        writeByte('\n');
    }

    public void println(String s) {
        print(s);
        println();
    }

    public void printInt(int v) {
        if (v == -2147483648) {
            print("-2147483648");
            return;
        }
        if (v < 0) {
            writeByte('-');
            v = -v;
        }
        if (v >= 10) printInt(v / 10);
        writeByte('0' + (v % 10));
    }

    public void printlnInt(int v) {
        printInt(v);
        println();
    }

    public void printHex(int v, int digits) {
        for (int i = digits - 1; i >= 0; i--) {
            int nib = (v >> (i * 4)) & 0xF;
            writeByte(nib < 10 ? ('0' + nib) : ('A' + nib - 10));
        }
    }
}
