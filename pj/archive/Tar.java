package pj.archive;

import pj.Native;
import pj.io.Files;
import pj.util.Bytes;

/*
 * Minimal ustar regular-file support.
 *
 * Scope is intentionally narrow:
 * - ASCII file names up to 100 bytes
 * - regular files only
 * - current native file model (one read handle, one write handle)
 */
public class Tar {
    public static final int BLOCK = 512;

    private static final int NAME_OFF = 0;
    private static final int NAME_LEN = 100;
    private static final int MODE_OFF = 100;
    private static final int UID_OFF = 108;
    private static final int GID_OFF = 116;
    private static final int SIZE_OFF = 124;
    private static final int SIZE_LEN = 12;
    private static final int MTIME_OFF = 136;
    private static final int CHKSUM_OFF = 148;
    private static final int CHKSUM_LEN = 8;
    private static final int TYPE_OFF = 156;
    private static final int MAGIC_OFF = 257;
    private static final int VERSION_OFF = 263;

    private static void writeAscii(byte[] hdr, int off, int len, String s) {
        int n = s.length();
        if (n > len) n = len;
        for (int i = 0; i < n; i++) hdr[off + i] = (byte)s.charAt(i);
    }

    private static void writeOctal(byte[] hdr, int off, int len, int v) {
        int pos = off + len - 2;
        hdr[off + len - 1] = 0;
        while (pos >= off && v > 0) {
            hdr[pos--] = (byte)('0' + (v & 7));
            v >>= 3;
        }
        while (pos >= off) hdr[pos--] = (byte)'0';
    }

    private static int parseOctal(byte[] hdr, int off, int len) {
        int v = 0;
        for (int i = 0; i < len; i++) {
            int ch = hdr[off + i] & 0xFF;
            if (ch == 0 || ch == ' ' || ch == '\n') break;
            if (ch < '0' || ch > '7') continue;
            v = (v << 3) + (ch - '0');
        }
        return v;
    }

    private static int checksum(byte[] hdr) {
        int sum = 0;
        for (int i = 0; i < BLOCK; i++) {
            if (i >= CHKSUM_OFF && i < CHKSUM_OFF + CHKSUM_LEN) sum += 32;
            else sum += hdr[i] & 0xFF;
        }
        return sum;
    }

    private static int roundUp(int n) {
        return (n + BLOCK - 1) & -BLOCK;
    }

    public static void buildFileHeader(byte[] hdr, String name, int size) {
        Bytes.clear(hdr, 0, hdr.length);
        writeAscii(hdr, NAME_OFF, NAME_LEN, name);
        writeOctal(hdr, MODE_OFF, 8, 0644);
        writeOctal(hdr, UID_OFF, 8, 0);
        writeOctal(hdr, GID_OFF, 8, 0);
        writeOctal(hdr, SIZE_OFF, SIZE_LEN, size);
        writeOctal(hdr, MTIME_OFF, 12, 0);
        hdr[TYPE_OFF] = (byte)'0';
        writeAscii(hdr, MAGIC_OFF, 6, "ustar");
        hdr[MAGIC_OFF + 5] = 0;
        writeAscii(hdr, VERSION_OFF, 2, "00");
        int sum = checksum(hdr);
        writeOctal(hdr, CHKSUM_OFF, CHKSUM_LEN, sum);
        hdr[CHKSUM_OFF + 6] = 0;
        hdr[CHKSUM_OFF + 7] = (byte)' ';
    }

    public static int addFile(String srcPath, String entryName, byte[] hdr, byte[] scratch) {
        int size = Files.countBytes(srcPath, scratch);
        if (size < 0) return -1;
        if (Files.openRead(srcPath) != 0) return -1;
        buildFileHeader(hdr, entryName, size);
        Files.write(hdr, 0, BLOCK);
        Files.copyCurrent(size, scratch);
        Files.close(Files.MODE_READ);
        Files.writeZeros(roundUp(size) - size, scratch);
        return size;
    }

    public static void finishArchive(byte[] scratch) {
        Files.writeZeros(BLOCK * 2, scratch);
    }

    public static int readHeader(byte[] hdr) {
        int n = Files.readFully(hdr, 0, BLOCK);
        if (n == 0) return 0;
        if (n < BLOCK) return -1;
        for (int i = 0; i < BLOCK; i++) {
            if (hdr[i] != 0) return 1;
        }
        return 0;
    }

    public static String entryName(byte[] hdr) {
        int len = 0;
        while (len < NAME_LEN && hdr[len] != 0) len++;
        return Native.stringFromBytes(hdr, 0, len);
    }

    public static int entrySize(byte[] hdr) {
        return parseOctal(hdr, SIZE_OFF, SIZE_LEN);
    }

    public static int entryType(byte[] hdr) {
        int t = hdr[TYPE_OFF] & 0xFF;
        if (t == 0) return '0';
        return t;
    }

    public static void extractCurrent(String dstPath, byte[] hdr, byte[] scratch) {
        int size = entrySize(hdr);
        if (Files.openWrite(dstPath) != 0) {
            skipCurrent(hdr, scratch);
            return;
        }
        Files.copyCurrent(size, scratch);
        Files.close(Files.MODE_WRITE);
        Files.discardCurrent(roundUp(size) - size, scratch);
    }

    public static void skipCurrent(byte[] hdr, byte[] scratch) {
        int size = entrySize(hdr);
        Files.discardCurrent(roundUp(size), scratch);
    }
}
