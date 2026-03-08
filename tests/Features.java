/**
 * Features.java — Test new JVM opcodes: byte arrays, switch, instanceof.
 *
 * Expected output (as bytes):
 *   'B' 'Y' 'T' 'E'     — byte array round-trip
 *   1 2 3 0              — tableswitch (dense)
 *   10 20 99             — lookupswitch (sparse)
 *   1 0 1                — instanceof checks
 *   'O' 'K'              — checkcast survived
 */
public class Features {
    /* --- byte[] test --- */
    static void testByteArray() {
        byte[] buf = new byte[4];
        buf[0] = 66;  // 'B'
        buf[1] = 89;  // 'Y'
        buf[2] = 84;  // 'T'
        buf[3] = 69;  // 'E'
        for (int i = 0; i < buf.length; i++) {
            Native.putchar(buf[i]);
        }
    }

    /* --- tableswitch (dense cases) --- */
    static int denseSwitch(int x) {
        switch (x) {
            case 0: return 1;
            case 1: return 2;
            case 2: return 3;
            default: return 0;
        }
    }

    static void testTableSwitch() {
        Native.putchar(denseSwitch(0));
        Native.putchar(denseSwitch(1));
        Native.putchar(denseSwitch(2));
        Native.putchar(denseSwitch(99));
    }

    /* --- lookupswitch (sparse cases) --- */
    static int sparseSwitch(int x) {
        switch (x) {
            case 100: return 10;
            case 200: return 20;
            default:  return 99;
        }
    }

    static void testLookupSwitch() {
        Native.putchar(sparseSwitch(100));
        Native.putchar(sparseSwitch(200));
        Native.putchar(sparseSwitch(999));
    }

    /* --- instanceof --- */
    static void testInstanceOf() {
        Square sq = new Square();
        sq.side = 5;
        Shape sh = sq;

        // sq instanceof Shape → true (1)
        Native.putchar(sh instanceof Shape ? 1 : 0);
        // sq instanceof Rect → false (0)
        Native.putchar(sh instanceof Rect ? 1 : 0);
        // sq instanceof Square → true (1)
        Native.putchar(sh instanceof Square ? 1 : 0);

        // checkcast: this should not trap
        Square sq2 = (Square) sh;
        Native.putchar(79);  // 'O'
        Native.putchar(75);  // 'K'
    }

    public static void main(String[] args) {
        testByteArray();
        testTableSwitch();
        testLookupSwitch();
        testInstanceOf();
        Native.halt();
    }
}
