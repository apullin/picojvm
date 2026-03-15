// Compiler -> C (size)
class C {
	// Limits — sized to fit in picoJVM 64KB heap
	static final int MAX_CLASSES  = 32;
	// Leave headroom for self-hosting feature work until method storage is dynamic.
	static final int MAX_METHODS  = 240;
	static final int MAX_FIELDS   = 416;
	static final int MAX_NAMES    = 896;
	static final int MAX_CP       = 2560;
	static final int MAX_CODE     = 19456;
	static final int MAX_LOCALS   = 64;
	static final int MAX_EXC      = 32;
	static final int MAX_VTABLE   = 128;
	static final int MAX_INT_CONST= 80;
	static final int MAX_STR_CONST= 80;
	static final int MAX_VA_SLOTS = 8;
	static final int NK_NONE = 0, NK_BYTE = 1, NK_CHAR = 2, NK_SHORT = 3;

	// --- Name pool (interning) ---
	static byte[] nPool = new byte[7168];
	static int npLen;
	static int[] nOff = new int[MAX_NAMES];
	static int[] nLen = new int[MAX_NAMES];
	static int nCount;

	// Well-known name IDs. These match the fixed seed order in initNames().
	static final int N_OBJECT = 0, N_STRING = 1, N_NATIVE = 2, N_MAIN = 3, N_INIT = 4, N_CLINIT = 5;
	static final int N_THROWABLE = 6, N_EXCEPTION = 7, N_RUNTIME_EX = 8;
	static final int N_PUTCHAR = 9, N_IN = 10, N_OUT = 11, N_PEEK = 12, N_POKE = 13, N_HALT = 14, N_PRINT = 15;
	static final int N_ARRAYCOPY = 16, N_MEMCMP = 17, N_WRITE_BYTES = 18, N_STRING_FROM_BYTES = 19;
	static final int N_FILE_OPEN = 20, N_FILE_READ_BYTE = 21, N_FILE_WRITE_BYTE = 22;
	static final int N_FILE_READ = 23, N_FILE_WRITE = 24, N_FILE_CLOSE = 25, N_FILE_DELETE = 26;
	static final int N_LENGTH = 27, N_CHARAT = 28, N_EQUALS = 29, N_TOSTRING = 30, N_HASHCODE = 31;
	static final int N_ARGS = 32;

	// --- Class table ---
	static int cCount;
	static short[] cName   = new short[MAX_CLASSES]; // name index
	static short[] cSimple = new short[MAX_CLASSES]; // simple source name
	static short[] cParent = new short[MAX_CLASSES]; // class id (-1 = Object)
	static byte[] cFieldC = new byte[MAX_CLASSES]; // instance field count (incl inherited)
	static byte[] cOwnF  = new byte[MAX_CLASSES]; // own instance fields
	static byte[] cVtSize = new byte[MAX_CLASSES];
	static short[] cClinit   = new short[MAX_CLASSES]; // 0xFF=none
	static byte[] cIfaceS = new byte[MAX_CLASSES]; // start in iface list
	static byte[] cIfaceC = new byte[MAX_CLASSES];
	static boolean[] cIsIface = new boolean[MAX_CLASSES];
	static boolean[] cIsEnum = new boolean[MAX_CLASSES];
	static int[] cBodyS = new int[MAX_CLASSES]; // source offset (must be int: >32KB sources)
	static int[] cBodyE   = new int[MAX_CLASSES];
	static short[] vtable = new short[MAX_VTABLE]; // flat: class vtables concatenated
	static byte[] vtBase = new byte[MAX_CLASSES]; // offset into vtable[]
	// Interface list (flat)
	static byte[] ifList = new byte[64]; // class IDs
	static int ifListLen;

