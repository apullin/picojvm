public class T62_ArrayInit {
	static byte[] bytes = { 'X', 'Y' };
	static String[] suffix = { "Z", "!" };

	public static void main() {
		int[] ints = { 'A', 'B' };
		short[] shorts = new short[]{ 'C', 'D' };
		String[] words = new String[]{ "E", "F" };
		int[] empty = new int[]{};

		Native.putchar(ints[0]);
		Native.putchar(ints[1]);
		Native.putchar(shorts[0]);
		Native.putchar(shorts[1]);
		Native.print(words[0]);
		Native.print(words[1]);
		Native.putchar(bytes[0]);
		Native.putchar(bytes[1]);
		Native.print(suffix[0]);
		Native.print(suffix[1]);
		if (empty.length == 0) Native.putchar('0');
		Native.putchar('\n');
	}
}
