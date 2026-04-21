package pj.archive;

import pj.io.Binary;
import pj.io.Files;

class ZipInflate {
    private static byte[] fixedLitLens;
    private static byte[] fixedDistLens;
    private static int[] fixedLitCodes;
    private static int[] fixedDistCodes;
    private static int[] fixedLitStarts;
    private static int[] fixedLitCounts;
    private static int[] fixedDistStarts;
    private static int[] fixedDistCounts;
    private static int[] fixedLitDecCodes;
    private static int[] fixedLitDecSyms;
    private static int[] fixedDistDecCodes;
    private static int[] fixedDistDecSyms;
    private static int fixedLitMaxBits;
    private static int fixedDistMaxBits;

    private static byte[] window;
    private static int[] bits;
    private static int[] outState;
    private static int compRemain;
    private static boolean discardOut;

    private static void ensureFixedTrees() {
        if (fixedLitLens != null) return;

        fixedLitLens = new byte[288];
        fixedDistLens = new byte[32];
        fixedLitCodes = new int[288];
        fixedDistCodes = new int[32];
        fixedLitStarts = new int[ZipTables.MAX_BITS + 1];
        fixedLitCounts = new int[ZipTables.MAX_BITS + 1];
        fixedDistStarts = new int[ZipTables.MAX_BITS + 1];
        fixedDistCounts = new int[ZipTables.MAX_BITS + 1];
        fixedLitDecCodes = new int[288];
        fixedLitDecSyms = new int[288];
        fixedDistDecCodes = new int[32];
        fixedDistDecSyms = new int[32];

        for (int i = 0; i <= 143; i++) fixedLitLens[i] = 8;
        for (int i = 144; i <= 255; i++) fixedLitLens[i] = 9;
        for (int i = 256; i <= 279; i++) fixedLitLens[i] = 7;
        for (int i = 280; i <= 287; i++) fixedLitLens[i] = 8;
        for (int i = 0; i < 32; i++) fixedDistLens[i] = 5;

        fixedLitMaxBits = ZipHuffman.buildCodes(fixedLitLens, 288, fixedLitCodes);
        fixedDistMaxBits = ZipHuffman.buildCodes(fixedDistLens, 32, fixedDistCodes);
        ZipHuffman.buildDecodeTables(fixedLitLens, fixedLitCodes, 288, fixedLitStarts, fixedLitCounts, fixedLitDecCodes, fixedLitDecSyms);
        ZipHuffman.buildDecodeTables(fixedDistLens, fixedDistCodes, 32, fixedDistStarts, fixedDistCounts, fixedDistDecCodes, fixedDistDecSyms);
    }

    private static void ensureWork() {
        if (window == null) window = new byte[ZipTables.HOUSE_WINDOW];
        if (bits == null) bits = new int[3];
        if (outState == null) outState = new int[4];
    }

    private static int readCompByte() {
        int b = 0;
        if (compRemain == 0) return -1;
        b = Files.readByte();
        if (b < 0) return -1;
        if (compRemain > 0) compRemain--;
        return b;
    }

    private static int ushr(int value, int count) {
        while (count > 0) {
            value = value >>> 1;
            count--;
        }
        return value;
    }

    private static int shl(int value, int count) {
        while (count > 0) {
            value = value << 1;
            count--;
        }
        return value;
    }

    private static int readBits(int need) {
        int b = 0;
        int mask = 0;
        int out = 0;
        int cur = 0;
        while (bits[2] < need) {
            b = readCompByte();
            if (b < 0) return -1;
            bits[1] = bits[1] | shl(b & 0xFF, bits[2]);
            bits[2] += 8;
        }
        if (need == 16) {
            mask = 0xFFFF;
        } else {
            mask = shl(1, need) - 1;
        }
        out = bits[1] & mask;
        cur = bits[1];
        cur = ushr(cur, need);
        bits[1] = cur;
        bits[2] = bits[2] - need;
        return out;
    }

    private static void alignBits() {
        bits[1] = ushr(bits[1], bits[2] & 7);
        bits[2] = bits[2] - (bits[2] & 7);
    }

    private static int decodeSymbol(int[] starts, int[] counts, int[] decCodes, int[] decSyms, int maxBits) {
        int code = 0;
        int bit = 0;
        int start = 0;
        int count = 0;
        for (int len = 1; len <= maxBits; len++) {
            bit = readBits(1);
            if (bit < 0) return -1;
            code = code | shl(bit, len - 1);
            start = starts[len];
            count = counts[len];
            for (int i = 0; i < count; i++) {
                if (decCodes[start + i] == code) return decSyms[start + i];
            }
        }
        return -1;
    }