	// --- Field table ---
	static int fCount;
	static byte[] fClass  = new byte[MAX_FIELDS];
	static short[] fName   = new short[MAX_FIELDS]; // name index
	static byte[] fType = new byte[MAX_FIELDS]; // 0=int-like, 1=ref, 2=object[]
	static byte[] fNarrow = new byte[MAX_FIELDS]; // 0=none/int/bool, 1=byte, 2=char, 3=short
	static boolean[] fStatic = new boolean[MAX_FIELDS];
	static short[] fSlot   = new short[MAX_FIELDS]; // assigned in resolve
	static int[] fInitPos  = new int[MAX_FIELDS]; // source pos of initializer (must be int: >32KB sources)
	static short[] fInitLn = new short[MAX_FIELDS]; // line of initializer
	static byte[] fArrKind = new byte[MAX_FIELDS]; // 0=non-array/int[], 4=byte[], 5=char[], 8=short[]
	static short[] fRefNm = new short[MAX_FIELDS]; // declared ref type name, -1 if unknown/non-ref
	static boolean[] fFinal = new boolean[MAX_FIELDS];
	static boolean[] fHasConst = new boolean[MAX_FIELDS]; // compile-time constant?
	static int[] fConstVal = new int[MAX_FIELDS]; // constant value (if fHasConst)

	// --- Method table ---
	static int mCount;
	static byte[] mClass     = new byte[MAX_METHODS];
	static short[] mName      = new short[MAX_METHODS]; // name index
	static byte[] mArgC  = new byte[MAX_METHODS];
	static byte[] mMaxLoc = new byte[MAX_METHODS];
	static byte[] mMaxStk  = new byte[MAX_METHODS];
	static byte[] mFlags     = new byte[MAX_METHODS]; // native encoding
	static short[] mCodeOff   = new short[MAX_METHODS];
	static short[] mCpBase    = new short[MAX_METHODS];
	static byte[] mVtSlot= new byte[MAX_METHODS];
	static byte[] mVmid      = new byte[MAX_METHODS];
	static byte[] mExcC  = new byte[MAX_METHODS];
	static byte[] mExcIdx    = new byte[MAX_METHODS];
	static boolean[] mStatic     = new boolean[MAX_METHODS];
	static boolean[] mIsCtor= new boolean[MAX_METHODS];
	static boolean[] mNative     = new boolean[MAX_METHODS];
	static boolean[] mVarargs    = new boolean[MAX_METHODS];
	static boolean[] mMainStrArgs = new boolean[MAX_METHODS];
	static byte[] mFixedArgs = new byte[MAX_METHODS];
	static int[] mBodyS = new int[MAX_METHODS]; // source pos (must be int: >32KB sources)
	static int[] mBodyE   = new int[MAX_METHODS]; // source pos (must be int: >32KB sources)
	static byte[] mRetT   = new byte[MAX_METHODS]; // 0=void,1=int,2=ref
	static byte[] mRetNarrow = new byte[MAX_METHODS]; // 0=none/int/bool, 1=byte, 2=char, 3=short
	static short[] mRetRefNm = new short[MAX_METHODS]; // declared ref return type, -1 if unknown/non-ref
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
	static int[] intC = new int[MAX_INT_CONST]; // must be int: stores 32-bit constant values
	static int intCC;

	// --- String constants ---
	static byte[][] strC = new byte[MAX_STR_CONST][];
	static byte[] strCLen = new byte[MAX_STR_CONST];
	static int strCC;

	// --- Exception table ---
	static short[] excSPc  = new short[MAX_EXC];
	static short[] excEPc    = new short[MAX_EXC];
	static short[] excHPc= new short[MAX_EXC];
	static byte[] excCCls= new byte[MAX_EXC];
	static int excC;

	// --- Locals (per method, during emit) ---
	static short[] locName = new short[MAX_LOCALS]; // name index
	static byte[] locSlot = new byte[MAX_LOCALS];
	static byte[] locType = new byte[MAX_LOCALS]; // 0=int,1=ref,2=object[],3=int[],4=byte[],5=char[],8=short[]
	static byte[] locNarrow = new byte[MAX_LOCALS]; // 0=none/int/bool, 1=byte, 2=char, 3=short
	static short[] locRefNm = new short[MAX_LOCALS]; // declared ref type name, -1 if unknown/non-ref
	static int locCount;
	static int locNext;
	static int maxLoc;
	static int stkDepth;
	static int maxStk;

