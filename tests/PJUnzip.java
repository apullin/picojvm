import pj.archive.Zip;
import pj.io.Console;
import pj.io.Files;

public class PJUnzip {
    private static void usage() {
        Console.println("usage: PJUnzip archive.zip");
    }

    private static void emitExtracted(String name, int size) {
        Console.print("x ");
        Console.print(name);
        Console.printChar(',');
        Console.printlnInt(size);
    }

    public static void main(String[] args) {
        byte[] hdr = new byte[64];
        byte[] nameBuf = new byte[128];
        byte[] scratch = new byte[128];
        int nameLen;
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
            nameLen = Zip.readLocalHeader(hdr, nameBuf, scratch);
            if (nameLen <= 0) break;
            name = Zip.entryName(nameBuf, nameLen);
            size = Zip.entrySize(hdr);
            if (Zip.isStoredEntry(hdr)) {
                Zip.extractStoredCurrent(name, hdr, scratch);
                emitExtracted(name, size);
            } else {
                Zip.skipCurrent(hdr, scratch);
            }
        }
        Files.close(Files.MODE_READ);
    }
}
