package pj.archive;

import pj.io.Files;

public class ZipWrite {
    public static int writeStoredFile(String srcPath, String entryName, byte[] hdr, byte[] scratch, int size, int crc) {
        int hdrLen;
        if (Files.openRead(srcPath) != 0) return -1;
        hdrLen = Zip.buildStoredLocalHeader(hdr, entryName, size, crc);
        Files.write(hdr, 0, hdrLen);
        Files.copyCurrent(size, scratch);
        Files.close(Files.MODE_READ);
        return hdrLen + size;
    }

    public static int writeStoredCentral(String entryName, byte[] hdr, int size, int crc, int localOffset) {
        int hdrLen = Zip.buildStoredCentralHeader(hdr, entryName, size, crc, localOffset);
        Files.write(hdr, 0, hdrLen);
        return hdrLen;
    }

    public static int writeCompressedFile(String srcPath, String entryName, byte[] hdr, byte[] scratch, int[] info) {
        return ZipDeflate.writeCompressedFile(srcPath, entryName, hdr, scratch, info);
    }

    public static int writeCentral(String entryName, byte[] hdr, int method, int size, int compSize, int crc, int localOffset) {
        int hdrLen = Zip.buildCentralHeader(hdr, entryName, 0, method, size, compSize, crc, localOffset);
        Files.write(hdr, 0, hdrLen);
        return hdrLen;
    }

    public static int writeEnd(byte[] hdr, int entries, int centralSize, int centralOffset) {
        int len = Zip.buildEndRecord(hdr, entries, centralSize, centralOffset);
        Files.write(hdr, 0, len);
        return len;
    }
}
