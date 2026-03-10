/**
 * CharArray — char array creation, store, load, and length.
 * Tests CALOAD/CASTORE opcodes and char type tracking.
 *
 * Expected output (as bytes): 72, 105, 33, 3
 *   H=72, i=105, !=33, length=3
 */
public class T36_CharArray {
    public static void main(String[] args) {
        char[] msg = new char[3];
        msg[0] = 'H';   // 72
        msg[1] = 'i';   // 105
        msg[2] = '!';   // 33
        for (int i = 0; i < msg.length; i++) {
            Native.putchar(msg[i]);
        }
        Native.putchar(msg.length);  // 3
        Native.halt();
    }
}