    private static boolean outByte(int b, byte[] scratch) {
        window[outState[0]] = (byte)b;
        outState[0] = (outState[0] + 1) & ZipTables.HOUSE_WINDOW_MASK;
        scratch[outState[1]++] = (byte)b;
        outState[2] = Zip.crc32UpdateByte(outState[2], b);
        outState[3]++;
        if (outState[1] == scratch.length) {
            if (!discardOut) Files.write(scratch, 0, outState[1]);
            outState[1] = 0;
        }
        return true;
    }

    private static void flushOut(byte[] scratch) {
        if (outState[1] > 0) {
            if (!discardOut) Files.write(scratch, 0, outState[1]);
            outState[1] = 0;
        }
    }

    private static boolean copyMatch(int len, int dist, byte[] scratch) {
        int src = 0;
        int b = 0;
        if (dist <= 0 || dist > ZipTables.HOUSE_WINDOW || dist > outState[3]) return false;
        src = (outState[0] - dist) & ZipTables.HOUSE_WINDOW_MASK;
        for (int i = 0; i < len; i++) {
            b = window[src] & 0xFF;
            src = (src + 1) & ZipTables.HOUSE_WINDOW_MASK;
            if (!outByte(b, scratch)) return false;
        }
        return true;
    }

    private static boolean inflateFixedBlock(byte[] scratch) {
        int sym = 0;
        int lenIdx = 0;
        int len = 0;
        int distSym = 0;
        int dist = 0;
        int extra = 0;
        for (;;) {
            sym = decodeSymbol(fixedLitStarts, fixedLitCounts, fixedLitDecCodes, fixedLitDecSyms, fixedLitMaxBits);
            if (sym < 0) return false;
            if (sym < 256) {
                if (!outByte(sym, scratch)) return false;
            } else if (sym == 256) {
                return true;
            } else {
                lenIdx = sym - 257;
                if (lenIdx < 0 || lenIdx >= ZipTables.LEN_BASE.length) return false;
                len = ZipTables.LEN_BASE[lenIdx];
                if (ZipTables.LEN_EXTRA[lenIdx] != 0) {
                    extra = readBits(ZipTables.LEN_EXTRA[lenIdx]);
                    if (extra < 0) return false;
                    len += extra;
                }

                distSym = decodeSymbol(fixedDistStarts, fixedDistCounts, fixedDistDecCodes, fixedDistDecSyms, fixedDistMaxBits);
                if (distSym < 0 || distSym >= ZipTables.DIST_BASE.length) return false;
                dist = ZipTables.DIST_BASE[distSym];
                if (ZipTables.DIST_EXTRA[distSym] != 0) {
                    extra = readBits(ZipTables.DIST_EXTRA[distSym]);
                    if (extra < 0) return false;
                    dist += extra;
                }
                if (!copyMatch(len, dist, scratch)) return false;
            }
        }
    }

    private static boolean inflateToFile(int expectedSize, int expectedCrc, byte[] scratch) {
        int finalBlock = 0;
        int type = 0;
        int len = 0;
        int nlen = 0;
        int b = 0;
        ensureWork();
        ensureFixedTrees();

        bits[1] = 0;
        bits[2] = 0;
        outState[0] = 0;
        outState[1] = 0;
        outState[2] = -1;
        outState[3] = 0;

        for (;;) {
            finalBlock = readBits(1);
            type = readBits(2);
            if (finalBlock < 0 || type < 0) return false;

            if (type == 0) {
                alignBits();
                len = readBits(16);
                nlen = readBits(16);
                if (len < 0 || nlen < 0) return false;
                if (((len ^ 0xFFFF) & 0xFFFF) != nlen) return false;
                for (int i = 0; i < len; i++) {
                    b = readBits(8);
                    if (b < 0 || !outByte(b, scratch)) return false;
                }
            } else if (type == 1) {
                if (!inflateFixedBlock(scratch)) return false;
            } else {
                return false;
            }

            if (finalBlock != 0) break;
        }

        flushOut(scratch);
        return outState[3] == expectedSize &&
               (~outState[2]) == expectedCrc;
    }

    static boolean extractDeflatedCurrent(String dstPath, byte[] hdr, byte[] scratch) {
        boolean ok = false;
        if (!Zip.isDeflatedEntry(hdr)) return false;

        compRemain = Zip.entryCompressedSize(hdr);
        discardOut = dstPath == null;
        if (!discardOut && Files.openWrite(dstPath) != 0) return false;
        ok = inflateToFile(Zip.entrySize(hdr), Binary.readIntLE(hdr, Zip.CRC_OFF), scratch);
        if (!discardOut) Files.close(Files.MODE_WRITE);
        if (!discardOut && !ok) Files.delete(dstPath);
        return ok;
    }
}
