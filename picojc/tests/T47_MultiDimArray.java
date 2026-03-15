class T47_MultiDimArray {
	public static void main() {
		// 2D int array
		int[][] grid = new int[3][4];
		grid[0][0] = 65; // 'A'
		grid[1][2] = 66; // 'B'
		grid[2][3] = 67; // 'C'
		Native.putchar(grid[0][0]);
		Native.putchar(grid[1][2]);
		Native.putchar(grid[2][3]);

		// 2D byte array
		byte[][] bGrid = new byte[2][3];
		bGrid[0][1] = 68; // 'D'
		bGrid[1][0] = 69; // 'E'
		Native.putchar(bGrid[0][1]);
		Native.putchar(bGrid[1][0]);

		// Jagged array (new Type[n][])
		int[][] jagged = new int[3][];
		jagged[0] = new int[2];
		jagged[1] = new int[1];
		jagged[0][0] = 70; // 'F'
		jagged[0][1] = 71; // 'G'
		jagged[1][0] = 72; // 'H'
		Native.putchar(jagged[0][0]);
		Native.putchar(jagged[0][1]);
		Native.putchar(jagged[1][0]);

		// array.length on inner arrays
		Native.putchar(jagged[0].length + 48); // '2'
		Native.putchar(jagged[1].length + 48); // '1'
	}
}
