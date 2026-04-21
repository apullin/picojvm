import pj.archive.Zip;
import pj.archive.ZipWrite;
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
        int[] info = new int[4];
        int n;
        int[] sizes;
        int[] compSizes;
        int[] crcs;
        int[] methods;
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
        compSizes = new int[n];
        crcs = new int[n];
        methods = new int[n];
        offs = new int[n];

        if (Files.openWrite(args[0]) != 0) {
            Console.println("open fail");
            return;
        }

        offset = 0;
        for (int i = 0; i < n; i++) {
            offs[i] = offset;
            offset += ZipWrite.writeCompressedFile(args[i + 1], args[i + 1], hdr, scratch, info);
            if (offset < 0) {
                Files.close(Files.MODE_WRITE);
                Console.println("zip fail");
                return;
            }
            methods[i] = info[0];
            sizes[i] = info[1];
            compSizes[i] = info[2];
            crcs[i] = info[3];
            emitAdded(args[i + 1], sizes[i]);
        }
        centralOff = offset;
        centralSize = 0;
        for (int i = 0; i < n; i++) {
            centralSize += ZipWrite.writeCentral(args[i + 1], hdr, methods[i], sizes[i], compSizes[i], crcs[i], offs[i]);
        }
        ZipWrite.writeEnd(hdr, n, centralSize, centralOff);
        Files.close(Files.MODE_WRITE);
    }
}
