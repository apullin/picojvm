/*
 * Optional alloc-heavy integration test for future GC work.
 *
 * This intentionally creates a steady stream of short-lived arrays while still
 * fitting in the current no-GC heap, so it is a stable baseline now and a more
 * interesting pressure test once mark/sweep exists.
 */
public class AllocHeavyTest {
    static int churn(int rounds) {
        int sum = 0;

        for (int i = 0; i < rounds; i++) {
            byte[] bytes = new byte[16];
            bytes[0] = (byte)i;
            bytes[15] = (byte)(i ^ 0x55);
            sum += (bytes[0] & 0xFF) + (bytes[15] & 0xFF) + bytes.length;

            int[] ints = new int[4];
            ints[0] = i;
            ints[1] = i * 3;
            ints[2] = i ^ 0x1234;
            ints[3] = sum;
            sum += ints[(i >> 1) & 3] & 0xFF;

            Object[] refs = new Object[3];
            refs[0] = bytes;
            refs[1] = ints;
            refs[2] = refs;
            sum ^= refs.length + ints.length;

            if ((i & 15) == 0) {
                byte[] burst = new byte[48];
                burst[i & 47] = (byte)sum;
                sum += burst[i & 47] & 0xFF;
            }
        }

        return sum;
    }

    public static void main(String[] args) {
        int v = churn(160);
        Native.putchar((v >>> 24) & 0xFF);
        Native.putchar((v >>> 16) & 0xFF);
        Native.putchar((v >>> 8) & 0xFF);
        Native.putchar(v & 0xFF);
        Native.halt();
    }
}
