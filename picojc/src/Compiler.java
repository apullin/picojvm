public class Compiler {
    // Limits — sized to fit in picoJVM 64KB heap
    static int MAX_CLASSES  = 32;
    static int MAX_METHODS  = 128;
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
        catalog();

        // Pass 2: Resolve
        resolve();

        // Pass 3: Emit
        Lexer.init(SRC_BASE, srcLen);
        Lexer.nextToken();
        emit();

        // Pass 4: Link (write .pjvm to stdout)
        writeOutput();

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

    static int internStr(String s) {
        int len = s.length();
        // Check existing
        for (int i = 0; i < nameCount; i++) {
            if (nameLen[i] == len) {
                boolean match = true;
                for (int j = 0; j < len; j++) {
                    if (namePool[nameOff[i] + j] != (byte)s.charAt(j)) {
                        match = false;
                        break;
                    }
                }
                if (match) return i;
            }
        }
        if (nameCount >= MAX_NAMES || namePoolLen + len > 10240) {
            Lexer.error(250); // name pool overflow
            return 0;
        }
        int idx = nameCount++;
        nameOff[idx] = namePoolLen;
        nameLen[idx] = len;
        for (int i = 0; i < len; i++) {
            namePool[namePoolLen++] = (byte)s.charAt(i);
        }
        return idx;
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

    // ==================== PASS 1: CATALOG ====================

    static int userClassStart; // first user class index

    static void catalog() {
        userClassStart = classCount;
        while (Token.type != Token.TOK_EOF) {
            catalogClassOrInterface();
        }
    }

    static void catalogClassOrInterface() {
        // Skip modifiers: public, abstract, final
        while (Token.type == Token.TOK_PUBLIC || Token.type == Token.TOK_ABSTRACT ||
               Token.type == Token.TOK_FINAL) {
            Lexer.nextToken();
        }

        boolean isIface = false;
        if (Token.type == Token.TOK_INTERFACE) {
            isIface = true;
            Lexer.nextToken();
        } else if (Token.type == Token.TOK_CLASS) {
            Lexer.nextToken();
        } else {
            Lexer.error(Token.TOK_CLASS);
            return;
        }

        // Class/interface name
        int nm = internBuf(Token.strBuf, Token.strLen);
        Lexer.nextToken();

        int ci = classCount++;
        className[ci] = nm;
        classParent[ci] = -1; // Object by default
        classIsInterface[ci] = isIface;
        classClinitMi[ci] = 0xFF;
        classIfaceStart[ci] = ifaceListLen;
        classIfaceCount[ci] = 0;
        classOwnFields[ci] = 0;

        // extends?
        if (Token.type == Token.TOK_EXTENDS) {
            Lexer.nextToken();
            int parentNm = internBuf(Token.strBuf, Token.strLen);
            Lexer.nextToken();
            // Resolve parent later; store name for now
            classParent[ci] = parentNm; // store as name index, resolve in Pass 2
        }

        // implements?
        if (Token.type == Token.TOK_IMPLEMENTS) {
            Lexer.nextToken();
            while (Token.type == Token.TOK_IDENT || Token.type == Token.TOK_STRING_KW) {
                int ifNm = internBuf(Token.strBuf, Token.strLen);
                ifaceList[ifaceListLen++] = ifNm; // store as name, resolve later
                classIfaceCount[ci]++;
                Lexer.nextToken();
                if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
                else break;
            }
        }

        Lexer.expect(Token.TOK_LBRACE);
        classBodyStart[ci] = Lexer.pos;

        // Scan class body for fields and methods
        while (Token.type != Token.TOK_RBRACE && Token.type != Token.TOK_EOF) {
            catalogMember(ci);
        }
        classBodyEnd[ci] = Lexer.pos;
        Lexer.expect(Token.TOK_RBRACE);
    }

    static void catalogMember(int ci) {
        // Collect modifiers
        boolean isStat = false;
        boolean isNat = false;
        boolean isAbstract = false;
        while (Token.type == Token.TOK_PUBLIC || Token.type == Token.TOK_PRIVATE ||
               Token.type == Token.TOK_PROTECTED || Token.type == Token.TOK_STATIC ||
               Token.type == Token.TOK_FINAL || Token.type == Token.TOK_NATIVE ||
               Token.type == Token.TOK_ABSTRACT) {
            if (Token.type == Token.TOK_STATIC) isStat = true;
            if (Token.type == Token.TOK_NATIVE) isNat = true;
            if (Token.type == Token.TOK_ABSTRACT) isAbstract = true;
            Lexer.nextToken();
        }

        // Static initializer block: static { ... }
        if (isStat && Token.type == Token.TOK_LBRACE) {
            int mi;
            if (classClinitMi[ci] != 0xFF && methodBodyStart[classClinitMi[ci]] == -2) {
                // Reuse synthetic <clinit> (created for field initializers)
                mi = classClinitMi[ci];
            } else {
                mi = methodCount++;
                methodClass[mi] = ci;
                methodName[mi] = N_CLINIT;
                methodArgCount[mi] = 0;
                methodIsStatic[mi] = true;
                methodIsConstructor[mi] = false;
                methodIsNative[mi] = false;
                methodRetType[mi] = 0; // void
                methodVtableSlot[mi] = 0xFF;
                methodVmid[mi] = 0xFF;
                methodExcCount[mi] = 0;
                classClinitMi[ci] = mi;
            }
            Lexer.nextToken(); // skip {
            methodBodyStart[mi] = Lexer.pos;
            skipBlock();
            methodBodyEnd[mi] = Lexer.pos;
            Lexer.expect(Token.TOK_RBRACE);
            return;
        }

        // Return type or constructor
        int retType = 0; // 0=void, 1=int, 2=ref
        int arrayKind = 0; // 0=non-array, 4=byte[], 5=char[]
        boolean isCtor = false;
        int retTypeToken = Token.type;

        // Check for constructor: ClassName(
        if (Token.type == Token.TOK_IDENT) {
            int nm = internBuf(Token.strBuf, Token.strLen);
            if (nm == className[ci]) {
                // Might be constructor or field/method named same as class
                Lexer.save();
                Lexer.nextToken();
                if (Token.type == Token.TOK_LPAREN) {
                    // It's a constructor
                    isCtor = true;
                    retType = 0; // void
                    catalogMethod(ci, nm, isStat, isCtor, isNat, isAbstract, retType);
                    return;
                }
                // Not a constructor, restore
                Lexer.restore();
                Token.type = Token.TOK_IDENT;
                Token.strLen = nameLen[nm];
                Native.arraycopy(namePool, nameOff[nm], Token.strBuf, 0, Token.strLen);
            }
        }

        // Parse return type
        if (Token.type == Token.TOK_VOID) { retType = 0; Lexer.nextToken(); }
        else if (Token.type == Token.TOK_INT || Token.type == Token.TOK_BYTE ||
                 Token.type == Token.TOK_CHAR || Token.type == Token.TOK_SHORT ||
                 Token.type == Token.TOK_BOOLEAN) {
            retType = 1; // int-like
            Lexer.nextToken();
            // Check for array type (supports multi-dimensional)
            {
                int dimCount = 0;
                while (Token.type == Token.TOK_LBRACKET) {
                    Lexer.nextToken();
                    Lexer.expect(Token.TOK_RBRACKET);
                    dimCount++;
                    if (retType == 1) {
                        if (retTypeToken == Token.TOK_BYTE || retTypeToken == Token.TOK_BOOLEAN) arrayKind = 4;
                        else if (retTypeToken == Token.TOK_CHAR) arrayKind = 5;
                    }
                    retType = 2; // array = ref
                }
                // Multi-dimensional: outer array is reference array, but track inner type
                // 6=byte[][], 7=char[][] — after first [i], type becomes 4 or 5
                if (dimCount > 1) {
                    if (arrayKind == 4) arrayKind = 6;      // byte[][]
                    else if (arrayKind == 5) arrayKind = 7;  // char[][]
                    else arrayKind = 0;
                }
            }
        }
        else if (Token.type == Token.TOK_STRING_KW || Token.type == Token.TOK_IDENT) {
            retType = 2; // reference
            Lexer.nextToken();
            // Check for array type (supports multi-dimensional)
            while (Token.type == Token.TOK_LBRACKET) {
                Lexer.nextToken();
                Lexer.expect(Token.TOK_RBRACKET);
            }
        }
        else {
            Lexer.error(100);
            return;
        }

        // Name
        int nm = internBuf(Token.strBuf, Token.strLen);
        Lexer.nextToken();

        // Method or field?
        if (Token.type == Token.TOK_LPAREN) {
            // Method
            catalogMethod(ci, nm, isStat, false, isNat, isAbstract, retType);
        } else {
            // Field
            catalogField(ci, nm, isStat, retType, arrayKind);
        }
    }

    static void catalogField(int ci, int nm, boolean isStat, int retType, int arrKind) {
        int fi = fieldCount++;
        fieldClass[fi] = ci;
        fieldName[fi] = nm;
        fieldIsStatic[fi] = isStat;
        fieldArrayKind[fi] = arrKind;
        fieldSlot[fi] = -1;
        fieldInitPos[fi] = -1;
        fieldInitLine[fi] = 0;
        if (!isStat) classOwnFields[ci]++;

        // Check for initializer
        if (Token.type == Token.TOK_ASSIGN && isStat) {
            // Save position right after '=' (before the value token)
            // Lexer.pos is already past '=' since TOK_ASSIGN was scanned
            fieldInitPos[fi] = Lexer.pos;
            fieldInitLine[fi] = Lexer.line;
            // Ensure class has a <clinit> for field initializer emission
            if (classClinitMi[ci] == 0xFF) {
                // Create synthetic <clinit> (bodyStart=-2 = synthetic marker)
                int smi = methodCount++;
                methodClass[smi] = ci;
                methodName[smi] = N_CLINIT;
                methodArgCount[smi] = 0;
                methodIsStatic[smi] = true;
                methodIsConstructor[smi] = false;
                methodIsNative[smi] = false;
                methodRetType[smi] = 0;
                methodVtableSlot[smi] = 0xFF;
                methodVmid[smi] = 0xFF;
                methodExcCount[smi] = 0;
                methodBodyStart[smi] = -2; // synthetic: no explicit body
                methodBodyEnd[smi] = -2;
                classClinitMi[ci] = smi;
            }
        }

        // Skip initializer and comma-separated declarations
        while (Token.type != Token.TOK_SEMI && Token.type != Token.TOK_EOF) {
            if (Token.type == Token.TOK_COMMA) {
                Lexer.nextToken();
                // Another field with same type
                int nm2 = internBuf(Token.strBuf, Token.strLen);
                Lexer.nextToken();
                int fi2 = fieldCount++;
                fieldClass[fi2] = ci;
                fieldName[fi2] = nm2;
                fieldIsStatic[fi2] = isStat;
                fieldArrayKind[fi2] = arrKind;
                fieldSlot[fi2] = -1;
                fieldInitPos[fi2] = -1;
                fieldInitLine[fi2] = 0;
                if (!isStat) classOwnFields[ci]++;
            } else {
                Lexer.nextToken();
            }
        }
        Lexer.expect(Token.TOK_SEMI);
    }

    static void catalogMethod(int ci, int nm, boolean isStat, boolean isCtor,
                               boolean isNat, boolean isAbstract, int retType) {
        int mi = methodCount++;
        methodClass[mi] = ci;
        methodName[mi] = isCtor ? N_INIT : nm;
        methodIsStatic[mi] = isStat;
        methodIsConstructor[mi] = isCtor;
        methodIsNative[mi] = isNat;
        methodRetType[mi] = retType;
        methodVtableSlot[mi] = 0xFF;
        methodVmid[mi] = 0xFF;
        methodExcCount[mi] = 0;

        // Parse parameters
        Lexer.expect(Token.TOK_LPAREN);
        int argc = isStat ? 0 : 1; // instance methods have 'this' as arg 0
        while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
            // Skip type
            skipType();
            // Skip name
            Lexer.nextToken();
            argc++;
            if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
        }
        Lexer.expect(Token.TOK_RPAREN);
        methodArgCount[mi] = argc;

        if (isNat || isAbstract || classIsInterface[ci]) {
            // Native/abstract/interface method: no body
            Lexer.expect(Token.TOK_SEMI);
            methodBodyStart[mi] = -1;
            methodBodyEnd[mi] = -1;
        } else {
            // Parse body
            Lexer.expect(Token.TOK_LBRACE);
            methodBodyStart[mi] = Lexer.pos;
            skipBlock();
            methodBodyEnd[mi] = Lexer.pos;
            Lexer.expect(Token.TOK_RBRACE);
        }
    }

    static void skipType() {
        // Skip a type declaration (int, String, ClassName, arrays)
        Lexer.nextToken(); // consume type keyword/name
        while (Token.type == Token.TOK_LBRACKET) {
            Lexer.nextToken(); // [
            if (Token.type == Token.TOK_RBRACKET) Lexer.nextToken(); // ]
        }
    }

    static void skipBlock() {
        // Skip brace-balanced block content (we're just past the opening {)
        int depth = 1;
        while (depth > 0 && Token.type != Token.TOK_EOF) {
            if (Token.type == Token.TOK_LBRACE) depth++;
            else if (Token.type == Token.TOK_RBRACE) {
                depth--;
                if (depth == 0) return; // don't consume the closing }
            }
            Lexer.nextToken();
        }
    }

    // ==================== PASS 2: RESOLVE ====================

    static int staticFieldCount;

    static void resolve() {
        // Resolve parent class references (name → class index)
        // Save classCount: synthesizeExceptionClass (called via findClassByName)
        // may add new classes whose classParent is ALREADY a class index.
        int origClassCount = classCount;
        for (int ci = 0; ci < origClassCount; ci++) {
            if (classParent[ci] != -1) {
                int parentNm = classParent[ci]; // currently a name index
                int pid = findClassByName(parentNm);
                classParent[ci] = pid; // now a class index, -1 if not found (Object)
            }
        }

        // Resolve interface references
        for (int ci = 0; ci < classCount; ci++) {
            int start = classIfaceStart[ci];
            for (int j = 0; j < classIfaceCount[ci]; j++) {
                int ifNm = ifaceList[start + j]; // name index
                int ifId = findClassByName(ifNm);
                ifaceList[start + j] = ifId; // now class index
            }
        }

        // Compute instance field counts (including inherited)
        for (int ci = 0; ci < classCount; ci++) {
            int inherited = 0;
            if (classParent[ci] >= 0) {
                inherited = classFieldCount[classParent[ci]];
            }
            classFieldCount[ci] = inherited + classOwnFields[ci];
        }

        // Assign field slots
        staticFieldCount = 0;
        for (int fi = 0; fi < fieldCount; fi++) {
            if (fieldIsStatic[fi]) {
                fieldSlot[fi] = staticFieldCount++;
            } else {
                // Instance field: slot = parent field count + own offset
                int ci = fieldClass[fi];
                int inherited = 0;
                if (classParent[ci] >= 0) {
                    inherited = classFieldCount[classParent[ci]];
                }
                int ownIdx = 0;
                for (int fj = 0; fj < fi; fj++) {
                    if (!fieldIsStatic[fj] && fieldClass[fj] == ci) {
                        ownIdx++;
                    }
                }
                fieldSlot[fi] = inherited + ownIdx;
            }
        }

        // Ensure all user classes have a default constructor if none declared
        for (int ci = userClassStart; ci < classCount; ci++) {
            if (classIsInterface[ci]) continue;
            boolean hasCtor = false;
            for (int mi = 0; mi < methodCount; mi++) {
                if (methodClass[mi] == ci && methodIsConstructor[mi]) {
                    hasCtor = true;
                    break;
                }
            }
            if (!hasCtor) {
                // Add default constructor
                int mi = methodCount++;
                methodClass[mi] = ci;
                methodName[mi] = N_INIT;
                methodArgCount[mi] = 1; // 'this'
                methodIsStatic[mi] = false;
                methodIsConstructor[mi] = true;
                methodIsNative[mi] = false;
                methodRetType[mi] = 0;
                methodVtableSlot[mi] = 0xFF;
                methodVmid[mi] = 0xFF;
                methodExcCount[mi] = 0;
                methodBodyStart[mi] = -2; // marker for auto-generated
                methodBodyEnd[mi] = -2;
            }
        }

        // Ensure Object.<init> exists
        ensureNative(N_OBJECT, N_INIT);

        // Build vtables
        for (int ci = 0; ci < classCount; ci++) {
            if (classIsInterface[ci]) continue;
            vtableBase[ci] = vtableLen();
            int parentVtSize = 0;
            if (classParent[ci] >= 0) {
                // Copy parent vtable
                int pid = classParent[ci];
                parentVtSize = classVtableSize[pid];
                int pBase = vtableBase[pid];
                for (int j = 0; j < parentVtSize; j++) {
                    vtable[vtableBase[ci] + j] = vtable[pBase + j];
                }
            }
            classVtableSize[ci] = parentVtSize;

            // Add/override methods
            for (int mi = 0; mi < methodCount; mi++) {
                if (methodClass[mi] != ci) continue;
                if (methodIsStatic[mi] || methodIsNative[mi]) continue;
                if (methodIsConstructor[mi]) continue;

                // Check if this overrides a parent method
                int slot = -1;
                for (int j = 0; j < classVtableSize[ci]; j++) {
                    int existingMi = vtable[vtableBase[ci] + j];
                    if (methodName[existingMi] == methodName[mi]) {
                        slot = j;
                        break;
                    }
                }
                if (slot >= 0) {
                    vtable[vtableBase[ci] + slot] = mi;
                    methodVtableSlot[mi] = slot;
                } else {
                    slot = classVtableSize[ci]++;
                    vtable[vtableBase[ci] + slot] = mi;
                    methodVtableSlot[mi] = slot;
                }
            }
        }

        // Assign vmids for interface methods
        int nextVmid = 0;
        for (int ci = 0; ci < classCount; ci++) {
            if (!classIsInterface[ci]) continue;
            for (int mi = 0; mi < methodCount; mi++) {
                if (methodClass[mi] != ci) continue;
                methodVmid[mi] = nextVmid++;
            }
        }
        // Copy vmids to implementing class methods
        for (int ci = 0; ci < classCount; ci++) {
            if (classIsInterface[ci]) continue;
            int start = classIfaceStart[ci];
            for (int j = 0; j < classIfaceCount[ci]; j++) {
                int ifId = ifaceList[start + j];
                if (ifId < 0) continue;
                for (int imi = 0; imi < methodCount; imi++) {
                    if (methodClass[imi] != ifId) continue;
                    // Find matching method in implementing class
                    for (int cmi = 0; cmi < methodCount; cmi++) {
                        if (methodClass[cmi] != ci) continue;
                        if (methodName[cmi] == methodName[imi]) {
                            methodVmid[cmi] = methodVmid[imi];
                        }
                    }
                }
            }
        }

        // Update clinit method indices
        for (int ci = 0; ci < classCount; ci++) {
            int clinitMi = classClinitMi[ci];
            if (clinitMi != 0xFF && clinitMi < methodCount) {
                // clinitMi is already the method index
            }
        }

        // Ensure all cataloged native methods have their flags set
        for (int mi = 0; mi < methodCount; mi++) {
            if (methodIsNative[mi] && methodFlags[mi] == 0) {
                int nm = methodName[mi];
                int nid = -1;
                if (nm == N_PUTCHAR)      nid = 0;
                else if (nm == N_IN)      nid = 1;
                else if (nm == N_OUT)     nid = 2;
                else if (nm == N_PEEK)    nid = 3;
                else if (nm == N_POKE)    nid = 4;
                else if (nm == N_HALT)    nid = 5;
                else if (nm == N_INIT)    nid = 6;
                else if (nm == N_LENGTH)  nid = 7;
                else if (nm == N_CHARAT)  nid = 8;
                else if (nm == N_EQUALS)  nid = 9;
                else if (nm == N_TOSTRING) nid = 10;
                else if (nm == N_PRINT)   nid = 11;
                else if (nm == N_HASHCODE) nid = 12;
                else if (nm == N_ARRAYCOPY)       nid = 13;
                else if (nm == N_MEMCMP)          nid = 14;
                else if (nm == N_WRITE_BYTES)     nid = 15;
                else if (nm == N_STRING_FROM_BYTES) nid = 16;
                if (nid >= 0) methodFlags[mi] = (nid << 1) | 1;
            }
        }

        // Find main method
        mainMi = -1;
        for (int mi = 0; mi < methodCount; mi++) {
            if (methodName[mi] == N_MAIN && methodIsStatic[mi] && !methodIsNative[mi]) {
                mainMi = mi;
                break;
            }
        }
        if (mainMi < 0) {
            Lexer.error(200); // No main method found
        }
        // picoJVM calls main without pushing arguments, so arg_count must be 0
        if (mainMi >= 0) {
            methodArgCount[mainMi] = 0;
        }
    }

    static int vtableLen() {
        // Sum of all vtable sizes so far
        int total = 0;
        for (int ci = 0; ci < classCount; ci++) {
            total += classVtableSize[ci];
        }
        return total;
    }

    static int findClassByName(int nm) {
        for (int ci = 0; ci < classCount; ci++) {
            if (className[ci] == nm) return ci;
        }
        // Check well-known names
        if (nm == N_THROWABLE || nm == N_EXCEPTION || nm == N_RUNTIME_EX) {
            // Synthesize exception class
            return synthesizeExceptionClass(nm);
        }
        return -1;
    }

    static int synthesizeExceptionClass(int nm) {
        // Ensure parent hierarchy exists
        int parentNm;
        if (nm == N_THROWABLE) parentNm = -1; // Object
        else if (nm == N_EXCEPTION) {
            parentNm = N_THROWABLE;
            synthesizeExceptionClass(N_THROWABLE); // ensure parent exists
        }
        else { // RuntimeException
            parentNm = N_EXCEPTION;
            synthesizeExceptionClass(N_EXCEPTION); // ensure parent exists
        }

        // Check if already exists
        for (int ci = 0; ci < classCount; ci++) {
            if (className[ci] == nm) return ci;
        }

        int ci = classCount++;
        className[ci] = nm;
        classParent[ci] = parentNm == -1 ? -1 : findClassByName(parentNm);
        classIsInterface[ci] = false;
        classClinitMi[ci] = 0xFF;
        classFieldCount[ci] = 0;
        classOwnFields[ci] = 0;
        classIfaceStart[ci] = ifaceListLen;
        classIfaceCount[ci] = 0;
        vtableBase[ci] = vtableLen();
        classVtableSize[ci] = 0;
        classBodyStart[ci] = -1;
        classBodyEnd[ci] = -1;
        return ci;
    }

    // ==================== PASS 3: EMIT ====================

    static void emit() {
        codeLen = 0;
        cpSize = 0;
        excCount = 0;
        autoCtorsEmitted = false;

        // Skip to class bodies and emit methods
        while (Token.type != Token.TOK_EOF) {
            emitClassMethods();
        }

        // Emit auto-generated default constructors after all user methods
        emitAutoConstructors();
    }

    static void emitClassMethods() {
        // Skip to class body
        while (Token.type != Token.TOK_LBRACE && Token.type != Token.TOK_EOF) {
            Lexer.nextToken();
        }
        if (Token.type == Token.TOK_EOF) return;
        Lexer.nextToken(); // skip {

        // We're inside a class body
        int ci = findCurrentClass();
        curClass = ci;

        while (Token.type != Token.TOK_RBRACE && Token.type != Token.TOK_EOF) {
            emitMember(ci);
        }
        if (Token.type == Token.TOK_RBRACE) Lexer.nextToken();
    }

    static int emitClassIdx; // tracking which class we're emitting
    static int findCurrentClass() {
        // Match by position - classes appear in source order
        for (int ci = userClassStart; ci < classCount; ci++) {
            if (classBodyStart[ci] >= 0 && Lexer.pos >= classBodyStart[ci] &&
                (classBodyEnd[ci] < 0 || Lexer.pos <= classBodyEnd[ci])) {
                return ci;
            }
        }
        return userClassStart;
    }

    static void emitMember(int ci) {
        // Skip modifiers
        boolean isStat = false;
        boolean isNat = false;
        boolean isAbstract = false;
        while (Token.type == Token.TOK_PUBLIC || Token.type == Token.TOK_PRIVATE ||
               Token.type == Token.TOK_PROTECTED || Token.type == Token.TOK_STATIC ||
               Token.type == Token.TOK_FINAL || Token.type == Token.TOK_NATIVE ||
               Token.type == Token.TOK_ABSTRACT) {
            if (Token.type == Token.TOK_STATIC) isStat = true;
            if (Token.type == Token.TOK_NATIVE) isNat = true;
            if (Token.type == Token.TOK_ABSTRACT) isAbstract = true;
            Lexer.nextToken();
        }

        // Static init block
        if (isStat && Token.type == Token.TOK_LBRACE) {
            int mi = findMethod(ci, N_CLINIT);
            if (mi >= 0) emitMethodBody(mi);
            else { Lexer.nextToken(); skipBlock(); Lexer.expect(Token.TOK_RBRACE); }
            return;
        }

        // Constructor check
        if (Token.type == Token.TOK_IDENT) {
            int nm = internBuf(Token.strBuf, Token.strLen);
            if (nm == className[ci]) {
                Lexer.save();
                Lexer.nextToken();
                if (Token.type == Token.TOK_LPAREN) {
                    int mi = findConstructor(ci);
                    if (mi >= 0) emitMethodBody(mi);
                    else skipMethodDecl();
                    return;
                }
                Lexer.restore();
                Token.strLen = nameLen[nm];
                Native.arraycopy(namePool, nameOff[nm], Token.strBuf, 0, Token.strLen);
                Token.type = Token.TOK_IDENT;
            }
        }

        // Return type
        skipType();

        // Name
        int nm = internBuf(Token.strBuf, Token.strLen);
        Lexer.nextToken();

        if (Token.type == Token.TOK_LPAREN) {
            // Method
            int mi = findMethod(ci, nm);
            if (mi >= 0 && !methodIsNative[mi] && methodBodyStart[mi] >= 0) {
                emitMethodBody(mi);
            } else {
                skipMethodDecl();
            }
        } else {
            // Field - skip to semicolon
            while (Token.type != Token.TOK_SEMI && Token.type != Token.TOK_EOF) {
                Lexer.nextToken();
            }
            Lexer.expect(Token.TOK_SEMI);
        }
    }

    static void skipMethodDecl() {
        // Skip parameter list
        Lexer.expect(Token.TOK_LPAREN);
        while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
            Lexer.nextToken();
        }
        Lexer.expect(Token.TOK_RPAREN);
        if (Token.type == Token.TOK_SEMI) {
            Lexer.nextToken(); // native/abstract method
        } else {
            Lexer.expect(Token.TOK_LBRACE);
            skipBlock();
            Lexer.expect(Token.TOK_RBRACE);
        }
    }

    static int findMethod(int ci, int nm) {
        for (int mi = 0; mi < methodCount; mi++) {
            if (methodClass[mi] == ci && methodName[mi] == nm && !methodIsNative[mi]) {
                return mi;
            }
        }
        return -1;
    }

    static int findConstructor(int ci) {
        for (int mi = 0; mi < methodCount; mi++) {
            if (methodClass[mi] == ci && methodIsConstructor[mi] && !methodIsNative[mi]) {
                return mi;
            }
        }
        return -1;
    }

    // ==================== METHOD EMISSION ====================

    static void emitMethodBody(int mi) {
        curMethod = mi;
        curMethodIsStatic = methodIsStatic[mi];
        mcodeLen = 0;
        patchCount = 0;
        labelCount = 0;
        localCount = 0;
        localNextSlot = 0;
        loopDepth = 0;
        stackDepth = 0;
        maxStack = 0;

        // Init per-method CP
        cpMethodCount = 0;
        cpMethodBase = cpSize;

        // Set up parameters as locals
        if (methodIsConstructor[mi]) {
            // Constructor: skip params in source, set up 'this'
            addLocal(N_INIT, 0); // 'this'
            // Skip to body
            Lexer.expect(Token.TOK_LPAREN);
            while (Token.type != Token.TOK_RPAREN) {
                // Type
                int pType = parseTypeForLocal();
                // Name
                int pNm = internBuf(Token.strBuf, Token.strLen);
                Lexer.nextToken();
                addLocal(pNm, pType);
                if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
            }
            Lexer.expect(Token.TOK_RPAREN);

            // Emit super() call
            emitByte(0x2A); // ALOAD_0 (this)
            pushStack();
            int objInitMi = ensureNative(N_OBJECT, N_INIT);
            int cpIdx = allocCP(objInitMi);
            emitByte(0xB7); // INVOKESPECIAL
            emitShortBE(cpIdx);
            popStack(); // 'this' consumed

            // Parse body
            Lexer.expect(Token.TOK_LBRACE);
            parseBlock();
            Lexer.expect(Token.TOK_RBRACE);

            // Emit RETURN
            emitByte(0xB1);
        } else if (methodName[mi] == N_CLINIT) {
            // Save current lexer position (at '{' of static block)
            int clinitPos = Lexer.pos;
            int clinitLine = Lexer.line;
            int clinitTok = Token.type;
            // Emit inline static field initializers first
            for (int fi = 0; fi < fieldCount; fi++) {
                if (fieldClass[fi] == curClass && fieldIsStatic[fi] && fieldInitPos[fi] >= 0) {
                    Lexer.pos = fieldInitPos[fi];
                    Lexer.line = fieldInitLine[fi];
                    Lexer.nextToken();
                    parseExpression();
                    int sfSlot = fieldSlot[fi];
                    int cpIdx2 = allocCP(sfSlot);
                    emitByte(0xB3); // PUTSTATIC
                    emitShortBE(cpIdx2);
                    popStack();
                }
            }
            // Restore lexer to static block body
            Lexer.pos = clinitPos;
            Lexer.line = clinitLine;
            Token.type = clinitTok;
            // Static initializer body: { ... }
            Lexer.nextToken(); // skip {
            parseBlock();
            Lexer.expect(Token.TOK_RBRACE);
            emitByte(0xB1); // RETURN
        } else {
            // Regular method
            if (!curMethodIsStatic) {
                addLocal(internStr("this"), 1); // 'this' is slot 0
            }

            // Parse parameters
            Lexer.expect(Token.TOK_LPAREN);
            while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
                int pType = parseTypeForLocal();
                int pNm = internBuf(Token.strBuf, Token.strLen);
                Lexer.nextToken();
                addLocal(pNm, pType);
                if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
            }
            Lexer.expect(Token.TOK_RPAREN);

            // Parse body
            Lexer.expect(Token.TOK_LBRACE);
            parseBlock();
            Lexer.expect(Token.TOK_RBRACE);

            // If method doesn't end with return, add implicit return
            if (mcodeLen == 0 || (mcode[mcodeLen - 1] & 0xFF) != 0xB1 &&
                (mcode[mcodeLen - 1] & 0xFF) != 0xAC && (mcode[mcodeLen - 1] & 0xFF) != 0xB0) {
                emitByte(0xB1); // RETURN
            }
        }

        // Resolve backpatches
        for (int i = 0; i < patchCount; i++) {
            int loc = patchLoc[i];
            int lbl = patchLabel[i];
            int target = labelAddr[lbl];
            int offset = target - (loc - 1); // relative to branch opcode
            mcode[loc] = (byte) ((offset >> 8) & 0xFF);
            mcode[loc + 1] = (byte) (offset & 0xFF);
        }

        // Copy method bytecodes to global
        methodCodeOff[mi] = codeLen;
        for (int i = 0; i < mcodeLen; i++) {
            Native.poke(codeBase + codeLen, mcode[i] & 0xFF); codeLen++;
        }

        // Store CP
        methodCpBase[mi] = cpMethodBase;
        for (int i = 0; i < cpMethodCount; i++) {
            cpEntries[cpMethodBase + i] = (byte) cpMethodVals[i];
        }
        cpSize = cpMethodBase + cpMethodCount;

        // Store metrics
        methodMaxLocals[mi] = localNextSlot > 0 ? localNextSlot : 1;
        methodMaxStack[mi] = maxStack > 0 ? maxStack : 1;
    }

    static boolean autoCtorsEmitted;

    static void emitAutoConstructors() {
        if (autoCtorsEmitted) return;
        autoCtorsEmitted = true;

        for (int mi = 0; mi < methodCount; mi++) {
            if (methodIsConstructor[mi] && !methodIsNative[mi] && methodBodyStart[mi] == -2) {
                // Auto-generated default constructor
                curMethod = mi;
                mcodeLen = 0;
                cpMethodCount = 0;
                cpMethodBase = cpSize;
                patchCount = 0;
                labelCount = 0;

                // ALOAD_0, INVOKESPECIAL Object.<init>, RETURN
                emitByte(0x2A); // ALOAD_0
                int objInitMi = ensureNative(N_OBJECT, N_INIT);
                int cpIdx = allocCP(objInitMi);
                emitByte(0xB7); // INVOKESPECIAL
                emitShortBE(cpIdx);
                emitByte(0xB1); // RETURN

                methodCodeOff[mi] = codeLen;
                for (int i = 0; i < mcodeLen; i++) {
                    Native.poke(codeBase + codeLen, mcode[i] & 0xFF); codeLen++;
                }
                methodCpBase[mi] = cpMethodBase;
                for (int i = 0; i < cpMethodCount; i++) {
                    cpEntries[cpMethodBase + i] = (byte) cpMethodVals[i];
                }
                cpSize = cpMethodBase + cpMethodCount;
                methodMaxLocals[mi] = 1;
                methodMaxStack[mi] = 1;
            }
            // Synthetic <clinit>: only field initializers, no explicit body
            if (methodName[mi] == N_CLINIT && !methodIsNative[mi] && methodBodyStart[mi] == -2) {
                curMethod = mi;
                curClass = methodClass[mi];
                curMethodIsStatic = true;
                mcodeLen = 0;
                cpMethodCount = 0;
                cpMethodBase = cpSize;
                patchCount = 0;
                labelCount = 0;
                localCount = 0;
                stackDepth = 0;
                maxStack = 0;

                // Emit field initializers
                for (int fi = 0; fi < fieldCount; fi++) {
                    if (fieldClass[fi] == curClass && fieldIsStatic[fi] && fieldInitPos[fi] >= 0) {
                        Lexer.pos = fieldInitPos[fi];
                        Lexer.line = fieldInitLine[fi];
                        Lexer.nextToken();
                        parseExpression();
                        int sfSlot = fieldSlot[fi];
                        int cpIdx2 = allocCP(sfSlot);
                        emitByte(0xB3); // PUTSTATIC
                        emitShortBE(cpIdx2);
                        popStack();
                    }
                }
                emitByte(0xB1); // RETURN

                methodCodeOff[mi] = codeLen;
                for (int i = 0; i < mcodeLen; i++) {
                    Native.poke(codeBase + codeLen, mcode[i] & 0xFF); codeLen++;
                }
                methodCpBase[mi] = cpMethodBase;
                for (int i = 0; i < cpMethodCount; i++) {
                    cpEntries[cpMethodBase + i] = (byte) cpMethodVals[i];
                }
                cpSize = cpMethodBase + cpMethodCount;
                methodMaxLocals[mi] = 1;
                methodMaxStack[mi] = maxStack > 0 ? maxStack : 1;
            }
        }
    }

    static int parseTypeForLocal() {
        // Returns: 0=int, 1=ref, 3=int[], 4=byte[], 5=char[]
        int baseType = 0;
        int elemKind = 0; // 0=int-like, 1=byte, 2=char
        if (Token.type == Token.TOK_BYTE || Token.type == Token.TOK_BOOLEAN) {
            elemKind = 1; Lexer.nextToken();
        } else if (Token.type == Token.TOK_CHAR) {
            elemKind = 2; Lexer.nextToken();
        } else if (Token.type == Token.TOK_INT || Token.type == Token.TOK_SHORT) {
            elemKind = 0; Lexer.nextToken();
        } else {
            baseType = 1; // reference
            Lexer.nextToken();
        }
        // Array dimensions
        int dimCount = 0;
        while (Token.type == Token.TOK_LBRACKET) {
            Lexer.nextToken();
            if (Token.type == Token.TOK_RBRACKET) Lexer.nextToken();
            dimCount++;
        }
        if (dimCount > 0) {
            if (baseType == 1 || dimCount > 1) return 1; // Object[]/multi-dim = reference
            if (elemKind == 1) return 4; // byte[]
            if (elemKind == 2) return 5; // char[]
            return 3; // int[]
        }
        return baseType; // 0=int, 1=ref
    }

    // ==================== BYTECODE EMISSION HELPERS ====================

    static void emitByte(int b) {
        mcode[mcodeLen++] = (byte)(b & 0xFF);
    }

    static void emitShortBE(int s) {
        mcode[mcodeLen++] = (byte)((s >> 8) & 0xFF);
        mcode[mcodeLen++] = (byte)(s & 0xFF);
    }

    static void emitIntBE(int v) {
        mcode[mcodeLen++] = (byte)((v >> 24) & 0xFF);
        mcode[mcodeLen++] = (byte)((v >> 16) & 0xFF);
        mcode[mcodeLen++] = (byte)((v >> 8) & 0xFF);
        mcode[mcodeLen++] = (byte)(v & 0xFF);
    }

    static int emitBranchPlaceholder() {
        int loc = mcodeLen;
        emitByte(0);
        emitByte(0);
        return loc;
    }

    static int newLabel() {
        return labelCount++;
    }

    static void setLabel(int lbl) {
        labelAddr[lbl] = mcodeLen;
    }

    static void emitBranch(int opcode, int label) {
        int branchPC = mcodeLen;
        emitByte(opcode);
        int loc = emitBranchPlaceholder();
        patchLoc[patchCount] = loc;
        patchLabel[patchCount] = label;
        patchCount++;
    }

    static void pushStack() {
        stackDepth++;
        if (stackDepth > maxStack) maxStack = stackDepth;
    }

    static void popStack() {
        if (stackDepth > 0) stackDepth--;
    }

    static void addLocal(int nm, int type) {
        localName[localCount] = nm;
        localSlot[localCount] = localCount;
        localType[localCount] = type;
        localCount++;
        if (localCount > localNextSlot) localNextSlot = localCount;
    }

    static int findLocal(int nm) {
        for (int i = localCount - 1; i >= 0; i--) {
            if (localName[i] == nm) return i;
        }
        return -1;
    }

    static int allocCP(int resolvedVal) {
        // Check for existing entry with same value
        for (int i = 0; i < cpMethodCount; i++) {
            if (cpMethodVals[i] == resolvedVal && cpMethodKeys[i] == resolvedVal) {
                return i;
            }
        }
        int idx = cpMethodCount++;
        cpMethodVals[idx] = resolvedVal;
        cpMethodKeys[idx] = resolvedVal;
        return idx;
    }

    static int allocFieldCP(int fieldSlotVal) {
        return allocCP(fieldSlotVal);
    }

    static int allocClassCP(int classId) {
        return allocCP(classId);
    }

    static int allocStringCP(byte[] buf, int len) {
        int strIdx = -1;
        for (int i = 0; i < strConstCount; i++) {
            if (strConstLen[i] == len && Native.memcmp(strConsts[i], 0, buf, 0, len) == 0) {
                strIdx = i; break;
            }
        }
        if (strIdx < 0) {
            strIdx = strConstCount++;
            strConsts[strIdx] = new byte[len];
            Native.arraycopy(buf, 0, strConsts[strIdx], 0, len);
            strConstLen[strIdx] = len;
        }
        return allocCP(0x80 | strIdx);
    }

    static int allocIntConstCP(int val) {
        // Find or add integer constant
        int idx = -1;
        for (int i = 0; i < intConstCount; i++) {
            if (intConsts[i] == val) { idx = i; break; }
        }
        if (idx < 0) {
            idx = intConstCount++;
            intConsts[idx] = val;
        }
        return allocCP(idx);
    }

    // ==================== STATEMENT PARSING ====================

    static void parseBlock() {
        while (Token.type != Token.TOK_RBRACE && Token.type != Token.TOK_EOF) {
            parseStatement();
        }
    }

    static void parseStatement() {
        if (Token.type == Token.TOK_LBRACE) {
            Lexer.nextToken();
            int savedLocalCount = localCount;
            parseBlock();
            localCount = savedLocalCount; // restore scope
            Lexer.expect(Token.TOK_RBRACE);
        }
        else if (Token.type == Token.TOK_IF) {
            parseIf();
        }
        else if (Token.type == Token.TOK_WHILE) {
            parseWhile();
        }
        else if (Token.type == Token.TOK_DO) {
            parseDoWhile();
        }
        else if (Token.type == Token.TOK_FOR) {
            parseFor();
        }
        else if (Token.type == Token.TOK_RETURN) {
            parseReturn();
        }
        else if (Token.type == Token.TOK_BREAK) {
            Lexer.nextToken();
            Lexer.expect(Token.TOK_SEMI);
            if (loopDepth > 0) {
                emitBranch(0xA7, loopBreakLabel[loopDepth - 1]); // GOTO break
            }
        }
        else if (Token.type == Token.TOK_CONTINUE) {
            Lexer.nextToken();
            Lexer.expect(Token.TOK_SEMI);
            if (loopDepth > 0) {
                emitBranch(0xA7, loopContLabel[loopDepth - 1]); // GOTO continue
            }
        }
        else if (Token.type == Token.TOK_SWITCH) {
            parseSwitch();
        }
        else if (Token.type == Token.TOK_THROW) {
            parseThrow();
        }
        else if (Token.type == Token.TOK_TRY) {
            parseTryCatch();
        }
        else if (isTypeToken(Token.type)) {
            parseLocalDecl();
        }
        else if (Token.type == Token.TOK_IDENT) {
            // Could be local declaration (ClassName var) or expression statement
            // Peek ahead: if next is ident (or [ ]), it's a declaration
            Lexer.save();
            int nm = internBuf(Token.strBuf, Token.strLen);
            int savedType = Token.type;
            Lexer.nextToken();
            if (Token.type == Token.TOK_IDENT ||
                (Token.type == Token.TOK_LBRACKET &&
                 findClassByName(nm) >= 0)) {
                // It's a type name followed by a variable name — declaration
                Lexer.restore();
                Token.type = savedType;
                Token.strLen = nameLen[nm];
                Native.arraycopy(namePool, nameOff[nm], Token.strBuf, 0, Token.strLen);
                parseLocalDecl();
            } else {
                // Expression statement
                Lexer.restore();
                Token.type = savedType;
                Token.strLen = nameLen[nm];
                Native.arraycopy(namePool, nameOff[nm], Token.strBuf, 0, Token.strLen);
                parseExpressionStatement();
            }
        }
        else {
            parseExpressionStatement();
        }
    }

    static boolean isTypeToken(int t) {
        return t == Token.TOK_INT || t == Token.TOK_BYTE || t == Token.TOK_CHAR ||
               t == Token.TOK_SHORT || t == Token.TOK_BOOLEAN || t == Token.TOK_VOID ||
               t == Token.TOK_STRING_KW;
    }

    static void parseExpressionStatement() {
        int type = parseExpression();
        if (type != 0) { // non-void expression, pop result
            emitByte(0x57); // POP
            popStack();
        }
        Lexer.expect(Token.TOK_SEMI);
    }

    // ==================== LOCAL VARIABLE DECLARATION ====================

    static void parseLocalDecl() {
        int varType = parseTypeForLocal(); // 0=int, 1=ref
        do {
            int nm = internBuf(Token.strBuf, Token.strLen);
            int slot = localCount;
            addLocal(nm, varType);
            Lexer.nextToken();

            if (Token.type == Token.TOK_ASSIGN) {
                Lexer.nextToken();
                parseExpression();
                emitStore(slot, varType);
                popStack();
            }

            if (Token.type == Token.TOK_COMMA) {
                Lexer.nextToken();
            } else {
                break;
            }
        } while (Token.type == Token.TOK_IDENT);
        Lexer.expect(Token.TOK_SEMI);
    }

    // ==================== CONTROL FLOW ====================

    static void parseIf() {
        Lexer.nextToken(); // skip 'if'
        Lexer.expect(Token.TOK_LPAREN);
        parseExpression();
        Lexer.expect(Token.TOK_RPAREN);
        popStack();

        int lblElse = newLabel();
        emitBranch(0x99, lblElse); // IFEQ → else

        parseStatement();

        if (Token.type == Token.TOK_ELSE) {
            int lblEnd = newLabel();
            emitBranch(0xA7, lblEnd); // GOTO end
            setLabel(lblElse);
            Lexer.nextToken(); // skip 'else'
            parseStatement();
            setLabel(lblEnd);
        } else {
            setLabel(lblElse);
        }
    }

    static void parseWhile() {
        Lexer.nextToken(); // skip 'while'
        int lblTop = newLabel();
        int lblEnd = newLabel();
        int lblCont = lblTop;
        setLabel(lblTop);

        Lexer.expect(Token.TOK_LPAREN);
        parseExpression();
        Lexer.expect(Token.TOK_RPAREN);
        popStack();
        emitBranch(0x99, lblEnd); // IFEQ → end

        loopBreakLabel[loopDepth] = lblEnd;
        loopContLabel[loopDepth] = lblCont;
        loopDepth++;
        parseStatement();
        loopDepth--;

        emitBranch(0xA7, lblTop); // GOTO top
        setLabel(lblEnd);
    }

    static void parseDoWhile() {
        Lexer.nextToken(); // skip 'do'
        int lblTop = newLabel();
        int lblEnd = newLabel();
        int lblCont = newLabel();
        setLabel(lblTop);

        loopBreakLabel[loopDepth] = lblEnd;
        loopContLabel[loopDepth] = lblCont;
        loopDepth++;
        parseStatement();
        loopDepth--;

        Lexer.expect(Token.TOK_WHILE);
        setLabel(lblCont);
        Lexer.expect(Token.TOK_LPAREN);
        parseExpression();
        Lexer.expect(Token.TOK_RPAREN);
        popStack();
        emitBranch(0x9A, lblTop); // IFNE → top
        setLabel(lblEnd);
        Lexer.expect(Token.TOK_SEMI);
    }

    static void parseFor() {
        Lexer.nextToken(); // skip 'for'
        Lexer.expect(Token.TOK_LPAREN);

        int savedLocalCount = localCount;

        // Init
        if (Token.type != Token.TOK_SEMI) {
            if (isTypeToken(Token.type)) {
                parseLocalDecl(); // includes semicolon
            } else {
                int type = parseExpression();
                if (type != 0) { emitByte(0x57); popStack(); }
                Lexer.expect(Token.TOK_SEMI);
            }
        } else {
            Lexer.nextToken(); // skip ;
        }

        int lblTop = newLabel();
        int lblEnd = newLabel();
        int lblUpdate = newLabel();
        setLabel(lblTop);

        // Condition
        if (Token.type != Token.TOK_SEMI) {
            parseExpression();
            popStack();
            emitBranch(0x99, lblEnd); // IFEQ → end
        }
        // Save update expression position BEFORE consuming the semicolon
        // Lexer.pos is right after ';', before the update tokens
        int updateStart = Lexer.pos;
        int updateLine = Lexer.line;
        Lexer.expect(Token.TOK_SEMI);

        // Skip update for now — emit it after body
        int parenDepth = 0;
        while (Token.type != Token.TOK_EOF) {
            if (Token.type == Token.TOK_LPAREN) parenDepth++;
            else if (Token.type == Token.TOK_RPAREN) {
                if (parenDepth == 0) break;
                parenDepth--;
            }
            Lexer.nextToken();
        }
        Lexer.expect(Token.TOK_RPAREN);

        // Body
        loopBreakLabel[loopDepth] = lblEnd;
        loopContLabel[loopDepth] = lblUpdate;
        loopDepth++;
        parseStatement();
        loopDepth--;

        // Update
        setLabel(lblUpdate);
        if (updateStart < Lexer.pos) {
            // Save full lexer + token state
            int savePos = Lexer.pos;
            int saveLine = Lexer.line;
            int saveTokType = Token.type;
            int saveTokInt = Token.intValue;
            int saveTokLine = Token.line;
            int saveTokStrLen = Token.strLen;
            byte[] saveTokStr = new byte[saveTokStrLen];
            Native.arraycopy(Token.strBuf, 0, saveTokStr, 0, saveTokStrLen);

            // Re-lex the update expression
            Lexer.pos = updateStart;
            Lexer.line = updateLine;
            Lexer.nextToken();
            if (Token.type != Token.TOK_RPAREN) {
                int type = parseExpression();
                if (type != 0) { emitByte(0x57); popStack(); }
            }

            // Restore full lexer + token state
            Lexer.pos = savePos;
            Lexer.line = saveLine;
            Token.type = saveTokType;
            Token.intValue = saveTokInt;
            Token.line = saveTokLine;
            Token.strLen = saveTokStrLen;
            Native.arraycopy(saveTokStr, 0, Token.strBuf, 0, saveTokStrLen);
        }

        emitBranch(0xA7, lblTop); // GOTO top
        setLabel(lblEnd);

        localCount = savedLocalCount;
    }

    static void parseReturn() {
        Lexer.nextToken(); // skip 'return'
        if (Token.type == Token.TOK_SEMI) {
            Lexer.nextToken();
            emitByte(0xB1); // RETURN
        } else {
            int retType = methodRetType[curMethod];
            parseExpression();
            popStack();
            if (retType == 2) emitByte(0xB0); // ARETURN
            else emitByte(0xAC); // IRETURN
            Lexer.expect(Token.TOK_SEMI);
        }
    }

    static void parseThrow() {
        Lexer.nextToken(); // skip 'throw'
        parseExpression();
        popStack();
        emitByte(0xBF); // ATHROW
        Lexer.expect(Token.TOK_SEMI);
    }

    static void parseSwitch() {
        Lexer.nextToken(); // skip 'switch'
        Lexer.expect(Token.TOK_LPAREN);
        parseExpression();
        Lexer.expect(Token.TOK_RPAREN);
        popStack();

        int lblEnd = newLabel();

        // Collect cases
        // Save body start BEFORE expect consumes { and advances past first token
        int bodyStart = Lexer.pos;
        int bodyLine = Lexer.line;
        Lexer.expect(Token.TOK_LBRACE);
        // Static to avoid heap allocation per switch (no nested switches)
        // Allocated once in <clinit>, reused for each switch statement
        int caseCount = 0;
        int defaultLabel = -1;

        // Use LOOKUPSWITCH for all switch statements (simplest)
        int switchPC = mcodeLen;
        emitByte(0xAB); // LOOKUPSWITCH

        // Padding to 4-byte alignment
        while ((switchPC + 1 + (mcodeLen - switchPC - 1)) % 4 != 0) {
            emitByte(0);
        }

        int defaultLoc = mcodeLen;
        emitIntBE(0); // default offset placeholder (4 bytes, standard JVM format)
        int npairsLoc = mcodeLen;
        emitIntBE(0); // npairs placeholder (4 bytes)

        // Scan cases
        while (Token.type != Token.TOK_RBRACE && Token.type != Token.TOK_EOF) {
            if (Token.type == Token.TOK_CASE) {
                Lexer.nextToken();
                int val;
                boolean neg = false;
                if (Token.type == Token.TOK_MINUS) {
                    neg = true;
                    Lexer.nextToken();
                }
                val = Token.intValue;
                if (neg) val = -val;
                Lexer.nextToken();
                Lexer.expect(Token.TOK_COLON);

                caseLabels[caseCount] = newLabel();
                caseVals[caseCount] = val;
                caseCount++;
            } else if (Token.type == Token.TOK_DEFAULT) {
                Lexer.nextToken();
                Lexer.expect(Token.TOK_COLON);
                defaultLabel = newLabel();
            } else {
                Lexer.nextToken();
            }
        }

        // Now fill in the lookupswitch table
        // Patch npairs (4-byte BE, value fits in low 16 bits)
        mcode[npairsLoc]     = 0;
        mcode[npairsLoc + 1] = 0;
        mcode[npairsLoc + 2] = (byte) ((caseCount >> 8) & 0xFF);
        mcode[npairsLoc + 3] = (byte) (caseCount & 0xFF);

        // Emit match-offset pairs (sorted by key, picoJVM requires this)
        // Simple insertion sort on caseVals
        for (int i = 1; i < caseCount; i++) {
            int kv = caseVals[i];
            int kl = caseLabels[i];
            int j = i - 1;
            while (j >= 0 && caseVals[j] > kv) {
                caseVals[j + 1] = caseVals[j];
                caseLabels[j + 1] = caseLabels[j];
                j--;
            }
            caseVals[j + 1] = kv;
            caseLabels[j + 1] = kl;
        }

        int[] pairLocs = new int[caseCount];
        for (int i = 0; i < caseCount; i++) {
            // 4-byte match key (big-endian)
            emitByte((caseVals[i] >> 24) & 0xFF);
            emitByte((caseVals[i] >> 16) & 0xFF);
            emitByte((caseVals[i] >> 8) & 0xFF);
            emitByte(caseVals[i] & 0xFF);
            // 4-byte offset placeholder (standard JVM format)
            pairLocs[i] = mcodeLen;
            emitIntBE(0);
        }

        // Now re-parse and emit case bodies
        Lexer.pos = bodyStart;
        Lexer.line = bodyLine;
        Lexer.nextToken();

        int curCaseIdx = -1;

        loopBreakLabel[loopDepth] = lblEnd;
        loopContLabel[loopDepth] = lblEnd; // continue in switch = break
        loopDepth++;

        while (Token.type != Token.TOK_RBRACE && Token.type != Token.TOK_EOF) {
            if (Token.type == Token.TOK_CASE) {
                Lexer.nextToken();
                boolean neg = false;
                if (Token.type == Token.TOK_MINUS) { neg = true; Lexer.nextToken(); }
                int val = Token.intValue;
                if (neg) val = -val;
                Lexer.nextToken();
                Lexer.expect(Token.TOK_COLON);

                // Find which case this is
                for (int i = 0; i < caseCount; i++) {
                    if (caseVals[i] == val) {
                        setLabel(caseLabels[i]);
                        // Patch offset (4-byte BE)
                        int offset = mcodeLen - switchPC;
                        mcode[pairLocs[i]]     = (byte) ((offset >> 24) & 0xFF);
                        mcode[pairLocs[i] + 1] = (byte) ((offset >> 16) & 0xFF);
                        mcode[pairLocs[i] + 2] = (byte) ((offset >> 8) & 0xFF);
                        mcode[pairLocs[i] + 3] = (byte) (offset & 0xFF);
                        break;
                    }
                }
            } else if (Token.type == Token.TOK_DEFAULT) {
                Lexer.nextToken();
                Lexer.expect(Token.TOK_COLON);
                if (defaultLabel >= 0) {
                    setLabel(defaultLabel);
                    int offset = mcodeLen - switchPC;
                    mcode[defaultLoc]     = (byte) ((offset >> 24) & 0xFF);
                    mcode[defaultLoc + 1] = (byte) ((offset >> 16) & 0xFF);
                    mcode[defaultLoc + 2] = (byte) ((offset >> 8) & 0xFF);
                    mcode[defaultLoc + 3] = (byte) (offset & 0xFF);
                }
            } else {
                parseStatement();
            }
        }

        loopDepth--;

        // If no default, patch default to end
        if (defaultLabel < 0) {
            setLabel(lblEnd);
            int offset = mcodeLen - switchPC;
            mcode[defaultLoc]     = (byte) ((offset >> 24) & 0xFF);
            mcode[defaultLoc + 1] = (byte) ((offset >> 16) & 0xFF);
            mcode[defaultLoc + 2] = (byte) ((offset >> 8) & 0xFF);
            mcode[defaultLoc + 3] = (byte) (offset & 0xFF);
        }

        setLabel(lblEnd);
        Lexer.expect(Token.TOK_RBRACE);
    }

    static void parseTryCatch() {
        Lexer.nextToken(); // skip 'try'

        int lblEnd = newLabel();
        int startPC = mcodeLen;

        Lexer.expect(Token.TOK_LBRACE);
        parseBlock();
        Lexer.expect(Token.TOK_RBRACE);

        int endPC = mcodeLen;
        emitBranch(0xA7, lblEnd); // GOTO after handlers

        // Catch clauses
        while (Token.type == Token.TOK_CATCH) {
            Lexer.nextToken();
            Lexer.expect(Token.TOK_LPAREN);

            // Exception type
            int excNm = internBuf(Token.strBuf, Token.strLen);
            Lexer.nextToken();

            // Exception variable name
            int varNm = internBuf(Token.strBuf, Token.strLen);
            Lexer.nextToken();
            Lexer.expect(Token.TOK_RPAREN);

            int handlerPC = mcodeLen;

            // Store exception in local
            int slot = localCount;
            addLocal(varNm, 1); // reference type
            emitStore(slot, 1); // ASTORE
            popStack(); // exception ref was on stack
            pushStack(); // but we consumed it

            // Record exception table entry
            int catchClassId = findClassByName(excNm);
            if (catchClassId < 0) catchClassId = 0xFF;
            excStartPc[excCount] = startPC;
            excEndPc[excCount] = endPC;
            excHandlerPc[excCount] = handlerPC;
            excCatchClass[excCount] = catchClassId;
            excCount++;
            methodExcCount[curMethod]++;

            Lexer.expect(Token.TOK_LBRACE);
            parseBlock();
            Lexer.expect(Token.TOK_RBRACE);

            emitBranch(0xA7, lblEnd); // GOTO end
        }

        // Finally clause
        int finallyBodyPos = -1;
        int finallyBodyLine = -1;
        if (Token.type == Token.TOK_FINALLY) {
            Lexer.nextToken();

            int handlerPC = mcodeLen;
            int excSlot = localCount;
            addLocal(internStr("$finally"), 1);
            emitStore(excSlot, 1); // ASTORE exception

            // Record catch-all handler
            excStartPc[excCount] = startPC;
            excEndPc[excCount] = endPC;
            excHandlerPc[excCount] = handlerPC;
            excCatchClass[excCount] = 0xFF; // catch all
            excCount++;
            methodExcCount[curMethod]++;

            // Save position for re-lexing on normal path (before { is consumed)
            finallyBodyPos = Lexer.pos;
            finallyBodyLine = Lexer.line;
            Lexer.expect(Token.TOK_LBRACE);
            parseBlock();
            Lexer.expect(Token.TOK_RBRACE);

            // Re-throw
            emitLoad(excSlot, 1); // ALOAD
            pushStack();
            emitByte(0xBF); // ATHROW
            popStack();
        }

        setLabel(lblEnd);

        // Emit finally body on normal path too (duplicate via re-lex)
        if (finallyBodyPos >= 0) {
            int savedPos = Lexer.pos;
            int savedLine = Lexer.line;
            int savedTok = Token.type;
            Lexer.pos = finallyBodyPos;
            Lexer.line = finallyBodyLine;
            Lexer.nextToken();
            parseBlock();
            // Restore lexer to after the finally block
            Lexer.pos = savedPos;
            Lexer.line = savedLine;
            Token.type = savedTok;
        }
    }

    // ==================== EXPRESSION PARSING ====================
    // Returns: 0=void, 1=int, 2=ref

    static int parseExpression() {
        return parseTernary();
    }

    static int parseTernary() {
        int type = parseOr();
        if (Token.type == Token.TOK_QUESTION) {
            Lexer.nextToken();
            popStack();
            int lblFalse = newLabel();
            int lblEnd = newLabel();
            emitBranch(0x99, lblFalse); // IFEQ → false
            int tType = parseExpression();
            emitBranch(0xA7, lblEnd); // GOTO end
            popStack();
            Lexer.expect(Token.TOK_COLON);
            setLabel(lblFalse);
            int fType = parseExpression();
            setLabel(lblEnd);
            type = tType;
        }
        return type;
    }

    static int parseOr() {
        int type = parseAnd();
        while (Token.type == Token.TOK_OR) {
            Lexer.nextToken();
            int lblTrue = newLabel();
            int lblEnd = newLabel();
            // Short-circuit: if left is true, skip right
            emitByte(0x59); // DUP
            pushStack();
            emitBranch(0x9A, lblTrue); // IFNE → true
            popStack();
            emitByte(0x57); // POP
            popStack();
            parseAnd();
            emitBranch(0xA7, lblEnd);
            setLabel(lblTrue);
            setLabel(lblEnd);
        }
        return type;
    }

    static int parseAnd() {
        int type = parseBitOr();
        while (Token.type == Token.TOK_AND) {
            Lexer.nextToken();
            int lblFalse = newLabel();
            int lblEnd = newLabel();
            emitByte(0x59); // DUP
            pushStack();
            emitBranch(0x99, lblFalse); // IFEQ → false
            popStack();
            emitByte(0x57); // POP
            popStack();
            parseBitOr();
            emitBranch(0xA7, lblEnd);
            setLabel(lblFalse);
            setLabel(lblEnd);
        }
        return type;
    }

    static int parseBitOr() {
        int type = parseBitXor();
        while (Token.type == Token.TOK_PIPE) {
            Lexer.nextToken();
            parseBitXor();
            emitByte(0x80); // IOR
            popStack();
        }
        return type;
    }

    static int parseBitXor() {
        int type = parseBitAnd();
        while (Token.type == Token.TOK_CARET) {
            Lexer.nextToken();
            parseBitAnd();
            emitByte(0x82); // IXOR
            popStack();
        }
        return type;
    }

    static int parseBitAnd() {
        int type = parseEquality();
        while (Token.type == Token.TOK_AMP) {
            Lexer.nextToken();
            parseEquality();
            emitByte(0x7E); // IAND
            popStack();
        }
        return type;
    }

    static int parseEquality() {
        int type = parseComparison();
        while (Token.type == Token.TOK_EQ || Token.type == Token.TOK_NE) {
            int op = Token.type;
            Lexer.nextToken();
            int rtype = parseComparison();
            popStack(); popStack();
            if (type == 2 || rtype == 2) {
                // Reference comparison
                int lbl = newLabel();
                int lblEnd = newLabel();
                emitBranch(op == Token.TOK_EQ ? 0xA5 : 0xA6, lbl); // IF_ACMPEQ/NE
                emitByte(0x03); // ICONST_0
                pushStack();
                emitBranch(0xA7, lblEnd);
                setLabel(lbl);
                emitByte(0x04); // ICONST_1
                pushStack();
                setLabel(lblEnd);
            } else {
                int lbl = newLabel();
                int lblEnd = newLabel();
                emitBranch(op == Token.TOK_EQ ? 0x9F : 0xA0, lbl); // IF_ICMPEQ/NE
                emitByte(0x03); pushStack();
                emitBranch(0xA7, lblEnd);
                setLabel(lbl);
                emitByte(0x04); pushStack();
                setLabel(lblEnd);
            }
            type = 1;
        }
        return type;
    }

    static int parseComparison() {
        int type = parseShift();
        while (Token.type == Token.TOK_LT || Token.type == Token.TOK_GT ||
               Token.type == Token.TOK_LE || Token.type == Token.TOK_GE ||
               Token.type == Token.TOK_INSTANCEOF) {
            if (Token.type == Token.TOK_INSTANCEOF) {
                Lexer.nextToken();
                int classNm = internBuf(Token.strBuf, Token.strLen);
                Lexer.nextToken();
                popStack();
                int ci = findClassByName(classNm);
                int cpIdx = allocClassCP(ci >= 0 ? ci : 0);
                emitByte(0xC1); // INSTANCEOF
                emitShortBE(cpIdx);
                pushStack();
                type = 1;
                continue;
            }
            int op = Token.type;
            Lexer.nextToken();
            parseShift();
            popStack(); popStack();

            int branchOp;
            if (op == Token.TOK_LT) branchOp = 0xA1; // IF_ICMPLT
            else if (op == Token.TOK_GE) branchOp = 0xA2; // IF_ICMPGE
            else if (op == Token.TOK_GT) branchOp = 0xA3; // IF_ICMPGT
            else branchOp = 0xA4; // IF_ICMPLE

            int lbl = newLabel();
            int lblEnd = newLabel();
            emitBranch(branchOp, lbl);
            emitByte(0x03); pushStack(); // ICONST_0
            emitBranch(0xA7, lblEnd);
            setLabel(lbl);
            emitByte(0x04); pushStack(); // ICONST_1
            setLabel(lblEnd);
            type = 1;
        }
        return type;
    }

    static int parseShift() {
        int type = parseAdditive();
        while (Token.type == Token.TOK_SHL || Token.type == Token.TOK_SHR ||
               Token.type == Token.TOK_USHR) {
            int op = Token.type;
            Lexer.nextToken();
            parseAdditive();
            popStack();
            if (op == Token.TOK_SHL) emitByte(0x78); // ISHL
            else if (op == Token.TOK_SHR) emitByte(0x7A); // ISHR
            else emitByte(0x7C); // IUSHR
        }
        return type;
    }

    static int parseAdditive() {
        int type = parseMultiplicative();
        while (Token.type == Token.TOK_PLUS || Token.type == Token.TOK_MINUS) {
            int op = Token.type;
            Lexer.nextToken();
            parseMultiplicative();
            popStack();
            emitByte(op == Token.TOK_PLUS ? 0x60 : 0x64); // IADD / ISUB
        }
        return type;
    }

    static int parseMultiplicative() {
        int type = parseUnary();
        while (Token.type == Token.TOK_STAR || Token.type == Token.TOK_SLASH ||
               Token.type == Token.TOK_PERCENT) {
            int op = Token.type;
            Lexer.nextToken();
            parseUnary();
            popStack();
            if (op == Token.TOK_STAR) emitByte(0x68); // IMUL
            else if (op == Token.TOK_SLASH) emitByte(0x6C); // IDIV
            else emitByte(0x70); // IREM
        }
        return type;
    }

    static int parseUnary() {
        if (Token.type == Token.TOK_MINUS) {
            Lexer.nextToken();
            if (Token.type == Token.TOK_INT_LIT) {
                // Negative literal
                Token.intValue = -Token.intValue;
                return parsePrimary();
            }
            parseUnary();
            emitByte(0x74); // INEG
            return 1;
        }
        if (Token.type == Token.TOK_TILDE) {
            Lexer.nextToken();
            parseUnary();
            // ~x = x ^ (-1)
            emitByte(0x02); // ICONST_M1
            pushStack();
            emitByte(0x82); // IXOR
            popStack();
            return 1;
        }
        if (Token.type == Token.TOK_BANG) {
            Lexer.nextToken();
            parseUnary();
            // !x: if x == 0, push 1, else push 0
            popStack();
            int lbl = newLabel();
            int lblEnd = newLabel();
            emitBranch(0x99, lbl); // IFEQ
            emitByte(0x03); pushStack(); // ICONST_0
            emitBranch(0xA7, lblEnd);
            setLabel(lbl);
            emitByte(0x04); pushStack(); // ICONST_1
            setLabel(lblEnd);
            return 1;
        }
        if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
            int op = Token.type;
            Lexer.nextToken();
            int nm = internBuf(Token.strBuf, Token.strLen);
            Lexer.nextToken();
            int li = findLocal(nm);
            if (li >= 0) {
                int slot = localSlot[li];
                // Pre-increment: load, add/sub 1, dup, store
                emitLoad(slot, 0);
                pushStack();
                emitByte(0x04); pushStack(); // ICONST_1
                emitByte(op == Token.TOK_INC ? 0x60 : 0x64); // IADD/ISUB
                popStack();
                emitByte(0x59); pushStack(); // DUP
                emitStore(slot, 0);
                popStack();
                return 1;
            }
            Lexer.error(201);
            return 1;
        }
        if (Token.type == Token.TOK_LPAREN) {
            // Check for cast: (Type)expr
            Lexer.save();
            Lexer.nextToken();
            if (isTypeToken(Token.type) || Token.type == Token.TOK_IDENT) {
                int castType = Token.type;
                int castNm = -1;
                if (Token.type == Token.TOK_IDENT) castNm = internBuf(Token.strBuf, Token.strLen);
                Lexer.nextToken();
                if (Token.type == Token.TOK_RPAREN) {
                    // It's a cast
                    Lexer.nextToken();
                    parseUnary();
                    // Emit cast instruction
                    if (castType == Token.TOK_BYTE) emitByte(0x91); // I2B
                    else if (castType == Token.TOK_CHAR) emitByte(0x92); // I2C
                    else if (castType == Token.TOK_SHORT) emitByte(0x93); // I2S
                    else if (castType == Token.TOK_IDENT && castNm >= 0) {
                        // Object cast = CHECKCAST
                        int ci = findClassByName(castNm);
                        int cpIdx = allocClassCP(ci >= 0 ? ci : 0);
                        emitByte(0xC0); // CHECKCAST
                        emitShortBE(cpIdx);
                    }
                    return castType == Token.TOK_IDENT ? 2 : 1;
                }
                // Not a cast, restore and parse as parenthesized expression
            }
            Lexer.restore();
            Lexer.nextToken(); // skip (
            int type = parseExpression();
            Lexer.expect(Token.TOK_RPAREN);
            return parsePostfix(type);
        }
        return parsePostfix(parsePrimary());
    }

    static int parsePostfix(int type) {
        while (true) {
            if (Token.type == Token.TOK_DOT) {
                Lexer.nextToken();
                int memberNm = internBuf(Token.strBuf, Token.strLen);
                Lexer.nextToken();

                if (memberNm == N_LENGTH && Token.type != Token.TOK_LPAREN) {
                    // array.length
                    emitByte(0xBE); // ARRAYLENGTH
                    type = 1;
                } else if (Token.type == Token.TOK_LPAREN) {
                    // Method call on object
                    type = emitMethodCall(type, memberNm, false);
                } else {
                    // Field access
                    type = emitFieldAccess(memberNm, false);
                }
            }
            else if (Token.type == Token.TOK_LBRACKET) {
                // Array access
                Lexer.nextToken();
                parseExpression();
                Lexer.expect(Token.TOK_RBRACKET);
                popStack(); // index

                // Check for store
                if (Token.type == Token.TOK_ASSIGN) {
                    Lexer.nextToken();
                    parseExpression();
                    popStack(); // value
                    popStack(); // array ref
                    if (type == 4) emitByte(0x54);       // BASTORE (byte[])
                    else if (type == 5) emitByte(0x55);  // CASTORE (char[])
                    else emitByte(0x4F);                  // IASTORE (int[]/ref[])
                    type = 0; // void
                } else if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
                    // Array element post-increment: arr[idx]++
                    // Stack: [arrRef, idx] (idx was popped from tracker but still on JVM stack)
                    int op = Token.type;
                    Lexer.nextToken();
                    pushStack(); // re-count idx
                    emitByte(0x5C); pushStack(); pushStack(); // DUP2
                    if (type == 4) emitByte(0x33);       // BALOAD
                    else if (type == 5) emitByte(0x34);  // CALOAD
                    else emitByte(0x2E);                  // IALOAD
                    popStack(); // IALOAD consumes dup'd pair, pushes value: net -1
                    emitByte(0x5B); pushStack(); // DUP_X2: old value below arrRef,idx,new
                    emitByte(0x04); pushStack(); // ICONST_1
                    emitByte(op == Token.TOK_INC ? 0x60 : 0x64); popStack(); // IADD/ISUB
                    if (type == 4) emitByte(0x54);       // BASTORE
                    else if (type == 5) emitByte(0x55);  // CASTORE
                    else emitByte(0x4F);                  // IASTORE
                    popStack(); popStack(); popStack(); // IASTORE consumes arrRef,idx,value
                    type = 1; // old value remains on stack
                } else {
                    if (type == 4) emitByte(0x33);       // BALOAD (byte[])
                    else if (type == 5) emitByte(0x34);  // CALOAD (char[])
                    else emitByte(0x2E);                  // IALOAD (int[]/ref[])
                    // Propagate inner type for multi-dim arrays
                    if (type == 6) type = 4;             // byte[][] elem → byte[]
                    else if (type == 7) type = 5;        // char[][] elem → char[]
                    else type = 1;
                }
            }
            else if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
                // Post-increment/decrement in general postfix position
                Lexer.nextToken();
                type = 1;
            }
            else if (Token.type == Token.TOK_ASSIGN ||
                     (Token.type >= Token.TOK_PLUS_EQ && Token.type <= Token.TOK_USHR_EQ)) {
                // Assignment to field/array element (already have object on stack)
                // This is handled in specific contexts
                break;
            }
            else {
                break;
            }
        }
        return type;
    }

    static int parsePrimary() {
        if (Token.type == Token.TOK_INT_LIT || Token.type == Token.TOK_CHAR_LIT) {
            int val = Token.intValue;
            Lexer.nextToken();
            emitIntConst(val);
            pushStack();
            return 1;
        }
        if (Token.type == Token.TOK_STR_LIT) {
            byte[] buf = new byte[Token.strLen];
            Native.arraycopy(Token.strBuf, 0, buf, 0, Token.strLen);
            int cpIdx = allocStringCP(buf, Token.strLen);
            Lexer.nextToken();
            emitByte(0x12); // LDC
            emitByte(cpIdx);
            pushStack();
            return 2; // reference
        }
        if (Token.type == Token.TOK_TRUE) {
            Lexer.nextToken();
            emitByte(0x04); // ICONST_1
            pushStack();
            return 1;
        }
        if (Token.type == Token.TOK_FALSE) {
            Lexer.nextToken();
            emitByte(0x03); // ICONST_0
            pushStack();
            return 1;
        }
        if (Token.type == Token.TOK_NULL) {
            Lexer.nextToken();
            emitByte(0x01); // ACONST_NULL
            pushStack();
            return 2;
        }
        if (Token.type == Token.TOK_THIS) {
            Lexer.nextToken();
            emitLoad(0, 1); // ALOAD_0
            pushStack();
            return 2;
        }
        if (Token.type == Token.TOK_NEW) {
            return parseNew();
        }
        if (Token.type == Token.TOK_IDENT || Token.type == Token.TOK_STRING_KW) {
            int nm = internBuf(Token.strBuf, Token.strLen);
            Lexer.nextToken();

            // Check for static method call or field access: Name.member
            if (Token.type == Token.TOK_DOT) {
                // Could be ClassName.method() or ClassName.field
                int ci = findClassByName(nm);
                if (ci >= 0 || nm == N_NATIVE || nm == N_STRING) {
                    Lexer.nextToken();
                    int memberNm = internBuf(Token.strBuf, Token.strLen);
                    Lexer.nextToken();

                    if (Token.type == Token.TOK_LPAREN) {
                        // Static method call
                        return emitStaticCall(nm, memberNm);
                    } else {
                        // Static field access
                        return emitStaticFieldAccess(ci, memberNm);
                    }
                }
            }

            // Check for local variable
            int li = findLocal(nm);
            if (li >= 0) {
                int slot = localSlot[li];
                int ltype = localType[li];

                // Check for assignment
                if (Token.type == Token.TOK_ASSIGN) {
                    Lexer.nextToken();
                    parseExpression();
                    emitByte(0x59); pushStack(); // DUP (keep value on stack)
                    emitStore(slot, ltype);
                    popStack();
                    return ltype == 1 ? 2 : 1;
                }
                if (Token.type >= Token.TOK_PLUS_EQ && Token.type <= Token.TOK_USHR_EQ) {
                    int op = Token.type;
                    Lexer.nextToken();
                    emitLoad(slot, ltype);
                    pushStack();
                    parseExpression();
                    emitCompoundOp(op);
                    popStack();
                    emitByte(0x59); pushStack(); // DUP
                    emitStore(slot, ltype);
                    popStack();
                    return 1;
                }
                if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
                    // Post-increment: load value, then increment
                    emitLoad(slot, 0);
                    pushStack();
                    int op = Token.type;
                    Lexer.nextToken();
                    // Use IINC for efficiency
                    emitByte(0x84); // IINC
                    emitByte(slot);
                    emitByte(op == Token.TOK_INC ? 1 : 0xFF); // +1 or -1
                    return 1;
                }

                emitLoad(slot, ltype);
                pushStack();
                // Map local type to expression type
                // 0=int→1, 1=ref→2, 3=int[]→3, 4=byte[]→4, 5=char[]→5
                if (ltype >= 3) return ltype;
                return ltype == 1 ? 2 : 1;
            }

            // Check for instance field (implicit this)
            if (!curMethodIsStatic) {
                int fi = findField(curClass, nm);
                if (fi >= 0 && !fieldIsStatic[fi]) {
                    // Check for assignment
                    if (Token.type == Token.TOK_ASSIGN) {
                        Lexer.nextToken();
                        emitLoad(0, 1); // ALOAD_0 (this)
                        pushStack();
                        parseExpression();
                        int cpIdx = allocFieldCP(fieldSlot[fi]);
                        emitByte(0xB5); // PUTFIELD
                        emitShortBE(cpIdx);
                        popStack(); popStack(); // obj + value consumed
                        // Push value back for expression result
                        // Actually for statement context this is void
                        return 0;
                    }
                    if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
                        int op = Token.type;
                        Lexer.nextToken();
                        int cpIdx = allocFieldCP(fieldSlot[fi]);
                        // Post-inc/dec: load old value, then update field
                        emitLoad(0, 1); pushStack(); // ALOAD_0
                        emitByte(0xB4); emitShortBE(cpIdx); // GETFIELD (old value)
                        // Keep old value for expression result
                        emitLoad(0, 1); pushStack(); // ALOAD_0 again
                        emitLoad(0, 1); pushStack(); // ALOAD_0 again
                        emitByte(0xB4); emitShortBE(cpIdx); // GETFIELD again
                        emitByte(0x04); pushStack(); // ICONST_1
                        emitByte(op == Token.TOK_INC ? 0x60 : 0x64); popStack(); // IADD/ISUB
                        emitByte(0xB5); emitShortBE(cpIdx); // PUTFIELD
                        popStack(); popStack(); // obj + value consumed by PUTFIELD
                        return 1; // old value remains on stack
                    }
                    if (Token.type >= Token.TOK_PLUS_EQ && Token.type <= Token.TOK_USHR_EQ) {
                        int op = Token.type;
                        Lexer.nextToken();
                        int cpIdx = allocFieldCP(fieldSlot[fi]);
                        emitLoad(0, 1); pushStack(); // ALOAD_0
                        emitLoad(0, 1); pushStack(); // ALOAD_0
                        emitByte(0xB4); emitShortBE(cpIdx); // GETFIELD
                        parseExpression();
                        emitCompoundOp(op);
                        popStack();
                        emitByte(0xB5); emitShortBE(cpIdx); // PUTFIELD
                        popStack(); popStack();
                        return 0;
                    }
                    emitLoad(0, 1); // ALOAD_0 (this)
                    pushStack();
                    int cpIdx = allocFieldCP(fieldSlot[fi]);
                    emitByte(0xB4); // GETFIELD
                    emitShortBE(cpIdx);
                    // stack: -1 (obj) +1 (value) = net 0
                    if (fieldArrayKind[fi] != 0) return fieldArrayKind[fi];
                    return 1;
                }
            }

            // Check for static field (prefer current class first)
            {
                int sfi = -1;
                for (int fi2 = 0; fi2 < fieldCount; fi2++) {
                    if (fieldName[fi2] == nm && fieldIsStatic[fi2]) {
                        if (fieldClass[fi2] == curClass) { sfi = fi2; break; }
                        if (sfi < 0) sfi = fi2;
                    }
                }
                if (sfi >= 0) {
                    int fi = sfi;
                    if (Token.type == Token.TOK_ASSIGN) {
                        Lexer.nextToken();
                        parseExpression();
                        emitByte(0x59); pushStack(); // DUP
                        int cpIdx = allocFieldCP(fieldSlot[fi]);
                        emitByte(0xB3); // PUTSTATIC
                        emitShortBE(cpIdx);
                        popStack();
                        return 1;
                    }
                    if (Token.type >= Token.TOK_PLUS_EQ && Token.type <= Token.TOK_USHR_EQ) {
                        int op = Token.type;
                        Lexer.nextToken();
                        int cpIdx = allocFieldCP(fieldSlot[fi]);
                        emitByte(0xB2); // GETSTATIC
                        emitShortBE(cpIdx);
                        pushStack();
                        parseExpression();
                        emitCompoundOp(op);
                        popStack();
                        emitByte(0x59); pushStack(); // DUP
                        emitByte(0xB3); // PUTSTATIC
                        emitShortBE(cpIdx);
                        popStack();
                        return 1;
                    }
                    if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
                        int op = Token.type;
                        Lexer.nextToken();
                        int cpIdx = allocFieldCP(fieldSlot[fi]);
                        emitByte(0xB2); // GETSTATIC
                        emitShortBE(cpIdx);
                        pushStack();
                        // Post: value before inc/dec is on stack
                        emitByte(0x59); pushStack(); // DUP
                        emitByte(0x04); pushStack(); // ICONST_1
                        emitByte(op == Token.TOK_INC ? 0x60 : 0x64); popStack(); // IADD/ISUB
                        emitByte(0xB3); // PUTSTATIC
                        emitShortBE(cpIdx);
                        popStack();
                        return 1;
                    }
                    int cpIdx = allocFieldCP(fieldSlot[fi]);
                    emitByte(0xB2); // GETSTATIC
                    emitShortBE(cpIdx);
                    pushStack();
                    if (fieldArrayKind[fi] != 0) return fieldArrayKind[fi];
                    return 1;
                }
            }

            // Check for method call in same class
            if (Token.type == Token.TOK_LPAREN) {
                // Check if it's an instance method (implicit this.method())
                if (!curMethodIsStatic) {
                    for (int mi2 = 0; mi2 < methodCount; mi2++) {
                        if (methodClass[mi2] == curClass && methodName[mi2] == nm &&
                            !methodIsStatic[mi2] && !methodIsNative[mi2] && !methodIsConstructor[mi2]) {
                            // Instance method: push this, then INVOKEVIRTUAL
                            emitLoad(0, 1); // ALOAD_0 (this)
                            pushStack();
                            return emitMethodCall(2, nm, false);
                        }
                    }
                }
                return emitStaticCall(className[curClass], nm);
            }

            Lexer.error(202); // Undefined identifier
            return 0;
        }
        Lexer.error(203); // Unexpected token
        return 0;
    }

    // ==================== NEW EXPRESSION ====================

    static int parseNew() {
        Lexer.nextToken(); // skip 'new'

        if (Token.type == Token.TOK_INT || Token.type == Token.TOK_BYTE ||
            Token.type == Token.TOK_CHAR || Token.type == Token.TOK_SHORT ||
            Token.type == Token.TOK_BOOLEAN) {
            // Primitive array: new int[size]
            int elemType = Token.type;
            Lexer.nextToken();
            Lexer.expect(Token.TOK_LBRACKET);
            parseExpression();
            Lexer.expect(Token.TOK_RBRACKET);

            int typeCode = 10; // int
            if (elemType == Token.TOK_BYTE) typeCode = 8;
            else if (elemType == Token.TOK_CHAR) typeCode = 5;
            else if (elemType == Token.TOK_SHORT) typeCode = 9;
            else if (elemType == Token.TOK_BOOLEAN) typeCode = 4;

            // Check for 2D array: new type[N][M] or new type[N][]
            if (Token.type == Token.TOK_LBRACKET) {
                Lexer.nextToken();
                if (Token.type == Token.TOK_RBRACKET) {
                    // new type[N][] — array of references, size N
                    Lexer.nextToken();
                    int cpIdx = allocClassCP(0);
                    emitByte(0xBD); // ANEWARRAY
                    emitShortBE(cpIdx);
                    return 2; // reference array
                }
                parseExpression();
                Lexer.expect(Token.TOK_RBRACKET);
                // MULTIANEWARRAY
                int cpIdx = allocClassCP(0); // type doesn't matter for int[][]
                emitByte(0xC5); // MULTIANEWARRAY
                emitShortBE(cpIdx);
                emitByte(2); // 2 dimensions
                popStack(); // second dimension
                // First dim still on stack, result replaces it
                return 2;
            }

            emitByte(0xBC); // NEWARRAY
            emitByte(typeCode);
            // Stack: count consumed, array ref pushed = net 0
            // Return specific array type for proper BALOAD/BASTORE emission
            if (typeCode == 8 || typeCode == 4) return 4;  // byte[] or boolean[]
            if (typeCode == 5) return 5;  // char[]
            return 3; // int[] (or short[])
        }

        // Object or reference array: new ClassName(...) or new ClassName[size]
        int classNm = internBuf(Token.strBuf, Token.strLen);
        Lexer.nextToken();

        if (Token.type == Token.TOK_LBRACKET) {
            // Reference array: new ClassName[size]
            Lexer.nextToken();
            parseExpression();
            Lexer.expect(Token.TOK_RBRACKET);

            int ci = findClassByName(classNm);
            int cpIdx = allocClassCP(ci >= 0 ? ci : 0);

            // Check for 2D
            if (Token.type == Token.TOK_LBRACKET) {
                Lexer.nextToken();
                parseExpression();
                Lexer.expect(Token.TOK_RBRACKET);
                emitByte(0xC5); // MULTIANEWARRAY
                emitShortBE(cpIdx);
                emitByte(2);
                popStack();
                return 2;
            }

            emitByte(0xBD); // ANEWARRAY
            emitShortBE(cpIdx);
            return 2;
        }

        // Object creation: new ClassName(args)
        int ci = findClassByName(classNm);
        if (ci < 0) ci = synthesizeExceptionClass(classNm);
        int cpIdx = allocClassCP(ci);
        emitByte(0xBB); // NEW
        emitShortBE(cpIdx);
        pushStack();
        emitByte(0x59); // DUP
        pushStack();

        // Parse constructor arguments
        Lexer.expect(Token.TOK_LPAREN);
        int argc = 1; // 'this' counts
        while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
            parseExpression();
            argc++;
            if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
        }
        Lexer.expect(Token.TOK_RPAREN);

        // Find constructor
        int ctorMi = -1;
        for (int mi = 0; mi < methodCount; mi++) {
            if (methodClass[mi] == ci && methodIsConstructor[mi] && methodArgCount[mi] == argc) {
                ctorMi = mi;
                break;
            }
        }
        if (ctorMi < 0) {
            // Try default constructor (0 user args = 1 total with 'this')
            for (int mi = 0; mi < methodCount; mi++) {
                if (methodClass[mi] == ci && methodIsConstructor[mi]) {
                    ctorMi = mi;
                    break;
                }
            }
        }
        if (ctorMi < 0) {
            // Use Object.<init> as fallback
            ctorMi = ensureNative(N_OBJECT, N_INIT);
        }

        int ctorCpIdx = allocCP(ctorMi);
        emitByte(0xB7); // INVOKESPECIAL
        emitShortBE(ctorCpIdx);
        // Pop args + dup from stack, keep original ref
        for (int i = 0; i < argc; i++) popStack();

        return 2; // reference on stack
    }

    // ==================== METHOD CALLS ====================

    static int emitStaticCall(int classNm, int methodNm) {
        Lexer.expect(Token.TOK_LPAREN);

        // First check native methods
        int nativeMi = ensureNative(classNm, methodNm);
        if (nativeMi >= 0) {
            int argc = 0;
            while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
                parseExpression();
                argc++;
                if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
            }
            Lexer.expect(Token.TOK_RPAREN);
            int cpIdx = allocCP(nativeMi);
            emitByte(0xB8); // INVOKESTATIC
            emitShortBE(cpIdx);
            for (int i = 0; i < argc; i++) popStack();
            int retType = methodRetType[nativeMi];
            if (retType != 0) pushStack();
            return retType;
        }

        // User-defined static method
        int mi = -1;
        for (int m = 0; m < methodCount; m++) {
            if (methodName[m] == methodNm && !methodIsNative[m]) {
                int mc = methodClass[m];
                if (mc < classCount && className[mc] == classNm) {
                    mi = m;
                    break;
                }
            }
        }

        int argc = 0;
        while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
            parseExpression();
            argc++;
            if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
        }
        Lexer.expect(Token.TOK_RPAREN);

        if (mi < 0) {
            Lexer.error(204); // Undefined method
            return 0;
        }

        int cpIdx = allocCP(mi);
        emitByte(0xB8); // INVOKESTATIC
        emitShortBE(cpIdx);
        for (int i = 0; i < argc; i++) popStack();
        int retType = methodRetType[mi];
        if (retType != 0) pushStack();
        return retType;
    }

    static int emitMethodCall(int objType, int methodNm, boolean isInterface) {
        // Object is already on stack
        Lexer.expect(Token.TOK_LPAREN);

        // Check for String methods
        int nativeMi = ensureNative(N_STRING, methodNm);

        int argc = 1; // 'this' already on stack
        while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
            parseExpression();
            argc++;
            if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
        }
        Lexer.expect(Token.TOK_RPAREN);

        if (nativeMi >= 0) {
            int cpIdx = allocCP(nativeMi);
            emitByte(0xB6); // INVOKEVIRTUAL
            emitShortBE(cpIdx);
            for (int i = 0; i < argc; i++) popStack();
            int retType = methodRetType[nativeMi];
            if (retType != 0) pushStack();
            return retType;
        }

        // Find the method in user classes
        int mi = -1;
        for (int m = 0; m < methodCount; m++) {
            if (methodName[m] == methodNm && !methodIsStatic[m] && !methodIsNative[m]) {
                mi = m;
                break;
            }
        }

        if (mi < 0) {
            Lexer.error(205);
            return 0;
        }

        // Check if this is an interface call
        boolean useInterface = false;
        int mci = methodClass[mi];
        if (mci < classCount && classIsInterface[mci]) {
            useInterface = true;
        }

        if (useInterface) {
            int cpIdx = allocCP(mi);
            emitByte(0xB9); // INVOKEINTERFACE
            emitShortBE(cpIdx);
            emitByte(argc);
            emitByte(0);
            for (int i = 0; i < argc; i++) popStack();
        } else {
            int cpIdx = allocCP(mi);
            emitByte(0xB6); // INVOKEVIRTUAL
            emitShortBE(cpIdx);
            for (int i = 0; i < argc; i++) popStack();
        }

        int retType = methodRetType[mi];
        if (retType != 0) pushStack();
        return retType;
    }

    // ==================== FIELD ACCESS ====================

    static int emitFieldAccess(int fieldNm, boolean isStore) {
        // Object ref is on stack
        // Find field
        int fi = -1;
        for (int f = 0; f < fieldCount; f++) {
            if (fieldName[f] == fieldNm && !fieldIsStatic[f]) {
                fi = f;
                break;
            }
        }
        if (fi < 0) {
            Lexer.error(206);
            return 0;
        }

        if (Token.type == Token.TOK_ASSIGN) {
            Lexer.nextToken();
            parseExpression();
            int cpIdx = allocFieldCP(fieldSlot[fi]);
            emitByte(0xB5); // PUTFIELD
            emitShortBE(cpIdx);
            popStack(); popStack();
            return 0;
        }
        if (Token.type >= Token.TOK_PLUS_EQ && Token.type <= Token.TOK_USHR_EQ) {
            int op = Token.type;
            Lexer.nextToken();
            emitByte(0x59); pushStack(); // DUP obj ref
            int cpIdx = allocFieldCP(fieldSlot[fi]);
            emitByte(0xB4); // GETFIELD
            emitShortBE(cpIdx);
            parseExpression();
            emitCompoundOp(op);
            popStack();
            emitByte(0xB5); // PUTFIELD
            emitShortBE(cpIdx);
            popStack(); popStack();
            return 0;
        }

        int cpIdx = allocFieldCP(fieldSlot[fi]);
        emitByte(0xB4); // GETFIELD
        emitShortBE(cpIdx);
        // Stack: obj consumed, value pushed = net 0
        if (fieldArrayKind[fi] != 0) return fieldArrayKind[fi];
        return 1;
    }

    static int emitStaticFieldAccess(int ci, int fieldNm) {
        int fi = -1;
        for (int f = 0; f < fieldCount; f++) {
            if (fieldName[f] == fieldNm && fieldIsStatic[f]) {
                if (fieldClass[f] == ci) { fi = f; break; }
                if (fi < 0) fi = f;
            }
        }
        if (fi < 0) {
            Lexer.error(207);
            return 0;
        }

        if (Token.type == Token.TOK_ASSIGN) {
            Lexer.nextToken();
            parseExpression();
            int cpIdx = allocFieldCP(fieldSlot[fi]);
            emitByte(0xB3); // PUTSTATIC
            emitShortBE(cpIdx);
            popStack();
            return 0;
        }
        if (Token.type >= Token.TOK_PLUS_EQ && Token.type <= Token.TOK_USHR_EQ) {
            int op = Token.type;
            Lexer.nextToken();
            int cpIdx = allocFieldCP(fieldSlot[fi]);
            emitByte(0xB2); // GETSTATIC
            emitShortBE(cpIdx);
            pushStack();
            parseExpression();
            emitCompoundOp(op);
            popStack();
            emitByte(0x59); pushStack(); // DUP
            emitByte(0xB3); // PUTSTATIC
            emitShortBE(cpIdx);
            popStack();
            return 1;
        }
        if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
            int op = Token.type;
            Lexer.nextToken();
            int cpIdx = allocFieldCP(fieldSlot[fi]);
            emitByte(0xB2); // GETSTATIC
            emitShortBE(cpIdx);
            pushStack();
            emitByte(0x59); pushStack(); // DUP
            emitByte(0x04); pushStack(); // ICONST_1
            emitByte(op == Token.TOK_INC ? 0x60 : 0x64); popStack(); // IADD/ISUB
            emitByte(0xB3); // PUTSTATIC
            emitShortBE(cpIdx);
            popStack();
            return 1;
        }

        int cpIdx = allocFieldCP(fieldSlot[fi]);
        emitByte(0xB2); // GETSTATIC
        emitShortBE(cpIdx);
        pushStack();
        if (fieldArrayKind[fi] != 0) return fieldArrayKind[fi];
        return 1;
    }

    static int findField(int ci, int nm) {
        // Search this class and parents
        while (ci >= 0) {
            for (int fi = 0; fi < fieldCount; fi++) {
                if (fieldClass[fi] == ci && fieldName[fi] == nm) return fi;
            }
            ci = classParent[ci];
        }
        return -1;
    }

    // ==================== EMIT HELPERS ====================

    static void emitIntConst(int val) {
        if (val >= -1 && val <= 5) {
            emitByte(0x03 + val); // ICONST_M1=0x02 .. ICONST_5=0x08
        } else if (val >= -128 && val <= 127) {
            emitByte(0x10); // BIPUSH
            emitByte(val & 0xFF);
        } else if (val >= -32768 && val <= 32767) {
            emitByte(0x11); // SIPUSH
            emitShortBE(val);
        } else {
            // LDC with integer constant
            int cpIdx = allocIntConstCP(val);
            emitByte(0x12); // LDC
            emitByte(cpIdx);
        }
    }

    static void emitLoad(int slot, int type) {
        if (type != 0) {
            // Reference (type 1=ref, 3=int[], 4=byte[], 5=char[])
            if (slot <= 3) emitByte(0x2A + slot); // ALOAD_0..3
            else { emitByte(0x19); emitByte(slot); } // ALOAD
        } else {
            // Int
            if (slot <= 3) emitByte(0x1A + slot); // ILOAD_0..3
            else { emitByte(0x15); emitByte(slot); } // ILOAD
        }
    }

    static void emitStore(int slot, int type) {
        if (type != 0) {
            // Reference (type 1=ref, 3=int[], 4=byte[], 5=char[])
            if (slot <= 3) emitByte(0x4B + slot); // ASTORE_0..3
            else { emitByte(0x3A); emitByte(slot); } // ASTORE
        } else {
            if (slot <= 3) emitByte(0x3B + slot); // ISTORE_0..3
            else { emitByte(0x36); emitByte(slot); } // ISTORE
        }
    }

    static void emitCompoundOp(int tok) {
        if (tok == Token.TOK_PLUS_EQ) emitByte(0x60); // IADD
        else if (tok == Token.TOK_MINUS_EQ) emitByte(0x64); // ISUB
        else if (tok == Token.TOK_STAR_EQ) emitByte(0x68); // IMUL
        else if (tok == Token.TOK_SLASH_EQ) emitByte(0x6C); // IDIV
        else if (tok == Token.TOK_PERCENT_EQ) emitByte(0x70); // IREM
        else if (tok == Token.TOK_AMP_EQ) emitByte(0x7E); // IAND
        else if (tok == Token.TOK_PIPE_EQ) emitByte(0x80); // IOR
        else if (tok == Token.TOK_CARET_EQ) emitByte(0x82); // IXOR
        else if (tok == Token.TOK_SHL_EQ) emitByte(0x78); // ISHL
        else if (tok == Token.TOK_SHR_EQ) emitByte(0x7A); // ISHR
        else if (tok == Token.TOK_USHR_EQ) emitByte(0x7C); // IUSHR
    }

    // ==================== PASS 4: LINK (WRITE OUTPUT) ====================

    static void writeOutput() {
        outLen = 0;
        int userClassCount = classCount - userClassStart;

        // Count non-interface user classes
        int pjvmClassCount = 0;
        for (int ci = userClassStart; ci < classCount; ci++) {
            if (!classIsInterface[ci]) pjvmClassCount++;
        }

        // Header (10 bytes)
        writeByte(0x85); // magic
        writeByte(0x4A);
        writeByte(methodCount); // n_methods
        writeByte(mainMi); // main_mi
        writeByte(staticFieldCount); // n_static
        writeByte(intConstCount); // n_integers
        writeByte(pjvmClassCount); // n_classes
        writeByte(strConstCount); // n_strings
        writeShortLE(codeLen); // bytecodes_size

        // Class table
        for (int ci = userClassStart; ci < classCount; ci++) {
            if (classIsInterface[ci]) continue;
            int parentId = 0xFF;
            if (classParent[ci] >= 0) {
                parentId = classParent[ci] - userClassStart;
                if (parentId < 0) parentId = 0xFF;
            }
            writeByte(parentId);
            writeByte(classFieldCount[ci]);
            writeByte(classVtableSize[ci]);
            int clinitIdx = 0xFF;
            if (classClinitMi[ci] != 0xFF) clinitIdx = classClinitMi[ci];
            writeByte(clinitIdx);
            // Vtable entries
            for (int j = 0; j < classVtableSize[ci]; j++) {
                writeByte(vtable[vtableBase[ci] + j]);
            }
        }

        // Compute exc_off_idx: cumulative exception entry count per method
        int excRunning = 0;
        for (int mi = 0; mi < methodCount; mi++) {
            methodExcIdx[mi] = excRunning;
            excRunning += methodExcCount[mi];
        }

        // Method table (12 bytes per method)
        for (int mi = 0; mi < methodCount; mi++) {
            writeByte(methodMaxLocals[mi]);
            writeByte(methodMaxStack[mi]);
            writeByte(methodArgCount[mi]);
            writeByte(methodFlags[mi]);
            writeShortLE(methodCodeOff[mi]);
            writeShortLE(methodCpBase[mi]);
            writeByte(methodVtableSlot[mi]);
            writeByte(methodVmid[mi]);
            writeByte(methodExcCount[mi]);
            writeByte(methodExcIdx[mi]);
        }

        // CP resolution table
        writeShortLE(cpSize);
        Native.writeBytes(cpEntries, 0, cpSize);
        outLen += cpSize;

        // Integer constants (4 bytes each, LE)
        for (int i = 0; i < intConstCount; i++) {
            int v = intConsts[i];
            writeByte(v & 0xFF);
            writeByte((v >> 8) & 0xFF);
            writeByte((v >> 16) & 0xFF);
            writeByte((v >> 24) & 0xFF);
        }

        // String constants
        for (int i = 0; i < strConstCount; i++) {
            int len = strConstLen[i];
            writeShortLE(len);
            Native.writeBytes(strConsts[i], 0, len);
            outLen += len;
        }

        // Bytecodes
        for (int i = 0; i < codeLen; i++) {
            writeByte(Native.peek(codeBase + i) & 0xFF);
        }

        // Exception table (7 bytes per entry)
        for (int i = 0; i < excCount; i++) {
            writeShortLE(excStartPc[i]);
            writeShortLE(excEndPc[i]);
            writeShortLE(excHandlerPc[i]);
            writeByte(excCatchClass[i]);
        }
    }

    static void writeByte(int b) {
        Native.putchar(b & 0xFF);
        outLen++;
    }

    static void writeShortLE(int s) {
        writeByte(s & 0xFF);
        writeByte((s >> 8) & 0xFF);
    }
}
