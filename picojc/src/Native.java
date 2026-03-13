public class Native {
	public static native void putchar(int c);
	public static native int  in(int port);
	public static native void out(int port, int val);
	public static native int  peek(int addr);
	public static native void poke(int addr, int val);
	public static native void halt();
	public static native void print(String s);
	public static native void arraycopy(byte[] src, int srcOff, byte[] dst, int dstOff, int len);
	public static native int  memcmp(byte[] a, int aOff, byte[] b, int bOff, int len);
	public static native void writeBytes(byte[] buf, int off, int len);
	public static native String stringFromBytes(byte[] src, int off, int len);
}
