// Test short[] array type (2 bytes per element)
// Expected output: ABCDEFGHx
public class T42_ShortArray {
	static short[] vals = new short[4];

	public static void main(String[] args) {
		vals[0] = 65;  // 'A'
		vals[1] = 66;  // 'B'
		vals[2] = 67;  // 'C'
		vals[3] = 68;  // 'D'
		for (int i = 0; i < 4; i++) {
			Native.putchar(vals[i]);
		}

		// Test local short[]
		short[] local = new short[4];
		local[0] = 69;  // 'E'
		local[1] = 70;  // 'F'
		local[2] = 71;  // 'G'
		local[3] = 72;  // 'H'
		for (int i = 0; i < 4; i++) {
			Native.putchar(local[i]);
		}

		// Test value > 127 (verify 2-byte element, not sign-extended byte)
		short[] big = new short[1];
		big[0] = 200;
		if (big[0] == 200) {
			Native.putchar(120); // 'x' = success
		}

		Native.halt();
	}
}
