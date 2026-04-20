import pj.Native;
import pj.io.Files;

public class FilesSmoke {
    public static void main(String[] args) {
        String path = "build/pjio.tmp";
        byte[] buf = new byte[16];
        int n;

        if (Files.openWrite(path) != 0) {
            Native.putchar('E');
            return;
        }
        Files.writeString("PJIO");
        Files.close(Files.MODE_WRITE);

        if (Files.openRead(path) != 0) {
            Native.putchar('E');
            return;
        }
        n = Files.read(buf, 0, 16);
        Files.close(Files.MODE_READ);
        Files.delete(path);

        if (n > 0) Native.writeBytes(buf, 0, n);
    }
}
