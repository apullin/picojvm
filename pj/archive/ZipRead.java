package pj.archive;

import pj.io.Files;

public class ZipRead {
    public static void extractStoredCurrent(String dstPath, byte[] hdr, byte[] scratch) {
        int size = Zip.entryCompressedSize(hdr);
        if (!Zip.isStoredEntry(hdr)) {
            skipCurrent(hdr, scratch);
            return;
        }
        if (Files.openWrite(dstPath) != 0) {
            skipCurrent(hdr, scratch);
            return;
        }
        Files.copyCurrent(size, scratch);
        Files.close(Files.MODE_WRITE);
    }

    public static boolean extractDeflatedCurrent(String dstPath, byte[] hdr, byte[] scratch) {
        return ZipInflate.extractDeflatedCurrent(dstPath, hdr, scratch);
    }

    public static boolean extractCurrent(String dstPath, byte[] hdr, byte[] scratch) {
        if (Zip.isStoredEntry(hdr)) {
            extractStoredCurrent(dstPath, hdr, scratch);
            return true;
        }
        if (Zip.isDeflatedEntry(hdr)) {
            return extractDeflatedCurrent(dstPath, hdr, scratch);
        }
        skipCurrent(hdr, scratch);
        return false;
    }

    public static void skipCurrent(byte[] hdr, byte[] scratch) {
        if (Zip.entryMethod(hdr) == Zip.METHOD_DEFLATE) {
            extractDeflatedCurrent(null, hdr, scratch);
        } else {
            Files.discardCurrent(Zip.entryCompressedSize(hdr), scratch);
        }
    }
}
