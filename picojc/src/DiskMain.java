/**
 * DiskMain — thin wrapper that replaces --preload with file I/O.
 *
 * Reads "input.java" from disk into memory at SRC_BASE, stores
 * the length at SRC_LEN_ADDR, then calls C.main() to compile.
 * Output goes to stdout (putchar) as before.
 */
public class DiskMain {
	public static void main(String[] args) {
		byte[] fname = new byte[10];
		fname[0] = (byte)'i'; fname[1] = (byte)'n'; fname[2] = (byte)'p';
		fname[3] = (byte)'u'; fname[4] = (byte)'t'; fname[5] = (byte)'.';
		fname[6] = (byte)'j'; fname[7] = (byte)'a'; fname[8] = (byte)'v';
		fname[9] = (byte)'a';

		int r = Native.fileOpen(fname, 10, 1);
		if (r != 0) {
			Native.print("ERR: cannot open input.java\n");
			Native.halt();
			return;
		}

		// Read source into memory at SRC_BASE (0xC000)
		int addr = C.SRC_BASE;
		int len = 0;
		int ch = Native.fileReadByte();
		while (ch >= 0) {
			Native.poke(addr + len, ch);
			len++;
			ch = Native.fileReadByte();
		}
		Native.fileClose();

		// Store length at SRC_LEN_ADDR (0xBFFC) as 32-bit LE
		Native.poke(C.SRC_LEN_ADDR,     len & 0xFF);
		Native.poke(C.SRC_LEN_ADDR + 1, (len >> 8) & 0xFF);
		Native.poke(C.SRC_LEN_ADDR + 2, (len >> 16) & 0xFF);
		Native.poke(C.SRC_LEN_ADDR + 3, (len >> 24) & 0xFF);

		// Invoke compiler
		C.main(null);
	}
}
