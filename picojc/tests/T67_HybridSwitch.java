class T67_HybridSwitch {
	static void dense(int x) {
		switch (x) {
			case 100: Native.putchar(65); break; // A
			case 101: Native.putchar(66); break; // B
			case 102: Native.putchar(67); break; // C
			case 103: Native.putchar(68); break; // D
			case 104: Native.putchar(69); break; // E
			case 105: Native.putchar(70); break; // F
			case 106: Native.putchar(71); break; // G
			case 107: Native.putchar(72); break; // H
			default:  Native.putchar(73); break; // I
		}
	}

	static void sparse(int x) {
		switch (x) {
			case -70000: Native.putchar(74); break; // J
			case -1000:  Native.putchar(75); break; // K
			case 7:      Native.putchar(76); break; // L
			case 42:     Native.putchar(77); break; // M
			case 40000:  Native.putchar(78); break; // N
			case 900000: Native.putchar(79); break; // O
			default:     Native.putchar(80); break; // P
		}
	}

	public static void main(String[] args) {
		dense(100);
		dense(101);
		dense(102);
		dense(103);
		dense(104);
		dense(105);
		dense(106);
		dense(107);
		dense(9);
		sparse(-70000);
		sparse(-1000);
		sparse(7);
		sparse(42);
		sparse(40000);
		sparse(900000);
		sparse(8);
		Native.putchar(10);
		Native.halt();
	}
}
