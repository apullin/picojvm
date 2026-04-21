package pj.archive;

import pj.io.Files;

class ZipDeflateBits {
    private static byte[] fixedLitLens;
    private static byte[] fixedDistLens;
    private static int[] fixedLitCodes;
    private static int[] fixedDistCodes;
    private static int[] bits;
    private static boolean writeOut;

    private static void ensureFixedTrees() {
        if (fixedLitLens != null) return;

        fixedLitLens = new byte[288];
        fixedDistLens = new byte[32];
        fixedLitCodes = new int[288];
        fixedDistCodes = new int[32];

        for (int i = 0; i <= 143; i++) fixedLitLens[i] = 8;
        for (int i = 144; i <= 255; i++) fixedLitLens[i] = 9;
        for (int i = 256; i <= 279; i++) fixedLitLens[i] = 7;
        for (int i = 280; i <= 287; i++) fixedLitLens[i] = 8;
        for (int i = 0; i < 32; i++) fixedDistLens[i] = 5;

        ZipHuffman.buildCodes(fixedLitLens, 288, fixedLitCodes);
        ZipHuffman.buildCodes(fixedDistLens, 32, fixedDistCodes);
    }

    private static void ensureWork() {
        if (bits == null) bits = new int[3];
    }

    private static int shl(int value, int count) {
        while (count > 0) {
            value = value << 1;
            count--;
        }
        return value;
    }

    private static boolean writeBits(int value, int count) {
        while (count > 0) {
            bits[1] = bits[1] | shl(value & 1, bits[2]);
            value = value >>> 1;
            bits[2]++;
            count--;
            if (bits[2] == 8) {
                if (writeOut) Files.writeByte(bits[1]);
                bits[0]++;
                bits[1] = 0;
                bits[2] = 0;
            }
        }
        return true;
    }

    static int begin(boolean doWrite) {
        ensureWork();
        ensureFixedTrees();
        bits[0] = 0;
        bits[1] = 0;
        bits[2] = 0;
        writeOut = doWrite;
        if (writeBits(3, 3)) return 0;
        return -1;
    }

    static boolean emitLiteral(int sym) {
        return writeBits(fixedLitCodes[sym], fixedLitLens[sym] & 0xFF);
    }

    static boolean emitMatch(int lenCode, int lenExtraVal, int lenExtraBits,
                             int distCode, int distExtraVal, int distExtraBits) {
        if (!emitLiteral(257 + lenCode)) return false;
        if (lenExtraBits != 0 && !writeBits(lenExtraVal, lenExtraBits)) return false;
        if (!writeBits(fixedDistCodes[distCode], fixedDistLens[distCode] & 0xFF)) return false;
        return distExtraBits == 0 || writeBits(distExtraVal, distExtraBits);
    }

    static int finish() {
        if (!emitLiteral(256)) return -1;
        if (bits[2] != 0) {
            if (writeOut) Files.writeByte(bits[1]);
            bits[0]++;
            bits[1] = 0;
            bits[2] = 0;
        }
        return bits[0];
    }
}
