class T50_SwitchEdge {
	// Fallthrough
	static void testFallthrough(int x) {
		switch (x) {
			case 1:
			case 2:
				Native.putchar(65); // 'A' for 1 or 2
				break;
			case 3:
				Native.putchar(66); // 'B' for 3
				// fallthrough to 4
			case 4:
				Native.putchar(67); // 'C' for 3 or 4
				break;
			default:
				Native.putchar(68); // 'D'
		}
	}

	// Large case values (was broken with short[] caseVals)
	static void testLarge(int x) {
		switch (x) {
			case 40000: Native.putchar(49); break; // '1'
			case -1000: Native.putchar(50); break; // '2'
			case 0:     Native.putchar(51); break; // '3'
			default:    Native.putchar(48); break; // '0'
		}
	}

	// Enum in switch
	static void testEnum(int ord) {
		switch (ord) {
			case 0: Native.putchar(82); break; // 'R' = Red
			case 1: Native.putchar(71); break; // 'G' = Green
			case 2: Native.putchar(66); break; // 'B' = Blue
		}
	}

	// String switch fallthrough
	static void testStrFall(String s) {
		switch (s) {
			case "yes":
			case "y":
				Native.putchar(89); // 'Y'
				break;
			case "no":
				Native.putchar(78); // 'N'
				break;
			default:
				Native.putchar(63); // '?'
		}
	}

	public static void main() {
		testFallthrough(1);  // A
		testFallthrough(2);  // A
		testFallthrough(3);  // BC
		testFallthrough(4);  // C
		testFallthrough(5);  // D
		testLarge(40000);    // 1
		testLarge(-1000);    // 2
		testLarge(0);        // 3
		testLarge(99);       // 0
		testEnum(0);         // R
		testEnum(1);         // G
		testEnum(2);         // B
		testStrFall("yes");  // Y
		testStrFall("y");    // Y
		testStrFall("no");   // N
		testStrFall("x");    // ?
	}
}
