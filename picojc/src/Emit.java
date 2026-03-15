// Emit -> E (size)
class E {
	// JVM opcodes
	static final int ACONST_NULL=0x01, ICONST_0=0x03, ICONST_1=0x04;
	static final int BIPUSH=0x10, SIPUSH=0x11, LDC=0x12, LDC_W=0x13;
	static final int ALOAD=0x19, ALOAD_0=0x2A, ILOAD_0=0x1A;
	static final int ASTORE=0x3A, ISTORE_0=0x3B, ASTORE_0=0x4B;
	static final int DUP=0x59, DUP_X2=0x5B, DUP2=0x5C, POP=0x57;
	static final int IADD=0x60, ISUB=0x64, IMUL=0x68, IDIV=0x6C;
	static final int IREM=0x70, INEG=0x74, ISHL=0x78, ISHR=0x7A;
	static final int IUSHR=0x7C, IAND=0x7E, IOR=0x80, IXOR=0x82;
	static final int IINC=0x84;
	static final int I2B=0x91, I2C=0x92, I2S=0x93;
	static final int IFEQ=0x99, IFNE=0x9A, GOTO=0xA7;
	static final int LOOKUPSWITCH=0xAB;
	static final int IRETURN=0xAC, ARETURN=0xB0, RETURN=0xB1;
	static final int GETSTATIC=0xB2, PUTSTATIC=0xB3;
	static final int GETFIELD=0xB4, PUTFIELD=0xB5;
	static final int INVOKEVIRTUAL=0xB6, INVOKESPECIAL=0xB7;
	static final int INVOKESTATIC=0xB8, INVOKEINTERFACE=0xB9;
	static final int NEW=0xBB, NEWARRAY=0xBC, ANEWARRAY=0xBD;
	static final int ARRAYLENGTH=0xBE, ATHROW=0xBF;
	static final int CHECKCAST=0xC0, INSTANCEOF=0xC1;
	static final int MULTIANEWARRAY=0xC5;

	// Per-class clinit accumulation buffer (field inits + static blocks)
	static byte[] cinitBuf = new byte[1024];
	static int cinitLen;
	static byte[] cinitCpL = new byte[128]; // CP staging lo bytes
	static byte[] cinitCpH = new byte[128]; // CP staging hi bytes
	static int cinitCpC;
	static int cinitMaxStk;
	static int cinitMaxLoc;
	static int tyNarrow; // declared scalar narrow kind for the last parsed local/param type

	static void emit() {
		C.cdLen = 0;
		C.cpSz = 0;
		C.excC = 0;
		autoCtorsEmitted = false;

		// Skip to class bodies and emit methods
		while (Tk.type != Tk.EOF) {
			eClsMethods();
		}

		// Emit auto-generated default constructors after all user methods
		eAutoCtors();
	}

