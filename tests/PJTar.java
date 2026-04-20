import pj.archive.Tar;
import pj.io.Console;
import pj.io.Files;

public class PJTar {
    private static void usage() {
        Console.println("usage: PJTar archive.tar file...");
    }

    private static void emitAdded(String name, int size) {
        Console.print("+ ");
        Console.print(name);
        Console.printChar(',');
        Console.printlnInt(size);
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
            Console.println("open fail");
            return;
        }
        for (int i = 1; i < args.length; i++) {
            size = Tar.addFile(args[i], args[i], hdr, scratch);
            if (size < 0) {
                Files.close(Files.MODE_WRITE);
                Console.println("add fail");
                return;
            }
            emitAdded(args[i], size);
        }
        Tar.finishArchive(scratch);
        Files.close(Files.MODE_WRITE);
    }
}
