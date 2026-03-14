// Emit -> E (size)
class E {
	// Branch opcodes
	static int IFEQ = 0x99, IFNE = 0x9A, GOTO = 0xA7;
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

		while (Tk.type != Tk.RBRACE && Tk.type != Tk.EOF) {
			eMem(ci);
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
		// Skip modifiers
		boolean isStat = false;
		boolean isNat = false;
		boolean isAbstract = false;
		while (Tk.type == Tk.PUBLIC || Tk.type == Tk.PRIVATE ||
			   Tk.type == Tk.PROTECTED || Tk.type == Tk.STATIC ||
			   Tk.type == Tk.FINAL || Tk.type == Tk.NATIVE ||
			   Tk.type == Tk.ABSTRACT) {
			if (Tk.type == Tk.STATIC) isStat = true;
			if (Tk.type == Tk.NATIVE) isNat = true;
			if (Tk.type == Tk.ABSTRACT) isAbstract = true;
			Lexer.nextToken();
		}

		// Static init block
		if (isStat && Tk.type == Tk.LBRACE) {
			int mi = fMeth(ci, C.N_CLINIT);
			if (mi >= 0) eMBody(mi);
			else { Lexer.nextToken(); Catalog.skipBlk(); Lexer.expect(Tk.RBRACE); }
			return;
		}

		// Constructor check
		if (Tk.type == Tk.IDENT) {
			int nm = C.intern(Tk.strBuf, Tk.strLen);
			if (nm == C.cName[ci]) {
				Lexer.save();
				Lexer.nextToken();
				if (Tk.type == Tk.LPAREN) {
					int mi = fCtor(ci);
					if (mi >= 0) eMBody(mi);
					else skipMDecl();
					return;
				}
				Lexer.restore();
				Tk.strLen = C.nLen[nm];
				Native.arraycopy(C.nPool, C.nOff[nm], Tk.strBuf, 0, Tk.strLen);
				Tk.type = Tk.IDENT;
			}
		}

		// Return type
		Catalog.skipTy();

		// Name
		int nm = C.intern(Tk.strBuf, Tk.strLen);
		Lexer.nextToken();

		if (Tk.type == Tk.LPAREN) {
			// Method
			int mi = fMeth(ci, nm);
			if (mi >= 0 && !C.mNative[mi] && C.mBodyS[mi] >= 0) {
				eMBody(mi);
			} else {
				skipMDecl();
			}
		} else {
			// Field - skip to semicolon
			while (Tk.type != Tk.SEMI && Tk.type != Tk.EOF) {
				Lexer.nextToken();
			}
			Lexer.expect(Tk.SEMI);
		}
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

	static int fMeth(int ci, int nm) {
		for (int mi = 0; mi < C.mCount; mi++) {
			if (C.mClass[mi] == ci && C.mName[mi] == nm && !C.mNative[mi]) {
				return mi;
			}
		}
		return -1;
	}

	static int fCtor(int ci) {
		for (int mi = 0; mi < C.mCount; mi++) {
			if (C.mClass[mi] == ci && C.mIsCtor[mi] && !C.mNative[mi]) {
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
			aLoc(C.N_INIT, 0); // 'this'
			// Parse constructor parameters
			pParams();

			// Emit super() call
			ethis();
			int objInitMi = C.ensNat(C.N_OBJECT, C.N_INIT);
			int cpIdx = aCP(objInitMi);
			eOp(0xB7, cpIdx); // INVOKESPECIAL
			pop(); // 'this' consumed

			// Parse body
			Lexer.expect(Tk.LBRACE);
			Stmt.pBlock();
			Lexer.expect(Tk.RBRACE);

			// Emit RETURN
			eb(0xB1);
		} else if (C.mName[mi] == C.N_CLINIT) {
			// Save current lexer position (at '{' of static block)
			int clinitPos = Lexer.pos;
			int clinitLine = Lexer.line;
			int clinitTok = Tk.type;
			emitStaticInits();
			// Restore lexer to static block body
			Lexer.pos = clinitPos;
			Lexer.line = clinitLine;
			Tk.type = clinitTok;
			// Static initializer body: { ... }
			Lexer.nextToken(); // skip {
			Stmt.pBlock();
			Lexer.expect(Tk.RBRACE);
			eb(0xB1); // RETURN
		} else {
			// Regular method
			if (!C.curMStatic) {
				aLoc(C.iStr("this"), 1); // 'this' is slot 0
			}

			pParams();

			// Parse body
			Lexer.expect(Tk.LBRACE);
			Stmt.pBlock();
			Lexer.expect(Tk.RBRACE);

			// If method doesn't end with return, add implicit return
			if (C.mcLen == 0 || (C.mcode[C.mcLen - 1] & 0xFF) != 0xB1 &&
				(C.mcode[C.mcLen - 1] & 0xFF) != 0xAC && (C.mcode[C.mcLen - 1] & 0xFF) != 0xB0) {
				eb(0xB1); // RETURN
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
		C.mMaxLoc[mi] = C.locNext > 0 ? C.locNext : 1;
		C.mMaxStk[mi] = C.maxStk > 0 ? C.maxStk : 1;
	}

	static void commitMC(int mi) {
		C.mCodeOff[mi] = C.cdLen;
		for (int i = 0; i < C.mcLen; i++) {
			Native.poke(C.cdBase + C.cdLen, C.mcode[i] & 0xFF); C.cdLen++;
		}
		C.mCpBase[mi] = C.cpMBase;
		for (int i = 0; i < C.cpMCount; i++) {
			C.cpEnt[C.cpMBase + i] = (byte) C.cpMVals[i];
		}
		C.cpSz = C.cpMBase + C.cpMCount;
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
				eb(0x2A); // ALOAD_0
				int objInitMi = C.ensNat(C.N_OBJECT, C.N_INIT);
				int cpIdx = aCP(objInitMi);
				eOp(0xB7, cpIdx); // INVOKESPECIAL
				eb(0xB1); // RETURN

				commitMC(mi);
				C.mMaxLoc[mi] = 1;
				C.mMaxStk[mi] = 1;
			}
			// Synthetic <clinit>: only field initializers, no explicit body
			if (C.mName[mi] == C.N_CLINIT && !C.mNative[mi] && C.mBodyS[mi] == -2) {
				// Synthetic clinit: field initializers only
				initMC(mi);
				C.curCi = C.mClass[mi];
				C.curMStatic = true;
				C.locCount = 0;
				C.stkDepth = 0;
				C.maxStk = 0;

				emitStaticInits();
				eb(0xB1); // RETURN

				commitMC(mi);
				C.mMaxLoc[mi] = 1;
				C.mMaxStk[mi] = C.maxStk > 0 ? C.maxStk : 1;
			}
		}
	}

	static void emitStaticInits() {
		for (int fi = 0; fi < C.fCount; fi++) {
			if (C.fClass[fi] == C.curCi && C.fStatic[fi] && C.fInitPos[fi] >= 0) {
				Lexer.pos = C.fInitPos[fi];
				Lexer.line = C.fInitLn[fi];
				Lexer.nextToken();
				Expr.pExpr();
				int cpIdx = aCP(C.fSlot[fi]);
				eOp(0xB3, cpIdx); pop(); // PUTSTATIC
			}
		}
	}

	static int pTypeLoc() {
		// Returns: 0=int, 1=ref, 3=int[], 4=byte[], 5=char[]
		int baseType = 0;
		int elemKind = 0; // 0=int-like, 1=byte, 2=char
		if (Tk.type == Tk.BYTE || Tk.type == Tk.BOOLEAN) {
			elemKind = 1; Lexer.nextToken();
		} else if (Tk.type == Tk.CHAR) {
			elemKind = 2; Lexer.nextToken();
		} else if (Tk.type == Tk.INT || Tk.type == Tk.SHORT) {
			elemKind = 0; Lexer.nextToken();
		} else {
			baseType = 1; // reference
			Lexer.nextToken();
		}
		// Array dimensions
		int dimCount = 0;
		while (Tk.type == Tk.LBRACKET) {
			Lexer.nextToken();
			if (Tk.type == Tk.RBRACKET) Lexer.nextToken();
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


	static void pParams() {
		Lexer.expect(Tk.LPAREN);
		while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
			int pType = pTypeLoc();
			int pNm = C.intern(Tk.strBuf, Tk.strLen);
			Lexer.nextToken();
			aLoc(pNm, pType);
			if (Tk.type == Tk.COMMA) Lexer.nextToken();
		}
		Lexer.expect(Tk.RPAREN);
	}

	// Bytecode emission shortcuts
	static void ic0() { eb(0x03); push(); } // ICONST_0
	static void ic1() { eb(0x04); push(); } // ICONST_1
	static void edup() { eb(0x59); push(); } // DUP
	static void cmpBool(int op) {
		int lbl = label(); int lblEnd = label();
		eBr(op, lbl); ic0(); eBr(GOTO, lblEnd);
		mark(lbl); ic1(); mark(lblEnd);
	}
	static void eOp(int op, int cp) { eb(op); eSBE(cp); }
	static void epop() { eb(0x57); pop(); } // POP
	static void ethis() { eLd(0, 1); push(); } // ALOAD_0 this
	static void eALd(int t) { eb(t==4 ? 0x33 : t==5 ? 0x34 : 0x2E); } // BALOAD/CALOAD/IALOAD
	static void eASt(int t) { eb(t==4 ? 0x54 : t==5 ? 0x55 : 0x4F); } // BASTORE/CASTORE/IASTORE
	static void pushLp(int brk, int cont) {
		C.lpBrkLbl[C.lpDepth] = brk;
		C.lpContLbl[C.lpDepth] = cont;
		C.lpDepth++;
	}
	static void popLp() { C.lpDepth--; }

	static void eb(int b) {
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
		return C.lblCount++;
	}

	static void mark(int lbl) {
		C.lblAddr[lbl] = C.mcLen;
	}

	static void eBr(int opcode, int label) {
		int branchPC = C.mcLen;
		eb(opcode);
		int loc = eBrPH();
		C.patLoc[C.patC] = loc;
		C.patLbl[C.patC] = label;
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
		C.locName[C.locCount] = nm;
		C.locSlot[C.locCount] = C.locCount;
		C.locType[C.locCount] = type;
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
		// Check for existing entry with same value
		for (int i = 0; i < C.cpMCount; i++) {
			if (C.cpMVals[i] == resolvedVal && C.cpMKeys[i] == resolvedVal) {
				return i;
			}
		}
		int idx = C.cpMCount++;
		C.cpMVals[idx] = resolvedVal;
		C.cpMKeys[idx] = resolvedVal;
		return idx;
	}

	static int aFCP(int fieldSlotVal) {
		return aCP(fieldSlotVal);
	}

	static int aCCP(int classId) {
		return aCP(classId);
	}

	static int aSCP(byte[] buf, int len) {
		int strIdx = -1;
		for (int i = 0; i < C.strCC; i++) {
			if (C.strCLen[i] == len && Native.memcmp(C.strC[i], 0, buf, 0, len) == 0) {
				strIdx = i; break;
			}
		}
		if (strIdx < 0) {
			strIdx = C.strCC++;
			C.strC[strIdx] = new byte[len];
			Native.arraycopy(buf, 0, C.strC[strIdx], 0, len);
			C.strCLen[strIdx] = len;
		}
		return aCP(0x80 | strIdx);
	}

	static int aICP(int val) {
		// Find or add integer constant
		int idx = -1;
		for (int i = 0; i < C.intCC; i++) {
			if (C.intC[i] == val) { idx = i; break; }
		}
		if (idx < 0) {
			idx = C.intCC++;
			C.intC[idx] = val;
		}
		return aCP(idx);
	}


	static void eIC(int val) {
		if (val >= -1 && val <= 5) {
			eb(0x03 + val); // ICONST_M1=0x02 .. ICONST_5=0x08
		} else if (val >= -128 && val <= 127) {
			eb(0x10); // BIPUSH
			eb(val & 0xFF);
		} else if (val >= -32768 && val <= 32767) {
			eb(0x11); // SIPUSH
			eSBE(val);
		} else {
			// LDC with integer constant
			int cpIdx = aICP(val);
			eb(0x12); // LDC
			eb(cpIdx);
		}
	}

	static void eLd(int slot, int type) {
		if (type != 0) {
			// Reference (type 1=ref, 3=int[], 4=byte[], 5=char[])
			if (slot <= 3) eb(0x2A + slot); // ALOAD_0..3
			else { eb(0x19); eb(slot); } // ALOAD
		} else {
			// Int
			if (slot <= 3) eb(0x1A + slot); // ILOAD_0..3
			else { eb(0x15); eb(slot); } // ILOAD
		}
	}

	static void eSt(int slot, int type) {
		if (type != 0) {
			// Reference (type 1=ref, 3=int[], 4=byte[], 5=char[])
			if (slot <= 3) eb(0x4B + slot); // ASTORE_0..3
			else { eb(0x3A); eb(slot); } // ASTORE
		} else {
			if (slot <= 3) eb(0x3B + slot); // ISTORE_0..3
			else { eb(0x36); eb(slot); } // ISTORE
		}
	}

	static void eCO(int tok) {
		if (tok == Tk.PLUS_EQ) eb(0x60); // IADD
		else if (tok == Tk.MINUS_EQ) eb(0x64); // ISUB
		else if (tok == Tk.STAR_EQ) eb(0x68); // IMUL
		else if (tok == Tk.SLASH_EQ) eb(0x6C); // IDIV
		else if (tok == Tk.PERCENT_EQ) eb(0x70); // IREM
		else if (tok == Tk.AMP_EQ) eb(0x7E); // IAND
		else if (tok == Tk.PIPE_EQ) eb(0x80); // IOR
		else if (tok == Tk.CARET_EQ) eb(0x82); // IXOR
		else if (tok == Tk.SHL_EQ) eb(0x78); // ISHL
		else if (tok == Tk.SHR_EQ) eb(0x7A); // ISHR
		else if (tok == Tk.USHR_EQ) eb(0x7C); // IUSHR
	}

	// Patch 4-byte big-endian value at mcode[loc]
	static void pBE(int loc, int v) {
		C.mcode[loc] = (byte)((v >> 24) & 0xFF);
		C.mcode[loc + 1] = (byte)((v >> 16) & 0xFF);
		C.mcode[loc + 2] = (byte)((v >> 8) & 0xFF);
		C.mcode[loc + 3] = (byte)(v & 0xFF);
	}
}
