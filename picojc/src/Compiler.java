// Compiler -> C (size)
class C {
	// Limits — sized to fit in picoJVM 64KB heap
	static final int MAX_CLASSES  = 32;
	static final int MAX_METHODS  = 192;
	static final int MAX_FIELDS   = 384;
	static final int MAX_NAMES    = 768;
	static final int MAX_CP       = 2560;
	static final int MAX_CODE     = 19456;
	static final int MAX_LOCALS   = 64;
	static final int MAX_EXC      = 32;
	static final int MAX_VTABLE   = 128;
	static final int MAX_INT_CONST= 80;
	static final int MAX_STR_CONST= 80;
	static final int MAX_VA_SLOTS = 8;

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
	static short[] cName   = new short[MAX_CLASSES]; // name index
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
	static byte[] fType = new byte[MAX_FIELDS]; // 0=int-like, 1=ref-like
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
	static byte[] mFixedArgs = new byte[MAX_METHODS];
	static int[] mBodyS = new int[MAX_METHODS]; // source pos (must be int: >32KB sources)
	static int[] mBodyE   = new int[MAX_METHODS]; // source pos (must be int: >32KB sources)
	static byte[] mRetT   = new byte[MAX_METHODS]; // 0=void,1=int,2=ref
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
	static byte[] locType = new byte[MAX_LOCALS]; // 0=int,1=ref,3=int[],4=byte[],5=char[],8=short[]
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

	static int iN() { int n = intern(Tk.strBuf, Tk.strLen); Lexer.nextToken(); return n; }

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
		mRetRefNm[mi] = (short)-1;
		mVarargs[mi] = false; mFixedArgs[mi] = 0;
		return mi;
	}

	static int initClass(int nm) {
		chk(cCount, MAX_CLASSES, 253);
		int ci = cCount++;
		cName[ci] = (short)nm; cParent[ci] = -1; cIsIface[ci] = false;
		cClinit[ci] = 0xFF; cIfaceS[ci] = (byte)ifListLen; cIfaceC[ci] = 0; cOwnF[ci] = 0;
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

	// Packed native method info: (nativeId << 8) | (argc << 2) | retType
	// Single source of truth for all native method metadata.
	static int natInfo(int nm) {
		if (nm == N_PUTCHAR)           return (0  << 8) | (1 << 2) | 0;
		if (nm == N_IN)                return (1  << 8) | (1 << 2) | 1;
		if (nm == N_OUT)               return (2  << 8) | (2 << 2) | 0;
		if (nm == N_PEEK)              return (3  << 8) | (1 << 2) | 1;
		if (nm == N_POKE)              return (4  << 8) | (2 << 2) | 0;
		if (nm == N_HALT)              return (5  << 8) | (0 << 2) | 0;
		if (nm == N_INIT)              return (6  << 8) | (1 << 2) | 0;
		if (nm == N_LENGTH)            return (7  << 8) | (1 << 2) | 1;
		if (nm == N_CHARAT)            return (8  << 8) | (2 << 2) | 1;
		if (nm == N_EQUALS)            return (9  << 8) | (2 << 2) | 1;
		if (nm == N_TOSTRING)          return (10 << 8) | (1 << 2) | 2;
		if (nm == N_PRINT)             return (11 << 8) | (1 << 2) | 0;
		if (nm == N_HASHCODE)          return (12 << 8) | (1 << 2) | 1;
		if (nm == N_ARRAYCOPY)         return (13 << 8) | (5 << 2) | 0;
		if (nm == N_MEMCMP)            return (14 << 8) | (5 << 2) | 1;
		if (nm == N_WRITE_BYTES)       return (15 << 8) | (3 << 2) | 0;
		if (nm == N_STRING_FROM_BYTES) return (16 << 8) | (3 << 2) | 2;
		if (nm == N_FILE_OPEN)         return (17 << 8) | (3 << 2) | 1;
		if (nm == N_FILE_READ_BYTE)    return (18 << 8) | (0 << 2) | 1;
		if (nm == N_FILE_WRITE_BYTE)   return (19 << 8) | (1 << 2) | 0;
		if (nm == N_FILE_READ)         return (20 << 8) | (3 << 2) | 1;
		if (nm == N_FILE_WRITE)        return (21 << 8) | (3 << 2) | 0;
		if (nm == N_FILE_CLOSE)        return (22 << 8) | (1 << 2) | 0;
		if (nm == N_FILE_DELETE)        return (23 << 8) | (2 << 2) | 1;
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
