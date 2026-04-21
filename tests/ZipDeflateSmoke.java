import pj.Native;
import pj.archive.Zip;
import pj.archive.ZipRead;
import pj.archive.ZipWrite;
import pj.io.Files;
import pj.text.Format;
import pj.text.Strings;

public class ZipDeflateSmoke {
    private static boolean eq(String a, String b) {
        return a.length() == b.length() &&
               Strings.regionEquals(a, 0, b, 0, a.length());
    }

    private static void dumpFile(String path, byte[] scratch) {
        int n;
        if (Files.openRead(path) != 0) return;
        for (;;) {
            n = Files.read(scratch, 0, scratch.length);
            if (n <= 0) break;
            Native.writeBytes(scratch, 0, n);
        }
        Files.close(Files.MODE_READ);
    }

    private static void makeRepeated(String path, String text, int count) {
        if (Files.openWrite(path) != 0) return;
        for (int i = 0; i < count; i++) Files.writeString(text);
        Files.close(Files.MODE_WRITE);
    }

    private static void cleanup() {
        Files.delete("build/pjzipdef_a.txt");
        Files.delete("build/pjzipdef_b.txt");
        Files.delete("build/pjzipdef_out_a.txt");
        Files.delete("build/pjzipdef_out_b.txt");
        Files.delete("build/pjzipdef.tmp");
    }

    public static void main(String[] args) {
        byte[] hdr = new byte[256];
        byte[] nameBuf = new byte[128];
        byte[] scratch = new byte[64];
        int[] info = new int[4];
        int offA, offB;
        int offset, centralOff, centralSize;
        int nameLen;

        cleanup();
        makeRepeated("build/pjzipdef_a.txt", "ALPHA\n", 16);
        makeRepeated("build/pjzipdef_b.txt", "BETA!\n", 16);

        if (Files.openWrite("build/pjzipdef.tmp") != 0) {
            Native.putchar('E');
            return;
        }
        offset = 0;
        offA = offset;
        offset += ZipWrite.writeCompressedFile("build/pjzipdef_a.txt", "a.txt", hdr, scratch, info);
        int methodA = info[0];
        int sizeA = info[1];
        int compA = info[2];
        int crcA = info[3];

        offB = offset;
        offset += ZipWrite.writeCompressedFile("build/pjzipdef_b.txt", "b.txt", hdr, scratch, info);
        int methodB = info[0];
        int sizeB = info[1];
        int compB = info[2];
        int crcB = info[3];

        centralOff = offset;
        centralSize = 0;
        centralSize += ZipWrite.writeCentral("a.txt", hdr, methodA, sizeA, compA, crcA, offA);
        centralSize += ZipWrite.writeCentral("b.txt", hdr, methodB, sizeB, compB, crcB, offB);
        ZipWrite.writeEnd(hdr, 2, centralSize, centralOff);
        Files.close(Files.MODE_WRITE);

        if (Files.openRead("build/pjzipdef.tmp") != 0) {
            Native.putchar('E');
            return;
        }
        for (;;) {
            nameLen = Zip.readLocalHeader(hdr, nameBuf, scratch);
            if (nameLen <= 0) break;
            if (eq(Zip.entryName(nameBuf, nameLen), "a.txt")) {
                ZipRead.extractCurrent("build/pjzipdef_out_a.txt", hdr, scratch);
            } else if (eq(Zip.entryName(nameBuf, nameLen), "b.txt")) {
                ZipRead.extractCurrent("build/pjzipdef_out_b.txt", hdr, scratch);
            } else {
                ZipRead.skipCurrent(hdr, scratch);
            }
            Native.print(Zip.entryName(nameBuf, nameLen));
            Native.putchar(',');
            Format.printInt(Zip.entryMethod(hdr));
            Native.putchar(',');
            Format.printInt(Zip.entrySize(hdr));
            Native.putchar('\n');
        }
        Files.close(Files.MODE_READ);

        dumpFile("build/pjzipdef_out_a.txt", scratch);
        dumpFile("build/pjzipdef_out_b.txt", scratch);
        cleanup();
    }
}
