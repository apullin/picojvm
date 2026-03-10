public class T25_TypeConversion {
    public static void main(String[] args) {
        int x = 300;
        // (byte) 300 = 44 (300 - 256)
        Native.putchar((byte) x);
        // (char) 65 = 'A'
        int y = 65;
        Native.putchar((char) y);
        // (short) 65535 = -1, then & 0xFF = 255
        int z = 65535;
        Native.putchar((short) z & 0xFF);
        Native.putchar(10);
        Native.halt();
    }
}
