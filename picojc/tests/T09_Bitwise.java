public class T09_Bitwise {
    public static void main(String[] args) {
        int a = 0xAA;  // 170
        int b = 0x55;  // 85
        // AND: 0xAA & 0x55 = 0x00
        Native.putchar(a & b);
        // OR: 0xAA | 0x55 = 0xFF = 255
        Native.putchar((a | b) & 0xFF);
        // XOR: 0xAA ^ 0xFF = 0x55 = 85
        Native.putchar(a ^ 0xFF);
        // NOT: ~0 & 0xFF = 255
        Native.putchar((~0) & 0xFF);
        // SHL: 1 << 5 = 32
        Native.putchar(1 << 5);
        // SHR: 64 >> 1 = 32
        Native.putchar(64 >> 1);
        Native.putchar(10);
        Native.halt();
    }
}
