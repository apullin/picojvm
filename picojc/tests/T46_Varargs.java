class T46_Varargs {
	static int sum(int... args) {
		int total = 0;
		for (int x : args) {
			total = total + x;
		}
		return total;
	}

	static void printChars(int... args) {
		for (int x : args) {
			Native.putchar(x);
		}
	}

	static int sumWith(int base, int... extra) {
		int total = base;
		for (int x : extra) {
			total = total + x;
		}
		return total;
	}

	static int countArgs(int... args) {
		return args.length;
	}

	public static void main() {
		// sum(10, 20, 30) = 60 = '<'
		Native.putchar(sum(10, 20, 30));

		// printChars prints "Hi"
		printChars(72, 105);

		// sumWith(1, 2, 3) = 6 → '6'
		Native.putchar(sumWith(1, 2, 3) + 48);

		// sum() with zero args = 0 → 'A'
		Native.putchar(sum() + 65);

		// countArgs(1, 2, 3, 4, 5) = 5 → '5'
		Native.putchar(countArgs(1, 2, 3, 4, 5) + 48);
	}
}
