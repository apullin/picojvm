// Compiler -> C (size)
class C {
	// Limits — sized to fit in picoJVM 64KB heap
	static int MAX_CLASSES  = 32;
	static int MAX_METHODS  = 192;
	static int MAX_FIELDS   = 320;
	static int MAX_NAMES    = 640;
	static int MAX_CP       = 2560;
	static int MAX_CODE     = 19456;
	static int MAX_LOCALS   = 64;
	static int MAX_EXC      = 32;
	static int MAX_VTABLE   = 128;
	static int MAX_INT_CONST= 80;
	static int MAX_STR_CONST= 80;

	// --- Name pool (interning) ---
	static byte[] nPool = new byte[6144];
	static int npLen;
	static int[] nOff = new int[MAX_NAMES];
	static int[] nLen = new int[MAX_NAMES];
	static int nCount;

	// Pre-interned well-known names
	static int N_OBJECT, N_STRING, N_NATIVE, N_MAIN, N_INIT, N_CLINIT;
	static int N_THROWABLE, N_EXCEPTION, N_RUNTIME_EX;
	static int N_PUTCHAR, N_IN, N_OUT, N_PEEK, N_POKE, N_HALT, N_PRINT;
	static int N_ARRAYCOPY, N_MEMCMP, N_WRITE_BYTES, N_STRING_FROM_BYTES;
	static int N_FILE_OPEN, N_FILE_READ_BYTE, N_FILE_WRITE_BYTE;
	static int N_FILE_READ, N_FILE_WRITE, N_FILE_CLOSE, N_FILE_DELETE;
	static int N_LENGTH, N_CHARAT, N_EQUALS, N_TOSTRING, N_HASHCODE;
	static int N_ARGS;

	// --- Class table ---
	static int cCount;
	static int[] cName   = new int[MAX_CLASSES]; // name index
	static int[] cParent = new int[MAX_CLASSES]; // class id (-1 = Object)
	static int[] cFieldC = new int[MAX_CLASSES]; // instance field count (incl inherited)
	static int[] cOwnF  = new int[MAX_CLASSES]; // own instance fields
	static int[] cVtSize = new int[MAX_CLASSES];
	static int[] cClinit   = new int[MAX_CLASSES]; // 0xFF=none
	static int[] cIfaceS = new int[MAX_CLASSES]; // start in iface list
	static int[] cIfaceC = new int[MAX_CLASSES];
	static boolean[] cIsIface = new boolean[MAX_CLASSES];
	static int[] cBodyS = new int[MAX_CLASSES]; // source offset
	static int[] cBodyE   = new int[MAX_CLASSES];
	static int[] vtable = new int[MAX_VTABLE]; // flat: class vtables concatenated
	static int[] vtBase = new int[MAX_CLASSES]; // offset into vtable[]
	// Interface list (flat)
	static int[] ifList = new int[64]; // class IDs
	static int ifListLen;

	// --- Field table ---
	static int fCount;
	static int[] fClass  = new int[MAX_FIELDS];
	static int[] fName   = new int[MAX_FIELDS]; // name index
	static boolean[] fStatic = new boolean[MAX_FIELDS];
	static int[] fSlot   = new int[MAX_FIELDS]; // assigned in resolve
	static int[] fInitPos  = new int[MAX_FIELDS]; // source pos of initializer (-1=none)
	static int[] fInitLn = new int[MAX_FIELDS]; // line of initializer
	static int[] fArrKind = new int[MAX_FIELDS]; // 0=non-array/int[], 4=byte[], 5=char[]