	static void eClsMethods() {
		// Skip to class body
		while (Tk.type != Tk.LBRACE && Tk.type != Tk.EOF) {
			Lexer.nextToken();
		}
		if (Tk.type == Tk.EOF) return;
		Lexer.nextToken(); // skip {

		// We're inside a class body
		int ci = fCurCls();
		C.curCi = ci;

		// Reset per-class clinit accumulation
		cinitLen = 0;
		cinitCpC = 0;
		cinitMaxStk = 0;
		cinitMaxLoc = 0;

		// Skip enum constant list
		if (C.cIsEnum[ci]) {
			while (Tk.type == Tk.IDENT) {
				Lexer.nextToken();
				if (Tk.type == Tk.COMMA) Lexer.nextToken();
				else break;
			}
			if (Tk.type == Tk.SEMI) Lexer.nextToken();
		}

		while (Tk.type != Tk.RBRACE && Tk.type != Tk.EOF) {
			eMem(ci);
		}

		// Finalize clinit from accumulated buffer (field inits + static blocks)
		if (cinitLen > 0 && C.cClinit[ci] != 0xFF) {
			int mi = C.cClinit[ci];
			if (!C.mNative[mi]) {
				initMC(mi);
				C.curMStatic = true;
				C.locCount = 0;
				C.stkDepth = 0;
				C.maxStk = cinitMaxStk;
				for (int i = 0; i < cinitLen; i++) C.mcode[i] = cinitBuf[i];
				C.mcLen = cinitLen;
				C.cpMCount = cinitCpC;
				for (int i = 0; i < cinitCpC; i++) {
					C.cpEnt[C.cpMBase + i] = cinitCpL[i];
					C.cpEntH[C.cpMBase + i] = cinitCpH[i];
				}
				eb(RETURN);
				commitMC(mi);
				C.mMaxLoc[mi] = (byte)(cinitMaxLoc > 0 ? cinitMaxLoc : 1);
				C.mMaxStk[mi] = (byte)(cinitMaxStk > 0 ? cinitMaxStk : 1);
			}
		} else if (cinitLen == 0 && C.cClinit[ci] != 0xFF) {
			// All field inits were inlined — emit minimal RETURN-only clinit
			int mi = C.cClinit[ci];
			initMC(mi);
			C.curMStatic = true;
			C.locCount = 0; C.stkDepth = 0;
			C.cpMCount = 0;
			eb(RETURN);
			commitMC(mi);
			C.mMaxLoc[mi] = (byte)1;
			C.mMaxStk[mi] = (byte)1;
		}

		if (Tk.type == Tk.RBRACE) Lexer.nextToken();
	}

	static int emitClassIdx; // tracking which class we're emitting
	static int fCurCls() {
		// Match by position - classes appear in source order
		for (int ci = C.uClsStart; ci < C.cCount; ci++) {
			if (C.cBodyS[ci] >= 0 && Lexer.pos >= C.cBodyS[ci] &&
				(C.cBodyE[ci] < 0 || Lexer.pos <= C.cBodyE[ci])) {
				return ci;
			}
		}
		return C.uClsStart;
	}

	static void eMem(int ci) {
		if (Catalog.parseMods()) { eStatBlock(); return; }
		if (Catalog.isCtor(ci)) {
			int mi = fCtor(ci, scanArgc(false));
			if (mi >= 0) eMBody(mi);
			else skipMDecl();
			return;
		}
		Catalog.skipTy();
		int nm = C.iN();
		if (Tk.type == Tk.LPAREN) {
			int mi = fMeth(ci, nm, Catalog.mStat, scanArgc(Catalog.mStat));
			if (mi >= 0 && !C.mNative[mi] && C.mBodyS[mi] >= 0) {
				eMBody(mi);
			} else {
				skipMDecl();
			}
		} else {
			if (Catalog.mStat) {
				eStatFieldDecl(ci, nm);
			} else {
				while (Tk.type != Tk.SEMI && Tk.type != Tk.EOF) Lexer.nextToken();
				Lexer.expect(Tk.SEMI);
			}
		}
	}

	// Parse static field declaration, accumulating initializers into clinit buffer
	static void eStatFieldDecl(int ci, int firstNm) {
		if (Tk.type == Tk.ASSIGN) {
			eStatFieldInit(ci, firstNm);
		}
		// Handle comma-separated: static int a = 1, b = 2;
		while (Tk.type == Tk.COMMA) {
			Lexer.nextToken();
			int nm = C.iN();
			if (Tk.type == Tk.ASSIGN) {
				eStatFieldInit(ci, nm);
			}
		}
		while (Tk.type != Tk.SEMI && Tk.type != Tk.EOF) Lexer.nextToken();
		Lexer.expect(Tk.SEMI);
	}

	static void beginClinitChunk(int nextLoc) {
		C.mcLen = 0;
		C.cpMBase = C.cpSz;
		C.cpMCount = cinitCpC;
		for (int i = 0; i < cinitCpC; i++) {
			C.cpEnt[C.cpMBase + i] = cinitCpL[i];
			C.cpEntH[C.cpMBase + i] = cinitCpH[i];
		}
		C.stkDepth = 0;
		C.maxStk = 0;
		C.curMStatic = true;
		C.locCount = 0;
		C.locNext = nextLoc;
		C.lblCount = 0;
		C.patC = 0;
		C.lpDepth = 0;
	}

