class T53_Precedence {
	public static void main() {
		// Operator precedence: * before +
		int a = 2 + 3 * 4; // 14
		Native.putchar(a + 48); // '>'  (62)
		// Actually 14+48=62='>'
		// Let's use simpler values

		// Shift vs addition: + binds tighter than <<
		int b = 1 << 2 + 3; // 1 << 5 = 32
		Native.putchar(b + 48); // 80 = 'P'

		// Comparison chain: (a < b) == true
		int x = 5, y = 10;
		int r = x < y ? 65 : 66;
		Native.putchar(r); // 'A'

		// Nested ternary
		int v = 2;
		int t = v == 1 ? 65 : v == 2 ? 66 : 67;
		Native.putchar(t); // 'B'

		// Short-circuit &&
		int[] arr = new int[1];
		arr[0] = 0;
		if (arr[0] == 0 && true) {
			Native.putchar(67); // 'C'
		}

		// Short-circuit ||
		if (false || arr[0] == 0) {
			Native.putchar(68); // 'D'
		}

		// Bitwise precedence: & before | before ^
		int m = 0xFF & 0x41; // 65 = 'A'
		Native.putchar(m); // 'A'

		// Complex: unary minus + multiply
		int n = -(-72); // 72 = 'H'
		Native.putchar(n);
	}
}
