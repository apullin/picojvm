public class T70_ArrayElemCompound {
	static int[] bits = { 1, 7, 1 };

	public static void main() {
		byte[] small = { 1 };

		bits[0] += 8;
		bits[1] -= 3;
		bits[2] <<= 2;
		small[0] += 2;

		Native.putchar('0' + bits[0]);
		Native.putchar('0' + bits[1]);
		Native.putchar('0' + bits[2]);
		Native.putchar('0' + small[0]);
		Native.putchar('\n');
	}
}
