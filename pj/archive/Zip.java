package pj.archive;

import pj.Native;
import pj.io.Binary;
import pj.io.Files;
import pj.util.Bytes;

/*
 * Common ZIP helpers for regular-file archives.
 *
 * Scope:
 * - local headers plus central directory
 * - CRC and field helpers
 * - no read/write orchestration
 */
public class Zip {
    public static final int LOCAL_SIG   = 0x04034B50;
    public static final int CENTRAL_SIG = 0x02014B50;
    public static final int END_SIG     = 0x06054B50;

    public static final int METHOD_STORE   = 0;
    public static final int METHOD_DEFLATE = 8;

    static final int LOCAL_NAME_LEN_OFF  = 26;
    static final int LOCAL_EXTRA_LEN_OFF = 28;
    static final int CRC_OFF             = 14;
    static final int COMP_SIZE_OFF       = 18;
    static final int SIZE_OFF            = 22;
    static final int METHOD_OFF          = 8;
    static final int FLAGS_OFF           = 6;

    static void writeAscii(byte[] hdr, int off, String s) {
        int len = s.length();
        for (int i = 0; i < len; i++) hdr[off + i] = (byte)s.charAt(i);
    }

    public static int crc32UpdateByte(int crc, int b) {
        crc = crc ^ (b & 0xFF);
        for (int i = 0; i < 8; i++) {
            if ((crc & 1) != 0) crc = (crc >>> 1) ^ 0xEDB88320;
            else crc = crc >>> 1;
        }
        return crc;
    }

    public static int crc32Bytes(byte[] buf, int off, int len, int crc) {
        for (int i = 0; i < len; i++) crc = crc32UpdateByte(crc, buf[off + i]);
        return crc;
    }

    public static int crc32File(String path, byte[] scratch) {
        int n;
        int crc = -1;
        if (Files.openRead(path) != 0) return -1;
        for (;;) {
            n = Files.read(scratch, 0, scratch.length);
            if (n <= 0) break;
            crc = crc32Bytes(scratch, 0, n, crc);
        }
        Files.close(Files.MODE_READ);
        return ~crc;
    }

    static int buildLocalHeader(byte[] hdr, String name, int flags, int method, int size, int compSize, int crc) {
        int nameLen = name.length();
        Bytes.clear(hdr, 0, 30 + nameLen);
        Binary.writeIntLE(hdr, 0, LOCAL_SIG);
        Binary.writeShortLE(hdr, 4, 20);
        Binary.writeShortLE(hdr, FLAGS_OFF, flags);
        Binary.writeShortLE(hdr, METHOD_OFF, method);
        Binary.writeShortLE(hdr, 10, 0);
        Binary.writeShortLE(hdr, 12, 0);
        Binary.writeIntLE(hdr, CRC_OFF, crc);
        Binary.writeIntLE(hdr, COMP_SIZE_OFF, compSize);
        Binary.writeIntLE(hdr, SIZE_OFF, size);
        Binary.writeShortLE(hdr, LOCAL_NAME_LEN_OFF, nameLen);
        Binary.writeShortLE(hdr, LOCAL_EXTRA_LEN_OFF, 0);
        writeAscii(hdr, 30, name);
        return 30 + nameLen;
    }

    public static int buildStoredLocalHeader(byte[] hdr, String name, int size, int crc) {
        return buildLocalHeader(hdr, name, 0, METHOD_STORE, size, size, crc);
    }

    static int buildCentralHeader(byte[] hdr, String name, int flags, int method, int size, int compSize, int crc, int localOffset) {
        int nameLen = name.length();
        Bytes.clear(hdr, 0, 46 + nameLen);
        Binary.writeIntLE(hdr, 0, CENTRAL_SIG);
        Binary.writeShortLE(hdr, 4, 20);
        Binary.writeShortLE(hdr, 6, 20);
        Binary.writeShortLE(hdr, 8, flags);
        Binary.writeShortLE(hdr, 10, method);
        Binary.writeShortLE(hdr, 12, 0);
        Binary.writeShortLE(hdr, 14, 0);
        Binary.writeIntLE(hdr, 16, crc);
        Binary.writeIntLE(hdr, 20, compSize);
        Binary.writeIntLE(hdr, 24, size);
        Binary.writeShortLE(hdr, 28, nameLen);
        Binary.writeShortLE(hdr, 30, 0);
        Binary.writeShortLE(hdr, 32, 0);
        Binary.writeShortLE(hdr, 34, 0);
        Binary.writeShortLE(hdr, 36, 0);
        Binary.writeIntLE(hdr, 38, 0);
        Binary.writeIntLE(hdr, 42, localOffset);
        writeAscii(hdr, 46, name);
        return 46 + nameLen;
    }

    public static int buildStoredCentralHeader(byte[] hdr, String name, int size, int crc, int localOffset) {
        return buildCentralHeader(hdr, name, 0, METHOD_STORE, size, size, crc, localOffset);
    }

    public static int buildEndRecord(byte[] hdr, int entries, int centralSize, int centralOffset) {
        Bytes.clear(hdr, 0, 22);
        Binary.writeIntLE(hdr, 0, END_SIG);
        Binary.writeShortLE(hdr, 4, 0);
        Binary.writeShortLE(hdr, 6, 0);
        Binary.writeShortLE(hdr, 8, entries);
        Binary.writeShortLE(hdr, 10, entries);
        Binary.writeIntLE(hdr, 12, centralSize);
        Binary.writeIntLE(hdr, 16, centralOffset);
        Binary.writeShortLE(hdr, 20, 0);
        return 22;
    }

    public static int readLocalHeader(byte[] hdr, byte[] nameBuf, byte[] scratch) {
        int sig;
        int nameLen;
        int extraLen;
        int n = Files.readFully(hdr, 0, 30);
        if (n == 0) return 0;
        if (n < 30) return -1;
        sig = Binary.readIntLE(hdr, 0);
        if (sig == CENTRAL_SIG || sig == END_SIG) return 0;
        if (sig != LOCAL_SIG) return -1;

        nameLen = Binary.readU16LE(hdr, LOCAL_NAME_LEN_OFF);
        extraLen = Binary.readU16LE(hdr, LOCAL_EXTRA_LEN_OFF);
        if (nameLen > nameBuf.length) return -1;
        if (Files.readFully(nameBuf, 0, nameLen) < nameLen) return -1;
        Files.discardCurrent(extraLen, scratch);
        return nameLen;
    }

    public static String entryName(byte[] nameBuf, int nameLen) {
        return Native.stringFromBytes(nameBuf, 0, nameLen);
    }

    public static int entryFlags(byte[] hdr) {
        return Binary.readU16LE(hdr, FLAGS_OFF);
    }

    public static int entryMethod(byte[] hdr) {
        return Binary.readU16LE(hdr, METHOD_OFF);
    }

    public static int entryCompressedSize(byte[] hdr) {
        return Binary.readIntLE(hdr, COMP_SIZE_OFF);
    }

    public static int entrySize(byte[] hdr) {
        return Binary.readIntLE(hdr, SIZE_OFF);
    }

    public static boolean isStoredEntry(byte[] hdr) {
        return entryMethod(hdr) == METHOD_STORE && entryFlags(hdr) == 0;
    }

    public static boolean isDeflatedEntry(byte[] hdr) {
        return entryMethod(hdr) == METHOD_DEFLATE;
    }
}
