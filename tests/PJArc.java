import pj.archive.Tar;
import pj.archive.Zip;
import pj.archive.ZipRead;
import pj.archive.ZipWrite;
import pj.io.Console;
import pj.io.Files;

public class PJArc {
    private static void usage() {
        Console.println("usage: PJArc tar|untar|zip|unzip archive [file...]");
    }

    private static void emit(char op, String name, int size) {
        Console.printChar(op);
        Console.printChar(' ');
        Console.print(name);
        Console.printChar(',');
        Console.printlnInt(size);
    }

    private static int doTar(String[] args) {
        byte[] hdr = new byte[Tar.BLOCK];
        byte[] scratch = new byte[128];
        int size;

        if (args.length < 3) {
            usage();
            return 1;
        }
        if (Files.openWrite(args[1]) != 0) {
            Console.println("open fail");
            return 1;
        }
        for (int i = 2; i < args.length; i++) {
            size = Tar.addFile(args[i], args[i], hdr, scratch);
            if (size < 0) {
                Files.close(Files.MODE_WRITE);
                Console.println("add fail");
                return 1;
            }
            emit('+', args[i], size);
        }
        Tar.finishArchive(scratch);
        Files.close(Files.MODE_WRITE);
        return 0;
    }

    private static int doUntar(String[] args) {
        byte[] hdr = new byte[Tar.BLOCK];
        byte[] scratch = new byte[128];
        int rc;
        String name;
        int size;

        if (args.length != 2) {
            usage();
            return 1;
        }
        if (Files.openRead(args[1]) != 0) {
            Console.println("open fail");
            return 1;
        }
        for (;;) {
            rc = Tar.readHeader(hdr);
            if (rc <= 0) break;
            name = Tar.entryName(hdr);
            size = Tar.entrySize(hdr);
            if (Tar.entryType(hdr) == '0') {
                Tar.extractCurrent(name, hdr, scratch);
                emit('x', name, size);
            } else {
                Tar.skipCurrent(hdr, scratch);
            }
        }
        Files.close(Files.MODE_READ);
        return 0;
    }

    private static int doZip(String[] args) {
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

        if (args.length < 3) {
            usage();
            return 1;
        }
        n = args.length - 2;
        sizes = new int[n];
        compSizes = new int[n];
        crcs = new int[n];
        methods = new int[n];
        offs = new int[n];

        if (Files.openWrite(args[1]) != 0) {
            Console.println("open fail");
            return 1;
        }

        offset = 0;
        for (int i = 0; i < n; i++) {
            offs[i] = offset;
            offset += ZipWrite.writeCompressedFile(args[i + 2], args[i + 2], hdr, scratch, info);
            if (offset < 0) {
                Files.close(Files.MODE_WRITE);
                Console.println("zip fail");
                return 1;
            }
            methods[i] = info[0];
            sizes[i] = info[1];
            compSizes[i] = info[2];
            crcs[i] = info[3];
            emit('+', args[i + 2], sizes[i]);
        }
        centralOff = offset;
        centralSize = 0;
        for (int i = 0; i < n; i++) {
            centralSize += ZipWrite.writeCentral(args[i + 2], hdr, methods[i], sizes[i], compSizes[i], crcs[i], offs[i]);
        }
        ZipWrite.writeEnd(hdr, n, centralSize, centralOff);
        Files.close(Files.MODE_WRITE);
        return 0;
    }

    private static int doUnzip(String[] args) {
        byte[] hdr = new byte[64];
        byte[] nameBuf = new byte[128];
        byte[] scratch = new byte[128];
        int nameLen;
        String name;
        int size;

        if (args.length != 2) {
            usage();
            return 1;
        }
        if (Files.openRead(args[1]) != 0) {
            Console.println("open fail");
            return 1;
        }
        for (;;) {
            nameLen = Zip.readLocalHeader(hdr, nameBuf, scratch);
            if (nameLen <= 0) break;
            name = Zip.entryName(nameBuf, nameLen);
            if (ZipRead.extractCurrent(name, hdr, scratch)) {
                size = Zip.entrySize(hdr);
                emit('x', name, size);
            }
        }
        Files.close(Files.MODE_READ);
        return 0;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            usage();
            return;
        }
        if (args[0].equals("tar")) {
            doTar(args);
        } else if (args[0].equals("untar")) {
            doUntar(args);
        } else if (args[0].equals("zip")) {
            doZip(args);
        } else if (args[0].equals("unzip")) {
            doUnzip(args);
        } else {
            usage();
        }
    }
}