	static void endClinitChunk(boolean trackLocals) {
		for (int i = 0; i < C.patC; i++) {
			int loc = C.patLoc[i];
			int lbl = C.patLbl[i];
			int target = C.lblAddr[lbl];
			int offset = target - (loc - 1);
			C.mcode[loc] = (byte)((offset >> 8) & 0xFF);
			C.mcode[loc + 1] = (byte)(offset & 0xFF);
		}
		for (int i = 0; i < C.mcLen; i++) {
			cinitBuf[cinitLen + i] = C.mcode[i];
		}
		cinitLen += C.mcLen;
		cinitCpC = C.cpMCount;
		for (int i = 0; i < cinitCpC; i++) {
			cinitCpL[i] = C.cpEnt[C.cpMBase + i];
			cinitCpH[i] = C.cpEntH[C.cpMBase + i];
		}
		if (C.maxStk > cinitMaxStk) cinitMaxStk = C.maxStk;
		if (trackLocals && C.locNext > cinitMaxLoc) cinitMaxLoc = C.locNext;
	}

	// Parse a single static field initializer expression into clinit buffer
	static void eStatFieldInit(int ci, int nm) {
		// Skip inlined final constants — no runtime init needed
		int fi0 = Resolver.fStatField(ci, nm);
		if (fi0 >= 0 && C.fFinal[fi0] && C.fHasConst[fi0]) {
			Lexer.nextToken(); // skip '='
			// Skip the initializer expression tokens
			while (Tk.type != Tk.SEMI && Tk.type != Tk.COMMA && Tk.type != Tk.EOF)
				Lexer.nextToken();
			return;
		}

		beginClinitChunk(0);

		Lexer.nextToken(); // skip '='
		Expr.pExpr();
		int fi = Resolver.fStatField(ci, nm);
		if (fi >= 0) {
			Expr.chkImplicitNarrow(C.fNarrow[fi]);
			eNarrow(C.fNarrow[fi]);
			int cpIdx = aCP(C.fSlot[fi]);
			eOp(PUTSTATIC, cpIdx); pop();
		}
		endClinitChunk(false);
	}

	// Accumulate explicit static { } block into clinit buffer
	static void eStatBlock() {
		beginClinitChunk(cinitMaxLoc);

		Lexer.nextToken(); // skip {
		Stmt.pBlock();
		Lexer.expect(Tk.RBRACE);
		endClinitChunk(true);
	}

