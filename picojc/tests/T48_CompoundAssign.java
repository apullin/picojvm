class T48_CompoundAssign {
	static int sx = 10;

	static void testStatic() {
		sx += 20;   // 30
		sx -= 5;    // 25
		sx *= 2;    // 50
		sx /= 5;    // 10
		sx %= 7;    // 3
		Native.putchar(sx + 48); // '3'
	}

	static void testLocal() {
		int x = 100;
		x += 5;     // 105
		x -= 40;    // 65 = 'A'
		Native.putchar(x);
		x <<= 1;    // 130
		x >>= 2;    // 32 = ' '
		x |= 65;    // 97 = 'a'
		x &= 0x5F;  // 65 = 'A' (clear bit 5)
		Native.putchar(x);
		x ^= 32;    // 97 = 'a'
		Native.putchar(x);
	}

	static void testArray() {
		int[] arr = new int[3];
		arr[0] = 60;
		arr[0] = arr[0] + 5; // 65 = 'A'
		Native.putchar(arr[0]);
		arr[1] = 70;
		arr[1] = arr[1] - 4; // 66 = 'B'
		Native.putchar(arr[1]);
	}

	static void testPostInc() {
		int[] arr = new int[3];
		arr[0] = 64;
		arr[0]++;  // 65
		Native.putchar(arr[0]); // 'A'
		arr[1] = 67;
		arr[1]--;  // 66
		Native.putchar(arr[1]); // 'B'
	}

	public static void main() {
		testStatic();
		testLocal();
		testArray();
		testPostInc();
	}
}
