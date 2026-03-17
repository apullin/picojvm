public class Catalog {
	static final int MAX_IMPORTS = 32;
	// Compilation-unit scope: one current package plus explicit single-type imports.
	static int curPkgNm = -1;
	static int[] impSimple = new int[MAX_IMPORTS];
	static int[] impFull = new int[MAX_IMPORTS];
	static int impCount;

	static void resetUnitScope() {
		curPkgNm = -1;
		impCount = 0;
	}

	static void catalog() {
		C.uClsStart = C.cCount;
		resetUnitScope();
		while (Tk.type != Tk.EOF) {
			scanUnitScope();
			if (Tk.type == Tk.EOF) break;
			catClass();
		}
	}

	static boolean isBuiltinType(int nm) {
		return nm == C.N_OBJECT || nm == C.N_STRING || nm == C.N_NATIVE ||
			   nm == C.N_THROWABLE || nm == C.N_EXCEPTION || nm == C.N_RUNTIME_EX;
	}

	static int resolveTypeNm(int nm) {
		if (isBuiltinType(nm)) return nm;
		for (int i = impCount - 1; i >= 0; i--) {
			if (impSimple[i] == nm) return impFull[i];
		}
		if (curPkgNm >= 0) return C.dotNm(curPkgNm, nm);
		return nm;
	}

	static int parseQName(boolean resolveSimple) {
		int nm = C.iN();
		boolean qualified = false;
		while (Tk.type == Tk.DOT) {
			Lexer.nextToken();
			nm = C.dotNm(nm, C.iN());
			qualified = true;
		}
		if (!qualified && resolveSimple) return resolveTypeNm(nm);
		return nm;
	}

	static int parseTypeNm() {
		return parseQName(true);
	}

	static void scanUnitScope() {
		while (Tk.type == Tk.PACKAGE || Tk.type == Tk.IMPORT) {
			if (Tk.type == Tk.PACKAGE) {
				Lexer.nextToken();
				curPkgNm = parseQName(false);
				impCount = 0;
				Lexer.expect(Tk.SEMI);
			} else {
				Lexer.nextToken();
				int fullNm = parseQName(false);
				if (impCount < MAX_IMPORTS) {
					impSimple[impCount] = C.tailNm(fullNm);
					impFull[impCount] = fullNm;
					impCount++;
				}
				Lexer.expect(Tk.SEMI);
			}
		}
	}

	static void catClass() {
		// Skip class-level annotations
		while (Tk.type == Tk.AT) {
			Lexer.nextToken(); // skip @
			Lexer.nextToken(); // skip name
			if (Tk.type == Tk.LPAREN) {
				Lexer.nextToken();
				while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) Lexer.nextToken();
				Lexer.expect(Tk.RPAREN);
			}
		}
		// Skip modifiers: public, abstract, final
		while (Tk.type == Tk.PUBLIC || Tk.type == Tk.ABSTRACT ||
			   Tk.type == Tk.FINAL) {
			Lexer.nextToken();
		}

		boolean isIface = false;
		boolean isEnum = false;
		if (Tk.type == Tk.INTERFACE) {
			isIface = true;
			Lexer.nextToken();
		} else if (Tk.type == Tk.ENUM) {
			isEnum = true;
			Lexer.nextToken();
		} else if (Tk.type == Tk.CLASS) {
			Lexer.nextToken();
		} else {
			Lexer.error(Tk.CLASS);
			return;
		}

		// Class/interface name
		int simpleNm = C.iN();
		int nm = curPkgNm >= 0 ? C.dotNm(curPkgNm, simpleNm) : simpleNm;

		int ci = C.initClass(nm);
		C.cSimple[ci] = (short)simpleNm;
		C.cIsIface[ci] = isIface;
		C.cIsEnum[ci] = isEnum;

		// extends?
		if (Tk.type == Tk.EXTENDS) {
			Lexer.nextToken();
			int parentNm = parseTypeNm();
			// Resolve parent later; store name for now
			C.cParent[ci] = (short)parentNm; // store as name index, resolve in Pass 2
		}

		// implements?
		if (Tk.type == Tk.IMPLEMENTS) {
			Lexer.nextToken();
			while (Tk.type == Tk.IDENT || Tk.type == Tk.STRING_KW) {
				int ifNm = parseTypeNm();
				C.chk(C.ifListLen, 64, 255);
				C.ifList[C.ifListLen++] = (byte)ifNm; // store as name, resolve later
				C.cIfaceC[ci]++;
				if (Tk.type == Tk.COMMA) Lexer.nextToken();
				else break;
			}
		}

		Lexer.expect(Tk.LBRACE);
		C.cBodyS[ci] = Lexer.pos;

		// Enum constants: NAME, NAME, NAME [;]
			if (isEnum) {
				int ordinal = 0;
				while (Tk.type == Tk.IDENT) {
					int cnm = C.intern(Tk.strBuf, Tk.strLen);
					int fi = initField(ci, cnm, true, true, 0, 0, -1, C.NK_NONE);
					C.fHasConst[fi] = true;
					C.fConstVal[fi] = ordinal++;
				Lexer.nextToken();
				if (Tk.type == Tk.COMMA) Lexer.nextToken();
				else break;
			}
			if (Tk.type == Tk.SEMI) Lexer.nextToken();
		}

		// Scan class body for fields and methods
		while (Tk.type != Tk.RBRACE && Tk.type != Tk.EOF) {
			catMember(ci);
		}
		C.cBodyE[ci] = Lexer.pos;
		Lexer.expect(Tk.RBRACE);
	}

	// Shared member-head parsing state (used by Catalog and Emit)
	static boolean mStat, mNat, mAbst, mFinal, mConst;

	// Parse member modifiers. Returns true if this is a static block.
	static boolean parseMods() {
		mStat = false; mNat = false; mAbst = false; mFinal = false; mConst = false;
		// Annotations: @Name or @Name(...)
		while (Tk.type == Tk.AT) {
			Lexer.nextToken(); // skip @
			int annNm = C.intern(Tk.strBuf, Tk.strLen);
			Lexer.nextToken(); // skip annotation name
			if (annNm == C.N_CONST) mConst = true;
			if (Tk.type == Tk.LPAREN) {
				Lexer.nextToken();
				while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) Lexer.nextToken();
				Lexer.expect(Tk.RPAREN);
			}
		}
		while (Tk.type == Tk.PUBLIC || Tk.type == Tk.PRIVATE ||
			   Tk.type == Tk.PROTECTED || Tk.type == Tk.STATIC ||
			   Tk.type == Tk.FINAL || Tk.type == Tk.NATIVE ||
			   Tk.type == Tk.ABSTRACT) {
			if (Tk.type == Tk.STATIC) mStat = true;
			if (Tk.type == Tk.NATIVE) mNat = true;
			if (Tk.type == Tk.ABSTRACT) mAbst = true;
			if (Tk.type == Tk.FINAL) mFinal = true;
			Lexer.nextToken();
		}
		return mStat && Tk.type == Tk.LBRACE;
	}

	// Check for constructor. Returns true if it is one (lexer at LPAREN).
	static boolean isCtor(int ci) {
		if (Tk.type != Tk.IDENT) return false;
		int nm = C.intern(Tk.strBuf, Tk.strLen);
		if (nm != C.cSimple[ci]) return false;
		Lexer.save();
		Lexer.nextToken();
		if (Tk.type == Tk.LPAREN) {
			Lexer.discardSave();
			return true;
		}
		Lexer.restore();
		Tk.type = Tk.IDENT;
		Tk.strLen = C.nLen[nm];
		Native.arraycopy(C.nPool, C.nOff[nm], Tk.strBuf, 0, Tk.strLen);
		return false;
	}

	static int scalarNarrow(int typeTok) {
		if (typeTok == Tk.BYTE) return C.NK_BYTE;
		if (typeTok == Tk.CHAR) return C.NK_CHAR;
		if (typeTok == Tk.SHORT) return C.NK_SHORT;
		return C.NK_NONE;
	}

	// Shared type scan state: cataloging and emit both read types through this view.
	static int tyBase = -1;   // -1=invalid, 0=void, 1=primitive int-like, 2=reference
	static int tyDims;
	static int tyRefNm = -1;
	static int tyNarrow = C.NK_NONE;
	static int tyArrKind;
	static int sigArgc;
	static boolean sigVarargs;
	static int sigFixedArgs;
	static boolean sigOneStringArray;

	static int primArrKind(int typeTok) {
		if (typeTok == Tk.BYTE || typeTok == Tk.BOOLEAN) return 4;
		if (typeTok == Tk.CHAR) return 5;
		if (typeTok == Tk.SHORT) return 8;
		return 0;
	}

	// Scan one declared type and leave its shape in tyBase/tyDims/tyRefNm/tyNarrow.
	static void scanTy(boolean allowVoid) {
		tyBase = -1;
		tyDims = 0;
		tyRefNm = -1;
		tyNarrow = C.NK_NONE;
		tyArrKind = 0;

		int typeTok = Tk.type;
		if (allowVoid && typeTok == Tk.VOID) {
			tyBase = 0;
			Lexer.nextToken();
			return;
		}
		if (typeTok == Tk.INT || typeTok == Tk.BYTE ||
			typeTok == Tk.CHAR || typeTok == Tk.SHORT ||
			typeTok == Tk.BOOLEAN) {
			tyBase = 1;
			tyNarrow = scalarNarrow(typeTok);
			tyArrKind = primArrKind(typeTok);
			Lexer.nextToken();
		} else if (typeTok == Tk.STRING_KW) {
			tyBase = 2;
			tyRefNm = C.N_STRING;
			Lexer.nextToken();
		} else if (typeTok == Tk.IDENT) {
			tyBase = 2;
			tyRefNm = parseTypeNm();
		} else {
			return;
		}

		while (Tk.type == Tk.LBRACKET) {
			Lexer.nextToken();
			Lexer.expect(Tk.RBRACKET);
			tyDims++;
		}
		if (tyDims > 0) {
			tyNarrow = C.NK_NONE;
			if (tyBase == 1) {
				if (tyDims > 1) {
					if (tyArrKind == 4) tyArrKind = 6;
					else if (tyArrKind == 5) tyArrKind = 7;
					else tyArrKind = 0;
				}
			} else if (tyDims > 1) {
				tyRefNm = -1;
			}
		}
	}

	static void catMember(int ci) {
		if (parseMods()) {
			// Static initializer block: static { ... }
			int mi;
			if (C.cClinit[ci] != 0xFF && C.mBodyS[C.cClinit[ci]] == -2) {
				mi = C.cClinit[ci];
			} else {
				mi = C.initMethod(ci, C.N_CLINIT, 0, true, false, false, 0);
				C.cClinit[ci] = (short)mi;
			}
			Lexer.nextToken(); // skip {
			C.mBodyS[mi] = Lexer.pos;
			skipBlk();
			C.mBodyE[mi] = Lexer.pos;
			Lexer.expect(Tk.RBRACE);
			return;
		}

		if (isCtor(ci)) {
			catMethod(ci, C.cName[ci], mStat, true, mNat, mAbst, 0, -1, C.NK_NONE);
			return;
		}

		// Parse return/field type once, then map it for methods or fields.
		int retType = 0;
		int retNarrow = C.NK_NONE;
		int fieldType = 0;
		int fieldNarrow = C.NK_NONE;
		int arrayKind = 0;
		int refNm = -1;

		scanTy(true);
		if (tyBase == 0) {
			retType = 0;
		} else if (tyBase == 1) {
			retType = tyDims == 0 ? 1 : 2;
			retNarrow = tyNarrow;
			fieldNarrow = tyNarrow;
			arrayKind = tyArrKind;
		} else if (tyBase == 2) {
			retType = 2;
			fieldType = tyDims == 1 ? 2 : 1;
			refNm = tyRefNm;
		} else {
			Native.putchar('T'); Lexer.printNum(Tk.type);
			Native.putchar('P'); Lexer.printNum(Lexer.pos);
			Native.putchar('K'); Lexer.printNum(Lexer.kwCount);
			Native.putchar('S'); Lexer.printNum(Tk.strLen);
			Native.putchar('[');
			for (int di = 0; di < Tk.strLen && di < 10; di++)
				Native.putchar(Tk.strBuf[di] & 0xFF);
			Native.putchar(']');
			Native.putchar('\n');
			Lexer.error(100);
			return;
		}

		int nm = C.iN();
		if (Tk.type == Tk.LPAREN) {
			catMethod(ci, nm, mStat, false, mNat, mAbst, retType, fieldType == 2 ? -1 : refNm, retNarrow);
		} else {
			catField(ci, nm, mStat, mFinal, fieldType, arrayKind, refNm, fieldNarrow);
		}
	}

	static int initField(int ci, int nm, boolean isStat, boolean isFinal,
					 int fieldType, int arrKind, int refNm, int narrowKind) {
		C.chk(C.fCount, C.MAX_FIELDS, 254);
		int fi = C.fCount++;
		C.fClass[fi] = (byte)ci; C.fName[fi] = (short)nm; C.fStatic[fi] = isStat;
		C.fType[fi] = (byte)fieldType;
		C.fNarrow[fi] = (byte)narrowKind;
		C.fArrKind[fi] = (byte)arrKind; C.fSlot[fi] = (short)-1;
		C.fRefNm[fi] = (short)refNm;
		C.fInitPos[fi] = -1; C.fInitLn[fi] = (short)0;
		C.fFinal[fi] = isFinal; C.fHasConst[fi] = false; C.fIsConst[fi] = false;
		if (!isStat) C.cOwnF[ci]++;
		return fi;
	}

	static void ensClinitFor(int ci) {
		if (C.cClinit[ci] == 0xFF) {
			int smi = C.initMethod(ci, C.N_CLINIT, 0, true, false, false, 0);
			C.mBodyS[smi] = -2; C.mBodyE[smi] = -2;
			C.cClinit[ci] = (short)smi;
		}
	}

	static void catField(int ci, int nm, boolean isStat, boolean isFinal,
						   int fieldType, int arrKind, int refNm, int narrowKind) {
		int fi = initField(ci, nm, isStat, isFinal, fieldType, arrKind, refNm, narrowKind);
		if (mConst) C.fIsConst[fi] = true;

		if (Tk.type == Tk.ASSIGN && isStat) {
			C.fInitPos[fi] = Lexer.pos;
			C.fInitLn[fi] = (short)Lexer.line;
			if (isFinal) extractConst(fi);
			ensClinitFor(ci);
		}

		while (Tk.type != Tk.SEMI && Tk.type != Tk.EOF) {
				if (Tk.type == Tk.COMMA) {
					Lexer.nextToken();
					int nm2 = C.iN();
					int fi2 = initField(ci, nm2, isStat, isFinal, fieldType, arrKind, refNm, narrowKind);
					// Record initializer for comma-separated fields: static int A=0, B=1;
				if (Tk.type == Tk.ASSIGN && isStat) {
					C.fInitPos[fi2] = Lexer.pos;
					C.fInitLn[fi2] = (short)Lexer.line;
					if (isFinal) extractConst(fi2);
					ensClinitFor(ci);
				}
			} else {
				Lexer.nextToken();
			}
		}
		Lexer.expect(Tk.SEMI);
	}

	// Extract compile-time constant from simple literal initializer.
	// Lexer is positioned AT the '='. Peek ahead without consuming.
	static void extractConst(int fi) {
		Lexer.save();
		Lexer.nextToken(); // skip '='
		boolean neg = false;
		if (Tk.type == Tk.MINUS) { neg = true; Lexer.nextToken(); }
		if (Tk.type == Tk.INT_LIT) {
			C.fConstVal[fi] = neg ? -Tk.intValue : Tk.intValue;
			C.fHasConst[fi] = true;
		} else if (!neg && Tk.type == Tk.CHAR_LIT) {
			C.fConstVal[fi] = Tk.intValue;
			C.fHasConst[fi] = true;
		} else if (!neg && Tk.type == Tk.TRUE) {
			C.fConstVal[fi] = 1;
			C.fHasConst[fi] = true;
		} else if (!neg && Tk.type == Tk.FALSE) {
			C.fConstVal[fi] = 0;
			C.fHasConst[fi] = true;
		}
		Lexer.restore();
	}

	static void catMethod(int ci, int nm, boolean isStat, boolean isCtor,
							   boolean isNat, boolean isAbstract, int retType, int retRefNm, int retNarrow) {
		int mi = C.initMethod(ci, isCtor ? C.N_INIT : nm, 0, isStat, isCtor, isNat, retType);
		C.mRetNarrow[mi] = (byte)retNarrow;
		C.mRetRefNm[mi] = (short)retRefNm;

		scanParamShape(isStat);
		C.mArgC[mi] = (byte)sigArgc;
		if (!isCtor && isStat && nm == C.N_MAIN) C.mMainStrArgs[mi] = sigOneStringArray;
		if (sigVarargs) {
			C.mVarargs[mi] = true;
			C.mFixedArgs[mi] = (byte)sigFixedArgs;
		}

		if (isNat || isAbstract || C.cIsIface[ci]) {
			// Native/abstract/interface method: no body
			Lexer.expect(Tk.SEMI);
			C.mBodyS[mi] = -1;
			C.mBodyE[mi] = -1;
		} else {
			// Parse body
			Lexer.expect(Tk.LBRACE);
			C.mBodyS[mi] = Lexer.pos;
			skipBlk();
			C.mBodyE[mi] = Lexer.pos;
			Lexer.expect(Tk.RBRACE);
		}
	}

	static void skipTy() {
		// Skip a type declaration or method return type using the shared scanner.
		scanTy(true);
	}

	// Shared formal-parameter scan: used by cataloging and emit lookahead.
	static void scanParamShape(boolean isStat) {
		Lexer.expect(Tk.LPAREN);
		sigArgc = isStat ? 0 : 1;
		sigVarargs = false;
		sigFixedArgs = 0;
		sigOneStringArray = false;
		int userArgc = 0;
		boolean firstIsStringArray = false;
		while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
			skipTy();
			boolean isStringArray = Catalog.tyBase == 2 && Catalog.tyRefNm == C.N_STRING &&
									Catalog.tyDims == 1;
			if (Tk.type == Tk.ELLIPSIS) {
				sigVarargs = true;
				sigFixedArgs = sigArgc - (isStat ? 0 : 1);
				isStringArray = false;
				Lexer.nextToken();
			}
			Lexer.nextToken();
			if (userArgc == 0) firstIsStringArray = isStringArray;
			userArgc++;
			sigArgc++;
			if (Tk.type == Tk.COMMA) Lexer.nextToken();
		}
		Lexer.expect(Tk.RPAREN);
		sigOneStringArray = !sigVarargs && userArgc == 1 && firstIsStringArray;
		if (sigVarargs) {
			sigArgc = sigArgc - 1 + C.MAX_VA_SLOTS + 1;
		}
	}

	static int peekParamArgc(boolean isStat) {
		int savedTok = Tk.type;
		int savedLine = Tk.line;
		Lexer.save();
		scanParamShape(isStat);
		int argc = sigArgc;
		Lexer.restore();
		Tk.type = savedTok;
		Tk.line = savedLine;
		return argc;
	}

	static void skipBlk() {
		// Skip brace-balanced block content (we're just past the opening {)
		int depth = 1;
		while (depth > 0 && Tk.type != Tk.EOF) {
			if (Tk.type == Tk.LBRACE) depth++;
			else if (Tk.type == Tk.RBRACE) {
				depth--;
				if (depth == 0) return; // don't consume the closing }
			}
			Lexer.nextToken();
		}
	}

}
