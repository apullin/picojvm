package pj.io;

/*
 * Binary helpers for little-endian buffers.
 */
public class Binary {
    public static int writeByte(byte[] buf, int off, int v) {
        buf[off] = (byte)v;
        return off + 1;
    }

    public static int writeShortLE(byte[] buf, int off, int v) {
        buf[off] = (byte)v;
        buf[off + 1] = (byte)(v >> 8);
        return off + 2;
    }

    public static int writeIntLE(byte[] buf, int off, int v) {
        off = writeShortLE(buf, off, v);
        return writeShortLE(buf, off, v >> 16);
    }

    public static int readU8(byte[] buf, int off) {
        return buf[off] & 0xFF;
    }

    public static int readU16LE(byte[] buf, int off) {
        return (buf[off] & 0xFF) | ((buf[off + 1] & 0xFF) << 8);
    }

    public static int readI16LE(byte[] buf, int off) {
        int v = readU16LE(buf, off);
        if (v >= 0x8000) return v - 0x10000;
        return v;
    }

    public static int readIntLE(byte[] buf, int off) {
        return readU16LE(buf, off) | (readU16LE(buf, off + 2) << 16);
    }
}
