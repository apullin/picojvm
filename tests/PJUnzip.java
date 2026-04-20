import pj.Native;
import pj.archive.Zip;
import pj.io.Files;
import pj.text.Format;

public class PJUnzip {
    private static void usage() {
        Native.print("usage: PJUnzip archive.zip\n");
    }

    private static void emitExtracted(String name, int size) {
        Native.print("x ");
        Native.print(name);
        Native.putchar(',');
        Format.printInt(size);
        Native.putchar('\n');
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
            Native.print("open fail\n");
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
