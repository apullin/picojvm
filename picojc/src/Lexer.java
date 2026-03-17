public class Lexer {
	static int srcBase;
	static int srcLen;
	static int pos;
	static int line;
	static int savedPos;
	static int savedLine;

	// Disk streaming mode
	static boolean diskMode;
	static byte[] dBuf;     // streaming buffer; can grow while lookahead pins old bytes
	static int dBase;       // file offset of dBuf[0]
	static int dLen;        // valid bytes in buffer
	static boolean dEof;    // true when file is exhausted
	static boolean dSaved;  // true between save() and restore()

	// Multi-file support
	static byte[][] dFiles;    // file list (max 16)
	static int[] dFileLens;    // filename lengths
	static int dFileCount;     // number of source files
	static int dFileCur;       // current file index

	static void init(int base, int length) {
		srcBase = base;
		srcLen = length;
		pos = 0;
		line = 1;
		diskMode = false;
	}

	static void initDisk(byte[] fname, int nameLen) {
		int r = Native.fileOpen(fname, nameLen, 1);
		if (r != 0) {
			Native.putchar('E');
			Native.putchar('F');
			Native.halt();
			return;
		}
		diskMode = true;
		if (dBuf == null) dBuf = new byte[256];
		dBase = 0;
		dLen = 0;
		dEof = false;
		dSaved = false;
		srcLen = 0x7FFFFFFF; // effectively infinite for pos < srcLen guards
		pos = 0;
		line = 1;
		dFill();
	}

	static void rewindDisk() {
		Native.fileClose(1);
		// Reopen same file — caller must set up fname again or use REWIND
		// For now, close and reopen is handled by the caller
		dBase = 0;
		dLen = 0;
		dEof = false;
		dSaved = false;
		pos = 0;
		line = 1;
	}

	static void closeDisk() {
		Native.fileClose(0); // close both read and write
		diskMode = false;
	}

	// Multi-file: open a list of source files as one continuous stream
	static void initDiskFiles(byte[][] files, int[] lens, int count) {
		dFiles = files;
		dFileLens = lens;
		dFileCount = count;
		dFileCur = 0;
		int r = Native.fileOpen(files[0], lens[0], 1);
		if (r != 0) { Native.putchar('E'); Native.putchar('F'); Native.halt(); return; }
		diskMode = true;
		if (dBuf == null) dBuf = new byte[256];
		dBase = 0;
		dLen = 0;
		dEof = false;
		dSaved = false;
		srcLen = 0x7FFFFFFF;
		pos = 0;
		line = 1;
		dFill();
	}

	// Multi-file: rewind to first file for re-streaming (Pass 3)
	static void rewindDiskFiles() {
		Native.fileClose(1);
		dFileCur = 0;
		int r = Native.fileOpen(dFiles[0], dFileLens[0], 1);
		if (r != 0) { Native.putchar('E'); Native.putchar('F'); Native.halt(); return; }
		dBase = 0;
		dLen = 0;
		dEof = false;
		dSaved = false;
		pos = 0;
		line = 1;
		dFill();
	}

	// Multi-file: advance to next file, return false if no more
	static boolean advanceFile() {
		Native.fileClose(1);
		dFileCur++;
		if (dFileCur >= dFileCount) return false;
		int r = Native.fileOpen(dFiles[dFileCur], dFileLens[dFileCur], 1);
		if (r != 0) { Native.putchar('E'); Native.putchar('F'); Native.halt(); return false; }
		dBase = pos; // new file's byte 0 maps to current global pos
		dLen = 0;
		dEof = false;
		dFill();
		return true;
	}

	// Fill buffer from disk (128B sector read)
	static void dFill() {
		if (dEof) return;
		int space = dBuf.length - dLen;
		if (space < 128 && dSaved) {
			int newLen = dBuf.length;
			while (newLen - dLen < 128) newLen = newLen << 1;
			byte[] bigger = new byte[newLen];
			Native.arraycopy(dBuf, 0, bigger, 0, dLen);
			dBuf = bigger;
			space = dBuf.length - dLen;
		}
		if (space < 128) return;
		int n = Native.fileRead(dBuf, dLen, 128);
		if (n <= 0) { dEof = true; return; }
		dLen += n;
	}

	// Compact buffer: discard consumed data before the keep point
	static void dCompact() {
		int keep = pos;
		if (dSaved && savedPos < keep) keep = savedPos;
		int shift = keep - dBase;
		if (shift <= 0) return;
		int rem = dLen - shift;
		if (rem > 0) Native.arraycopy(dBuf, shift, dBuf, 0, rem);
		dBase += shift;
		dLen = rem;
	}

	static int ch() {
		if (diskMode) {
			int bi = pos - dBase;
			if (bi >= 0 && bi < dLen) return dBuf[bi] & 0xFF;
			if (dEof) {
				if (dFileCount > 0 && advanceFile()) {
					bi = pos - dBase;
					if (bi >= 0 && bi < dLen) return dBuf[bi] & 0xFF;
				}
				return -1;
			}
			dCompact();
			dFill();
			bi = pos - dBase;
			if (bi >= 0 && bi < dLen) return dBuf[bi] & 0xFF;
			// dFill may have set dEof — try next file
			if (dEof && dFileCount > 0 && advanceFile()) {
				bi = pos - dBase;
				if (bi >= 0 && bi < dLen) return dBuf[bi] & 0xFF;
			}
			return -1;
		}
		if (pos >= srcLen) return -1;
		return Native.peek(srcBase + pos) & 0xFF;
	}

	// Peek at pos+1 without advancing
	static int chNext() {
		if (diskMode) {
			int bi = (pos + 1) - dBase;
			if (bi >= 0 && bi < dLen) return dBuf[bi] & 0xFF;
			if (dEof) return -1;
			dCompact();
			dFill();
			bi = (pos + 1) - dBase;
			if (bi >= 0 && bi < dLen) return dBuf[bi] & 0xFF;
			return -1;
		}
		if (pos + 1 >= srcLen) return -1;
		return Native.peek(srcBase + pos + 1) & 0xFF;
	}

	static void advance() {
		pos++;
	}

	static void save() {
		savedPos = pos;
		savedLine = line;
		if (diskMode) dSaved = true;
	}

	static void restore() {
		pos = savedPos;
		line = savedLine;
		if (diskMode) dSaved = false;
	}

	// Discard saved position without restoring (e.g., constructor lookahead succeeded)
	static void discardSave() {
		dSaved = false;
	}

	static void skipWhitespaceAndComments() {
		while (true) {
			int c = ch();
			if (c < 0) return;
			if (c == ' ' || c == '\t' || c == '\r') {
				advance();
			} else if (c == '\n') {
				advance();
				line++;
			} else if (c == '/') {
				int next = chNext();
				if (next == '/') {
					// Single-line comment
					advance(); advance();
					int sc = ch();
					while (sc >= 0 && sc != '\n') { advance(); sc = ch(); }
				} else if (next == '*') {
					// Multi-line comment
					advance(); advance();
					while (true) {
						int mc = ch();
						if (mc < 0) break;
						if (mc == '*') {
							advance();
							if (ch() == '/') { advance(); break; }
						} else {
							if (mc == '\n') line++;
							advance();
						}
					}
				} else {
					return;
				}
			} else {
				return;
			}
		}
	}

	static boolean isAlpha(int c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
	}

	static boolean isDigit(int c) {
		return c >= '0' && c <= '9';
	}

	static boolean isAlphaNum(int c) {
		return isAlpha(c) || isDigit(c);
	}

	// Keyword tables
	static String[] kwNames;
	static int[] kwTokens;
	static int kwCount;

	static void initKeywords() {
		// Keyword tables are fixed compiler data; keep them as compact local literals.
		kwNames = new String[] {
			"class", "extends", "implements", "interface",
			"static", "public", "private", "protected",
			"void", "int", "byte", "char", "short", "boolean",
			"if", "else", "while", "do", "for",
			"switch", "case", "default", "break", "continue", "return",
			"new", "null", "this", "throw", "try", "catch", "finally",
			"instanceof", "true", "false", "native", "super",
			"final", "abstract", "String", "package", "import", "enum"
		};
		kwTokens = new int[] {
			Tk.CLASS, Tk.EXTENDS, Tk.IMPLEMENTS, Tk.INTERFACE,
			Tk.STATIC, Tk.PUBLIC, Tk.PRIVATE, Tk.PROTECTED,
			Tk.VOID, Tk.INT, Tk.BYTE, Tk.CHAR, Tk.SHORT, Tk.BOOLEAN,
			Tk.IF, Tk.ELSE, Tk.WHILE, Tk.DO, Tk.FOR,
			Tk.SWITCH, Tk.CASE, Tk.DEFAULT, Tk.BREAK, Tk.CONTINUE, Tk.RETURN,
			Tk.NEW, Tk.NULL, Tk.THIS, Tk.THROW, Tk.TRY, Tk.CATCH, Tk.FINALLY,
			Tk.INSTANCEOF, Tk.TRUE, Tk.FALSE, Tk.NATIVE, Tk.SUPER,
			Tk.FINAL, Tk.ABSTRACT, Tk.STRING_KW, Tk.PACKAGE, Tk.IMPORT, Tk.ENUM
		};
		kwCount = kwNames.length;
	}

	static int lookupKeyword() {
		for (int i = 0; i < kwCount; i++) {
			String kw = kwNames[i];
			if (kw.length() == Tk.strLen) {
				boolean match = true;
				for (int j = 0; j < Tk.strLen; j++) {
					if ((byte) kw.charAt(j) != Tk.strBuf[j]) { match = false; break; }
				}
				if (match) return kwTokens[i];
			}
		}
		return Tk.IDENT;
	}

	static void readIdentifier() {
		Tk.strLen = 0;
		int c = ch();
		while (c >= 0 && isAlphaNum(c)) {
			if (Tk.strLen < 255) {
				Tk.strBuf[Tk.strLen] = (byte) c;
				Tk.strLen++;
			}
			advance();
			c = ch();
		}
		Tk.type = lookupKeyword();
	}

	static void readNumber() {
		int c = ch();
		if (c == '0') {
			int next = chNext();
			if (next == 'x' || next == 'X') {
				// Hex literal
				advance(); advance();
				int val = 0;
				while (true) {
					c = ch();
					if (c >= '0' && c <= '9') { val = val * 16 + (c - '0'); advance(); }
					else if (c >= 'a' && c <= 'f') { val = val * 16 + (c - 'a' + 10); advance(); }
					else if (c >= 'A' && c <= 'F') { val = val * 16 + (c - 'A' + 10); advance(); }
					else break;
				}
				Tk.type = Tk.INT_LIT;
				Tk.intValue = val;
				return;
			} else if (next == 'b' || next == 'B') {
				// Binary literal
				advance(); advance();
				int val = 0;
				while (true) {
					c = ch();
					if (c == '0' || c == '1') { val = val * 2 + (c - '0'); advance(); }
					else break;
				}
				Tk.type = Tk.INT_LIT;
				Tk.intValue = val;
				return;
			}
		}
		// Decimal literal
		int val = 0;
		c = ch();
		while (c >= 0 && isDigit(c)) {
			val = val * 10 + (c - '0');
			advance();
			c = ch();
		}
		// Skip L/l suffix if present
		if (c == 'L' || c == 'l') advance();
		Tk.type = Tk.INT_LIT;
		Tk.intValue = val;
	}

	static int readEscape() {
		advance(); // skip backslash
		int c = ch();
		advance();
		if (c == 'n') return '\n';
		if (c == 't') return '\t';
		if (c == 'r') return '\r';
		if (c == '\\') return '\\';
		if (c == '\'') return '\'';
		if (c == '"') return '"';
		if (c == '0') return 0;
		return c;
	}

	static void readCharLiteral() {
		advance(); // skip opening '
		int val;
		if (ch() == '\\') {
			val = readEscape();
		} else {
			val = ch();
			advance();
		}
		advance(); // skip closing '
		Tk.type = Tk.CHAR_LIT;
		Tk.intValue = val;
	}

	static void readStringLiteral() {
		advance(); // skip opening "
		Tk.strLen = 0;
		int c = ch();
		while (c >= 0 && c != '"') {
			if (c == '\\') {
				c = readEscape();
			} else {
				advance();
			}
			if (Tk.strLen < 255) {
				Tk.strBuf[Tk.strLen] = (byte) c;
				Tk.strLen++;
			}
			c = ch();
		}
		if (c >= 0) advance(); // skip closing "
		Tk.type = Tk.STR_LIT;
	}

	static boolean matchChar(int expected) {
		int c = ch();
		if (c >= 0 && c == expected) {
			advance();
			return true;
		}
		return false;
	}

	static void nextToken() {
		skipWhitespaceAndComments();
		Tk.line = line;

		int c = ch();
		if (c < 0) {
			Tk.type = Tk.EOF;
			return;
		}

		if (isAlpha(c)) {
			readIdentifier();
			return;
		}
		if (isDigit(c)) {
			readNumber();
			return;
		}

		advance(); // consume the character
		if (c == '{') { Tk.type = Tk.LBRACE; }
		else if (c == '}') { Tk.type = Tk.RBRACE; }
		else if (c == '(') { Tk.type = Tk.LPAREN; }
		else if (c == ')') { Tk.type = Tk.RPAREN; }
		else if (c == '[') { Tk.type = Tk.LBRACKET; }
		else if (c == ']') { Tk.type = Tk.RBRACKET; }
		else if (c == ';') { Tk.type = Tk.SEMI; }
		else if (c == ',') { Tk.type = Tk.COMMA; }
		else if (c == '.') {
		if (ch() == '.' && chNext() == '.') {
			advance(); advance();
			Tk.type = Tk.ELLIPSIS;
		} else {
			Tk.type = Tk.DOT;
		}
	}
		else if (c == '~') { Tk.type = Tk.TILDE; }
		else if (c == '?') { Tk.type = Tk.QUESTION; }
		else if (c == ':') { Tk.type = Tk.COLON; }
		else if (c == '\'') { pos--; readCharLiteral(); }
		else if (c == '"') { pos--; readStringLiteral(); }
		else if (c == '=') { Tk.type = matchChar('=') ? Tk.EQ : Tk.ASSIGN; }
		else if (c == '!') { Tk.type = matchChar('=') ? Tk.NE : Tk.BANG; }
		else if (c == '<') {
			if (matchChar('=')) Tk.type = Tk.LE;
			else if (matchChar('<')) Tk.type = matchChar('=') ? Tk.SHL_EQ : Tk.SHL;
			else Tk.type = Tk.LT;
		}
		else if (c == '>') {
			if (matchChar('=')) Tk.type = Tk.GE;
			else if (matchChar('>')) {
				if (matchChar('>')) Tk.type = matchChar('=') ? Tk.USHR_EQ : Tk.USHR;
				else Tk.type = matchChar('=') ? Tk.SHR_EQ : Tk.SHR;
			}
			else Tk.type = Tk.GT;
		}
		else if (c == '+') {
			if (matchChar('+')) Tk.type = Tk.INC;
			else if (matchChar('=')) Tk.type = Tk.PLUS_EQ;
			else Tk.type = Tk.PLUS;
		}
		else if (c == '-') {
			if (matchChar('-')) Tk.type = Tk.DEC;
			else if (matchChar('=')) Tk.type = Tk.MINUS_EQ;
			else Tk.type = Tk.MINUS;
		}
		else if (c == '*') { Tk.type = matchChar('=') ? Tk.STAR_EQ : Tk.STAR; }
		else if (c == '/') { Tk.type = matchChar('=') ? Tk.SLASH_EQ : Tk.SLASH; }
		else if (c == '%') { Tk.type = matchChar('=') ? Tk.PERCENT_EQ : Tk.PERCENT; }
		else if (c == '&') {
			if (matchChar('&')) Tk.type = Tk.AND;
			else if (matchChar('=')) Tk.type = Tk.AMP_EQ;
			else Tk.type = Tk.AMP;
		}
		else if (c == '|') {
			if (matchChar('|')) Tk.type = Tk.OR;
			else if (matchChar('=')) Tk.type = Tk.PIPE_EQ;
			else Tk.type = Tk.PIPE;
		}
		else if (c == '^') { Tk.type = matchChar('=') ? Tk.CARET_EQ : Tk.CARET; }
		else if (c == '@') { Tk.type = Tk.AT; }
		else {
			error(c);
		}
	}

	static void expect(int tok) {
		if (Tk.type != tok) {
			error(tok);
		}
		nextToken();
	}

	static void error(int code) {
		// Output: E<line><code> then halt
		Native.putchar('E');
		printNum(Tk.line);
		Native.putchar(':');
		printNum(code);
		Native.putchar('\n');
		Native.halt();
	}

	static void printNum(int n) {
		if (n < 0) { Native.putchar('-'); n = -n; }
		if (n >= 10) printNum(n / 10);
		Native.putchar('0' + (n % 10));
	}
}
