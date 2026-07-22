public class Expr {
	// Returns: 0=void, 1=scalar, 2+=reference/array

	// Lvalue descriptor for unified assign/compound/inc-dec/load
	static int lvK;      // 0=local, 1=this_field, 2=static, 3=obj_field
	static int lvI;      // slot (local) or cpIdx (field)
	static int lvT;      // local type (0=int, 1=ref, 3+=array)
	static int lvN;      // scalar narrow kind (0=int-like, 1=byte, 2=char, 3=short, 4=boolean)
	static int lvArr;    // field array kind
	static int lvRefNm;  // declared ref type name, -1 if unknown/non-ref
	static boolean lvG;  // storage holds a heap reference (object/array)
	static boolean lvRV; // assign DUPs and returns value
		static int exprRefNm = -1; // declared ref type name for the last parsed ref expression (-2 = null literal)
	static int exprArrRefNm = -1; // element ref type when the last parsed expression is object[]
	static int exprNarrow = C.NK_NONE; // exact scalar kind when known, else int-like
	static boolean exprConst;
	static int exprConstVal;
	static short[] argSigStack = new short[C.MAX_CALL_ARGS * 8];
	static int argSigDepth;
	static short[] lastArgSig = new short[C.MAX_CALL_ARGS];
	static int lastArgSigC;

	// Expression result bookkeeping: the parser threads exact ref/narrow info forward.
	static void clearRefInfo() {
		exprRefNm = -1;
		exprArrRefNm = -1;
		exprNarrow = C.NK_NONE;
		exprConst = false;
	}

	static void setObjRef(int refNm) {
		exprRefNm = refNm;
		exprArrRefNm = -1;
		exprNarrow = C.NK_NONE;
		exprConst = false;
	}

	static void setObjArrayRef(int refNm) {
		exprRefNm = -1;
		exprArrRefNm = refNm;
		exprNarrow = C.NK_NONE;
		exprConst = false;
	}

	static void setScalarKind(int narrowKind) {
		exprRefNm = -1;
		exprArrRefNm = -1;
		exprNarrow = narrowKind;
		exprConst = false;
	}

	static void setConstInt(int val, int narrowKind) {
		exprRefNm = -1;
		exprArrRefNm = -1;
		exprNarrow = narrowKind;
		exprConst = true;
		exprConstVal = val;
	}

	static boolean fitsNarrow(int val, int narrowKind) {
		if (narrowKind == C.NK_BYTE) return val >= -128 && val <= 127;
		if (narrowKind == C.NK_CHAR) return val >= 0 && val <= 65535;
		if (narrowKind == C.NK_SHORT) return val >= -32768 && val <= 32767;
		return true;
	}

	static boolean widensTo(int srcNarrow, int dstNarrow) {
		if (dstNarrow == srcNarrow) return true;
		if (dstNarrow == C.NK_SHORT && srcNarrow == C.NK_BYTE) return true;
		return false;
	}

	static void chkImplicitNarrow(int narrowKind) {
		if (exprNarrow == C.NK_BOOL) {
			if (narrowKind == C.NK_BOOL) return;
			Lexer.error(208);
		}
		if (narrowKind == C.NK_BOOL) Lexer.error(208);
		if (narrowKind == C.NK_NONE) return;
		if (exprConst && fitsNarrow(exprConstVal, narrowKind)) return;
		if (widensTo(exprNarrow, narrowKind)) return;
		Lexer.error(208);
	}

	static void chkStoreCompat(int exprType, int dstType, int dstRefNm, int dstNarrow) {
		int srcCi, dstCi;
		if (dstType == 0) {
			chkImplicitNarrow(dstNarrow);
			return;
		}
		if (dstType == 1) {
			if (exprType == 2) {
				if (exprRefNm == -2) return;
				if (exprArrRefNm >= 0) {
					if (dstRefNm < 0 || dstRefNm == C.N_OBJECT) return;
					Lexer.error(208);
				}
				if (dstRefNm < 0 || exprRefNm < 0 || exprRefNm == dstRefNm || dstRefNm == C.N_OBJECT) return;
				srcCi = Resolver.fClsByNm(exprRefNm);
				dstCi = Resolver.fClsByNm(dstRefNm);
				if (srcCi >= 0 && dstCi >= 0) {
					for (int ci = srcCi; ci >= 0; ci = C.cParent[ci]) {
						if (ci == dstCi) return;
					}
					if (C.cIsIface[dstCi] && Linker.hasInterface(srcCi, dstCi)) return;
				}
			} else if (exprType >= 3 && (dstRefNm < 0 || dstRefNm == C.N_OBJECT)) {
				return;
			}
			Lexer.error(208);
		}
		if (dstType == 2) {
			if (exprType == 2) {
				if (exprRefNm == -2) return;
				if (exprArrRefNm >= 0) {
					if (dstRefNm < 0 || exprArrRefNm < 0 || exprArrRefNm == dstRefNm || dstRefNm == C.N_OBJECT) return;
					srcCi = Resolver.fClsByNm(exprArrRefNm);
					dstCi = Resolver.fClsByNm(dstRefNm);
					if (srcCi >= 0 && dstCi >= 0) {
						for (int ci = srcCi; ci >= 0; ci = C.cParent[ci]) {
							if (ci == dstCi) return;
						}
						if (C.cIsIface[dstCi] && Linker.hasInterface(srcCi, dstCi)) return;
					}
				}
				if (exprRefNm < 0 && exprArrRefNm < 0) return;
			}
			Lexer.error(208);
		}
		if (exprType == dstType) return;
		if (exprType == 2 && exprRefNm == -2) return;
		if (exprType == 2 && exprRefNm < 0 && exprArrRefNm < 0) return;
		Lexer.error(208);
	}

	static int packVarargs(int mi, int argc) {
		if (!C.mVarargs[mi]) return argc == C.mArgC[mi] ? argc : -1;
		int minArgc = (C.mStatic[mi] ? 0 : 1) + C.mFixedArgs[mi];
		int vaCount = argc - minArgc;
		if (vaCount < 0 || vaCount > C.MAX_VA_SLOTS) return -1;
		for (int i = vaCount; i < C.MAX_VA_SLOTS; i++) {
			E.eIC(0);
			E.push();
			argc++;
		}
		E.eIC(vaCount);
		E.push();
		return argc + 1;
	}

	static int fInstFieldTarget(int recvNm, int fieldNm) {
		int ci = Resolver.fClsByNm(recvNm);
		if (ci < 0) return -1;
		return Resolver.fInstField(ci, fieldNm);
	}

	// Lvalue sites reuse the same descriptor shape across locals, statics, and fields.
	static void setLValue(int kind, int index, int type, int narrowKind, int arrKind, int refNm, boolean gcRef, boolean returnsValue) {
		lvK = kind;
		lvI = index;
		lvT = type;
		lvN = narrowKind;
		lvArr = arrKind;
		lvRefNm = refNm;
		lvG = gcRef;
		lvRV = returnsValue;
	}

	static int storedType(int type, int arrKind, int refNm, int narrowKind) {
		return fRetType(arrKind != 0 ? arrKind : type, arrKind != 0 ? -1 : refNm, narrowKind);
	}

	static int emitConstField(int fi) {
		E.eIC(C.fConstVal[fi]);
		E.push();
		setConstInt(C.fConstVal[fi], C.fNarrow[fi]);
		return 1;
	}

	static int fRetType(int t, int refNm, int narrowKind) {
		if (t == 2) {
			setObjArrayRef(refNm);
			return 2;
		}
		if (t >= 3) {
			clearRefInfo();
			return t;
		}
		if (t == 1) {
			setObjRef(refNm);
			return 2;
		}
		setScalarKind(narrowKind);
		return 1;
	}

	static int arrTypeCode(int arrType) {
		if (arrType == 4) return 8;
		if (arrType == 5) return 5;
		if (arrType == 8) return 9;
		if (arrType == 9) return 4;
		return 10;
	}

	static int arrNarrow(int arrType) {
		if (arrType == 4) return C.NK_BYTE;
		if (arrType == 5) return C.NK_CHAR;
		if (arrType == 8) return C.NK_SHORT;
		if (arrType == 9) return C.NK_BOOL;
		return C.NK_NONE;
	}

	static int primArrKind(int elemTok) {
		if (elemTok == Tk.BYTE) return 4;
		if (elemTok == Tk.BOOLEAN) return 9;
		if (elemTok == Tk.CHAR) return 5;
		if (elemTok == Tk.SHORT) return 8;
		return 3;
	}

	static int newArrayTypeCode(int elemTok) {
		if (elemTok == Tk.BYTE) return 8;
		if (elemTok == Tk.CHAR) return 5;
		if (elemTok == Tk.SHORT) return 9;
		if (elemTok == Tk.BOOLEAN) return 4;
		return 10;
	}

	// Count top-level elements so array creation can emit its size before the stores.
	static int countArrayInitElems() {
		int savedType = Tk.type, savedLine = Tk.line, savedInt = Tk.intValue, savedLen = Tk.strLen;
		Lexer.save();
		int count = 0;
		int depth = 0;
		Lexer.nextToken();
		if (Tk.type != Tk.RBRACE) {
			count = 1;
			while (Tk.type != Tk.EOF) {
				if (Tk.type == Tk.LPAREN || Tk.type == Tk.LBRACKET || Tk.type == Tk.LBRACE) depth++;
				else if (Tk.type == Tk.RPAREN || Tk.type == Tk.RBRACKET) depth--;
				else if (Tk.type == Tk.RBRACE) {
					if (depth == 0) break;
					depth--;
				} else if (Tk.type == Tk.COMMA && depth == 0) {
					count++;
				}
				Lexer.nextToken();
			}
		}
		Lexer.restore();
		Tk.type = savedType;
		Tk.line = savedLine;
		Tk.intValue = savedInt;
		Tk.strLen = savedLen;
		return count;
	}

	// Typed array literals share one path for local declarations, static init, and new T[]{...}.
	static int pArrayInit(int arrType, int refNm) {
		int count = countArrayInitElems();
		E.eIC(count);
		E.push();
		if (arrType == 2) {
			int ci = Resolver.fClsByNm(refNm);
			if (ci < 0 && !Catalog.isBuiltinType(refNm)) Lexer.error(202);
			E.eOp(E.ANEWARRAY, E.aCP(ci >= 0 ? ci : 0));
		} else {
			E.eb(E.NEWARRAY);
			E.eb(arrTypeCode(arrType));
		}

		Lexer.expect(Tk.LBRACE);
			for (int i = 0; Tk.type != Tk.RBRACE && Tk.type != Tk.EOF; i++) {
				E.edup();
				E.eIC(i);
				E.push();
				int elemType = pExpr();
				if (elemType == 0) Lexer.error(210); // array element initializer needs a value
				if (arrType == 2) {
					chkStoreCompat(elemType, 1, refNm, C.NK_NONE);
				} else {
					int narrowKind = arrNarrow(arrType);
					chkImplicitNarrow(narrowKind);
					E.eNarrow(narrowKind);
				}
				E.eASt(arrType);
				E.pop(); E.pop(); E.pop();
				if (Tk.type == Tk.COMMA) Lexer.nextToken();
			}
		Lexer.expect(Tk.RBRACE);
		if (arrType == 2) setObjArrayRef(refNm);
		else clearRefInfo();
		return arrType;
	}

	static int pTypedInit(int type, int refNm) {
		if (Tk.type == Tk.LBRACE && (type == 2 || type == 3 || type == 4 || type == 5 || type == 8 || type == 9)) {
			return pArrayInit(type, refNm);
		}
		type = pExpr();
		if (type == 0) Lexer.error(210); // value required here
		return type;
	}

	static int pExpr() {
		return pTern();
	}

	static int pTern() {
		int type = pBin(1);
		if (Tk.type == Tk.QUESTION) {
			if (type == 0) Lexer.error(210); // ternary condition needs a value
			if (type != 1 || exprNarrow != C.NK_BOOL) Lexer.error(211);
			Lexer.nextToken();
			E.pop();
			int lblFalse = E.label();
			int lblEnd = E.label();
			E.eBr(E.IFEQ, lblFalse); // IFEQ → false
			int tType = pExpr();
			if (tType == 0) Lexer.error(210); // ternary arm needs a value
			int tRefNm = exprRefNm;
			int tArrRefNm = exprArrRefNm;
			int tNarrow = exprNarrow;
			E.eBr(E.GOTO, lblEnd); // GOTO end
			E.pop();
			Lexer.expect(Tk.COLON);
			E.mark(lblFalse);
			int fType = pExpr();
			if (fType == 0) Lexer.error(210); // ternary arm needs a value
			int fRefNm = exprRefNm;
			int fArrRefNm = exprArrRefNm;
			int fNarrow = exprNarrow;
			E.mark(lblEnd);
			if (tType == 1 || fType == 1) {
				if (tType != 1 || fType != 1 ||
					(tNarrow == C.NK_BOOL) != (fNarrow == C.NK_BOOL)) Lexer.error(211);
				type = 1;
				setScalarKind(tNarrow == fNarrow ? tNarrow : C.NK_NONE);
			} else {
				short tSig;
				short fSig;
				if (tType == 2) {
					if (tArrRefNm >= 0) tSig = (short)(C.SIG_OBJ_ARRAY_BASE + tArrRefNm);
					else if (tRefNm == -2) tSig = C.SIG_NULL;
					else tSig = (short)(tRefNm >= 0 ? tRefNm : C.N_OBJECT);
				} else if (tType == 4) tSig = C.SIG_BYTE_ARR;
				else if (tType == 5) tSig = C.SIG_CHAR_ARR;
				else if (tType == 8) tSig = C.SIG_SHORT_ARR;
				else if (tType == 9) tSig = C.SIG_BOOL_ARR;
				else tSig = C.SIG_INT_ARR;
				if (fType == 2) {
					if (fArrRefNm >= 0) fSig = (short)(C.SIG_OBJ_ARRAY_BASE + fArrRefNm);
					else if (fRefNm == -2) fSig = C.SIG_NULL;
					else fSig = (short)(fRefNm >= 0 ? fRefNm : C.N_OBJECT);
				} else if (fType == 4) fSig = C.SIG_BYTE_ARR;
				else if (fType == 5) fSig = C.SIG_CHAR_ARR;
				else if (fType == 8) fSig = C.SIG_SHORT_ARR;
				else if (fType == 9) fSig = C.SIG_BOOL_ARR;
				else fSig = C.SIG_INT_ARR;
				boolean trueToFalse = Resolver.sigAssignable(tSig, fSig);
				boolean falseToTrue = Resolver.sigAssignable(fSig, tSig);
				if (!trueToFalse && !falseToTrue) Lexer.error(211);
				if (trueToFalse && !falseToTrue) {
					type = fType; exprRefNm = fRefNm; exprArrRefNm = fArrRefNm;
				} else {
					type = tType; exprRefNm = tRefNm; exprArrRefNm = tArrRefNm;
				}
				exprNarrow = C.NK_NONE;
				exprConst = false;
			}
		}
		return type;
	}

	static int binInfo(int tok) {
		if (tok == Tk.OR) return 0x100;
		if (tok == Tk.AND) return 0x200;
		if (tok == Tk.PIPE) return 0x380;    // IOR
		if (tok == Tk.CARET) return 0x482;   // IXOR
		if (tok == Tk.AMP) return 0x57E;     // IAND
		if (tok == Tk.EQ) return 0x69F;      // IF_ICMPEQ
		if (tok == Tk.NE) return 0x6A0;      // IF_ICMPNE
		if (tok == Tk.LT) return 0x7A1;      // IF_ICMPLT
		if (tok == Tk.GE) return 0x7A2;      // IF_ICMPGE
		if (tok == Tk.GT) return 0x7A3;      // IF_ICMPGT
		if (tok == Tk.LE) return 0x7A4;      // IF_ICMPLE
		if (tok == Tk.INSTANCEOF) return 0x7C1;
		if (tok == Tk.SHL) return 0x878;     // ISHL
		if (tok == Tk.SHR) return 0x87A;     // ISHR
		if (tok == Tk.USHR) return 0x87C;    // IUSHR
		if (tok == Tk.PLUS) return 0x960;    // IADD
		if (tok == Tk.MINUS) return 0x964;   // ISUB
		if (tok == Tk.STAR) return 0xA68;    // IMUL
		if (tok == Tk.SLASH) return 0xA6C;   // IDIV
		if (tok == Tk.PERCENT) return 0xA70; // IREM
		return -1;
	}

	static int pBin(int minPrec) {
		int type = pUnary();
		boolean stringBuild = false;
		while (true) {
			int info = binInfo(Tk.type);
			if (info < 0) break;
			int prec = info >> 8;
			if (prec < minPrec) break;
			int tok = Tk.type;
			if (stringBuild && tok != Tk.PLUS) {
				lastArgSigC = 0;
				int mi = Resolver.fCallTarget(C.N_STRING_BUILDER, C.N_TOSTRING, false, 1);
				if (mi < 0) Lexer.error(205);
				emitInvoke(mi, E.INVOKEVIRTUAL, 1);
				E.pop();
				E.push();
				setObjRef(C.N_STRING);
				type = 2;
				stringBuild = false;
				continue;
			}
			if (type == 0) Lexer.error(210); // binary operator operand needs a value
			int opcode = info & 0xFF;
			int lhsType = type;
			int lhsRefNm = exprRefNm;
			int lhsArrRefNm = exprArrRefNm;
			int lhsNarrow = exprNarrow;
			boolean lhsConst = exprConst;
			int lhsVal = exprConstVal;
			Lexer.nextToken();
			if (prec <= 2) {
				// Short-circuit: || (prec 1) or && (prec 2)
				int lbl1 = E.label(); int lbl2 = E.label();
				E.edup();
				E.eBr(prec == 1 ? E.IFNE : E.IFEQ, lbl1);
				E.pop(); E.epop();
				int rhsType = pBin(prec + 1);
				if (rhsType == 0) Lexer.error(210); // operator operand needs a value
				if (lhsType != 1 || lhsNarrow != C.NK_BOOL || rhsType != 1 || exprNarrow != C.NK_BOOL) Lexer.error(211); // &&/|| need boolean operands
				E.eBr(E.GOTO, lbl2);
				E.mark(lbl1); E.mark(lbl2);
				type = 1;
				setScalarKind(C.NK_BOOL);
			} else if (prec == 6) {
				// Equality: ==, !=
				int rtype = pBin(prec + 1);
				if (rtype == 0) Lexer.error(210); // operator operand needs a value
				boolean lhsRef = lhsType >= 2;
				boolean rhsRef = rtype >= 2;
				if (lhsRef || rhsRef) {
					if (!lhsRef || !rhsRef) Lexer.error(211); // don't mix scalar and reference equality
				} else if ((lhsType == 1 && lhsNarrow == C.NK_BOOL) != (rtype == 1 && exprNarrow == C.NK_BOOL)) {
					Lexer.error(211); // don't mix boolean and integer equality
				}
				E.pop(); E.pop();
				if (lhsRef) E.cmpBool(tok == Tk.EQ ? 0xA5 : 0xA6);
				else E.cmpBool(tok == Tk.EQ ? 0x9F : 0xA0);
				type = 1;
				setScalarKind(C.NK_BOOL);
			} else if (tok == Tk.INSTANCEOF) {
				if (lhsType < 2) Lexer.error(211); // instanceof needs a reference lhs
				int classNm = Catalog.parseTypeNm();
				E.pop();
				int ci = Resolver.fClsByNm(classNm);
				if (ci < 0 && !Catalog.isBuiltinType(classNm)) Lexer.error(202);
				int cpIdx = E.aCP(ci >= 0 ? ci : 0);
				E.eOp(E.INSTANCEOF, cpIdx); E.push();
				type = 1;
				setScalarKind(C.NK_BOOL);
			} else if (prec == 7) {
				// Comparison: <, >, <=, >=
				int rhsType = pBin(prec + 1);
				if (rhsType == 0) Lexer.error(210); // operator operand needs a value
				if (lhsType != 1 || lhsNarrow == C.NK_BOOL || rhsType != 1 || exprNarrow == C.NK_BOOL) Lexer.error(211); // ordering needs int-like scalars
				E.pop(); E.pop();
				E.cmpBool(opcode);
				type = 1;
				setScalarKind(C.NK_BOOL);
			} else {
				// Standard: |, ^, &, <<, >>, >>>, +, -, *, /, %
				int rhsType = pBin(prec + 1);
				if (rhsType == 0) Lexer.error(210); // operator operand needs a value
				int rhsRefNm = exprRefNm;
				int rhsArrRefNm = exprArrRefNm;
				int rhsNarrow = exprNarrow;
				boolean lhsBool = lhsType == 1 && lhsNarrow == C.NK_BOOL;
				boolean rhsBool = rhsType == 1 && rhsNarrow == C.NK_BOOL;
				if (tok == Tk.PLUS && (stringBuild || (lhsType == 2 && lhsRefNm == C.N_STRING) || (rhsType == 2 && rhsRefNm == C.N_STRING))) {
					int lhsSlot = -1, rhsSlot = -1;
					if (!stringBuild) {
						boolean rref = rhsType != 1;
						int rnm = C.iStr(rref ? "$sbr1" : "$sbi1");
						int rli = E.fLoc(rnm);
						if (rli < 0) { E.aLoc(rnm, rref ? 1 : 0, rref ? C.N_OBJECT : -1, C.NK_NONE); rli = E.fLoc(rnm); }
						rhsSlot = C.locSlot[rli];
						E.eSt(rhsSlot, rref ? 1 : 0); E.pop();

						boolean lref = lhsType != 1;
						int lnm = C.iStr(lref ? "$sbr0" : "$sbi0");
						int lli = E.fLoc(lnm);
						if (lli < 0) { E.aLoc(lnm, lref ? 1 : 0, lref ? C.N_OBJECT : -1, C.NK_NONE); lli = E.fLoc(lnm); }
						lhsSlot = C.locSlot[lli];
						E.eSt(lhsSlot, lref ? 1 : 0); E.pop();

						int sbCi = Resolver.fClsByNm(C.N_STRING_BUILDER);
						if (sbCi < 0) Lexer.error(205);
						E.eOp(E.NEW, E.aCP(sbCi)); E.push(); E.edup();
						lastArgSigC = 0;
						Resolver.sigFromCatalog = false;
						int ctorMi = Resolver.fCtor(sbCi, 1);
						if (ctorMi < 0) Lexer.error(205);
						E.eOp(E.INVOKESPECIAL, E.aCP(ctorMi));
						E.pop();
					}
					int appendCount = stringBuild ? 1 : 2;
					for (int ai = 0; ai < appendCount; ai++) {
						int aType = (stringBuild || ai == 1) ? rhsType : lhsType;
						int aRef = (stringBuild || ai == 1) ? rhsRefNm : lhsRefNm;
						int aArr = (stringBuild || ai == 1) ? rhsArrRefNm : lhsArrRefNm;
						int aNarrow = (stringBuild || ai == 1) ? rhsNarrow : lhsNarrow;
						if (!stringBuild) {
							int slot = ai == 0 ? lhsSlot : rhsSlot;
							E.eLd(slot, aType != 1 ? 1 : 0);
							E.push();
						}
						if (aType == 1) {
							if (aNarrow == C.NK_BYTE) lastArgSig[0] = C.SIG_BYTE;
							else if (aNarrow == C.NK_CHAR) lastArgSig[0] = C.SIG_CHAR;
							else if (aNarrow == C.NK_SHORT) lastArgSig[0] = C.SIG_SHORT;
							else if (aNarrow == C.NK_BOOL) lastArgSig[0] = C.SIG_BOOL;
							else lastArgSig[0] = C.SIG_INT;
						} else if (aType == 2) {
							if (aArr >= 0) lastArgSig[0] = (short)(C.SIG_OBJ_ARRAY_BASE + aArr);
							else if (aRef == -2) lastArgSig[0] = C.SIG_NULL;
							else lastArgSig[0] = (short)(aRef >= 0 ? aRef : C.N_OBJECT);
						} else if (aType == 4) lastArgSig[0] = C.SIG_BYTE_ARR;
						else if (aType == 5) lastArgSig[0] = C.SIG_CHAR_ARR;
						else if (aType == 8) lastArgSig[0] = C.SIG_SHORT_ARR;
						else if (aType == 9) lastArgSig[0] = C.SIG_BOOL_ARR;
						else lastArgSig[0] = C.SIG_INT_ARR;
						lastArgSigC = 1;
						int ami = Resolver.fCallTarget(C.N_STRING_BUILDER, C.N_APPEND, false, 2);
						if (ami < 0) Lexer.error(205);
						emitInvoke(ami, E.INVOKEVIRTUAL, 2);
						E.pop(); E.pop(); E.push();
					}
					stringBuild = true;
					type = 2;
					setObjRef(C.N_STRING_BUILDER);
				} else if (tok == Tk.PIPE || tok == Tk.CARET || tok == Tk.AMP) {
					if (lhsType != 1 || rhsType != 1) Lexer.error(211); // bitwise ops need scalar operands
					if (lhsBool != rhsBool) Lexer.error(211); // don't mix boolean and integer bitwise ops
					E.pop();
					E.eb(opcode);
					if (lhsConst && exprConst) {
						int rhsVal = exprConstVal;
						if (tok == Tk.PIPE) setConstInt(lhsVal | rhsVal, lhsBool ? C.NK_BOOL : C.NK_NONE);
						else if (tok == Tk.CARET) setConstInt(lhsVal ^ rhsVal, lhsBool ? C.NK_BOOL : C.NK_NONE);
						else if (tok == Tk.AMP) setConstInt(lhsVal & rhsVal, lhsBool ? C.NK_BOOL : C.NK_NONE);
						else clearRefInfo();
					} else if (lhsBool && rhsBool) setScalarKind(C.NK_BOOL);
					else setScalarKind(C.NK_NONE);
				} else {
					if (lhsType != 1 || lhsNarrow == C.NK_BOOL || rhsType != 1 || rhsNarrow == C.NK_BOOL) {
						Lexer.error(211); // arithmetic ops need int-like scalars
					}
					E.pop();
					E.eb(opcode);
					if (lhsConst && exprConst) {
						int rhsVal = exprConstVal;
						if (tok == Tk.SHL) setConstInt(lhsVal << rhsVal, C.NK_NONE);
						else if (tok == Tk.SHR) setConstInt(lhsVal >> rhsVal, C.NK_NONE);
						else if (tok == Tk.USHR) setConstInt(lhsVal >>> rhsVal, C.NK_NONE);
						else if (tok == Tk.PLUS) setConstInt(lhsVal + rhsVal, C.NK_NONE);
						else if (tok == Tk.MINUS) setConstInt(lhsVal - rhsVal, C.NK_NONE);
						else if (tok == Tk.STAR) setConstInt(lhsVal * rhsVal, C.NK_NONE);
						else if (tok == Tk.SLASH && rhsVal != 0) setConstInt(lhsVal / rhsVal, C.NK_NONE);
						else if (tok == Tk.PERCENT && rhsVal != 0) setConstInt(lhsVal % rhsVal, C.NK_NONE);
						else clearRefInfo();
					} else setScalarKind(C.NK_NONE);
				}
			}
		}
		if (stringBuild) {
			lastArgSigC = 0;
			int mi = Resolver.fCallTarget(C.N_STRING_BUILDER, C.N_TOSTRING, false, 1);
			if (mi < 0) Lexer.error(205);
			emitInvoke(mi, E.INVOKEVIRTUAL, 1);
			E.pop();
			E.push();
			setObjRef(C.N_STRING);
			type = 2;
		}
		return type;
	}

	static int pUnary() {
		if (Tk.type == Tk.MINUS) {
			Lexer.nextToken();
			if (Tk.type == Tk.INT_LIT) {
				// Negative literal
				Tk.intValue = -Tk.intValue;
				return pPrim();
			}
				int type = pUnary();
				if (type != 1 || exprNarrow == C.NK_BOOL) Lexer.error(211); // unary minus needs an int-like scalar
				E.eb(E.INEG);
				if (exprConst) {
					exprConstVal = -exprConstVal;
					exprNarrow = C.NK_NONE;
				} else setScalarKind(C.NK_NONE);
				return 1;
			}
		if (Tk.type == Tk.TILDE) {
			Lexer.nextToken();
			int type = pUnary();
			if (type != 1 || exprNarrow == C.NK_BOOL) Lexer.error(211); // bitwise complement needs an int-like scalar
			// ~x = x ^ (-1)
			E.eb(E.ICONST_0 - 1); // ICONST_M1
				E.push();
				E.eb(E.IXOR);
				E.pop();
				if (exprConst) {
					exprConstVal = ~exprConstVal;
					exprNarrow = C.NK_NONE;
				} else setScalarKind(C.NK_NONE);
				return 1;
			}
		if (Tk.type == Tk.BANG) {
			Lexer.nextToken();
				int type = pUnary();
				if (type != 1 || exprNarrow != C.NK_BOOL) Lexer.error(211); // logical not needs a boolean scalar
				E.pop();
				E.cmpBool(E.IFEQ); // IFEQ: !x
				if (exprConst) {
					exprConstVal = exprConstVal == 0 ? 1 : 0;
					exprNarrow = C.NK_BOOL;
				} else setScalarKind(C.NK_BOOL);
				return 1;
			}
		if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
			int op = Tk.type;
			Lexer.nextToken();
			int nm = C.iN();
			int li = E.fLoc(nm);
			if (li >= 0) {
				if (C.locType[li] != 0 || C.locNarrow[li] == C.NK_BOOL) Lexer.error(211); // ++/-- needs an int-like local
				int slot = C.locSlot[li];
				int narrowKind = C.locNarrow[li];
				// Pre-increment: load, add/sub 1, dup, store
				E.eLd(slot, 0);
				E.push();
				E.ic1();
				E.eb(op == Tk.INC ? E.IADD : E.ISUB);
				E.pop();
				E.eNarrow(narrowKind);
				E.edup();
				E.eSt(slot, 0);
				E.pop();
				setScalarKind(narrowKind);
				return 1;
			}
			Lexer.error(201);
			return 1;
		}
		if (Tk.type == Tk.LPAREN) {
			// Check for cast: (Type)expr
			Lexer.save();
			Lexer.nextToken();
			// Only a primitive type token or an identifier naming a known
			// class counts as a cast; "(x)" for a variable x must fall
			// through to the parenthesized-expression parse below.
			// (resolveTypeNm only qualifies the name - it never fails -
			// so the class catalog decides.)
			boolean isCast = Stmt.isTyTok(Tk.type);
			int castNm = -1;
			if (!isCast && Tk.type == Tk.IDENT) {
				castNm = Catalog.resolveTypeNm(C.intern(Tk.strBuf, Tk.strLen));
				isCast = Resolver.fClsByNm(castNm) >= 0 || Catalog.isBuiltinType(castNm);
			}
			if (isCast) {
				int castType = Tk.type;
				Lexer.nextToken();
				if (Tk.type == Tk.RPAREN) {
					// It's a cast
					Lexer.discardSave();
					Lexer.nextToken();
					pUnary();
					// Emit cast instruction
					if (castType == Tk.BYTE) {
						E.eb(E.I2B);
						if (exprConst) setConstInt((byte)exprConstVal, C.NK_BYTE);
						else setScalarKind(C.NK_BYTE);
					}
					else if (castType == Tk.CHAR) {
						E.eb(E.I2C);
						if (exprConst) setConstInt((char)exprConstVal, C.NK_CHAR);
						else setScalarKind(C.NK_CHAR);
					}
					else if (castType == Tk.SHORT) {
						E.eb(E.I2S);
						if (exprConst) setConstInt((short)exprConstVal, C.NK_SHORT);
						else setScalarKind(C.NK_SHORT);
					}
						else if (castType == Tk.IDENT && castNm >= 0) {
							int ci = Resolver.fClsByNm(castNm);
							int cpIdx = E.aCP(ci >= 0 ? ci : 0);
							E.eOp(E.CHECKCAST, cpIdx);
							setObjRef(castNm);
						}
						else clearRefInfo();
						return castType == Tk.IDENT ? 2 : 1;
					}
				// Not a cast, restore and parse as parenthesized expression
			}
			Lexer.restore();
			Lexer.nextToken(); // skip (
			int type = pExpr();
			Lexer.expect(Tk.RPAREN);
			return pPost(type);
		}
		return pPost(pPrim());
	}

	static int pPost(int type) {
		while (true) {
			if (Tk.type == Tk.DOT) {
				int recvRefNm = exprRefNm;
				int recvArrRefNm = exprArrRefNm;
				Lexer.nextToken();
				int memberNm = C.iN();

				if (memberNm == C.N_LENGTH && Tk.type != Tk.LPAREN) {
					// array.length
					if (type < 3 && (type != 2 || recvArrRefNm < 0 && recvRefNm != -1)) Lexer.error(211);
					E.eb(E.ARRAYLENGTH);
					type = 1;
					clearRefInfo();
				} else if (Tk.type == Tk.LPAREN) {
					// Method call on object
					if (recvRefNm < 0) { Lexer.error(205); return 0; }
					type = eCall(recvRefNm, memberNm, E.INVOKEVIRTUAL, 205);
				} else {
					// Field access
					type = eFldAcc(recvRefNm, memberNm);
				}
			}
				else if (Tk.type == Tk.LBRACKET) {
					// Array access
					int arrElemRefNm = exprArrRefNm;
					int arrRefNm = exprRefNm;
					if (type < 3 && (type != 2 || arrElemRefNm < 0 && arrRefNm != -1)) Lexer.error(211);
					Lexer.nextToken();
					int indexType = pExpr();
					if (indexType == 0) Lexer.error(210); // array index needs a value
					if (indexType != 1 || exprNarrow == C.NK_BOOL) Lexer.error(211);
					Lexer.expect(Tk.RBRACKET);
					E.pop(); // index

					// Check for store
					if (Tk.type == Tk.ASSIGN) {
						Lexer.nextToken();
						int rhsType = pExpr();
						if (rhsType == 0) Lexer.error(210); // assignment rhs needs a value
						if (type == 2 && arrElemRefNm >= 0) {
							chkStoreCompat(rhsType, 1, arrElemRefNm, C.NK_NONE);
						} else {
							int narrowKind = arrNarrow(type);
							chkImplicitNarrow(narrowKind);
							E.eNarrow(narrowKind);
						}
						E.pop(); E.pop();
						E.eASt(type);
						type = 0;
						clearRefInfo();
					} else if (Tk.type >= Tk.PLUS_EQ && Tk.type <= Tk.USHR_EQ) {
						if (type != 3 && type != 4 && type != 5 && type != 8) Lexer.error(211); // arithmetic update needs an int-like array element
						int op = Tk.type;
						int elemType = type;
						int narrowKind = arrNarrow(type);
						Lexer.nextToken();
						E.push(); // re-count idx
						E.eb(E.DUP2); E.push(); E.push();
						E.eALd(type);
						E.pop();
						int rhsType = pExpr();
						if (rhsType == 0) Lexer.error(210); // assignment rhs needs a value
						E.eCO(op); E.pop();
						E.eNarrow(narrowKind);
						E.eb(E.DUP_X2); E.push();
						E.eASt(type);
						E.pop(); E.pop(); E.pop();
						type = 1;
						if (elemType == 4) setScalarKind(C.NK_BYTE);
						else if (elemType == 5) setScalarKind(C.NK_CHAR);
						else if (elemType == 8) setScalarKind(C.NK_SHORT);
						else if (elemType == 9) setScalarKind(C.NK_BOOL);
						else clearRefInfo();
					} else if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
						if (type != 3 && type != 4 && type != 5 && type != 8) Lexer.error(211); // arithmetic update needs an int-like array element
						// Array element post-increment: arr[idx]++
						int op = Tk.type;
						Lexer.nextToken();
						E.push(); // re-count idx
						E.eb(E.DUP2); E.push(); E.push();
						E.eALd(type);
						E.pop();
						E.eb(E.DUP_X2); E.push();
						E.ic1();
						E.eb(op == Tk.INC ? E.IADD : E.ISUB); E.pop();
						E.eASt(type);
						E.pop(); E.pop(); E.pop();
						type = 1;
						clearRefInfo();
					} else {
						int elemType = type;
						E.eALd(type);
						// Propagate inner type for multi-dim arrays
						if (type == 6) type = 4;
						else if (type == 7) type = 5;
						else if (type == 2) {
							type = 2;
							setObjRef(arrElemRefNm);
						} else {
							type = 1;
							if (elemType == 4) setScalarKind(C.NK_BYTE);
							else if (elemType == 5) setScalarKind(C.NK_CHAR);
							else if (elemType == 8) setScalarKind(C.NK_SHORT);
							else if (elemType == 9) setScalarKind(C.NK_BOOL);
							else clearRefInfo();
						}
					}
				}
				else if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
					Lexer.error(211); // postfix ++/-- requires a supported lvalue
				}
				else if (Tk.type == Tk.ASSIGN ||
						 (Tk.type >= Tk.PLUS_EQ && Tk.type <= Tk.USHR_EQ)) {
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

	static int pPrim() {
		if (Tk.type == Tk.INT_LIT || Tk.type == Tk.CHAR_LIT) {
			int val = Tk.intValue;
			int narrowKind = Tk.type == Tk.CHAR_LIT ? C.NK_CHAR : C.NK_NONE;
			Lexer.nextToken();
			E.eIC(val);
			E.push();
			setConstInt(val, narrowKind);
			return 1;
		}
		if (Tk.type == Tk.STR_LIT) {
			byte[] buf = new byte[Tk.strLen];
			Native.arraycopy(Tk.strBuf, 0, buf, 0, Tk.strLen);
			int cpIdx = E.aSCP(buf, Tk.strLen);
			Lexer.nextToken();
			E.eLdc(cpIdx);
			E.push();
			setObjRef(C.N_STRING);
			return 2; // reference
		}
		if (Tk.type == Tk.TRUE) { Lexer.nextToken(); E.ic1(); setConstInt(1, C.NK_BOOL); return 1; }
		if (Tk.type == Tk.FALSE) { Lexer.nextToken(); E.ic0(); setConstInt(0, C.NK_BOOL); return 1; }
		if (Tk.type == Tk.NULL) {
			Lexer.nextToken();
			E.eb(E.ACONST_NULL);
			E.push();
			exprRefNm = -2;
			exprArrRefNm = -1;
			exprNarrow = C.NK_NONE;
			exprConst = false;
			return 2;
		}
		if (Tk.type == Tk.THIS) {
			Lexer.nextToken();
			E.eLd(0, 1); // ALOAD_0
			E.push();
			setObjRef(C.cName[C.curCi]);
			return 2;
		}
		if (Tk.type == Tk.SUPER) {
			if (C.curMStatic) { Lexer.error(205); return 0; }
			int parentCi = C.cParent[C.curCi];
			if (parentCi < 0) { Lexer.error(205); return 0; }
			int parentNm = C.cName[parentCi];
			Lexer.nextToken();
			Lexer.expect(Tk.DOT);
			int memberNm = C.iN();
			if (Tk.type == Tk.LPAREN) {
				E.ethis();
				return eCall(parentNm, memberNm, E.INVOKESPECIAL, 205);
			}
			int fi = fInstFieldTarget(parentNm, memberNm);
			if (fi < 0) { Lexer.error(206); return 0; }
			E.ethis();
			setLValue(3, E.aCP(C.fSlot[fi]), C.fType[fi], C.fNarrow[fi], C.fArrKind[fi], C.fRefNm[fi], C.fGcRef[fi], false);
			return lvOps();
		}
		if (Tk.type == Tk.NEW) {
			return pNew();
		}
		if (Tk.type == Tk.IDENT || Tk.type == Tk.STRING_KW) {
			int nm = C.iN();
			int classNm = Catalog.resolveTypeNm(nm);

			// Check for static method call or field access: Name.member
				if (Tk.type == Tk.DOT) {
					// Could be ClassName.method() or ClassName.field
					int ci = Resolver.fClsByNm(classNm);
					if (ci >= 0 || classNm == C.N_NATIVE || classNm == C.N_PJ_NATIVE ||
						classNm == C.N_STRING) {
						Lexer.nextToken();
						int memberNm = C.iN();

						if (Tk.type == Tk.LPAREN) {
							// Static method call
							return eCall(classNm, memberNm, E.INVOKESTATIC, 204);
						} else {
							// Static field access
							int fi = Resolver.fStatField(ci, memberNm);
							if (fi < 0) { Lexer.error(207); return 0; }
							if (C.fFinal[fi] && C.fHasConst[fi]) return emitConstField(fi);
							// ClassName.field — no return value on assign
							setLValue(2, E.aCP(C.fSlot[fi]), C.fType[fi], C.fNarrow[fi], C.fArrKind[fi], C.fRefNm[fi], C.fGcRef[fi], false);
							return lvOps();
						}
					}
				}

				// Check for local variable
				int li = E.fLoc(nm);
				if (li >= 0) {
					// Local var lvalue
					setLValue(0, C.locSlot[li], C.locType[li], C.locNarrow[li], 0, C.locRefNm[li], C.locType[li] != 0, true);
					return lvOps();
				}

				// Check for instance field (implicit this)
				if (!C.curMStatic) {
					int fi = Resolver.fField(C.curCi, nm);
					if (fi >= 0 && !C.fStatic[fi]) {
						// Implicit this.field lvalue
						setLValue(1, E.aCP(C.fSlot[fi]), C.fType[fi], C.fNarrow[fi], C.fArrKind[fi], C.fRefNm[fi], C.fGcRef[fi], false);
						return lvOps();
					}
				}

				// Check for static field (prefer current class first)
				{
					int fi = Resolver.fStatField(C.curCi, nm);
						if (fi >= 0) {
							if (C.fFinal[fi] && C.fHasConst[fi]) return emitConstField(fi);
						// Static field lvalue (in-class, returns value on assign)
						setLValue(2, E.aCP(C.fSlot[fi]), C.fType[fi], C.fNarrow[fi], C.fArrKind[fi], C.fRefNm[fi], C.fGcRef[fi], true);
						return lvOps();
					}
				}

				// Check for method call in same class
				if (Tk.type == Tk.LPAREN) {
					return eSelfCall(nm);
				}

				Lexer.error(202); // Undefined identifier
				clearRefInfo();
				return 0;
		}
		Lexer.error(203); // Unexpected token
		clearRefInfo();
		return 0;
	}

	// ==================== NEW EXPRESSION ====================

	static int pNew() {
		Lexer.nextToken(); // skip 'new'

		if (Tk.type == Tk.INT || Tk.type == Tk.BYTE ||
			Tk.type == Tk.CHAR || Tk.type == Tk.SHORT ||
			Tk.type == Tk.BOOLEAN) {
			// Primitive array: new int[size] or new int[]{...}
			int elemType = Tk.type;
			Lexer.nextToken();
			Lexer.expect(Tk.LBRACKET);
			if (Tk.type == Tk.RBRACKET) {
				Lexer.nextToken();
				if (Tk.type == Tk.LBRACE) {
					return pArrayInit(primArrKind(elemType), -1);
				}
				Lexer.error(203);
				clearRefInfo();
				return 0;
			}
			int sizeType = pExpr();
			if (sizeType == 0) Lexer.error(210); // array size needs a value
			if (sizeType != 1 || exprNarrow == C.NK_BOOL) Lexer.error(211);
			Lexer.expect(Tk.RBRACKET);

			int arrType = primArrKind(elemType);
			int typeCode = newArrayTypeCode(elemType);

			// Check for 2D array: new type[N][M] or new type[N][]
			if (Tk.type == Tk.LBRACKET) {
				Lexer.nextToken();
				if (Tk.type == Tk.RBRACKET) {
					// new type[N][] — array of references, size N
					Lexer.nextToken();
					int cpIdx = E.aCP(0);
					E.eOp(E.ANEWARRAY, cpIdx);
					clearRefInfo();
					return 2; // reference array
				}
				int sizeType2 = pExpr();
				if (sizeType2 == 0) Lexer.error(210); // array size needs a value
				if (sizeType2 != 1 || exprNarrow == C.NK_BOOL) Lexer.error(211);
				Lexer.expect(Tk.RBRACKET);
				int cpIdx = E.aCP((2 << 8) | typeCode);
				E.eOp(E.MULTIANEWARRAY, cpIdx);
				E.eb(2); // 2 dimensions
				E.pop(); // second dimension
				// First dim still on stack, result replaces it
				clearRefInfo();
				return 2;
			}

			E.eb(E.NEWARRAY);
			E.eb(typeCode);
			// Stack: count consumed, array ref pushed = net 0
			// Return specific array type for proper BALOAD/BASTORE emission
			clearRefInfo();
			return arrType;
		}

		// Object or reference array: new ClassName(...), new ClassName[size], new ClassName[]{...}
		int classNm = Catalog.parseTypeNm();

		if (Tk.type == Tk.LBRACKET) {
			// Reference array: new ClassName[size] or new ClassName[]{...}
			Lexer.nextToken();
			if (Tk.type == Tk.RBRACKET) {
				Lexer.nextToken();
				if (Tk.type == Tk.LBRACE) {
					return pArrayInit(2, classNm);
				}
				Lexer.error(203);
				clearRefInfo();
				return 0;
			}
			int refSizeType = pExpr();
			if (refSizeType == 0) Lexer.error(210); // array size needs a value
			if (refSizeType != 1 || exprNarrow == C.NK_BOOL) Lexer.error(211);
			Lexer.expect(Tk.RBRACKET);

			int ci = Resolver.fClsByNm(classNm);
			if (ci < 0 && !Catalog.isBuiltinType(classNm)) Lexer.error(202);
			int cpIdx = E.aCP(ci >= 0 ? ci : 0);

			// Check for 2D
			if (Tk.type == Tk.LBRACKET) {
				Lexer.nextToken();
				int sizeType2 = pExpr();
				if (sizeType2 == 0) Lexer.error(210); // array size needs a value
				if (sizeType2 != 1 || exprNarrow == C.NK_BOOL) Lexer.error(211);
				Lexer.expect(Tk.RBRACKET);
				// The runtime needs total dimensions even for reference leaves.
				cpIdx = E.aCP(2 << 8);
				E.eOp(E.MULTIANEWARRAY, cpIdx);
				E.eb(2);
				E.pop();
				clearRefInfo();
				return 2;
			}

			E.eOp(E.ANEWARRAY, cpIdx);
			setObjArrayRef(classNm);
			return 2;
		}

		// Object creation: new ClassName(args)
		int ci = Resolver.fClsByNm(classNm);
		if (ci < 0) { Lexer.error(202); return 0; }
		if (C.cIsIface[ci] || C.cAbstract[ci]) { Lexer.error(274); return 0; }
		int cpIdx = E.aCP(ci);
		E.eOp(E.NEW, cpIdx);
		E.push();
		E.edup();

		Lexer.expect(Tk.LPAREN);
		int argc = pArgs(1); // 'this' counts

		// Resolve only a matching constructor; unrelated fallbacks corrupt the stack.
		Resolver.sigFromCatalog = false;
		int ctorMi = Resolver.fCtor(ci, argc);
		if (ctorMi < 0) { Lexer.error(205); return 0; }

		int ctorCpIdx = E.aCP(ctorMi);
		E.eOp(E.INVOKESPECIAL, ctorCpIdx);
		// Pop args + dup from stack, keep original ref
		for (int i = 0; i < argc; i++) E.pop();

		setObjRef(classNm);
		return 2; // reference on stack
	}

	// ==================== METHOD CALLS ====================

	// Normalize stack bookkeeping and result typing after any invoke.
	static int eCallRet(int mi, int argc) {
		for (int j = 0; j < argc; j++) E.pop();
		int rt = C.mRetT[mi];
		if (rt == 2) {
			int arrKind = C.mRetNarrow[mi];
			if (arrKind == 2) setObjArrayRef(C.mRetRefNm[mi]);
			else if (arrKind != 0) clearRefInfo();
			else setObjRef(C.mRetRefNm[mi]);
			E.push();
			return arrKind != 0 ? arrKind : 2;
		}
		if (rt == 1) {
			setScalarKind(C.mRetNarrow[mi]);
			E.push();
			return 1;
		}
		clearRefInfo();
		return 0;
	}

	// Shared invoke emission keeps direct, virtual, and interface calls aligned.
	static void emitInvoke(int mi, int invokeKind, int argc) {
		int cpIdx = E.aCP(mi);
		if (invokeKind == E.INVOKESTATIC || invokeKind == E.INVOKESPECIAL) {
			E.eOp(invokeKind, cpIdx);
			return;
		}
		int mci = C.mClass[mi];
		if (!C.mNative[mi] && mci < C.cCount && C.cIsIface[mci]) {
			E.eOp(E.INVOKEINTERFACE, cpIdx);
			E.eb(argc);
			E.eb(0);
		} else {
			E.eOp(E.INVOKEVIRTUAL, cpIdx);
		}
	}

	// Unqualified calls inside a method prefer an instance target, then static.
	static int eSelfCall(int methodNm) {
		int ownerNm = C.cName[C.curCi];
		if (!C.curMStatic && Resolver.fMethodExact(C.curCi, methodNm, false, -1) >= 0) {
			E.ethis();
			return eCall(ownerNm, methodNm, E.INVOKEVIRTUAL, 205);
		}
		return eCall(ownerNm, methodNm, E.INVOKESTATIC, 204);
	}

	// Resolve, varargs-pack, emit, then fold the call back into expression typing.
	static int eCall(int ownerNm, int methodNm, int invokeKind, int errCode) {
		Lexer.expect(Tk.LPAREN);
		boolean isStatic = invokeKind == E.INVOKESTATIC;
		int argc = pArgs(isStatic ? 0 : 1);
		int mi = ownerNm >= 0 ? Resolver.fCallTarget(ownerNm, methodNm, isStatic, argc) : -1;
		argc = mi >= 0 ? packVarargs(mi, argc) : -1;
		if (mi < 0 || argc < 0) { Lexer.error(errCode); return 0; }
		emitInvoke(mi, invokeKind, argc);
		return eCallRet(mi, argc);
	}

	// ==================== LVALUE OPS ====================

	// Snapshot descriptors into locals — pExpr() may recurse into lvOps
	static int lvOps() {
		int k = lvK, i = lvI, t = lvT, n = lvN, arr = lvArr, refNm = lvRefNm; boolean g = lvG, rv = lvRV;
		if (Tk.type == Tk.ASSIGN) {
			Lexer.nextToken();
			if (k == 1) E.ethis();
			int rhsType = pExpr();
			if (rhsType == 0) Lexer.error(210); // assignment rhs needs a value
			chkStoreCompat(rhsType, arr != 0 ? arr : t, refNm, n);
			if (k == 0) { E.eNarrow(n); E.edup(); E.eSt(i, t); E.pop(); return fRetType(t, refNm, n); }
			if (k == 2) {
				E.eNarrow(n);
				if (rv) { E.edup(); E.eOp(E.PUTSTATIC, i); E.pop(); return storedType(t, arr, refNm, n); }
				E.eOp(E.PUTSTATIC, i); E.pop(); return 0;
			}
			E.eNarrow(n);
			E.eOp(E.PUTFIELD, i); E.pop(); E.pop(); return 0; // PUTFIELD
		}
		if (Tk.type >= Tk.PLUS_EQ && Tk.type <= Tk.USHR_EQ) {
			int op = Tk.type; Lexer.nextToken();
			if (g || n == C.NK_BOOL) Lexer.error(211); // compound assign needs an int-like scalar lvalue
			if (k == 0) {
				E.eLd(i, t); E.push();
				int rhsType = pExpr();
				if (rhsType == 0) Lexer.error(210); // assignment rhs needs a value
				E.eCO(op); E.pop();
				E.eNarrow(n); E.edup(); E.eSt(i, t); E.pop(); return fRetType(t, refNm, n);
			}
			if (k == 1) {
				E.ethis(); E.ethis(); E.eOp(E.GETFIELD, i);
				int rhsType = pExpr();
				if (rhsType == 0) Lexer.error(210); // assignment rhs needs a value
				E.eCO(op); E.pop();
				E.eNarrow(n);
				E.eOp(E.PUTFIELD, i); E.pop(); E.pop(); return 0;
			}
			if (k == 2) {
				E.eOp(E.GETSTATIC, i); E.push();
				int rhsType = pExpr();
				if (rhsType == 0) Lexer.error(210); // assignment rhs needs a value
				E.eCO(op); E.pop();
				E.eNarrow(n); E.edup(); E.eOp(E.PUTSTATIC, i); E.pop(); return storedType(t, arr, refNm, n);
			}
			// k == 3: obj already on stack
			E.edup(); E.eOp(E.GETFIELD, i);
			int rhsType = pExpr();
			if (rhsType == 0) Lexer.error(210); // assignment rhs needs a value
			E.eCO(op); E.pop();
			E.eNarrow(n);
			E.eOp(E.PUTFIELD, i); E.pop(); E.pop(); return 0;
		}
		if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
			int op = Tk.type; Lexer.nextToken();
			if (k == 3) Lexer.error(211); // explicit obj.field++/-- unsupported for now
			if (g || n == C.NK_BOOL) Lexer.error(211); // ++/-- needs an int-like scalar lvalue
			if (k == 0) {
				if (n != C.NK_NONE) {
					E.eLd(i, 0); E.push();
					E.edup(); E.push();
					E.ic1(); E.eb(op == Tk.INC ? E.IADD : E.ISUB); E.pop();
					E.eNarrow(n);
					E.eSt(i, 0); E.pop();
					return fRetType(t, refNm, n);
				}
				E.eLd(i, 0); E.push();
				E.eb(E.IINC); E.eb(i); E.eb(op == Tk.INC ? 1 : 0xFF); return fRetType(t, refNm, n);
			}
			if (k == 1) {
				E.ethis(); E.eOp(E.GETFIELD, i);
				E.ethis(); E.ethis(); E.eOp(E.GETFIELD, i);
				E.ic1(); E.eb(op == Tk.INC ? E.IADD : E.ISUB); E.pop();
				E.eNarrow(n);
				E.eOp(E.PUTFIELD, i); E.pop(); E.pop(); return fRetType(t, refNm, n);
			}
			// k == 2: GETSTATIC + DUP + 1 + op + PUTSTATIC
			E.eOp(E.GETSTATIC, i); E.push(); E.edup();
			E.ic1(); E.eb(op == Tk.INC ? E.IADD : E.ISUB); E.pop();
			E.eNarrow(n);
			E.eOp(E.PUTSTATIC, i); E.pop(); return storedType(t, arr, refNm, n);
			// k == 3: not supported, falls through to load
		}
		// Load
		if (k == 0) {
			E.eLd(i, t); E.push();
			return fRetType(t, refNm, n);
		}
		if (k == 1) { E.ethis(); E.eOp(E.GETFIELD, i); }
		else if (k == 2) { E.eOp(E.GETSTATIC, i); E.push(); }
		else { E.eOp(E.GETFIELD, i); }
		if (arr != 0) return fRetType(arr, -1, C.NK_NONE);
		return fRetType(t, refNm, n);
	}

	// Explicit obj.field lvalue (obj already on stack)
	static int eFldAcc(int recvNm, int fieldNm) {
		int fi = recvNm >= 0 ? fInstFieldTarget(recvNm, fieldNm) : -1;
		if (fi < 0) { Lexer.error(206); return 0; }
		setLValue(3, E.aCP(C.fSlot[fi]), C.fType[fi], C.fNarrow[fi], C.fArrKind[fi], C.fRefNm[fi], C.fGcRef[fi], false);
		return lvOps();
	}

	static int pArgs(int start) {
		int argc = start;
		int depth = argSigDepth++;
		if (depth >= 8) Lexer.error(257);
		int base = depth * C.MAX_CALL_ARGS;
		int sigC = 0;
		while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
			int argType = pExpr();
			if (argType == 0) Lexer.error(210); // call argument needs a value
			C.chk(sigC, C.MAX_CALL_ARGS, 257);
			short sc;
			if (argType == 1) {
				if (exprNarrow == C.NK_BYTE) sc = C.SIG_BYTE;
				else if (exprNarrow == C.NK_CHAR) sc = C.SIG_CHAR;
				else if (exprNarrow == C.NK_SHORT) sc = C.SIG_SHORT;
				else if (exprNarrow == C.NK_BOOL) sc = C.SIG_BOOL;
				else sc = C.SIG_INT;
			} else if (argType == 2) {
				if (exprArrRefNm >= 0) sc = (short)(C.SIG_OBJ_ARRAY_BASE + exprArrRefNm);
				else if (exprRefNm == -2) sc = C.SIG_NULL;
				else sc = (short)(exprRefNm >= 0 ? exprRefNm : C.N_OBJECT);
			} else if (argType == 4) sc = C.SIG_BYTE_ARR;
			else if (argType == 5) sc = C.SIG_CHAR_ARR;
			else if (argType == 8) sc = C.SIG_SHORT_ARR;
			else if (argType == 9) sc = C.SIG_BOOL_ARR;
			else sc = C.SIG_INT_ARR;
			argSigStack[base + sigC] = sc;
			sigC++;
			argc++;
			if (Tk.type == Tk.COMMA) Lexer.nextToken();
		}
		Lexer.expect(Tk.RPAREN);
		lastArgSigC = sigC;
		for (int i = 0; i < sigC; i++) lastArgSig[i] = argSigStack[base + i];
		argSigDepth--;
		return argc;
	}


}