	// --- Method table ---
	static int mCount;
	static int[] mClass     = new int[MAX_METHODS];
	static int[] mName      = new int[MAX_METHODS]; // name index
	static int[] mArgC  = new int[MAX_METHODS];
	static int[] mMaxLoc = new int[MAX_METHODS];
	static int[] mMaxStk  = new int[MAX_METHODS];
	static int[] mFlags     = new int[MAX_METHODS]; // native encoding
	static int[] mCodeOff   = new int[MAX_METHODS];
	static int[] mCpBase    = new int[MAX_METHODS];
	static int[] mVtSlot= new int[MAX_METHODS];
	static int[] mVmid      = new int[MAX_METHODS];
	static int[] mExcC  = new int[MAX_METHODS];
	static int[] mExcIdx    = new int[MAX_METHODS];
	static boolean[] mStatic     = new boolean[MAX_METHODS];
	static boolean[] mIsCtor= new boolean[MAX_METHODS];
	static boolean[] mNative     = new boolean[MAX_METHODS];
	static int[] mBodyS = new int[MAX_METHODS]; // source pos
	static int[] mBodyE   = new int[MAX_METHODS]; // source pos
	static int[] mRetT   = new int[MAX_METHODS]; // 0=void,1=int,2=ref
	static int mainMi;

	// --- CP resolution table (16-bit entries, stored as lo/hi byte arrays) ---
	static byte[] cpEnt = new byte[MAX_CP];
	static byte[] cpEntH = new byte[MAX_CP];
	static int cpSz;

	// --- Per-method CP (during emit) ---
	// Entries written directly to cpEnt/cpEntH[cpMBase..cpMBase+cpMCount]
	static int cpMCount;
	static int cpMBase; // global offset

	// --- Bytecodes ---
	// Bytecodes stored in raw memory beyond source (not on heap),
	// or spilled to disk file when diskSpill=true
	static int cdBase; // set at runtime to SRC_BASE + srcLen
	static int cdLen;
	static boolean diskSpill; // true = write bytecodes to disk file
	static byte[] mcode = new byte[2048]; // current method bytecodes
	static int mcLen;

	// --- Integer constants ---
	static int[] intC = new int[MAX_INT_CONST];
	static int intCC;

	// --- String constants ---
	static byte[][] strC = new byte[MAX_STR_CONST][];
	static int[] strCLen = new int[MAX_STR_CONST];
	static int strCC;

	// --- Exception table ---
	static int[] excSPc  = new int[MAX_EXC];
	static int[] excEPc    = new int[MAX_EXC];
	static int[] excHPc= new int[MAX_EXC];
	static int[] excCCls= new int[MAX_EXC];
	static int excC;

	// --- Locals (per method, during emit) ---
	static int[] locName = new int[MAX_LOCALS]; // name index
	static int[] locSlot = new int[MAX_LOCALS];
	static int[] locType = new int[MAX_LOCALS]; // 0=int,1=ref,3=int[],4=byte[],5=char[]
	static int locCount;
	static int locNext;
	static int maxLoc;
	static int stkDepth;
	static int maxStk;

	// --- Backpatch / Labels ---
	static int[] patLoc   = new int[320]; // offset in mcode of branch operand
	static int[] patLbl = new int[320]; // which label
	static int patC;
	static int[] lblAddr  = new int[320]; // address for each label
	static int lblCount;

	// --- Loop context stack (break/continue targets) ---
	static int[] lpBrkLbl = new int[32];
	static int[] lpContLbl  = new int[32];
	static int lpDepth;

	// --- Switch case arrays (reused, not nested) ---
	static int[] caseVals   = new int[64];
	static int[] caseLbls = new int[64];

	// --- Current context ---
	static int curCi;
	static int curMi;
	static boolean curMStatic;

	// Source address
	static int SRC_BASE = 0xC000;
	static int SRC_LEN_ADDR = 0xBFFC;

	// Output to stdout via putchar
	static int outLen;
	static int uClsStart;
	static int sfCount;

	// ==================== MAIN ====================

	public static void main(String[] args) {
		int srcLen = (Native.peek(SRC_LEN_ADDR) & 0xFF) |
					 ((Native.peek(SRC_LEN_ADDR + 1) & 0xFF) << 8) |
					 ((Native.peek(SRC_LEN_ADDR + 2) & 0xFF) << 16) |
					 ((Native.peek(SRC_LEN_ADDR + 3) & 0xFF) << 24);
		if (srcLen == 0) {
			Native.halt();
			return;
		}

		cdBase = SRC_BASE + srcLen; // store bytecodes beyond source

		Lexer.initKeywords();
		initNames();
		initBuiltins();

		// Pass 1: Catalog
		Lexer.init(SRC_BASE, srcLen);
		Lexer.nextToken();
		Catalog.catalog();

		// Pass 2: Resolve
		Resolver.resolve();

		// Pass 3: Emit
		Lexer.init(SRC_BASE, srcLen);
		Lexer.nextToken();
		E.emit();

		// Pass 4: Link (write .pjvm to stdout)
		Linker.writeOut();

		Native.halt();
	}

