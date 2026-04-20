import pj.Native;
import pj.archive.Zip;
import pj.io.Files;
import pj.text.Format;
import pj.text.Strings;

public class ZipSmoke {
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

    private static void makeFile(String path, String text) {
        if (Files.openWrite(path) != 0) return;
        Files.writeString(text);
        Files.close(Files.MODE_WRITE);
    }

    private static void cleanup() {
        Files.delete("build/pjzip_a.txt");
        Files.delete("build/pjzip_b.txt");
        Files.delete("build/pjzip_out_a.txt");
        Files.delete("build/pjzip_out_b.txt");
        Files.delete("build/pjzip.tmp");
    }

    public static void main(String[] args) {
        byte[] hdr = new byte[256];
        byte[] nameBuf = new byte[128];
        byte[] scratch = new byte[64];
        int sizeA, sizeB, crcA, crcB;
        int offA, offB;
        int offset, centralOff, centralSize;
        int nameLen;

        cleanup();
        makeFile("build/pjzip_a.txt", "ALPHA\n");
        makeFile("build/pjzip_b.txt", "BETA!\n");

        sizeA = Files.countBytes("build/pjzip_a.txt", scratch);
        sizeB = Files.countBytes("build/pjzip_b.txt", scratch);
        crcA = Zip.crc32File("build/pjzip_a.txt", scratch);
        crcB = Zip.crc32File("build/pjzip_b.txt", scratch);

        if (Files.openWrite("build/pjzip.tmp") != 0) {
            Native.putchar('E');
            return;
        }
        offset = 0;
        offA = offset;
        offset += Zip.writeStoredFile("build/pjzip_a.txt", "a.txt", hdr, scratch, sizeA, crcA);
        offB = offset;
        offset += Zip.writeStoredFile("build/pjzip_b.txt", "b.txt", hdr, scratch, sizeB, crcB);
        centralOff = offset;
        centralSize = 0;
        centralSize += Zip.writeStoredCentral("a.txt", hdr, sizeA, crcA, offA);
        centralSize += Zip.writeStoredCentral("b.txt", hdr, sizeB, crcB, offB);
        Zip.writeEnd(hdr, 2, centralSize, centralOff);
        Files.close(Files.MODE_WRITE);

        if (Files.openRead("build/pjzip.tmp") != 0) {
            Native.putchar('E');
            return;
        }
        for (;;) {
            nameLen = Zip.readLocalHeader(hdr, nameBuf, scratch);
            if (nameLen <= 0) break;
            Native.print(Zip.entryName(nameBuf, nameLen));
            Native.putchar(',');
            Format.printInt(Zip.entryMethod(hdr));
            Native.putchar(',');
            Format.printInt(Zip.entrySize(hdr));
            Native.putchar('\n');

            if (Zip.isStoredEntry(hdr) && eq(Zip.entryName(nameBuf, nameLen), "a.txt")) {
                Zip.extractStoredCurrent("build/pjzip_out_a.txt", hdr, scratch);
            } else if (Zip.isStoredEntry(hdr) && eq(Zip.entryName(nameBuf, nameLen), "b.txt")) {
                Zip.extractStoredCurrent("build/pjzip_out_b.txt", hdr, scratch);
            } else {
                Zip.skipCurrent(hdr, scratch);
            }
        }
        Files.close(Files.MODE_READ);

        dumpFile("build/pjzip_out_a.txt", scratch);
        dumpFile("build/pjzip_out_b.txt", scratch);
        cleanup();
    }
}
