import pj.Native;
import pj.archive.Tar;
import pj.io.Files;
import pj.text.Format;

public class PJTar {
    private static void usage() {
        Native.print("usage: PJTar archive.tar file...\n");
    }

    private static void emitAdded(String name, int size) {
        Native.print("+ ");
        Native.print(name);
        Native.putchar(',');
        Format.printInt(size);
        Native.putchar('\n');
    }

    public static void main(String[] args) {
        byte[] hdr = new byte[Tar.BLOCK];
        byte[] scratch = new byte[128];
        int size;

        if (args.length < 2) {
            usage();
            return;
        }
        if (Files.openWrite(args[0]) != 0) {
            Native.print("open fail\n");
            return;
        }
        for (int i = 1; i < args.length; i++) {
            size = Tar.addFile(args[i], args[i], hdr, scratch);
            if (size < 0) {
                Files.close(Files.MODE_WRITE);
                Native.print("add fail\n");
                return;
            }
            emitAdded(args[i], size);
        }
        Tar.finishArchive(scratch);
        Files.close(Files.MODE_WRITE);
    }
}
