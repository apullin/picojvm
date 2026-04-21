class T72_BooleanOps {
	public static void main(String[] args) {
		boolean t = true;
		boolean f = false;
		boolean[] bits = { t, f };
		boolean q = t ? bits[0] : bits[1];

		if (t && !f) Native.putchar('A');
		if (t || f) Native.putchar('B');
		if (t ^ f) Native.putchar('C');
		if ((t & !f) == q) Native.putchar('D');
		if (bits[0] != bits[1]) Native.putchar('E');
		Native.putchar('\n');
	}
}