	// ==================== NAME INTERNING ====================

	static void initNames() {
		npLen = 0;
		nCount = 0;
		N_OBJECT     = iStr("Object");
		N_STRING     = iStr("String");
		N_NATIVE     = iStr("Native");
		N_MAIN       = iStr("main");
		N_INIT       = iStr("<init>");
		N_CLINIT     = iStr("<clinit>");
		N_THROWABLE  = iStr("Throwable");
		N_EXCEPTION  = iStr("Exception");
		N_RUNTIME_EX = iStr("RuntimeException");
		N_PUTCHAR    = iStr("putchar");
		N_IN         = iStr("in");
		N_OUT        = iStr("out");
		N_PEEK       = iStr("peek");
		N_POKE       = iStr("poke");
		N_HALT       = iStr("halt");
		N_PRINT      = iStr("print");
		N_ARRAYCOPY       = iStr("arraycopy");
		N_MEMCMP          = iStr("memcmp");
		N_WRITE_BYTES     = iStr("writeBytes");
		N_STRING_FROM_BYTES = iStr("stringFromBytes");
		N_FILE_OPEN       = iStr("fileOpen");
		N_FILE_READ_BYTE  = iStr("fileReadByte");
		N_FILE_WRITE_BYTE = iStr("fileWriteByte");
		N_FILE_READ       = iStr("fileRead");
		N_FILE_WRITE      = iStr("fileWrite");
		N_FILE_CLOSE      = iStr("fileClose");
		N_FILE_DELETE     = iStr("fileDelete");
		N_LENGTH     = iStr("length");
		N_CHARAT     = iStr("charAt");
		N_EQUALS     = iStr("equals");
		N_TOSTRING   = iStr("toString");
		N_HASHCODE   = iStr("hashCode");
		N_ARGS       = iStr("args");
	}

	static byte[] iTmp = new byte[256];

	static int iStr(String s) {
		int len = s.length();
		for (int i = 0; i < len; i++) iTmp[i] = (byte) s.charAt(i);
		return intern(iTmp, len);
	}

	static int intern(byte[] buf, int len) {
		for (int i = 0; i < nCount; i++) {
			if (nLen[i] == len && Native.memcmp(nPool, nOff[i], buf, 0, len) == 0)
				return i;
		}
		if (nCount >= MAX_NAMES || npLen + len > 6144) {
			Lexer.error(251);
			return 0;
		}
		int idx = nCount++;
		nOff[idx] = npLen;
		nLen[idx] = len;
		Native.arraycopy(buf, 0, nPool, npLen, len);
		npLen += len;
		return idx;
	}

	static boolean nameEquals(int a, int b) {
		return a == b;
	}

	// ==================== HELPERS ====================

	static int initMethod(int ci, int nm, int argc, boolean isStat,
						  boolean isCtor, boolean isNat, int retType) {
		int mi = mCount++;
		mClass[mi] = ci; mName[mi] = nm; mArgC[mi] = argc;
		mStatic[mi] = isStat; mIsCtor[mi] = isCtor; mNative[mi] = isNat;
		mRetT[mi] = retType; mVtSlot[mi] = 0xFF; mVmid[mi] = 0xFF; mExcC[mi] = 0;
		return mi;
	}

	static int initClass(int nm) {
		int ci = cCount++;
		cName[ci] = nm; cParent[ci] = -1; cIsIface[ci] = false;
		cClinit[ci] = 0xFF; cIfaceS[ci] = ifListLen; cIfaceC[ci] = 0; cOwnF[ci] = 0;
		return ci;
	}

	// ==================== BUILTIN CLASSES/METHODS ====================

	static void initBuiltins() {
		cCount = 0; mCount = 0; fCount = 0;
	}