	// --- Backpatch / Labels ---
	static short[] patLoc   = new short[320]; // offset in mcode of branch operand
	static short[] patLbl = new short[320]; // which label
	static int patC;
	static short[] lblAddr  = new short[320]; // address for each label
	static int lblCount;

	// --- Loop context stack (break/continue targets) ---
	static short[] lpBrkLbl = new short[32];
	static short[] lpContLbl  = new short[32];
	static int lpDepth;

	// --- Switch case arrays (reused, not nested) ---
	static int[] caseVals     = new int[64];
	static short[] caseLbls = new short[64];

	// --- Current context ---
	static int curCi;
	static int curMi;
	static boolean curMStatic;

	// Source address
	static final int SRC_BASE = 0xC000;
	static final int SRC_LEN_ADDR = 0xBFFC;

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

	// Seed names in a fixed order so the N_* constants stay stable across passes.
	static void initNames() {
		npLen = 0;
		nCount = 0;
		String[] seeds = {
			"Object", "String", "Native", "main", "<init>", "<clinit>",
			"Throwable", "Exception", "RuntimeException",
			"putchar", "in", "out", "peek", "poke", "halt", "print",
			"arraycopy", "memcmp", "writeBytes", "stringFromBytes",
			"fileOpen", "fileReadByte", "fileWriteByte", "fileRead",
			"fileWrite", "fileClose", "fileDelete",
			"length", "charAt", "equals", "toString", "hashCode", "args"
		};
		for (int i = 0; i < seeds.length; i++) iStr(seeds[i]);
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
		if (nCount >= MAX_NAMES || npLen + len > 7168) {
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

	static int iN() { int n = intern(Tk.strBuf, Tk.strLen); Lexer.nextToken(); return n; }

	static int dotNm(int leftNm, int rightNm) {
		int leftLen = nLen[leftNm];
		int rightLen = nLen[rightNm];
		int totalLen = leftLen + 1 + rightLen;
		if (totalLen > 255) {
			Lexer.error(251);
			return rightNm;
		}
		Native.arraycopy(nPool, nOff[leftNm], iTmp, 0, leftLen);
		iTmp[leftLen] = (byte)'.';
		Native.arraycopy(nPool, nOff[rightNm], iTmp, leftLen + 1, rightLen);
		return intern(iTmp, totalLen);
	}

	static int tailNm(int nm) {
		int off = nOff[nm];
		int len = nLen[nm];
		int start = 0;
		for (int i = 0; i < len; i++) {
			if (nPool[off + i] == '.') start = i + 1;
		}
		if (start == 0) return nm;
		return intern(nPool, off + start, len - start);
	}

	static int intern(byte[] buf, int off, int len) {
		for (int i = 0; i < nCount; i++) {
			if (nLen[i] == len && Native.memcmp(nPool, nOff[i], buf, off, len) == 0)
				return i;
		}
		if (nCount >= MAX_NAMES || npLen + len > 7168) {
			Lexer.error(251);
			return 0;
		}
		int idx = nCount++;
		nOff[idx] = npLen;
		nLen[idx] = len;
		Native.arraycopy(buf, off, nPool, npLen, len);
		npLen += len;
		return idx;
	}

	// ==================== HELPERS ====================

	// Limit check: error if count >= max
	static void chk(int count, int max, int code) {
		if (count >= max) Lexer.error(code);
	}

	static int initMethod(int ci, int nm, int argc, boolean isStat,
						  boolean isCtor, boolean isNat, int retType) {
		chk(mCount, MAX_METHODS, 252);
		int mi = mCount++;
		mClass[mi] = (byte)ci; mName[mi] = (short)nm; mArgC[mi] = (byte)argc;
		mStatic[mi] = isStat; mIsCtor[mi] = isCtor; mNative[mi] = isNat;
		mRetT[mi] = (byte)retType; mVtSlot[mi] = (byte)0xFF; mVmid[mi] = (byte)0xFF; mExcC[mi] = 0;
			mRetNarrow[mi] = (byte)NK_NONE;
			mRetRefNm[mi] = (short)-1;
			mVarargs[mi] = false; mFixedArgs[mi] = 0;
			mMainStrArgs[mi] = false;
			return mi;
		}

	static int initClass(int nm) {
		chk(cCount, MAX_CLASSES, 253);
		int ci = cCount++;
		cName[ci] = (short)nm; cParent[ci] = -1; cIsIface[ci] = false;
		cSimple[ci] = (short)nm;
		cClinit[ci] = 0xFF; cIfaceS[ci] = (byte)ifListLen; cIfaceC[ci] = 0; cOwnF[ci] = 0;
		return ci;
	}

	// ==================== BUILTIN CLASSES/METHODS ====================

	static void initBuiltins() {
		cCount = 0; mCount = 0; fCount = 0;
		natTable = new int[] {
			N_PUTCHAR, packNat(0, 1, 0),
			N_IN, packNat(1, 1, 1),
			N_OUT, packNat(2, 2, 0),
			N_PEEK, packNat(3, 1, 1),
			N_POKE, packNat(4, 2, 0),
			N_HALT, packNat(5, 0, 0),
			N_INIT, packNat(6, 1, 0),
			N_LENGTH, packNat(7, 1, 1),
			N_CHARAT, packNat(8, 2, 1),
			N_EQUALS, packNat(9, 2, 1),
			N_TOSTRING, packNat(10, 1, 2),
			N_PRINT, packNat(11, 1, 0),
			N_HASHCODE, packNat(12, 1, 1),
			N_ARRAYCOPY, packNat(13, 5, 0),
			N_MEMCMP, packNat(14, 5, 1),
			N_WRITE_BYTES, packNat(15, 3, 0),
			N_STRING_FROM_BYTES, packNat(16, 3, 2),
			N_FILE_OPEN, packNat(17, 3, 1),
			N_FILE_READ_BYTE, packNat(18, 0, 1),
			N_FILE_WRITE_BYTE, packNat(19, 1, 0),
			N_FILE_READ, packNat(20, 3, 1),
			N_FILE_WRITE, packNat(21, 3, 0),
			N_FILE_CLOSE, packNat(22, 1, 0),
			N_FILE_DELETE, packNat(23, 2, 1)
		};
	}

	static int addNat(int classNm, int methodNm, int argCount,
					  int nativeId, boolean isStatic, int retType) {
		for (int i = 0; i < mCount; i++) {
			if (mNative[i] && mName[i] == methodNm && mClass[i] == classNm) {
				mFlags[i] = (byte)((nativeId << 1) | 1);
				return i;
			}
		}
		int mi = initMethod(classNm, methodNm, argCount, isStatic, false, true, retType);
		mMaxLoc[mi] = (byte)argCount;
		mMaxStk[mi] = (byte)(argCount > 0 ? argCount : 1);
		mFlags[mi] = (byte)((nativeId << 1) | 1);
		mBodyS[mi] = -1; mBodyE[mi] = -1;
		return mi;
	}

	static int packNat(int id, int argc, int retType) {
		return (id << 8) | (argc << 2) | retType;
	}

	static int[] natTable;

	// Packed native method info: (nativeId << 8) | (argc << 2) | retType
	// Single source of truth for all native method metadata.
	static int natInfo(int nm) {
		if (natTable == null) return -1;
		for (int i = 0; i < natTable.length; i += 2) {
			if (natTable[i] == nm) return natTable[i + 1];
		}
		return -1;
	}

	// Ensure a native method exists, return its index
	static int ensNat(int classNm, int methodNm) {
		int info = natInfo(methodNm);
		if (info < 0) return -1;
		int id = info >> 8, argc = (info >> 2) & 63, ret = info & 3;
		if (classNm == N_OBJECT && methodNm == N_INIT)
			return addNat(N_OBJECT, methodNm, argc, id, false, ret);
		if (classNm == N_NATIVE) return addNat(N_NATIVE, methodNm, argc, id, true, ret);
		if (classNm == N_STRING) return addNat(N_STRING, methodNm, argc, id, false, ret);
		return -1;
	}

}
