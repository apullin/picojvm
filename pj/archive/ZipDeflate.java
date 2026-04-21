package pj.archive;

import pj.io.Files;

class ZipDeflate {
    private static final int LOOK_MAX = 258;
    private static final int LOOK_BUF = 512;

    private static byte[] history;
    private static byte[] lookBuf;
    private static int[] match;
    private static int histPos;
    private static int histLen;
    private static int lookPos;
    private static int lookLen;

    private static void ensureWork() {
        if (history == null) history = new byte[ZipTables.HOUSE_WINDOW];
        if (lookBuf == null) lookBuf = new byte[LOOK_BUF];
        if (match == null) match = new int[2];
    }

    private static void resetState() {
        histPos = 0;
        histLen = 0;
        lookPos = 0;
        lookLen = 0;
    }

    private static int curByte(int off) {
        return lookBuf[lookPos + off] & 0xFF;
    }

    private static int histByte(int dist, int off) {
        int idx = histPos - dist + off;
        while (idx < 0) idx += ZipTables.HOUSE_WINDOW;
        return history[idx] & 0xFF;
    }

    private static void compactLook() {
        if (lookPos == 0) return;
        for (int i = 0; i < lookLen; i++) lookBuf[i] = lookBuf[lookPos + i];
        lookPos = 0;
    }

    private static void fillLook() {
        int b = 0;
        if (lookPos + lookLen >= LOOK_BUF) compactLook();
        while (lookLen < LOOK_MAX && lookPos + lookLen < LOOK_BUF) {
            b = Files.readByte();
            if (b < 0) break;
            lookBuf[lookPos + lookLen] = (byte)b;
            lookLen++;
        }
    }

    private static void pushHistory(int b) {
        history[histPos] = (byte)b;
        histPos++;
        if (histPos == ZipTables.HOUSE_WINDOW) histPos = 0;
        if (histLen < ZipTables.HOUSE_WINDOW) histLen++;
    }

    private static int consumeByte(int[] stats) {
        int b = curByte(0);
        stats[0] = Zip.crc32UpdateByte(stats[0], b);
        stats[1]++;
        pushHistory(b);
        lookPos++;
        lookLen--;
        fillLook();
        return b;
    }

    private static int lengthCode(int len) {
        for (int i = 0; i < ZipTables.LEN_BASE.length; i++) {
            int base = ZipTables.LEN_BASE[i];
            int max = base + ((1 << ZipTables.LEN_EXTRA[i]) - 1);
            if (len >= base && len <= max) return i;
        }
        return -1;
    }

    private static int distCode(int dist) {
        for (int i = 0; i < ZipTables.DIST_BASE.length; i++) {
            int base = ZipTables.DIST_BASE[i];
            int max = base + ((1 << ZipTables.DIST_EXTRA[i]) - 1);
            if (dist >= base && dist <= max) return i;
        }
        return -1;
    }

    private static int findMatch() {
        int bestLen = 0;
        int bestDist = 0;
        int maxDist = histLen;
        int maxLen = lookLen;
        if (maxLen > LOOK_MAX) maxLen = LOOK_MAX;
        if (maxLen < 3) {
            match[0] = 0;
            match[1] = 0;
            return 0;
        }

        for (int dist = 1; dist <= maxDist; dist++) {
            if (histByte(dist, 0) != curByte(0)) continue;
            int len = 1;
            while (len < maxLen) {
                int cand;
                if (len < dist) cand = histByte(dist, len);
                else cand = curByte(len - dist);
                if (cand != curByte(len)) break;
                len++;
            }
            if (len >= 3 && len > bestLen) {
                bestLen = len;
                bestDist = dist;
                if (len >= 64) break;
            }
        }

        match[0] = bestLen;
        match[1] = bestDist;
        return bestLen;
    }

    private static int deflateFixed(int[] stats, boolean doWrite) {
        ensureWork();
        resetState();
        fillLook();
        if (ZipDeflateBits.begin(doWrite) < 0) return -1;
        while (lookLen > 0) {
            int len = findMatch();
            if (len >= 3) {
                int code = lengthCode(len);
                int dist = distCode(match[1]);
                if (code < 0 || dist < 0) return -1;
                if (!ZipDeflateBits.emitMatch(
                        code,
                        len - ZipTables.LEN_BASE[code],
                        ZipTables.LEN_EXTRA[code],
                        dist,
                        match[1] - ZipTables.DIST_BASE[dist],
                        ZipTables.DIST_EXTRA[dist])) return -1;
                for (int i = 0; i < len; i++) consumeByte(stats);
            } else {
                if (!ZipDeflateBits.emitLiteral(curByte(0))) return -1;
                consumeByte(stats);
            }
        }
        return ZipDeflateBits.finish();
    }

    static int writeCompressedFile(String srcPath, String entryName, byte[] hdr, byte[] scratch, int[] info) {
        int[] stats = new int[2];
        int crc = 0;
        int size = 0;
        int compLen = 0;
        int hdrLen = 0;

        if (Files.openRead(srcPath) != 0) return -1;
        stats[0] = -1;
        stats[1] = 0;
        compLen = deflateFixed(stats, false);
        Files.close(Files.MODE_READ);
        if (compLen < 0) return -1;

        crc = ~stats[0];
        size = stats[1];

        hdrLen = Zip.buildLocalHeader(hdr, entryName, 0, Zip.METHOD_DEFLATE, size, compLen, crc);
        Files.write(hdr, 0, hdrLen);

        if (Files.openRead(srcPath) != 0) return -1;
        stats[0] = -1;
        stats[1] = 0;
        if (deflateFixed(stats, true) != compLen) {
            Files.close(Files.MODE_READ);
            return -1;
        }
        Files.close(Files.MODE_READ);

        info[0] = Zip.METHOD_DEFLATE;
        info[1] = size;
        info[2] = compLen;
        info[3] = crc;
        return hdrLen + compLen;
    }
}
