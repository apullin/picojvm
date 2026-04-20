import pj.archive.Tar;
import pj.io.Console;
import pj.io.Files;

public class PJUntar {
    private static void usage() {
        Console.println("usage: PJUntar archive.tar");
    }

    private static void emitExtracted(String name, int size) {
        Console.print("x ");
        Console.print(name);
        Console.printChar(',');
        Console.printlnInt(size);
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
            Console.println("open fail");
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
