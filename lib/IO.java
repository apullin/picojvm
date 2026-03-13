/**
 * IO.java — Binary output helpers for picoJVM programs.
 *
 * Little-endian write routines and bulk byte output.
 */
public class IO {
    public static void writeShortLE(int v) {
        Native.putchar(v & 0xFF);
        Native.putchar((v >> 8) & 0xFF);
    }

    public static void writeIntLE(int v) {
        writeShortLE(v);
        writeShortLE(v >> 16);
    }

    public static void writeBytes(byte[] buf, int off, int len) {
        Native.writeBytes(buf, off, len);
    }

    public static void writeByte(int b) {
        Native.putchar(b & 0xFF);
    }
}
