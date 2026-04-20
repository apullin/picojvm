import pj.Native;
import pj.io.Binary;
import pj.io.Console;
import pj.io.TextWriter;
import pj.text.Format;
import pj.text.Parse;
import pj.text.Strings;
import pj.util.Bytes;
import pj.util.Ints;

public class PicoJseStdSmoke {
    public static void main(String[] args) {
        byte[] a = new byte[8];
        byte[] src = new byte[4];

        src[0] = 'P';
        src[1] = 'J';
        src[2] = 'S';
        src[3] = 'E';
        Bytes.fill(a, 0, 8, '.');
        Bytes.copy(src, 0, a, 2, 4);
        Native.writeBytes(a, 0, 8);
        Native.putchar('\n');

        Format.printInt(Parse.parseInt("-12345"));
        Native.putchar('\n');

        Format.printHex(Parse.parseHex("1a2b"), 4);
        Native.putchar('\n');

        Format.printInt(Ints.min(42, -7));
        Native.putchar(',');
        Format.printInt(Ints.max(42, -7));
        Native.putchar(',');
        Format.printInt(Ints.clamp(999, 0, 9));
        Native.putchar('\n');

        Native.putchar(Strings.startsWith("terminal", "term") ? '1' : '0');
        Native.putchar(',');
        Native.putchar(Strings.endsWith("terminal", "nal") ? '1' : '0');
        Native.putchar(',');
        Format.printInt(Strings.indexOf("terminal", 'm'));
        Native.putchar(',');
        Native.putchar(Strings.equalsIgnoreAsciiCase("PjSe", "pJsE") ? '1' : '0');
        Native.putchar('\n');

        Binary.writeIntLE(a, 0, 0x12345678);
        Format.printHex(Binary.readU16LE(a, 0), 4);
        Native.putchar(',');
        Format.printHex(Binary.readIntLE(a, 0), 8);
        Native.putchar('\n');

        Console.println("console");

        TextWriter out = TextWriter.stdout();
        out.print("writer,");
        out.printlnInt(7);
    }
}
