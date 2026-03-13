/**
 * PlatformPkgTest.java — Test the platform package (Console, Arrays, IO).
 *
 * Expected output (as bytes):
 *   '4' '2' '\n'                  — Console.println(42)
 *   '-' '1' '0' '0' '\n'         — Console.println(-100)
 *   'H' 'e' 'l' 'l' 'o' '\n'    — Console.println("Hello")
 *   'D' 'E' 'A' 'D'             — Console.printHex(0xDEAD, 4)
 *   1                             — Arrays.equals true
 *   0                             — Arrays.equals false
 *   65 66 67                      — Arrays.copy result
 *   99 99 99                      — Arrays.fill result
 *   0x78 0x56 0x34 0x12          — IO.writeIntLE(0x12345678)
 */
public class PlatformPkgTest {
    public static void main(String[] args) {
        // Console
        Console.println(42);
        Console.println(-100);
        Console.println("Hello");
        Console.printHex(0xDEAD, 4);

        // Arrays
        byte[] a = new byte[3];
        a[0] = 10; a[1] = 20; a[2] = 30;
        byte[] b = new byte[3];
        b[0] = 10; b[1] = 20; b[2] = 30;
        byte[] c = new byte[3];
        c[0] = 10; c[1] = 99; c[2] = 30;
        Native.putchar(Arrays.equals(a, 0, b, 0, 3) ? 1 : 0);  // 1
        Native.putchar(Arrays.equals(a, 0, c, 0, 3) ? 1 : 0);  // 0

        byte[] dst = new byte[3];
        Arrays.copy(a, 0, dst, 0, 3);
        // Add 55 to distinguish from original values
        Native.putchar(dst[0] + 55);  // 65 'A'
        Native.putchar(dst[1] + 46);  // 66 'B'
        Native.putchar(dst[2] + 37);  // 67 'C'

        byte[] f = new byte[3];
        Arrays.fill(f, 0, 3, (byte)99);
        Native.putchar(f[0]);  // 99
        Native.putchar(f[1]);  // 99
        Native.putchar(f[2]);  // 99

        // IO
        IO.writeIntLE(0x12345678);

        Native.halt();
    }
}
