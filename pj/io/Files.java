package pj.io;

import pj.Native;

/*
 * Minimal file helpers for picoJSE.
 *
 * The API stays intentionally small: path strings are treated as ASCII bytes
 * and the wrapper keeps close to the underlying native operations.
 */
public class Files {
    public static final int MODE_READ = 1;
    public static final int MODE_WRITE = 2;

    public static byte[] asciiBytes(String s) {
        int len = s.length();
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) buf[i] = (byte)s.charAt(i);
        return buf;
    }

    public static int openRead(String path) {
        byte[] name = asciiBytes(path);
        return Native.fileOpen(name, name.length, MODE_READ);
    }

    public static int openWrite(String path) {
        byte[] name = asciiBytes(path);
        return Native.fileOpen(name, name.length, MODE_WRITE);
    }

    public static int readByte() {
        return Native.fileReadByte();
    }

    public static int read(byte[] buf, int off, int len) {
        return Native.fileRead(buf, off, len);
    }

    public static void writeByte(int b) {
        Native.fileWriteByte(b);
    }

    public static void write(byte[] buf, int off, int len) {
        Native.fileWrite(buf, off, len);
    }

    public static void writeString(String s) {
        byte[] buf = asciiBytes(s);
        Native.fileWrite(buf, 0, buf.length);
    }

    public static void close(int mode) {
        Native.fileClose(mode);
    }

    public static int delete(String path) {
        byte[] name = asciiBytes(path);
        return Native.fileDelete(name, name.length);
    }
}
