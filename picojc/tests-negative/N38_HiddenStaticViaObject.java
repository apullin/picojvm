class N38Base {
	int value;
}

class N38Child extends N38Base {
	static int value;
}

class N38_HiddenStaticViaObject {
	public static void main() {
		N38Child child = new N38Child();
		Native.putchar(child.value);
	}
}
