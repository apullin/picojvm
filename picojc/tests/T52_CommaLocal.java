class T52_CommaLocal {
	public static void main() {
		// Comma-separated local declarations
		int a = 65, b = 66, c = 67;
		Native.putchar(a); // 'A'
		Native.putchar(b); // 'B'
		Native.putchar(c); // 'C'

		// Without initializers (default to 0)
		int x, y;
		x = 68;
		y = 69;
		Native.putchar(x); // 'D'
		Native.putchar(y); // 'E'

		// Mixed
		int p = 70, q;
		q = 71;
		Native.putchar(p); // 'F'
		Native.putchar(q); // 'G'

		// Comma-separated static fields
		Native.putchar(Holder.sa); // 'H'
		Native.putchar(Holder.sb); // 'I'
	}
}

class Holder {
	static int sa = 72, sb = 73;
}
