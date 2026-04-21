package pj.archive;

class ZipHuffman {
    static int reverseBits(int code, int len) {
        int out = 0;
        for (int i = 0; i < len; i++) {
            out = (out << 1) | (code & 1);
            code = code >>> 1;
        }
        return out;
    }

    static int buildCodes(byte[] lens, int symbols, int[] codes) {
        int[] counts = new int[ZipTables.MAX_BITS + 1];
        int[] next = new int[ZipTables.MAX_BITS + 1];
        int code = 0;
        int maxBits = 0;

        for (int i = 0; i < symbols; i++) {
            int len = lens[i] & 0xFF;
            if (len != 0) {
                counts[len]++;
                if (len > maxBits) {
                    maxBits = len;
                }
            }
        }

        for (int len = 1; len <= ZipTables.MAX_BITS; len++) {
            code = (code + counts[len - 1]) << 1;
            next[len] = code;
        }

        for (int sym = 0; sym < symbols; sym++) {
            int len = lens[sym] & 0xFF;
            if (len == 0) {
                codes[sym] = -1;
            } else {
                codes[sym] = reverseBits(next[len], len);
                next[len]++;
            }
        }
        return maxBits;
    }

    static void buildDecodeTables(byte[] lens, int[] codes, int symbols,
                                  int[] starts, int[] counts,
                                  int[] decCodes, int[] decSyms) {
        int[] fill = new int[ZipTables.MAX_BITS + 1];

        for (int i = 0; i <= ZipTables.MAX_BITS; i++) {
            starts[i] = 0;
            counts[i] = 0;
        }
        for (int i = 0; i < symbols; i++) {
            int len = lens[i] & 0xFF;
            if (len != 0) {
                counts[len]++;
            }
        }
        for (int len = 1; len <= ZipTables.MAX_BITS; len++) {
            starts[len] = starts[len - 1] + counts[len - 1];
            fill[len] = starts[len];
        }
        for (int sym = 0; sym < symbols; sym++) {
            int len = lens[sym] & 0xFF;
            if (len != 0) {
                int idx = fill[len]++;
                decCodes[idx] = codes[sym];
                decSyms[idx] = sym;
            }
        }
    }
}
