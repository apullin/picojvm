import pj.archive.Zip;
import pj.io.Console;
import pj.io.Files;

public class PJZip {
    private static void usage() {
        Console.println("usage: PJZip archive.zip file...");
    }

    private static void emitAdded(String name, int size) {
        Console.print("+ ");
        Console.print(name);
        Console.printChar(',');
        Console.printlnInt(size);
    }

    public static void main(String[] args) {
        byte[] hdr = new byte[256];
        byte[] scratch = new byte[128];
        int n;
        int[] sizes;
        int[] crcs;
        int[] offs;
        int offset;
        int centralOff;
        int centralSize;

        if (args.length < 2) {
            usage();
            return;
        }
        n = args.length - 1;
        sizes = new int[n];
        crcs = new int[n];
        offs = new int[n];

        for (int i = 0; i < n; i++) {
            sizes[i] = Files.countBytes(args[i + 1], scratch);
            if (sizes[i] < 0) {
                Console.println("read fail");
                return;
            }
            crcs[i] = Zip.crc32File(args[i + 1], scratch);
            if (crcs[i] < 0) {
                Console.println("crc fail");
                return;
            }
        }

        if (Files.openWrite(args[0]) != 0) {
            Console.println("open fail");
            return;
        }

        offset = 0;
        for (int i = 0; i < n; i++) {
            offs[i] = offset;
            offset += Zip.writeStoredFile(args[i + 1], args[i + 1], hdr, scratch, sizes[i], crcs[i]);
            emitAdded(args[i + 1], sizes[i]);
        }
        centralOff = offset;
        centralSize = 0;
        for (int i = 0; i < n; i++) {
            centralSize += Zip.writeStoredCentral(args[i + 1], hdr, sizes[i], crcs[i], offs[i]);
        }
        Zip.writeEnd(hdr, n, centralSize, centralOff);
        Files.close(Files.MODE_WRITE);
    }
}
