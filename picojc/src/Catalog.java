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
		int nm = C.intern(Tk.strBuf, Tk.strLen);
		Lexer.nextToken();

		int ci = C.initClass(nm);
		C.cIsIface[ci] = isIface;
		C.cIsEnum[ci] = isEnum;

		// extends?
		if (Tk.type == Tk.EXTENDS) {
			Lexer.nextToken();
			int parentNm = C.intern(Tk.strBuf, Tk.strLen);
			Lexer.nextToken();
			// Resolve parent later; store name for now
			C.cParent[ci] = (short)parentNm; // store as name index, resolve in Pass 2
		}

		// implements?
		if (Tk.type == Tk.IMPLEMENTS) {
			Lexer.nextToken();
			while (Tk.type == Tk.IDENT || Tk.type == Tk.STRING_KW) {
				int ifNm = C.intern(Tk.strBuf, Tk.strLen);
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
				int fi = initField(ci, cnm, true, true, 0);
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

	static void catMember(int ci) {
		// Collect modifiers
		boolean isStat = false;
		boolean isNat = false;
		boolean isAbstract = false;
		boolean isFinal = false;
		while (Tk.type == Tk.PUBLIC || Tk.type == Tk.PRIVATE ||
			   Tk.type == Tk.PROTECTED || Tk.type == Tk.STATIC ||
			   Tk.type == Tk.FINAL || Tk.type == Tk.NATIVE ||
			   Tk.type == Tk.ABSTRACT) {
			if (Tk.type == Tk.STATIC) isStat = true;
			if (Tk.type == Tk.NATIVE) isNat = true;
			if (Tk.type == Tk.ABSTRACT) isAbstract = true;
			if (Tk.type == Tk.FINAL) isFinal = true;
			Lexer.nextToken();
		}

		// Static initializer block: static { ... }
		if (isStat && Tk.type == Tk.LBRACE) {
			int mi;
			if (C.cClinit[ci] != 0xFF && C.mBodyS[C.cClinit[ci]] == -2) {
				// Reuse synthetic <clinit> (created for field initializers)
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

		// Return type or constructor
		int retType = 0; // 0=void, 1=int, 2=ref
		int arrayKind = 0; // 0=non-array, 4=byte[], 5=char[]
		boolean isCtor = false;
		int retTypeToken = Tk.type;

		// Check for constructor: ClassName(
		if (Tk.type == Tk.IDENT) {
			int nm = C.intern(Tk.strBuf, Tk.strLen);
			if (nm == C.cName[ci]) {
				// Might be constructor or field/method named same as class
				Lexer.save();
				Lexer.nextToken();
				if (Tk.type == Tk.LPAREN) {
					// It's a constructor
					Lexer.discardSave();
					isCtor = true;
					retType = 0; // void
					catMethod(ci, nm, isStat, isCtor, isNat, isAbstract, retType);
					return;
				}
				// Not a constructor, restore
				Lexer.restore();
				Tk.type = Tk.IDENT;
				Tk.strLen = C.nLen[nm];
				Native.arraycopy(C.nPool, C.nOff[nm], Tk.strBuf, 0, Tk.strLen);
			}
		}

		// Parse return type
		if (Tk.type == Tk.VOID) { retType = 0; Lexer.nextToken(); }
		else if (Tk.type == Tk.INT || Tk.type == Tk.BYTE ||
				 Tk.type == Tk.CHAR || Tk.type == Tk.SHORT ||
				 Tk.type == Tk.BOOLEAN) {
			retType = 1; // int-like
			Lexer.nextToken();
			// Check for array type (supports multi-dimensional)
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
		else if (Tk.type == Tk.STRING_KW || Tk.type == Tk.IDENT) {
			retType = 2; // reference
			Lexer.nextToken();
			// Check for array type (supports multi-dimensional)
			while (Tk.type == Tk.LBRACKET) {
				Lexer.nextToken();
				Lexer.expect(Tk.RBRACKET);
			}
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

		// Name
		int nm = C.intern(Tk.strBuf, Tk.strLen);
		Lexer.nextToken();

		// Method or field?
		if (Tk.type == Tk.LPAREN) {
			// Method
			catMethod(ci, nm, isStat, false, isNat, isAbstract, retType);
		} else {
			// Field
			catField(ci, nm, isStat, isFinal, retType, arrayKind);
		}
	}

	static int initField(int ci, int nm, boolean isStat, boolean isFinal, int arrKind) {
		int fi = C.fCount++;
		C.fClass[fi] = (byte)ci; C.fName[fi] = (short)nm; C.fStatic[fi] = isStat;
		C.fArrKind[fi] = (byte)arrKind; C.fSlot[fi] = (short)-1;
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
						   int retType, int arrKind) {
		int fi = initField(ci, nm, isStat, isFinal, arrKind);

		if (Tk.type == Tk.ASSIGN && isStat) {
			C.fInitPos[fi] = Lexer.pos;
			C.fInitLn[fi] = (short)Lexer.line;
			if (isFinal) extractConst(fi);
			ensClinitFor(ci);
		}

		while (Tk.type != Tk.SEMI && Tk.type != Tk.EOF) {
			if (Tk.type == Tk.COMMA) {
				Lexer.nextToken();
				int nm2 = C.intern(Tk.strBuf, Tk.strLen);
				Lexer.nextToken();
				int fi2 = initField(ci, nm2, isStat, isFinal, arrKind);
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
							   boolean isNat, boolean isAbstract, int retType) {
		int mi = C.initMethod(ci, isCtor ? C.N_INIT : nm, 0, isStat, isCtor, isNat, retType);

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
