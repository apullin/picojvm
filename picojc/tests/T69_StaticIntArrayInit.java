public class T69_StaticIntArrayInit {
	static int[] nums = { 1, 2, 3 };
	static final int[] more = { 4, 5, 6 };

	public static void main() {
		Native.putchar('0' + nums[0]);
		Native.putchar('0' + nums[1]);
		Native.putchar('0' + nums[2]);
		Native.putchar('0' + more[0]);
		Native.putchar('0' + more[1]);
		Native.putchar('0' + more[2]);
		Native.putchar('\n');
	}
}
