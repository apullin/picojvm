class N37Base {
	static int value;
}

class N37Child extends N37Base {
	int value;
}

class N37_HiddenInstanceAsStatic {
	public static void main() {
		Native.putchar(N37Child.value);
	}
}
