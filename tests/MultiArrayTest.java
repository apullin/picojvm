/**
 * MultiArrayTest.java — Test multi-dimensional arrays (multianewarray).
 *
 * Expected output (as bytes): 01 05 09 03 04 4d 80 80 01
 */
public class MultiArrayTest {
    public static void main(String[] args) {
        int[][] grid = new int[3][4];
        grid[0][0] = 1;
        grid[1][2] = 5;
        grid[2][3] = 9;
        Native.putchar(grid[0][0]);   // 1
        Native.putchar(grid[1][2]);   // 5
        Native.putchar(grid[2][3]);   // 9
        Native.putchar(grid.length);  // 3
        Native.putchar(grid[0].length); // 4

        // This needs about 17KB when terminal rows are byte arrays, but more
        // than the VM's 64KB heap if they are incorrectly reference arrays.
        byte[][] bytes = new byte[128][128];
        bytes[127][127] = 77;
        Native.putchar(bytes[127][127]);
        Native.putchar(bytes.length);
        Native.putchar(bytes[0].length);

        boolean[][] flags = new boolean[2][2];
        flags[1][1] = true;
        Native.putchar(flags[1][1] ? 1 : 0);
        Native.halt();
    }
}
