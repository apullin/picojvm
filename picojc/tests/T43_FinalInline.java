class T43_FinalInline {
	static final int A = 42;
	static final int B = -7;
	static final int C = 0;
	static final int D = 255;
	static final boolean T = true;
	static final boolean F = false;
	static final char CH = 'X';

	public static void main() {
		Native.putchar(A);       // '*'
		Native.putchar(B + 55);  // '0'
		Native.putchar(C + 79);  // 'O'
		Native.putchar(D - 177); // 'N'
		if (T) Native.putchar('Y');
		if (!F) Native.putchar('!');
		Native.putchar(CH);      // 'X'
		// Arithmetic with finals
		Native.putchar(A + D - 248); // '*' + 255 - 248 = 49 = '1'
	}
}
