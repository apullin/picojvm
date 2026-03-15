class T45_ForEach {
	public static void main() {
		int[] nums = new int[5];
		nums[0] = 72;  // H
		nums[1] = 101; // e
		nums[2] = 108; // l
		nums[3] = 108; // l
		nums[4] = 111; // o

		for (int ch : nums) {
			Native.putchar(ch);
		}

		byte[] bytes = new byte[3];
		bytes[0] = 33;  // !
		bytes[1] = 63;  // ?
		bytes[2] = 46;  // .

		for (byte b : bytes) {
			Native.putchar(b);
		}

		// Sum with for-each
		int[] vals = new int[4];
		vals[0] = 10;
		vals[1] = 20;
		vals[2] = 30;
		vals[3] = 40;
		int sum = 0;
		for (int v : vals) {
			sum = sum + v;
		}
		Native.putchar(sum + '0' - 100); // 100 -> '0'
	}
}
