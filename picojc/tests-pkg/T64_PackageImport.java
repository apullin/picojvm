package demo.app;

import demo.util.Helper;

public class T64_PackageImport {
	public static void main() {
		Native.putchar(Local.ch());
		Native.putchar(Helper.twice(33));
		Native.putchar('\n');
	}
}