	static void skipMDecl() {
		// Skip parameter list
		Lexer.expect(Tk.LPAREN);
		while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
			Lexer.nextToken();
		}
		Lexer.expect(Tk.RPAREN);
		if (Tk.type == Tk.SEMI) {
			Lexer.nextToken(); // native/abstract method
		} else {
			Lexer.expect(Tk.LBRACE);
			Catalog.skipBlk();
			Lexer.expect(Tk.RBRACE);
		}
	}

	static int scanArgc(boolean isStat) {
		int savedTok = Tk.type;
		int savedLine = Tk.line;
		Lexer.save();
		Lexer.expect(Tk.LPAREN);
		int argc = isStat ? 0 : 1;
		boolean isVarargs = false;
		int fixedCount = 0;
		while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
			Catalog.skipTy();
			if (Tk.type == Tk.ELLIPSIS) {
				isVarargs = true;
				fixedCount = argc - (isStat ? 0 : 1);
				Lexer.nextToken();
			}
			Lexer.nextToken();
			argc++;
			if (Tk.type == Tk.COMMA) Lexer.nextToken();
		}
		Lexer.expect(Tk.RPAREN);
		Lexer.restore();
		Tk.type = savedTok;
		Tk.line = savedLine;
		if (isVarargs) argc = argc - 1 + C.MAX_VA_SLOTS + 1;
		return argc;
	}

	static int fMeth(int ci, int nm, boolean isStat, int argc) {
		for (int mi = 0; mi < C.mCount; mi++) {
			if (C.mClass[mi] == ci && C.mName[mi] == nm &&
				C.mStatic[mi] == isStat && !C.mNative[mi] &&
				(C.mArgC[mi] == argc ||
				 (nm == C.N_MAIN && isStat && argc == 1 && C.mArgC[mi] == 0))) {
				return mi;
			}
		}
		return -1;
	}

	static int fCtor(int ci, int argc) {
		for (int mi = 0; mi < C.mCount; mi++) {
			if (C.mClass[mi] == ci && C.mIsCtor[mi] &&
				C.mArgC[mi] == argc && !C.mNative[mi]) {
				return mi;
			}
		}
		return -1;
	}

	// Shared method context init
	static void initMC(int mi) {
		C.curMi = mi; C.mcLen = 0; C.patC = 0; C.lblCount = 0;
		C.cpMCount = 0; C.cpMBase = C.cpSz;
	}

	static void eMBody(int mi) {
		initMC(mi);
		C.curMStatic = C.mStatic[mi];
		C.locCount = 0;
		C.locNext = 0;
		C.lpDepth = 0;
		C.stkDepth = 0;
		C.maxStk = 0;

		// Set up parameters as locals
		if (C.mIsCtor[mi]) {
			// Constructor: skip params in source, set up 'this'
			aLoc(C.N_INIT, 1, C.cName[C.curCi]); // 'this'
			// Parse constructor parameters
			pParams();
			narrowScalarParams();

			// Parse body
			Lexer.expect(Tk.LBRACE);
			if (Tk.type == Tk.SUPER) {
				ethis();
				Lexer.expect(Tk.SUPER);
				Lexer.expect(Tk.LPAREN);
				int argc = Expr.pArgs(1);
				int parentCi = C.cParent[C.curCi];
				int targetMi = parentCi >= 0 ? fCtor(parentCi, argc) : (argc == 1 ? C.ensNat(C.N_OBJECT, C.N_INIT) : -1);
				argc = targetMi >= 0 ? Expr.packVarargs(targetMi, argc) : -1;
				if (targetMi < 0 || argc < 0) { Lexer.error(205); return; }
				eOp(INVOKESPECIAL, aCP(targetMi));
				for (int i = 0; i < argc; i++) pop();
				Lexer.expect(Tk.SEMI);
			} else {
				ethis();
				int targetMi = -1;
				if (C.cParent[C.curCi] >= 0) targetMi = fCtor(C.cParent[C.curCi], 1);
				if (targetMi < 0) targetMi = C.ensNat(C.N_OBJECT, C.N_INIT);
				if (targetMi < 0) { Lexer.error(205); return; }
				eOp(INVOKESPECIAL, aCP(targetMi));
				pop();
			}
			Stmt.pBlock();
			Lexer.expect(Tk.RBRACE);

			eb(RETURN);
		} else {
			// Regular method
			if (!C.curMStatic) {
				aLoc(C.iStr("this"), 1, C.cName[C.curCi]); // 'this' is slot 0
			}

			pParams();
			narrowScalarParams();

			// Parse body
			Lexer.expect(Tk.LBRACE);
			Stmt.pBlock();
			Lexer.expect(Tk.RBRACE);

			// If method doesn't end with return, add implicit return
			if (C.mcLen == 0 || (C.mcode[C.mcLen - 1] & 0xFF) != RETURN &&
				(C.mcode[C.mcLen - 1] & 0xFF) != IRETURN && (C.mcode[C.mcLen - 1] & 0xFF) != ARETURN) {
				eb(RETURN);
			}
		}

		// Resolve backpatches
		for (int i = 0; i < C.patC; i++) {
			int loc = C.patLoc[i];
			int lbl = C.patLbl[i];
			int target = C.lblAddr[lbl];
			int offset = target - (loc - 1); // relative to branch opcode
			C.mcode[loc] = (byte) ((offset >> 8) & 0xFF);
			C.mcode[loc + 1] = (byte) (offset & 0xFF);
		}

		commitMC(mi);
		C.mMaxLoc[mi] = (byte)(C.locNext > 0 ? C.locNext : 1);
		C.mMaxStk[mi] = (byte)(C.maxStk > 0 ? C.maxStk : 1);
	}

	static void commitMC(int mi) {
		C.mCodeOff[mi] = (short)C.cdLen;
		if (C.diskSpill) {
			for (int i = 0; i < C.mcLen; i++) {
				Native.fileWriteByte(C.mcode[i] & 0xFF); C.cdLen++;
			}
		} else {
			for (int i = 0; i < C.mcLen; i++) {
				Native.poke(C.cdBase + C.cdLen, C.mcode[i] & 0xFF); C.cdLen++;
			}
		}
		C.mCpBase[mi] = (short)C.cpMBase;
		// Entries already in cpEnt/cpEntH — just advance cpSz
		int end = C.cpMBase + C.cpMCount;
		if (end > C.cpSz) C.cpSz = end;
	}

	static boolean autoCtorsEmitted;

	static void eAutoCtors() {
		if (autoCtorsEmitted) return;
		autoCtorsEmitted = true;

		for (int mi = 0; mi < C.mCount; mi++) {
			if (C.mIsCtor[mi] && !C.mNative[mi] && C.mBodyS[mi] == -2) {
				// Auto-generated default constructor
				initMC(mi);

				// ALOAD_0, INVOKESPECIAL Object.<init>, RETURN
				eb(ALOAD_0);
				int objInitMi = C.ensNat(C.N_OBJECT, C.N_INIT);
				int cpIdx = aCP(objInitMi);
				eOp(INVOKESPECIAL, cpIdx);
				eb(RETURN);

				commitMC(mi);
				C.mMaxLoc[mi] = (byte)1;
				C.mMaxStk[mi] = (byte)1;
			}
			// Synthetic clinits are now handled in eClsMethods via cinitBuf
		}
	}

	static int pTypeLoc() {
		// Returns: 0=int, 1=ref, 2=object[], 3=int[], 4=byte[], 5=char[], 8=short[]
		int baseType = 0;
		int elemKind = 0; // 0=int-like, 1=byte, 2=char, 3=short
		tyRefNm = -1;
		tyNarrow = C.NK_NONE;
		if (Tk.type == Tk.BYTE || Tk.type == Tk.BOOLEAN) {
			if (Tk.type == Tk.BYTE) tyNarrow = C.NK_BYTE;
			elemKind = 1; Lexer.nextToken();
		} else if (Tk.type == Tk.CHAR) {
			tyNarrow = C.NK_CHAR;
			elemKind = 2; Lexer.nextToken();
		} else if (Tk.type == Tk.SHORT) {
			tyNarrow = C.NK_SHORT;
			elemKind = 3; Lexer.nextToken();
		} else if (Tk.type == Tk.INT) {
			elemKind = 0; Lexer.nextToken();
		} else if (Tk.type == Tk.STRING_KW) {
			baseType = 1;
			tyRefNm = C.N_STRING;
			Lexer.nextToken();
		} else {
			baseType = 1; // reference
			tyRefNm = C.iN();
		}
		// Array dimensions
		int dimCount = 0;
		while (Tk.type == Tk.LBRACKET) {
			Lexer.nextToken();
			if (Tk.type == Tk.RBRACKET) Lexer.nextToken();
			dimCount++;
		}
		if (dimCount > 0) {
			tyNarrow = C.NK_NONE;
			if (baseType == 1) {
				if (dimCount == 1 && tyRefNm >= 0) return 2; // object[]
				tyRefNm = -1;
				return 1; // generic reference array
			}
			tyRefNm = -1;
			if (dimCount > 1) return 1;
			if (elemKind == 1) return 4; // byte[]
			if (elemKind == 2) return 5; // char[]
			if (elemKind == 3) return 8; // short[]
			return 3; // int[]
		}
		return baseType; // 0=int, 1=ref
	}

	static int tyRefNm = -1; // declared ref type for the last parsed local/param type
	static int vaPN = -1;  // varargs param name index (-1 = not varargs)
	static int vaET;       // varargs element type token (Tk.INT, Tk.BYTE, etc.)

	static void pParams() {
		vaPN = -1;
		Lexer.expect(Tk.LPAREN);
		while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
			int savedTyTok = Tk.type;
			int pType = pTypeLoc();
			int pRefNm = tyRefNm;
			int pNarrow = tyNarrow;
			if (Tk.type == Tk.ELLIPSIS) {
				// Varargs parameter — must be last
				Lexer.nextToken(); // skip '...'
				vaPN = C.iN();
				vaET = savedTyTok;
				// Allocate MAX_VA_SLOTS hidden locals for spread values
				byte[] sb = Tk.strBuf;
				sb[0] = (byte)'$'; sb[1] = (byte)'v';
				for (int vi = 0; vi < C.MAX_VA_SLOTS; vi++) {
					sb[2] = (byte)('0' + vi);
					aLoc(C.intern(sb, 3), 0);
				}
				// Allocate count local
				sb[1] = (byte)'c';
				aLoc(C.intern(sb, 2), 0);
				break;
			}
			int pNm = C.iN();
			aLoc(pNm, pType, pRefNm, pNarrow);
			if (Tk.type == Tk.COMMA) Lexer.nextToken();
		}
		Lexer.expect(Tk.RPAREN);
		if (vaPN >= 0) {
			emitVAPrologue();
		}
	}

	static void emitVAPrologue() {
		int countSlot = C.locCount - 1;
		int firstVASlot = countSlot - C.MAX_VA_SLOTS;

		// Determine NEWARRAY type code and local array type
		int typeCode = 10; // T_INT
		int arrLocType = 3; // int[]
		if (vaET == Tk.BYTE || vaET == Tk.BOOLEAN) { typeCode = 8; arrLocType = 4; }
		else if (vaET == Tk.CHAR) { typeCode = 5; arrLocType = 5; }
		else if (vaET == Tk.SHORT) { typeCode = 9; arrLocType = 8; }

		// Create array: ILOAD count, NEWARRAY
		eLd(countSlot, 0); push();
		eb(NEWARRAY); eb(typeCode);

		// Store array in new local bound to the varargs parameter name
		int arrSlot = C.locCount;
		aLoc(vaPN, arrLocType);
		eSt(arrSlot, 1); pop();

		// Copy: for i = 0..MAX_VA_SLOTS-1, if (count > i) arr[i] = va_i
		for (int vi = 0; vi < C.MAX_VA_SLOTS; vi++) {
			eLd(countSlot, 0); push();
			eIC(vi); push();
			int skipLbl = label();
			eBr(0xA4, skipLbl); // IF_ICMPLE: count <= i → skip
			pop(); pop();

			eLd(arrSlot, 1); push();
			eIC(vi); push();
			eLd(firstVASlot + vi, 0); push();
			eASt(arrLocType); pop(); pop(); pop();

			mark(skipLbl);
		}
	}

	// Bytecode emission shortcuts
	static void ic0() { eb(ICONST_0); push(); }
	static void ic1() { eb(ICONST_1); push(); }
	static void edup() { eb(DUP); push(); }
	static void cmpBool(int op) {
		int lbl = label(); int lblEnd = label();
		eBr(op, lbl); ic0(); eBr(GOTO, lblEnd);
		mark(lbl); ic1(); mark(lblEnd);
	}
	static void eOp(int op, int cp) { eb(op); eSBE(cp); }
	static void epop() { eb(POP); pop(); }
	static void ethis() { eLd(0, 1); push(); } // ALOAD_0 this
	static void eALd(int t) { eb(t==4 ? 0x33 : t==5 ? 0x34 : t==8 ? 0x35 : 0x2E); } // backend/runtime use generic IALOAD for ref arrays too
	static void eASt(int t) { eb(t==4 ? 0x54 : t==5 ? 0x55 : t==8 ? 0x56 : 0x4F); } // backend/runtime use generic IASTORE for ref arrays too
	static void pushLp(int brk, int cont) {
		C.chk(C.lpDepth, 32, 263);
		C.lpBrkLbl[C.lpDepth] = (short)brk;
		C.lpContLbl[C.lpDepth] = (short)cont;
		C.lpDepth++;
	}
	static void popLp() { C.lpDepth--; }

	static void eb(int b) {
		C.chk(C.mcLen, 2048, 256);
		C.mcode[C.mcLen++] = (byte)(b & 0xFF);
	}

	static void eSBE(int s) {
		C.mcode[C.mcLen++] = (byte)((s >> 8) & 0xFF);
		C.mcode[C.mcLen++] = (byte)(s & 0xFF);
	}

	static void eIBE(int v) {
		C.mcode[C.mcLen++] = (byte)((v >> 24) & 0xFF);
		C.mcode[C.mcLen++] = (byte)((v >> 16) & 0xFF);
		C.mcode[C.mcLen++] = (byte)((v >> 8) & 0xFF);
		C.mcode[C.mcLen++] = (byte)(v & 0xFF);
	}

	static int eBrPH() {
		int loc = C.mcLen;
		eb(0);
		eb(0);
		return loc;
	}

	static int label() {
		C.chk(C.lblCount, 320, 261);
		return C.lblCount++;
	}

	static void mark(int lbl) {
		C.lblAddr[lbl] = (short)C.mcLen;
	}

	static void eBr(int opcode, int label) {
		int branchPC = C.mcLen;
		eb(opcode);
		int loc = eBrPH();
		C.chk(C.patC, 320, 262);
		C.patLoc[C.patC] = (short)loc;
		C.patLbl[C.patC] = (short)label;
		C.patC++;
	}

	static void push() {
		C.stkDepth++;
		if (C.stkDepth > C.maxStk) C.maxStk = C.stkDepth;
	}

	static void pop() {
		if (C.stkDepth > 0) C.stkDepth--;
	}

	static void aLoc(int nm, int type) {
		aLoc(nm, type, -1, C.NK_NONE);
	}

	static void aLoc(int nm, int type, int refNm) {
		aLoc(nm, type, refNm, C.NK_NONE);
	}

	static void aLoc(int nm, int type, int refNm, int narrowKind) {
		C.chk(C.locCount, C.MAX_LOCALS, 257);
		C.locName[C.locCount] = (short)nm;
		C.locSlot[C.locCount] = (byte)C.locCount;
		C.locType[C.locCount] = (byte)type;
		C.locRefNm[C.locCount] = (short)refNm;
		C.locNarrow[C.locCount] = (byte)narrowKind;
		C.locCount++;
		if (C.locCount > C.locNext) C.locNext = C.locCount;
	}

	static int fLoc(int nm) {
		for (int i = C.locCount - 1; i >= 0; i--) {
			if (C.locName[i] == nm) return i;
		}
		return -1;
	}

	static int aCP(int resolvedVal) {
		// Check for existing entry with same value (stored as byte pairs)
		byte lo = (byte)(resolvedVal & 0xFF);
		byte hi = (byte)((resolvedVal >> 8) & 0xFF);
		for (int i = 0; i < C.cpMCount; i++) {
			if (C.cpEnt[C.cpMBase + i] == lo && C.cpEntH[C.cpMBase + i] == hi) {
				return i;
			}
		}
		C.chk(C.cpMBase + C.cpMCount, C.MAX_CP, 258);
		int idx = C.cpMCount++;
		C.cpEnt[C.cpMBase + idx] = lo;
		C.cpEntH[C.cpMBase + idx] = hi;
		return idx;
	}


	static int aSCP(byte[] buf, int len) {
		int strIdx = -1;
		for (int i = 0; i < C.strCC; i++) {
			if (C.strCLen[i] == len && Native.memcmp(C.strC[i], 0, buf, 0, len) == 0) {
				strIdx = i; break;
			}
		}
		if (strIdx < 0) {
			C.chk(C.strCC, C.MAX_STR_CONST, 259);
			strIdx = C.strCC++;
			C.strC[strIdx] = new byte[len];
			Native.arraycopy(buf, 0, C.strC[strIdx], 0, len);
			C.strCLen[strIdx] = (byte)len;
		}
		return aCP(0x8000 | strIdx);
	}

	static int aICP(int val) {
		// Find or add integer constant
		int idx = -1;
		for (int i = 0; i < C.intCC; i++) {
			if (C.intC[i] == val) { idx = i; break; }
		}
		if (idx < 0) {
			C.chk(C.intCC, C.MAX_INT_CONST, 260);
			idx = C.intCC++;
			C.intC[idx] = val;
		}
		return aCP(idx);
	}


	// LDC or LDC_W depending on CP index
	static void eLdc(int cpIdx) {
		if (cpIdx < 256) { eb(LDC); eb(cpIdx); }
		else { eb(LDC_W); eSBE(cpIdx); }
	}

	static void eIC(int val) {
		if (val >= -1 && val <= 5) {
			eb(ICONST_0 + val); // ICONST_M1..ICONST_5
		} else if (val >= -128 && val <= 127) {
			eb(BIPUSH); eb(val & 0xFF);
		} else if (val >= -32768 && val <= 32767) {
			eb(SIPUSH); eSBE(val);
		} else {
			eLdc(aICP(val));
		}
	}

	static void eLd(int slot, int type) {
		if (type != 0) {
			if (slot <= 3) eb(ALOAD_0 + slot);
			else { eb(ALOAD); eb(slot); }
		} else {
			if (slot <= 3) eb(ILOAD_0 + slot);
			else { eb(0x15); eb(slot); } // ILOAD
		}
	}

	static void eNarrow(int narrowKind) {
		if (narrowKind == C.NK_BYTE) eb(I2B);
		else if (narrowKind == C.NK_CHAR) eb(I2C);
		else if (narrowKind == C.NK_SHORT) eb(I2S);
	}

	static void narrowScalarParams() {
		for (int li = 0; li < C.locCount; li++) {
			int narrowKind = C.locNarrow[li] & 0xFF;
			if (narrowKind == C.NK_NONE || C.locType[li] != 0) continue;
			int slot = C.locSlot[li] & 0xFF;
			eLd(slot, 0); push();
			eNarrow(narrowKind);
			eSt(slot, 0); pop();
		}
	}

	static void eSt(int slot, int type) {
		if (type != 0) {
			if (slot <= 3) eb(ASTORE_0 + slot);
			else { eb(ASTORE); eb(slot); }
		} else {
			if (slot <= 3) eb(ISTORE_0 + slot);
			else { eb(0x36); eb(slot); } // ISTORE
		}
	}

	static void eStN(int slot, int type, int narrowKind) {
		eNarrow(narrowKind);
		eSt(slot, type);
	}

	static void eCO(int tok) {
		if (tok == Tk.PLUS_EQ) eb(IADD);
		else if (tok == Tk.MINUS_EQ) eb(ISUB);
		else if (tok == Tk.STAR_EQ) eb(IMUL);
		else if (tok == Tk.SLASH_EQ) eb(IDIV);
		else if (tok == Tk.PERCENT_EQ) eb(IREM);
		else if (tok == Tk.AMP_EQ) eb(IAND);
		else if (tok == Tk.PIPE_EQ) eb(IOR);
		else if (tok == Tk.CARET_EQ) eb(IXOR);
		else if (tok == Tk.SHL_EQ) eb(ISHL);
		else if (tok == Tk.SHR_EQ) eb(ISHR);
		else if (tok == Tk.USHR_EQ) eb(IUSHR);
	}

	// Patch 4-byte big-endian value at mcode[loc]
	static void pBE(int loc, int v) {
		C.mcode[loc] = (byte)((v >> 24) & 0xFF);
		C.mcode[loc + 1] = (byte)((v >> 16) & 0xFF);
		C.mcode[loc + 2] = (byte)((v >> 8) & 0xFF);
		C.mcode[loc + 3] = (byte)(v & 0xFF);
	}
}
