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

    public static int readFully(byte[] buf, int off, int len) {
        int done = 0;
        while (done < len) {
            int n = Native.fileRead(buf, off + done, len - done);
            if (n <= 0) break;
            done += n;
        }
        return done;
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

    public static int countBytes(String path, byte[] scratch) {
        int total = 0;
        int n;
        if (openRead(path) != 0) return -1;
        for (;;) {
            n = read(scratch, 0, scratch.length);
            if (n <= 0) break;
            total += n;
        }
        close(MODE_READ);
        return total;
    }

    public static void copyCurrent(int len, byte[] scratch) {
        int left = len;
        while (left > 0) {
            int want = left;
            if (want > scratch.length) want = scratch.length;
            int n = readFully(scratch, 0, want);
            if (n <= 0) return;
            write(scratch, 0, n);
            left -= n;
        }
    }

    public static void discardCurrent(int len, byte[] scratch) {
        int left = len;
        while (left > 0) {
            int want = left;
            if (want > scratch.length) want = scratch.length;
            int n = readFully(scratch, 0, want);
            if (n <= 0) return;
            left -= n;
        }
    }

    public static void writeZeros(int len, byte[] scratch) {
        int left = len;
        clear(scratch);
        while (left > 0) {
            int n = left;
            if (n > scratch.length) n = scratch.length;
            write(scratch, 0, n);
            left -= n;
        }
    }

    public static void clear(byte[] buf) {
        for (int i = 0; i < buf.length; i++) buf[i] = 0;
    }

    public static void close(int mode) {
        Native.fileClose(mode);
    }

    public static int delete(String path) {
        byte[] name = asciiBytes(path);
        return Native.fileDelete(name, name.length);
    }
}