	static int addNat(int classNm, int methodNm, int argCount,
					  int nativeId, boolean isStatic, int retType) {
		for (int i = 0; i < mCount; i++) {
			if (mNative[i] && mName[i] == methodNm && mClass[i] == classNm) {
				mFlags[i] = (nativeId << 1) | 1;
				return i;
			}
		}
		int mi = initMethod(classNm, methodNm, argCount, isStatic, false, true, retType);
		mMaxLoc[mi] = argCount;
		mMaxStk[mi] = argCount > 0 ? argCount : 1;
		mFlags[mi] = (nativeId << 1) | 1;
		mBodyS[mi] = -1; mBodyE[mi] = -1;
		return mi;
	}

	// Ensure a native method exists, return its index
	static int ensNat(int classNm, int methodNm) {
		// Object.<init>
		if (classNm == N_OBJECT && methodNm == N_INIT)
			return addNat(N_OBJECT, N_INIT, 1, 6, false, 0);
		// Native class methods
		if (classNm == N_NATIVE) {
			if (methodNm == N_PUTCHAR) return addNat(N_NATIVE, N_PUTCHAR, 1, 0, true, 0);
			if (methodNm == N_IN)      return addNat(N_NATIVE, N_IN, 1, 1, true, 1);
			if (methodNm == N_OUT)     return addNat(N_NATIVE, N_OUT, 2, 2, true, 0);
			if (methodNm == N_PEEK)    return addNat(N_NATIVE, N_PEEK, 1, 3, true, 1);
			if (methodNm == N_POKE)    return addNat(N_NATIVE, N_POKE, 2, 4, true, 0);
			if (methodNm == N_HALT)    return addNat(N_NATIVE, N_HALT, 0, 5, true, 0);
			if (methodNm == N_PRINT)   return addNat(N_NATIVE, N_PRINT, 1, 11, true, 0);
			if (methodNm == N_ARRAYCOPY)       return addNat(N_NATIVE, N_ARRAYCOPY, 5, 13, true, 0);
			if (methodNm == N_MEMCMP)          return addNat(N_NATIVE, N_MEMCMP, 5, 14, true, 1);
			if (methodNm == N_WRITE_BYTES)     return addNat(N_NATIVE, N_WRITE_BYTES, 3, 15, true, 0);
			if (methodNm == N_STRING_FROM_BYTES) return addNat(N_NATIVE, N_STRING_FROM_BYTES, 3, 16, true, 2);
			if (methodNm == N_FILE_OPEN)       return addNat(N_NATIVE, N_FILE_OPEN, 3, 17, true, 1);
			if (methodNm == N_FILE_READ_BYTE)  return addNat(N_NATIVE, N_FILE_READ_BYTE, 0, 18, true, 1);
			if (methodNm == N_FILE_WRITE_BYTE) return addNat(N_NATIVE, N_FILE_WRITE_BYTE, 1, 19, true, 0);
			if (methodNm == N_FILE_READ)       return addNat(N_NATIVE, N_FILE_READ, 3, 20, true, 1);
			if (methodNm == N_FILE_WRITE)      return addNat(N_NATIVE, N_FILE_WRITE, 3, 21, true, 0);
			if (methodNm == N_FILE_CLOSE)      return addNat(N_NATIVE, N_FILE_CLOSE, 1, 22, true, 0);
			if (methodNm == N_FILE_DELETE)     return addNat(N_NATIVE, N_FILE_DELETE, 2, 23, true, 1);
		}
		// String methods
		if (classNm == N_STRING) {
			if (methodNm == N_LENGTH)   return addNat(N_STRING, N_LENGTH, 1, 7, false, 1);
			if (methodNm == N_CHARAT)   return addNat(N_STRING, N_CHARAT, 2, 8, false, 1);
			if (methodNm == N_EQUALS)   return addNat(N_STRING, N_EQUALS, 2, 9, false, 1);
			if (methodNm == N_TOSTRING) return addNat(N_STRING, N_TOSTRING, 1, 10, false, 2);
			if (methodNm == N_HASHCODE) return addNat(N_STRING, N_HASHCODE, 1, 12, false, 1);
		}
		return -1;
	}

}