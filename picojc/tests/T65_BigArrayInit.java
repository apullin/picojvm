public class T65_BigArrayInit {
	public static void main() {
		int[] vals = {
			'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
			'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
			'U', 'V', 'W', 'X', 'Y', 'Z',
			'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
			'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
			'u', 'v', 'w', 'x', 'y', 'z'
		};

		if (vals.length == 52) Native.putchar('L');
		else Native.putchar('!');
		Native.putchar(vals[0]);
		Native.putchar(vals[25]);
		Native.putchar(vals[26]);
		Native.putchar(vals[51]);
		Native.putchar('\n');
	}
}
