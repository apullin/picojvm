import pj.Native;
import pj.archive.Tar;
import pj.io.Files;
import pj.text.Format;
import pj.text.Strings;

public class TarSmoke {
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
        Files.delete("build/pjtar_a.txt");
        Files.delete("build/pjtar_b.txt");
        Files.delete("build/pjtar_out_a.txt");
        Files.delete("build/pjtar_out_b.txt");
        Files.delete("build/pjtar.tmp");
    }

    public static void main(String[] args) {
        byte[] hdr = new byte[Tar.BLOCK];
        byte[] scratch = new byte[64];
        int rc;

        cleanup();
        makeFile("build/pjtar_a.txt", "ALPHA\n");
        makeFile("build/pjtar_b.txt", "BETA!\n");

        if (Files.openWrite("build/pjtar.tmp") != 0) {
            Native.putchar('E');
            return;
        }
        Tar.addFile("build/pjtar_a.txt", "a.txt", hdr, scratch);
        Tar.addFile("build/pjtar_b.txt", "b.txt", hdr, scratch);
        Tar.finishArchive(scratch);
        Files.close(Files.MODE_WRITE);

        if (Files.openRead("build/pjtar.tmp") != 0) {
            Native.putchar('E');
            return;
        }
        for (;;) {
            rc = Tar.readHeader(hdr);
            if (rc <= 0) break;
            Native.print(Tar.entryName(hdr));
            Native.putchar(',');
            Format.printInt(Tar.entrySize(hdr));
            Native.putchar('\n');

            if (Tar.entryType(hdr) != '0') {
                Tar.skipCurrent(hdr, scratch);
            } else if (eq(Tar.entryName(hdr), "a.txt")) {
                Tar.extractCurrent("build/pjtar_out_a.txt", hdr, scratch);
            } else if (eq(Tar.entryName(hdr), "b.txt")) {
                Tar.extractCurrent("build/pjtar_out_b.txt", hdr, scratch);
            } else {
                Tar.skipCurrent(hdr, scratch);
            }
        }
        Files.close(Files.MODE_READ);

        dumpFile("build/pjtar_out_a.txt", scratch);
        dumpFile("build/pjtar_out_b.txt", scratch);
        cleanup();
    }
}
