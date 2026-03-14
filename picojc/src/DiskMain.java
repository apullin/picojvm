/**
 * DiskMain — streaming disk-backed compiler entry point.
 *
 * Reads source from disk via file I/O (128B sector reads).
 * Source is never loaded into memory — the Lexer streams it from disk.
 * Output goes to stdout via putchar.
 *
 * If "sources.lst" exists, reads it as a manifest (one filename per line).
 * Otherwise falls back to single-file "input.java".
 *
 * Usage: picojvm picojc-disk.pjvm
 */
public class DiskMain {
	static byte[] fname = new byte[10];

	// Multi-file manifest support
	static int MAX_FILES = 16;
	static int MAX_FNAME = 64;
	static byte[][] fileNames;
	static int[] fileNameLens;
	static int fileCount;

	public static void main(String[] args) {
		// "input.java" fallback name
		fname[0] = (byte)'i'; fname[1] = (byte)'n'; fname[2] = (byte)'p';
		fname[3] = (byte)'u'; fname[4] = (byte)'t'; fname[5] = (byte)'.';
		fname[6] = (byte)'j'; fname[7] = (byte)'a'; fname[8] = (byte)'v';
		fname[9] = (byte)'a';

		// Bytecodes stored at 0xC000 (source is on disk, not in memory)
		C.cdBase = C.SRC_BASE;

		Lexer.initKeywords();
		C.initNames();
		C.initBuiltins();

		boolean multiFile = readManifest();

		if (multiFile) {
			// Pass 1: Catalog (stream all files)
			Lexer.initDiskFiles(fileNames, fileNameLens, fileCount);
			Lexer.nextToken();
			Catalog.catalog();

			// Pass 2: Resolve (no source access)
			Resolver.resolve();

			// Pass 3: Emit (rewind and re-stream all files)
			Lexer.rewindDiskFiles();
			Lexer.nextToken();
			E.emit();
		} else {
			// Single-file fallback
			Lexer.initDisk(fname, 10);
			Lexer.nextToken();
			Catalog.catalog();

			Resolver.resolve();

			Lexer.rewindDisk();
			Lexer.initDisk(fname, 10);
			Lexer.nextToken();
			E.emit();
		}

		// Pass 4: Link (write .pjvm to stdout)
		Linker.writeOut();

		Lexer.closeDisk();
		Native.halt();
	}

	static byte[] mfName = new byte[11];

	static boolean readManifest() {
		// "sources.lst"
		mfName[0]=(byte)'s'; mfName[1]=(byte)'o'; mfName[2]=(byte)'u';
		mfName[3]=(byte)'r'; mfName[4]=(byte)'c'; mfName[5]=(byte)'e';
		mfName[6]=(byte)'s'; mfName[7]=(byte)'.'; mfName[8]=(byte)'l';
		mfName[9]=(byte)'s'; mfName[10]=(byte)'t';

		int r = Native.fileOpen(mfName, 11, 1);
		if (r != 0) return false; // no manifest — single-file mode

		fileNames = new byte[MAX_FILES][];
		fileNameLens = new int[MAX_FILES];
		fileCount = 0;

		byte[] lineBuf = new byte[MAX_FNAME];
		int lineLen = 0;

		while (true) {
			int ch = Native.fileReadByte();
			if (ch < 0) {
				if (lineLen > 0) addFile(lineBuf, lineLen);
				break;
			}
			if (ch == '\n' || ch == '\r') {
				if (lineLen > 0) addFile(lineBuf, lineLen);
				lineLen = 0;
			} else if (ch == '#') {
				// Comment: skip to end of line
				while (true) {
					ch = Native.fileReadByte();
					if (ch < 0 || ch == '\n') break;
				}
				if (lineLen > 0) addFile(lineBuf, lineLen);
				lineLen = 0;
			} else {
				if (lineLen < MAX_FNAME) {
					lineBuf[lineLen] = (byte) ch;
					lineLen++;
				}
			}
		}

		Native.fileClose();
		return fileCount > 0;
	}

	static void addFile(byte[] lineBuf, int lineLen) {
		// Trim trailing spaces/tabs
		while (lineLen > 0) {
			int c = lineBuf[lineLen - 1] & 0xFF;
			if (c == ' ' || c == '\t') lineLen--;
			else break;
		}
		// Skip leading spaces/tabs
		int start = 0;
		while (start < lineLen) {
			int c = lineBuf[start] & 0xFF;
			if (c == ' ' || c == '\t') start++;
			else break;
		}
		int trimLen = lineLen - start;
		if (trimLen <= 0) return;
		if (fileCount >= MAX_FILES) return;
		fileNames[fileCount] = new byte[trimLen];
		Native.arraycopy(lineBuf, start, fileNames[fileCount], 0, trimLen);
		fileNameLens[fileCount] = trimLen;
		fileCount++;
	}
}
