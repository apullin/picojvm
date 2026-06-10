/*
 * GC temp-root regression test.
 *
 * Run with the freelist heap, GC triggers, and a heap limit small enough
 * that the static `pressure` array keeps usage above the collection
 * watermark. Every allocation then runs a full collection, so any ref the
 * VM holds only in a C local across that allocation (e.g. the receiver a
 * native popped off the operand stack) is reclaimed unless it is
 * temp-rooted.
 *
 * Each chained expression below produces an intermediate heap string that
 * is popped before the next native allocates its result. Without temp
 * roots the intermediate's block is freed, handed back out as the result
 * block, and zeroed before the copy reads from it.
 */
public class GcTempRootTest {
    static byte[] pressure;

    static int diff(String s, String expect) {
        if (s.length() != expect.length()) return 1;
        for (int i = 0; i < expect.length(); i++) {
            if (s.charAt(i) != expect.charAt(i)) return 1;
        }
        return 0;
    }

    public static void main(String[] args) {
        pressure = new byte[1540];

        int bad = 0;

        // make_string_range: substring of a popped heap string
        bad += diff("abcdefghijklmnopqrstuvwxyz".substring(0, 26).substring(1, 25),
                    "bcdefghijklmnopqrstuvwxy");

        // make_case_string: toLowerCase of a popped heap string
        bad += diff("HELLO GC WORLD".substring(0, 14).toLowerCase(),
                    "hello gc world");

        // replace handler: allocates with the popped source still needed
        bad += diff("mississippi".substring(0, 11).replace('s', 'z'),
                    "mizzizzippi");

        // toCharArray handler: short-array allocation from a popped string
        char[] w = "0123456789".substring(0, 10).toCharArray();
        if (w.length != 10) bad++;
        for (int i = 0; i < w.length; i++) {
            if (w[i] != (char)('0' + i)) bad++;
        }

        // getBytes: byte-array allocation from a popped string
        byte[] g = "PICOJVM".substring(0, 7).getBytes();
        if (g.length != 7) bad++;
        if (g[0] != 'P' || g[3] != 'O' || g[6] != 'M') bad++;

        if (bad == 0) {
            Native.print("OK");
        } else {
            Native.print("BAD");
        }
        Native.putchar('\n');
        Native.halt();
    }
}
