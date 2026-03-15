package demo.app;

class HelperLocal {
	static int ch() { return 'P'; }
}

public class T63_PackageLocal {
	public static void main() {
		Native.putchar(HelperLocal.ch());
		Native.putchar('\n');
	}
}
