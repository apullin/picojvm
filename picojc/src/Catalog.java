public class Catalog {
	static void catalog() {
		C.uClsStart = C.cCount;
		while (Tk.type != Tk.EOF) {
			catClass();
		}
	}

	static void catClass() {
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
		int nm = C.iN();

		int ci = C.initClass(nm);
		C.cIsIface[ci] = isIface;
		C.cIsEnum[ci] = isEnum;

		// extends?
		if (Tk.type == Tk.EXTENDS) {
			Lexer.nextToken();
			int parentNm = C.iN();
			// Resolve parent later; store name for now
			C.cParent[ci] = (short)parentNm; // store as name index, resolve in Pass 2
		}

		// implements?
		if (Tk.type == Tk.IMPLEMENTS) {
			Lexer.nextToken();
			while (Tk.type == Tk.IDENT || Tk.type == Tk.STRING_KW) {
				int ifNm = C.intern(Tk.strBuf, Tk.strLen);
				C.chk(C.ifListLen, 64, 255);
				C.ifList[C.ifListLen++] = (byte)ifNm; // store as name, resolve later
				C.cIfaceC[ci]++;
				Lexer.nextToken();
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
					int fi = initField(ci, cnm, true, true, 0, 0, -1);
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
	static boolean mStat, mNat, mAbst, mFinal;

	// Parse member modifiers. Returns true if this is a static block.
	static boolean parseMods() {
		mStat = false; mNat = false; mAbst = false; mFinal = false;
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
		if (nm != C.cName[ci]) return false;
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
			catMethod(ci, C.cName[ci], mStat, true, mNat, mAbst, 0, -1);
			return;
		}

		// Parse return type
		int retType = 0;
		int fieldType = 0;
		int arrayKind = 0;
		int refNm = -1;
		int retTypeToken = Tk.type;

		if (Tk.type == Tk.VOID) { retType = 0; Lexer.nextToken(); }
		else if (Tk.type == Tk.INT || Tk.type == Tk.BYTE ||
				 Tk.type == Tk.CHAR || Tk.type == Tk.SHORT ||
				 Tk.type == Tk.BOOLEAN) {
			retType = 1;
			Lexer.nextToken();
			{
				int dimCount = 0;
				while (Tk.type == Tk.LBRACKET) {
					Lexer.nextToken();
					Lexer.expect(Tk.RBRACKET);
					dimCount++;
					if (retType == 1) {
						if (retTypeToken == Tk.BYTE || retTypeToken == Tk.BOOLEAN) arrayKind = 4;
						else if (retTypeToken == Tk.CHAR) arrayKind = 5;
						else if (retTypeToken == Tk.SHORT) arrayKind = 8;
					}
					retType = 2;
				}
				if (dimCount > 1) {
					if (arrayKind == 4) arrayKind = 6;
					else if (arrayKind == 5) arrayKind = 7;
					else arrayKind = 0;
				}
			}
		}
		else if (Tk.type == Tk.STRING_KW) {
			retType = 2;
			fieldType = 1;
			refNm = C.N_STRING;
			Lexer.nextToken();
			int dimCount = 0;
			while (Tk.type == Tk.LBRACKET) {
				Lexer.nextToken();
				Lexer.expect(Tk.RBRACKET);
				dimCount++;
			}
			if (dimCount == 1) fieldType = 2;
			else if (dimCount > 1) refNm = -1;
		}
		else if (Tk.type == Tk.IDENT) {
			retType = 2;
			fieldType = 1;
			refNm = C.iN();
			int dimCount = 0;
			while (Tk.type == Tk.LBRACKET) {
				Lexer.nextToken();
				Lexer.expect(Tk.RBRACKET);
				dimCount++;
			}
			if (dimCount == 1) fieldType = 2;
			else if (dimCount > 1) refNm = -1;
		}
		else {
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
			catMethod(ci, nm, mStat, false, mNat, mAbst, retType, fieldType == 2 ? -1 : refNm);
		} else {
			catField(ci, nm, mStat, mFinal, fieldType, arrayKind, refNm);
		}
	}

	static int initField(int ci, int nm, boolean isStat, boolean isFinal,
					 int fieldType, int arrKind, int refNm) {
		C.chk(C.fCount, C.MAX_FIELDS, 254);
		int fi = C.fCount++;
		C.fClass[fi] = (byte)ci; C.fName[fi] = (short)nm; C.fStatic[fi] = isStat;
		C.fType[fi] = (byte)fieldType;
		C.fArrKind[fi] = (byte)arrKind; C.fSlot[fi] = (short)-1;
		C.fRefNm[fi] = (short)refNm;
		C.fInitPos[fi] = -1; C.fInitLn[fi] = (short)0;
		C.fFinal[fi] = isFinal; C.fHasConst[fi] = false;
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
						   int fieldType, int arrKind, int refNm) {
		int fi = initField(ci, nm, isStat, isFinal, fieldType, arrKind, refNm);

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
					int fi2 = initField(ci, nm2, isStat, isFinal, fieldType, arrKind, refNm);
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
							   boolean isNat, boolean isAbstract, int retType, int retRefNm) {
		int mi = C.initMethod(ci, isCtor ? C.N_INIT : nm, 0, isStat, isCtor, isNat, retType);
		C.mRetRefNm[mi] = (short)retRefNm;

		// Parse parameters
		Lexer.expect(Tk.LPAREN);
		int argc = isStat ? 0 : 1; // instance methods have 'this' as arg 0
		boolean isVarargs = false;
		int fixedCount = 0;
		while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
			// Skip type
			skipTy();
			// Check for varargs: Type... name
			if (Tk.type == Tk.ELLIPSIS) {
				isVarargs = true;
				fixedCount = argc - (isStat ? 0 : 1);
				Lexer.nextToken(); // skip '...'
			}
			// Skip name
			Lexer.nextToken();
			argc++;
			if (Tk.type == Tk.COMMA) Lexer.nextToken();
		}
		Lexer.expect(Tk.RPAREN);
		if (isVarargs) {
			C.mVarargs[mi] = true;
			C.mFixedArgs[mi] = (byte)fixedCount;
			// Replace varargs param with MAX_VA_SLOTS + 1(count)
			argc = argc - 1 + C.MAX_VA_SLOTS + 1;
		}
		C.mArgC[mi] = (byte)argc;

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
		// Skip a type declaration (int, String, ClassName, arrays)
		Lexer.nextToken(); // consume type keyword/name
		while (Tk.type == Tk.LBRACKET) {
			Lexer.nextToken(); // [
			if (Tk.type == Tk.RBRACKET) Lexer.nextToken(); // ]
		}
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
