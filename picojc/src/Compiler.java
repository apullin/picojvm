public class Compiler {
    // Limits — sized to fit in picoJVM 64KB heap
    static int MAX_CLASSES  = 32;
    static int MAX_METHODS  = 192;
    static int MAX_FIELDS   = 384;
    static int MAX_NAMES    = 640;
    static int MAX_CP       = 2560;
    static int MAX_CODE     = 19456;
    static int MAX_LOCALS   = 64;
    static int MAX_EXC      = 32;
    static int MAX_VTABLE   = 128;
    static int MAX_INT_CONST= 80;
    static int MAX_STR_CONST= 80;

    // --- Name pool (interning) ---
    static byte[] namePool = new byte[10240];
    static int namePoolLen;
    static int[] nameOff = new int[MAX_NAMES];
    static int[] nameLen = new int[MAX_NAMES];
    static int nameCount;

    // Pre-interned well-known names
    static int N_OBJECT, N_STRING, N_NATIVE, N_MAIN, N_INIT, N_CLINIT;
    static int N_THROWABLE, N_EXCEPTION, N_RUNTIME_EX;
    static int N_PUTCHAR, N_IN, N_OUT, N_PEEK, N_POKE, N_HALT, N_PRINT;
    static int N_ARRAYCOPY, N_MEMCMP, N_WRITE_BYTES, N_STRING_FROM_BYTES;
    static int N_LENGTH, N_CHARAT, N_EQUALS, N_TOSTRING, N_HASHCODE;
    static int N_ARGS;

    // --- Class table ---
    static int classCount;
    static int[] className   = new int[MAX_CLASSES]; // name index
    static int[] classParent = new int[MAX_CLASSES]; // class id (-1 = Object)
    static int[] classFieldCount = new int[MAX_CLASSES]; // instance field count (incl inherited)
    static int[] classOwnFields  = new int[MAX_CLASSES]; // own instance fields
    static int[] classVtableSize = new int[MAX_CLASSES];
    static int[] classClinitMi   = new int[MAX_CLASSES]; // 0xFF=none
    static int[] classIfaceStart = new int[MAX_CLASSES]; // start in iface list
    static int[] classIfaceCount = new int[MAX_CLASSES];
    static boolean[] classIsInterface = new boolean[MAX_CLASSES];
    static int[] classBodyStart = new int[MAX_CLASSES]; // source offset
    static int[] classBodyEnd   = new int[MAX_CLASSES];
    static int[] vtable = new int[MAX_VTABLE]; // flat: class vtables concatenated
    static int[] vtableBase = new int[MAX_CLASSES]; // offset into vtable[]
    // Interface list (flat)
    static int[] ifaceList = new int[64]; // class IDs
    static int ifaceListLen;

    // --- Field table ---
    static int fieldCount;
    static int[] fieldClass  = new int[MAX_FIELDS];
    static int[] fieldName   = new int[MAX_FIELDS]; // name index
    static boolean[] fieldIsStatic = new boolean[MAX_FIELDS];
    static int[] fieldSlot   = new int[MAX_FIELDS]; // assigned in resolve
    static int[] fieldInitPos  = new int[MAX_FIELDS]; // source pos of initializer (-1=none)
    static int[] fieldInitLine = new int[MAX_FIELDS]; // line of initializer
    static int[] fieldArrayKind = new int[MAX_FIELDS]; // 0=non-array/int[], 4=byte[], 5=char[]

    // --- Method table ---
    static int methodCount;
    static int[] methodClass     = new int[MAX_METHODS];
    static int[] methodName      = new int[MAX_METHODS]; // name index
    static int[] methodArgCount  = new int[MAX_METHODS];
    static int[] methodMaxLocals = new int[MAX_METHODS];
    static int[] methodMaxStack  = new int[MAX_METHODS];
    static int[] methodFlags     = new int[MAX_METHODS]; // native encoding
    static int[] methodCodeOff   = new int[MAX_METHODS];
    static int[] methodCpBase    = new int[MAX_METHODS];
    static int[] methodVtableSlot= new int[MAX_METHODS];
    static int[] methodVmid      = new int[MAX_METHODS];
    static int[] methodExcCount  = new int[MAX_METHODS];
    static int[] methodExcIdx    = new int[MAX_METHODS];
    static boolean[] methodIsStatic     = new boolean[MAX_METHODS];
    static boolean[] methodIsConstructor= new boolean[MAX_METHODS];
    static boolean[] methodIsNative     = new boolean[MAX_METHODS];
    static int[] methodBodyStart = new int[MAX_METHODS]; // source pos
    static int[] methodBodyEnd   = new int[MAX_METHODS]; // source pos
    static int[] methodRetType   = new int[MAX_METHODS]; // 0=void,1=int,2=ref
    static int mainMi;

    // --- CP resolution table ---
    static byte[] cpEntries = new byte[MAX_CP];
    static int cpSize;

    // --- Per-method CP (during emit) ---
    static int[] cpMethodVals = new int[1280]; // resolved values
    static int[] cpMethodKeys = new int[1280]; // hash keys for dedup
    static int cpMethodCount;
    static int cpMethodBase; // global offset

    // --- Bytecodes ---
    // Bytecodes stored in raw memory beyond source (not on heap)
    static int codeBase; // set at runtime to SRC_BASE + srcLen
    static int codeLen;
    static byte[] mcode = new byte[2048]; // current method bytecodes
    static int mcodeLen;

    // --- Integer constants ---
    static int[] intConsts = new int[MAX_INT_CONST];
    static int intConstCount;

    // --- String constants ---
    static byte[][] strConsts = new byte[MAX_STR_CONST][];
    static int[] strConstLen = new int[MAX_STR_CONST];
    static int strConstCount;

    // --- Exception table ---
    static int[] excStartPc  = new int[MAX_EXC];
    static int[] excEndPc    = new int[MAX_EXC];
    static int[] excHandlerPc= new int[MAX_EXC];
    static int[] excCatchClass= new int[MAX_EXC];
    static int excCount;

    // --- Locals (per method, during emit) ---
    static int[] localName = new int[MAX_LOCALS]; // name index
    static int[] localSlot = new int[MAX_LOCALS];
    static int[] localType = new int[MAX_LOCALS]; // 0=int,1=ref,3=int[],4=byte[],5=char[]
    static int localCount;
    static int localNextSlot;
    static int maxLocals;
    static int stackDepth;
    static int maxStack;

    // --- Backpatch / Labels ---
    static int[] patchLoc   = new int[320]; // offset in mcode of branch operand
    static int[] patchLabel = new int[320]; // which label
    static int patchCount;
    static int[] labelAddr  = new int[320]; // address for each label
    static int labelCount;

    // --- Loop context stack (break/continue targets) ---
    static int[] loopBreakLabel = new int[32];
    static int[] loopContLabel  = new int[32];
    static int loopDepth;

    // --- Switch case arrays (reused, not nested) ---
    static int[] caseVals   = new int[64];
    static int[] caseLabels = new int[64];

    // --- Current context ---
    static int curClass;
    static int curMethod;
    static boolean curMethodIsStatic;

    // Source address
    static int SRC_BASE = 0xC000;
    static int SRC_LEN_ADDR = 0xBFFC;

    // Output to stdout via putchar
    static int outLen;
    static int userClassStart;
    static int staticFieldCount;

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

        codeBase = SRC_BASE + srcLen; // store bytecodes beyond source

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
        Emit.emit();

        // Pass 4: Link (write .pjvm to stdout)
        Linker.writeOutput();

        Native.halt();
    }

    // ==================== NAME INTERNING ====================

    static void initNames() {
        namePoolLen = 0;
        nameCount = 0;
        N_OBJECT     = internStr("Object");
        N_STRING     = internStr("String");
        N_NATIVE     = internStr("Native");
        N_MAIN       = internStr("main");
        N_INIT       = internStr("<init>");
        N_CLINIT     = internStr("<clinit>");
        N_THROWABLE  = internStr("Throwable");
        N_EXCEPTION  = internStr("Exception");
        N_RUNTIME_EX = internStr("RuntimeException");
        N_PUTCHAR    = internStr("putchar");
        N_IN         = internStr("in");
        N_OUT        = internStr("out");
        N_PEEK       = internStr("peek");
        N_POKE       = internStr("poke");
        N_HALT       = internStr("halt");
        N_PRINT      = internStr("print");
        N_ARRAYCOPY       = internStr("arraycopy");
        N_MEMCMP          = internStr("memcmp");
        N_WRITE_BYTES     = internStr("writeBytes");
        N_STRING_FROM_BYTES = internStr("stringFromBytes");
        N_LENGTH     = internStr("length");
        N_CHARAT     = internStr("charAt");
        N_EQUALS     = internStr("equals");
        N_TOSTRING   = internStr("toString");
        N_HASHCODE   = internStr("hashCode");
        N_ARGS       = internStr("args");
    }

    static byte[] internTmp = new byte[256];

    static int internStr(String s) {
        int len = s.length();
        for (int i = 0; i < len; i++) internTmp[i] = (byte) s.charAt(i);
        return internBuf(internTmp, len);
    }

    static int internBuf(byte[] buf, int len) {
        for (int i = 0; i < nameCount; i++) {
            if (nameLen[i] == len && Native.memcmp(namePool, nameOff[i], buf, 0, len) == 0)
                return i;
        }
        if (nameCount >= MAX_NAMES || namePoolLen + len > 10240) {
            Lexer.error(251);
            return 0;
        }
        int idx = nameCount++;
        nameOff[idx] = namePoolLen;
        nameLen[idx] = len;
        Native.arraycopy(buf, 0, namePool, namePoolLen, len);
        namePoolLen += len;
        return idx;
    }

    static boolean nameEquals(int a, int b) {
        return a == b;
    }

    // ==================== BUILTIN CLASSES/METHODS ====================

    static void initBuiltins() {
        classCount = 0;
        methodCount = 0;
        fieldCount = 0;
        // Note: built-in classes (Object, String, Native) are NOT added to class table
        // They are implicit. Native methods are added to the method table on-demand.
    }

    // Add a native method, returns method index
    static int addNativeMethod(int classNameIdx, int methodNameIdx, int argCount,
                               int nativeId, boolean isStatic, int retType) {
        // Check if already added
        for (int i = 0; i < methodCount; i++) {
            if (methodIsNative[i] && methodName[i] == methodNameIdx &&
                methodClass[i] == classNameIdx) {
                methodFlags[i] = (nativeId << 1) | 1;
                return i;
            }
        }
        int mi = methodCount++;
        methodClass[mi] = classNameIdx;
        methodName[mi] = methodNameIdx;
        methodArgCount[mi] = argCount;
        methodMaxLocals[mi] = argCount;
        methodMaxStack[mi] = argCount > 0 ? argCount : 1;
        methodFlags[mi] = (nativeId << 1) | 1;
        methodCodeOff[mi] = 0;
        methodCpBase[mi] = 0;
        methodVtableSlot[mi] = 0xFF;
        methodVmid[mi] = 0xFF;
        methodExcCount[mi] = 0;
        methodExcIdx[mi] = 0;
        methodIsStatic[mi] = isStatic;
        methodIsConstructor[mi] = false;
        methodIsNative[mi] = true;
        methodBodyStart[mi] = -1;
        methodBodyEnd[mi] = -1;
        methodRetType[mi] = retType;
        return mi;
    }

    // Ensure a native method exists, return its index
    static int ensureNative(int classNm, int methodNm) {
        // Object.<init>
        if (classNm == N_OBJECT && methodNm == N_INIT)
            return addNativeMethod(N_OBJECT, N_INIT, 1, 6, false, 0);
        // Native class methods
        if (classNm == N_NATIVE) {
            if (methodNm == N_PUTCHAR) return addNativeMethod(N_NATIVE, N_PUTCHAR, 1, 0, true, 0);
            if (methodNm == N_IN)      return addNativeMethod(N_NATIVE, N_IN, 1, 1, true, 1);
            if (methodNm == N_OUT)     return addNativeMethod(N_NATIVE, N_OUT, 2, 2, true, 0);
            if (methodNm == N_PEEK)    return addNativeMethod(N_NATIVE, N_PEEK, 1, 3, true, 1);
            if (methodNm == N_POKE)    return addNativeMethod(N_NATIVE, N_POKE, 2, 4, true, 0);
            if (methodNm == N_HALT)    return addNativeMethod(N_NATIVE, N_HALT, 0, 5, true, 0);
            if (methodNm == N_PRINT)   return addNativeMethod(N_NATIVE, N_PRINT, 1, 11, true, 0);
            if (methodNm == N_ARRAYCOPY)       return addNativeMethod(N_NATIVE, N_ARRAYCOPY, 5, 13, true, 0);
            if (methodNm == N_MEMCMP)          return addNativeMethod(N_NATIVE, N_MEMCMP, 5, 14, true, 1);
            if (methodNm == N_WRITE_BYTES)     return addNativeMethod(N_NATIVE, N_WRITE_BYTES, 3, 15, true, 0);
            if (methodNm == N_STRING_FROM_BYTES) return addNativeMethod(N_NATIVE, N_STRING_FROM_BYTES, 3, 16, true, 2);
        }
        // String methods
        if (classNm == N_STRING) {
            if (methodNm == N_LENGTH)   return addNativeMethod(N_STRING, N_LENGTH, 1, 7, false, 1);
            if (methodNm == N_CHARAT)   return addNativeMethod(N_STRING, N_CHARAT, 2, 8, false, 1);
            if (methodNm == N_EQUALS)   return addNativeMethod(N_STRING, N_EQUALS, 2, 9, false, 1);
            if (methodNm == N_TOSTRING) return addNativeMethod(N_STRING, N_TOSTRING, 1, 10, false, 2);
            if (methodNm == N_HASHCODE) return addNativeMethod(N_STRING, N_HASHCODE, 1, 12, false, 1);
        }
        return -1;
    }

}