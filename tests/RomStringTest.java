/**
 * RomStringTest.java — Exercise ROM string literals alongside heap strings.
 *
 * Expected output (as bytes):
 *   1                           — "AB".equals(heapString)
 *   1                           — heapString.equals("AB")
 *   1                           — literal interning via ref equality
 *   0                           — ROM literal ref != heap string ref
 *   'A' 'B' 'A' 'B'           — array load preserves both ref kinds
 */
public class RomStringTest {
    public static void main(String[] args) {
        String lit = "AB";
        byte[] bytes = new byte[2];
        bytes[0] = 'A';
        bytes[1] = 'B';

        String heap = Native.stringFromBytes(bytes, 0, 2);
        String lit2 = "AB";

        Native.putchar(lit.equals(heap) ? 1 : 0);
        Native.putchar(heap.equals(lit) ? 1 : 0);
        Native.putchar(lit == lit2 ? 1 : 0);
        Native.putchar(lit == heap ? 1 : 0);

        String[] arr = new String[2];
        arr[0] = lit;
        arr[1] = heap;
        Native.print(arr[0]);
        Native.print(arr[1]);

        Native.halt();
    }
}
