public class T40_PlatformPkg {
    public static void main(String[] args) {
        // Test Console.print(int) / println(int)
        Console.print(42);
        Console.newline();
        Console.println(0);
        Console.println(-7);
        Console.println(2147483647);
        Console.println(-2147483648);

        // Test Console with Native.print for strings
        Native.print("Hi");
        Console.newline();

        // Test Console.printHex
        Console.printHex(0xAB, 2);
        Console.newline();
        Console.printHex(0x1234, 4);
        Console.newline();

        // Test Arrays.equals
        byte[] a = new byte[3];
        byte[] b = new byte[3];
        a[0] = 10; a[1] = 20; a[2] = 30;
        b[0] = 10; b[1] = 20; b[2] = 30;
        if (Arrays.equals(a, 0, b, 0, 3)) Native.print("EQ\n");
        else Native.print("NE\n");

        b[1] = 99;
        if (Arrays.equals(a, 0, b, 0, 3)) Native.print("EQ\n");
        else Native.print("NE\n");

        // Test Arrays.compare
        int cmp = Arrays.compare(a, 0, b, 0, 3);
        if (cmp < 0) Native.print("LT\n");
        else if (cmp > 0) Native.print("GT\n");
        else Native.print("EQ\n");

        // Test Arrays.copy
        byte[] c = new byte[3];
        Arrays.copy(a, 0, c, 0, 3);
        Console.println(c[0] + c[1] + c[2]);

        // Test Arrays.fillBytes
        Arrays.fillBytes(c, 0, 3, (byte) 5);
        Console.println(c[0] + c[1] + c[2]);

        // Test Arrays.fillInts
        int[] d = new int[3];
        Arrays.fillInts(d, 0, 3, 7);
        Console.println(d[0] + d[1] + d[2]);

        // Test IO.writeByte / writeBytes
        IO.writeByte(79);
        IO.writeByte(75);
        IO.writeByte(10);

        byte[] msg = new byte[5];
        msg[0] = 68; msg[1] = 111; msg[2] = 110; msg[3] = 101; msg[4] = 10;
        IO.writeBytes(msg, 0, 5);
    }
}
