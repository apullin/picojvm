import pj.Native;
import pj.archive.Tar;
import pj.io.Files;
import pj.text.Format;

public class PJUntar {
    private static void usage() {
        Native.print("usage: PJUntar archive.tar\n");
    }

    private static void emitExtracted(String name, int size) {
        Native.print("x ");
        Native.print(name);
        Native.putchar(',');
        Format.printInt(size);
        Native.putchar('\n');
    }

    public static void main(String[] args) {
        byte[] hdr = new byte[Tar.BLOCK];
        byte[] scratch = new byte[128];
        int rc;
        String name;
        int size;

        if (args.length != 1) {
            usage();
            return;
        }
        if (Files.openRead(args[0]) != 0) {
            Native.print("open fail\n");
            return;
        }
        for (;;) {
            rc = Tar.readHeader(hdr);
            if (rc <= 0) break;
            name = Tar.entryName(hdr);
            size = Tar.entrySize(hdr);
            if (Tar.entryType(hdr) == '0') {
                Tar.extractCurrent(name, hdr, scratch);
                emitExtracted(name, size);
            } else {
                Tar.skipCurrent(hdr, scratch);
            }
        }
        Files.close(Files.MODE_READ);
    }
}
