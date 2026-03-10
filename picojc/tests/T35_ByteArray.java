/**
 * ByteArray — byte array creation, store, load, and length.
 *
 * Expected output (as bytes): 66, 89, 84, 69, 4
 *   B=66, Y=89, T=84, E=69, length=4
 */
public class T35_ByteArray {
    public static void main(String[] args) {
        byte[] buf = new byte[4];
        buf[0] = 66;  // 'B'
        buf[1] = 89;  // 'Y'
        buf[2] = 84;  // 'T'
        buf[3] = 69;  // 'E'
        for (int i = 0; i < buf.length; i++) {
            Native.putchar(buf[i]);
        }
        Native.putchar(buf.length);  // 4
        Native.halt();
    }
}
