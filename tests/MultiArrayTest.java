/**
 * MultiArrayTest.java — Test multi-dimensional arrays (multianewarray).
 *
 * Expected output (as bytes): 01 05 09 03 04
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
        Native.halt();
    }
}
