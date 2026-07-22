public class Stmt {
	static boolean pBlock() {
		boolean completes = true;
		while (Tk.type != Tk.RBRACE && Tk.type != Tk.EOF) {
			boolean stmtCompletes = pStmt();
			if (completes) completes = stmtCompletes;
		}
		return completes;
	}

	static boolean pStmt() {
		if (Tk.type == Tk.LBRACE) {
			Lexer.nextToken();
			int savedLocalCount = C.locCount;
			boolean completes = pBlock();
			C.locCount = savedLocalCount; // restore scope
			Lexer.expect(Tk.RBRACE);
			return completes;
		}
		else if (Tk.type == Tk.IF) {
			return pIf();
		}
		else if (Tk.type == Tk.WHILE) {
			return pWhile();
		}
		else if (Tk.type == Tk.DO) {
			return pDoWhile();
		}
		else if (Tk.type == Tk.FOR) {
			return pFor();
		}
		else if (Tk.type == Tk.RETURN) {
			pRet();
			return false;
		}
		else if (Tk.type == Tk.BREAK) {
			Lexer.nextToken();
			Lexer.expect(Tk.SEMI);
			if (C.lpDepth <= 0) Lexer.error(268); // break outside loop/switch
			C.lpContOwn[C.lpDepth - 1] = (byte)(C.lpContOwn[C.lpDepth - 1] | 64);
			int target = C.lpBrkLbl[C.lpDepth - 1];
			for (int i = C.flowSwitchDepth - 1; i >= 0; i--) {
				if (C.flowSwitchEnd[i] == target) { C.flowSwitchBreak[i] = true; break; }
			}
			markTryEscape(C.lpDepth - 1);
			E.eBr(E.GOTO, target); // GOTO break
			return false;
		}
		else if (Tk.type == Tk.CONTINUE) {
			Lexer.nextToken();
			Lexer.expect(Tk.SEMI);
			if (C.lpDepth <= 0 || C.lpContLbl[C.lpDepth - 1] < 0) Lexer.error(268); // continue outside loop
			int owner = C.lpContOwn[C.lpDepth - 1] & 31;
			C.lpContOwn[owner] = (byte)(C.lpContOwn[owner] | 128);
			markTryEscape(owner);
			E.eBr(E.GOTO, C.lpContLbl[C.lpDepth - 1]); // GOTO continue
			return false;
		}
		else if (Tk.type == Tk.SWITCH) {
			return pSwitch();
		}
		else if (Tk.type == Tk.THROW) {
			pThrow();
			return false;
		}
		else if (Tk.type == Tk.TRY) {
			pTry();
			return true;
		}
		else if (isTyTok(Tk.type)) {
			pLocal();
			return true;
		}
		else if (Tk.type == Tk.IDENT) {
			// Could be local declaration (ClassName var) or expression statement
			// Peek ahead: if next is ident (or [ ]), it's a declaration
			Lexer.save();
			int nm = C.intern(Tk.strBuf, Tk.strLen);
			int savedType = Tk.type;
			Lexer.nextToken();
			if (Tk.type == Tk.IDENT ||
				(Tk.type == Tk.LBRACKET &&
				 Resolver.fClsByNm(Catalog.resolveTypeNm(nm)) >= 0)) {
				// It's a type name followed by a variable name — declaration
				Lexer.restore();
				Tk.type = savedType;
				Tk.strLen = C.nLen[nm];
				Native.arraycopy(C.nPool, C.nOff[nm], Tk.strBuf, 0, Tk.strLen);
				pLocal();
			} else {
				// Expression statement
				Lexer.restore();
				Tk.type = savedType;
				Tk.strLen = C.nLen[nm];
				Native.arraycopy(C.nPool, C.nOff[nm], Tk.strBuf, 0, Tk.strLen);
				pExprStmt();
			}
			return true;
		}
		else {
			pExprStmt();
			return true;
		}
	}

	static boolean isTyTok(int t) {
		return t == Tk.INT || t == Tk.BYTE || t == Tk.CHAR ||
			   t == Tk.SHORT || t == Tk.BOOLEAN || t == Tk.VOID ||
			   t == Tk.STRING_KW;
	}

	static void pExprStmt() {
		int type = Expr.pExpr();
		if (type != 0) E.epop();
		Lexer.expect(Tk.SEMI);
	}

	// ==================== LOCAL VARIABLE DECLARATION ====================

	static void pLocal() {
		int varType = E.pTypeLoc(); // 0=int, 1=ref
		int refNm = E.tyRefNm;
		int varNarrow = E.tyNarrow;
		do {
			int nm = C.intern(Tk.strBuf, Tk.strLen);
			int slot = C.locCount;
			E.aLoc(nm, varType, refNm, varNarrow);
			Lexer.nextToken();

				if (Tk.type == Tk.ASSIGN) {
					Lexer.nextToken();
					int initType = Expr.pTypedInit(varType, refNm);
					Expr.chkStoreCompat(initType, varType, refNm, varNarrow);
					E.eStN(slot, varType, varNarrow);
					E.pop();
				}

			if (Tk.type == Tk.COMMA) {
				Lexer.nextToken();
			} else {
				break;
			}
		} while (Tk.type == Tk.IDENT);
		Lexer.expect(Tk.SEMI);
	}

	// ==================== CONTROL FLOW ====================

	// Parse a condition and branch directly when the tail is a materialized cmpBool.
	static boolean pCondBr(int lbl, boolean onTrue) {
		int savedDepth = C.stkDepth;
		int condType = Expr.pExpr();
		boolean alwaysTrue = Expr.exprConst && Expr.exprConstVal != 0;
		if (condType == 0) Lexer.error(210); // condition needs a value
		if (condType != 1 || Expr.exprNarrow != C.NK_BOOL) Lexer.error(211);
		if (C.mcLen >= 8 && C.patC >= 2) {
			int start = C.mcLen - 8;
			int op = C.mcode[start] & 0xFF;
			int br = op;
			int p0 = C.patC - 2;
			if (!onTrue) {
				if (op == E.IFEQ) br = E.IFNE;
				else if (op == E.IFNE) br = E.IFEQ;
				else if (op >= 0x9F && op <= 0xA6) br = ((op & 1) == 1) ? op + 1 : op - 1;
				else br = -1;
			}
			if (br >= 0 &&
				(C.mcode[start + 1] & 0xFF) == 0 && (C.mcode[start + 2] & 0xFF) == 0 &&
				(C.mcode[start + 3] & 0xFF) == E.ICONST_0 &&
				(C.mcode[start + 4] & 0xFF) == E.GOTO &&
				(C.mcode[start + 5] & 0xFF) == 0 && (C.mcode[start + 6] & 0xFF) == 0 &&
				(C.mcode[start + 7] & 0xFF) == E.ICONST_1 &&
				(C.patLoc[p0] & 0xFFFF) == start + 1 &&
				(C.patLoc[p0 + 1] & 0xFFFF) == start + 5) {
				C.mcode[start] = (byte)br;
				C.patLbl[p0] = (short)lbl;
				C.patC--;
				C.mcLen = start + 3;
				C.stkDepth = savedDepth;
				return alwaysTrue;
			}
		}
		E.pop();
		E.eBr(onTrue ? E.IFNE : E.IFEQ, lbl);
		return alwaysTrue;
	}

	static boolean pIf() {
		Lexer.nextToken(); // skip 'if'
		Lexer.expect(Tk.LPAREN);
		int lblElse = E.label();
		pCondBr(lblElse, false);
		Lexer.expect(Tk.RPAREN);

		boolean thenCompletes = pStmt();

		if (Tk.type == Tk.ELSE) {
			int lblEnd = E.label();
			E.eBr(E.GOTO, lblEnd); // GOTO end
			E.mark(lblElse);
			Lexer.nextToken(); // skip 'else'
			boolean elseCompletes = pStmt();
			E.mark(lblEnd);
			return thenCompletes || elseCompletes;
		} else {
			E.mark(lblElse);
			return true;
		}
	}

	static boolean pWhile() {
		Lexer.nextToken(); // skip 'while'
		int lblTop = E.label();
		int lblEnd = E.label();
		int lblCont = lblTop;
		E.mark(lblTop);

		Lexer.expect(Tk.LPAREN);
		boolean alwaysTrue = pCondBr(lblEnd, false);
		Lexer.expect(Tk.RPAREN);

		int loopDepth = C.lpDepth;
		E.pushLp(lblEnd, lblCont);
		pStmt();
		boolean hasBreak = (C.lpContOwn[loopDepth] & 64) != 0;
		E.popLp();

		E.eBr(E.GOTO, lblTop); // GOTO top
		E.mark(lblEnd);
		return !alwaysTrue || hasBreak;
	}

	static boolean pDoWhile() {
		Lexer.nextToken(); // skip 'do'
		int lblTop = E.label();
		int lblEnd = E.label();
		int lblCont = E.label();
		E.mark(lblTop);

		int loopDepth = C.lpDepth;
		E.pushLp(lblEnd, lblCont);
		boolean bodyCompletes = pStmt();
		boolean hasBreak = (C.lpContOwn[loopDepth] & 64) != 0;
		boolean hasContinue = (C.lpContOwn[loopDepth] & 128) != 0;
		E.popLp();

		Lexer.expect(Tk.WHILE);
		E.mark(lblCont);
		Lexer.expect(Tk.LPAREN);
		boolean alwaysTrue = pCondBr(lblTop, true);
		Lexer.expect(Tk.RPAREN);
		E.mark(lblEnd);
		Lexer.expect(Tk.SEMI);
		return hasBreak || (!alwaysTrue && (bodyCompletes || hasContinue));
	}

	static boolean pFor() {
		Lexer.nextToken(); // skip 'for'
		Lexer.expect(Tk.LPAREN);

		int savedLocalCount = C.locCount;

		// Init — check for for-each: for (type name : expr)
		if (Tk.type != Tk.SEMI) {
			boolean localDecl = isTyTok(Tk.type);
			if (!localDecl && Tk.type == Tk.IDENT) {
				Lexer.save();
				int typeNm = C.intern(Tk.strBuf, Tk.strLen);
				int savedType = Tk.type;
				Lexer.nextToken();
				localDecl = Tk.type == Tk.IDENT ||
					(Tk.type == Tk.LBRACKET &&
					 Resolver.fClsByNm(Catalog.resolveTypeNm(typeNm)) >= 0);
				Lexer.restore();
				Tk.type = savedType;
				Tk.strLen = C.nLen[typeNm];
				Native.arraycopy(C.nPool, C.nOff[typeNm], Tk.strBuf, 0, Tk.strLen);
			}
			if (localDecl) {
				int varType = E.pTypeLoc();
				int varRefNm = E.tyRefNm;
				int varNarrow = E.tyNarrow;
				int nm = C.intern(Tk.strBuf, Tk.strLen);
				int slot = C.locCount;
				E.aLoc(nm, varType, varRefNm, varNarrow);
				Lexer.nextToken(); // consume name

				if (Tk.type == Tk.COLON) {
					pForEach(varType, varRefNm, E.tyNarrow, slot);
					C.locCount = savedLocalCount;
					return true;
				}

					// Traditional for — already declared the local, handle initializer
					if (Tk.type == Tk.ASSIGN) {
						Lexer.nextToken();
						int initType = Expr.pTypedInit(varType, varRefNm);
						Expr.chkStoreCompat(initType, varType, varRefNm, varNarrow);
						E.eStN(slot, varType, varNarrow);
						E.pop();
					}
				while (Tk.type == Tk.COMMA) {
					Lexer.nextToken();
					int nm2 = C.intern(Tk.strBuf, Tk.strLen);
					int slot2 = C.locCount;
					E.aLoc(nm2, varType, varRefNm, varNarrow);
						Lexer.nextToken();
						if (Tk.type == Tk.ASSIGN) {
							Lexer.nextToken();
							int initType = Expr.pTypedInit(varType, varRefNm);
							Expr.chkStoreCompat(initType, varType, varRefNm, varNarrow);
							E.eStN(slot2, varType, varNarrow);
							E.pop();
						}
				}
				Lexer.expect(Tk.SEMI);
			} else {
				int type = Expr.pExpr();
				if (type != 0) E.epop();
				Lexer.expect(Tk.SEMI);
			}
		} else {
			Lexer.nextToken(); // skip ;
		}

		// Forward-only layout: cond → GOTO body → update → GOTO cond → body → GOTO update
		// No re-lexing: everything parsed in source order.
		int lblCond = E.label();
		int lblEnd = E.label();
		int lblUpdate = E.label();
		int lblBody = E.label();

		// Condition
		E.mark(lblCond);
		boolean hasUpdate = false;
		boolean alwaysTrue = true;
		if (Tk.type != Tk.SEMI) {
			alwaysTrue = pCondBr(lblEnd, false);
		}
		Lexer.expect(Tk.SEMI);

		// Update (parsed in source order, right after condition)
		if (Tk.type != Tk.RPAREN) {
			hasUpdate = true;
			E.eBr(E.GOTO, lblBody); // GOTO body (skip update on first iteration)
			E.mark(lblUpdate);
			int type = Expr.pExpr();
			if (type != 0) E.epop();
			E.eBr(E.GOTO, lblCond); // GOTO cond
		} else {
			// No update — lblUpdate is same as lblCond
			E.mark(lblUpdate);
			// No GOTO needed; fall through handled by body's GOTO
		}
		Lexer.expect(Tk.RPAREN);

		// Body
		E.mark(lblBody);
		int loopDepth = C.lpDepth;
		E.pushLp(lblEnd, lblUpdate);
		pStmt();
		boolean hasBreak = (C.lpContOwn[loopDepth] & 64) != 0;
		E.popLp();

		if (hasUpdate) {
			E.eBr(E.GOTO, lblUpdate); // GOTO update
		} else {
			E.eBr(E.GOTO, lblCond); // GOTO cond (no update to run)
		}
		E.mark(lblEnd);

		C.locCount = savedLocalCount;
		return !alwaysTrue || hasBreak;
	}

	static void pForEach(int elemType, int elemRefNm, int elemNarrow, int elemSlot) {
		Lexer.nextToken(); // skip ':'

		// Parse array expression
		int arrType = Expr.pExpr(); // array ref on stack
		if (arrType == 0) Lexer.error(210); // foreach source needs a value
		int arrRefNm = Expr.exprRefNm;
		int arrElemRefNm = Expr.exprArrRefNm;
		if (arrType < 3 && (arrType != 2 || arrElemRefNm < 0 && arrRefNm != -1)) Lexer.error(211);
		if (arrType >= 3) {
			if (elemType != 0) Lexer.error(208);
			Expr.setScalarKind(Expr.arrNarrow(arrType));
			Expr.chkStoreCompat(1, 0, -1, elemNarrow);
		} else if (arrElemRefNm >= 0) {
			if (elemType != 1) Lexer.error(208);
			Expr.setObjRef(arrElemRefNm);
			Expr.chkStoreCompat(2, 1, elemRefNm, C.NK_NONE);
		} else if (elemType == 0) {
			Lexer.error(208);
		}

		// Allocate hidden locals: $a (array ref), $i (index), $n (length)
		byte[] sb = Tk.strBuf;
		sb[0] = (byte)'$';
		int arrSlot = C.locCount;
		sb[1] = (byte)'a'; E.aLoc(C.intern(sb, 2), 1);
		int iSlot = C.locCount;
		sb[1] = (byte)'i'; E.aLoc(C.intern(sb, 2), 0);
		int lenSlot = C.locCount;
		sb[1] = (byte)'n'; E.aLoc(C.intern(sb, 2), 0);

		// $a = arrayExpr (already on stack)
		E.eSt(arrSlot, 1); E.pop();
		// $n = $a.length
		E.eLd(arrSlot, 1); E.push();
		E.eb(E.ARRAYLENGTH); // replaces ref with int, no stack change
		E.eSt(lenSlot, 0); E.pop();
		// $i = 0
		E.ic0();
		E.eSt(iSlot, 0); E.pop();

		Lexer.expect(Tk.RPAREN);

		int lblCond = E.label();
		int lblEnd = E.label();
		int lblUpdate = E.label();

		// Condition: if ($i >= $n) goto end
		E.mark(lblCond);
		E.eLd(iSlot, 0); E.push();
		E.eLd(lenSlot, 0); E.push();
		E.eBr(0xA2, lblEnd); // IF_ICMPGE
		E.pop(); E.pop();

		// elem = $a[$i]
		E.eLd(arrSlot, 1); E.push();
		E.eLd(iSlot, 0); E.push();
		E.eALd(arrType); E.pop(); // xALOAD: pops index+ref, pushes element = net -1
		E.eStN(elemSlot, elemType != 0 ? 1 : 0, elemNarrow); E.pop();

		// Body
		E.pushLp(lblEnd, lblUpdate);
		pStmt();
		E.popLp();

		// Update: $i++
		E.mark(lblUpdate);
		E.eb(E.IINC); E.eb(iSlot); E.eb(1);
		E.eBr(E.GOTO, lblCond);

		E.mark(lblEnd);
	}

	// Record a control transfer whose target lp entry sits below `target`;
	// any active try region entered after that entry is escaped, and a
	// finally attached to it would be skipped, so pTry rejects the program.
	static void markTryEscape(int target) {
		for (int i = 0; i < C.tryDepth; i++) {
			if (target < C.tryLpD[i]) C.tryEsc[i]++;
		}
	}

	static void pRet() {
		for (int i = 0; i < C.tryDepth; i++) C.tryEsc[i]++;
		Lexer.nextToken(); // skip 'return'
		int retType = C.mRetT[C.curMi];
		if (Tk.type == Tk.SEMI) {
			if (retType != 0) Lexer.error(272);
			Lexer.nextToken();
			E.eb(E.RETURN);
			} else {
				if (retType == 0) Lexer.error(272);
				int retArrKind = retType == 2 ? C.mRetNarrow[C.curMi] : 0;
				int exprType = Expr.pExpr();
				if (exprType == 0) Lexer.error(210); // return expression needs a value
				E.pop();
				if (retType == 2) {
					Expr.chkStoreCompat(exprType, retArrKind != 0 ? retArrKind : 1, C.mRetRefNm[C.curMi], C.NK_NONE);
					E.eb(E.ARETURN);
				} else {
					Expr.chkStoreCompat(exprType, 0, -1, C.mRetNarrow[C.curMi]);
					E.eNarrow(C.mRetNarrow[C.curMi]);
					E.eb(E.IRETURN);
				}
			Lexer.expect(Tk.SEMI);
		}
	}

	static void pThrow() {
		Lexer.nextToken(); // skip 'throw'
		int exprType = Expr.pExpr();
		if (exprType == 0) Lexer.error(210); // throw expression needs a value
		Expr.chkStoreCompat(exprType, 1, C.N_THROWABLE, C.NK_NONE);
		E.pop();
		E.eb(E.ATHROW);
		Lexer.expect(Tk.SEMI);
	}

	// Hidden local used to hold the switch discriminator while the body is parsed.
	static int aSwitchLocal(int type, int refNm) {
		int swSlot = C.locCount;
		byte[] sb = Tk.strBuf;
		sb[0] = (byte)'$'; sb[1] = (byte)'s'; sb[2] = (byte)'w';
		if (type == 0) E.aLoc(C.intern(sb, 3), 0);
		else E.aLoc(C.intern(sb, 3), 1, refNm);
		return swSlot;
	}

	static final int SW_LINEAR = 0;
	static final int SW_LOOKUP = 1;
	static final int SW_TABLE  = 2;

	static int switchLoadBytes(int slot) {
		return slot <= 3 ? 1 : 2;
	}

	static int switchConstBytes(int val) {
		if (val >= -1 && val <= 5) return 1;
		if (val >= -128 && val <= 127) return 2;
		return 3;
	}

	// Choose the smallest dispatch encoding that preserves int-switch semantics.
	static int pickIntSwitchMode(int swSlot, int base, int caseCount) {
		if (caseCount <= 1) return SW_LINEAR;

		int linear = 3;
		int minVal = C.caseVals[base], maxVal = C.caseVals[base];
		boolean fit16 = true;
		for (int i = base; i < base + caseCount; i++) {
			int v = C.caseVals[i];
			linear += switchLoadBytes(swSlot) + switchConstBytes(v) + 3;
			if (v < minVal) minVal = v;
			if (v > maxVal) maxVal = v;
			if (v < -32768 || v > 32767) fit16 = false;
		}

		int switchPc = C.mcLen + switchLoadBytes(swSlot);
		int pad = (4 - ((switchPc + 1) & 3)) & 3;
		int lookup = switchLoadBytes(swSlot) + 1 + pad + 8 + caseCount * 8;
		int mode = SW_LINEAR;

		if (fit16) {
			int range = maxVal - minVal + 1;
			if (range > 0 && range <= 255) {
				int table = switchLoadBytes(swSlot) + 1 + pad + 12 + range * 4;
				if (table < linear && table <= lookup) return SW_TABLE;
			}
		}

		// lookupswitch does not usually beat the linear chain on bytes alone here,
		// but once the sparse case list is long enough it is still a better runtime
		// trade than reloading and comparing through a long branch chain.
		if (caseCount >= 6) mode = SW_LOOKUP;
		return mode;
	}

	static void emitLinearSwitch(int swSlot, int base, int caseCount, int defaultLabel) {
		for (int i = base; i < base + caseCount; i++) {
			E.eLd(swSlot, 0); E.push();
			E.eIC(C.caseVals[i]); E.push();
			E.eBr(0x9F, C.caseLbls[i]); // IF_ICMPEQ
			E.pop(); E.pop();
		}
		E.eBr(E.GOTO, defaultLabel);
	}

	static void emitLookupSwitch(int swSlot, int base, int caseCount, int defaultLabel) {
		E.eLd(swSlot, 0); E.push();
		int switchPc = C.mcLen;
		E.eb(E.LOOKUPSWITCH);
		E.eAlign4();
		E.eSwitchOff(switchPc, defaultLabel);
		E.eIBE(caseCount);
		for (int i = base; i < base + caseCount; i++) {
			E.eIBE(C.caseVals[i]);
			E.eSwitchOff(switchPc, C.caseLbls[i]);
		}
		E.pop();
	}

	static void emitTableSwitch(int swSlot, int base, int caseCount, int defaultLabel) {
		int minVal = C.caseVals[base], maxVal = C.caseVals[base];
		for (int i = base + 1; i < base + caseCount; i++) {
			int v = C.caseVals[i];
			if (v < minVal) minVal = v;
			if (v > maxVal) maxVal = v;
		}

		E.eLd(swSlot, 0); E.push();
		int switchPc = C.mcLen;
		E.eb(E.TABLESWITCH);
		E.eAlign4();
		E.eSwitchOff(switchPc, defaultLabel);
		E.eIBE(minVal);
		E.eIBE(maxVal);
		for (int val = minVal; val <= maxVal; val++) {
			int target = defaultLabel;
			for (int i = base; i < base + caseCount; i++) {
				if (C.caseVals[i] == val) {
					target = C.caseLbls[i];
					break;
				}
			}
			E.eSwitchOff(switchPc, target);
		}
		E.pop();
	}

	static boolean pSwitch() {
		int savedLocalCount = C.locCount;
		Lexer.nextToken(); // skip 'switch'
		Lexer.expect(Tk.LPAREN);
		int switchType = Expr.pExpr();
		if (switchType == 0) Lexer.error(210); // switch expression needs a value
		Lexer.expect(Tk.RPAREN);

		if (switchType == 2) {
			// String switch — value still on stack
			boolean completes = pStringSwitch();
			C.locCount = savedLocalCount;
			return completes;
		}

		int swSlot = aSwitchLocal(0, -1);
		E.eSt(swSlot, 0);
		E.pop();

		int lblDispatch = E.label();
		int lblEnd = E.label();
		int base = C.caseTop;
		int caseCount = 0;
		int defaultLabel = -1;
		boolean lastGroupCompletes = true;
		C.chk(C.flowSwitchDepth, 8, 265);
		int flowIndex = C.flowSwitchDepth++;
		C.flowSwitchEnd[flowIndex] = (short)lblEnd;
		C.flowSwitchBreak[flowIndex] = false;

		// Parse the body first, then branch into the selected case block.
		E.eBr(E.GOTO, lblDispatch);
		Lexer.expect(Tk.LBRACE);
		// continue inside a switch targets the enclosing loop, if any
		int outerCont = C.lpDepth > 0 ? C.lpContLbl[C.lpDepth - 1] : -1;
		E.pushLp(lblEnd, outerCont);
		C.lpContOwn[C.lpDepth - 1] = (byte)(C.lpDepth >= 2 ? C.lpContOwn[C.lpDepth - 2] & 31 : -1);

		while (Tk.type != Tk.RBRACE && Tk.type != Tk.EOF) {
			if (Tk.type == Tk.CASE) {
				lastGroupCompletes = true;
				Lexer.nextToken();
				int val;
				boolean neg = false;
				if (Tk.type == Tk.MINUS) {
					neg = true;
					Lexer.nextToken();
				}
				if (Tk.type != Tk.INT_LIT && (neg || Tk.type != Tk.CHAR_LIT))
					Lexer.error(267); // case label needs an int or char literal
				val = Tk.intValue;
				if (neg) val = -val;
					Lexer.nextToken();
					Lexer.expect(Tk.COLON);

					C.chk(C.caseTop, 64, 266);
					C.caseLbls[C.caseTop] = (short)E.label();
					E.mark(C.caseLbls[C.caseTop]);
					C.caseVals[C.caseTop] = val;
				C.caseTop++;
				caseCount++;
			} else if (Tk.type == Tk.DEFAULT) {
				lastGroupCompletes = true;
				Lexer.nextToken();
				Lexer.expect(Tk.COLON);
				defaultLabel = E.label();
				E.mark(defaultLabel);
			} else {
				boolean stmtCompletes = pStmt();
				if (lastGroupCompletes) lastGroupCompletes = stmtCompletes;
			}
		}

		E.popLp();
		E.eBr(E.GOTO, lblEnd);
		E.mark(lblDispatch);
		int dispatchDefault = defaultLabel >= 0 ? defaultLabel : lblEnd;
		if (caseCount == 0) {
			E.eBr(E.GOTO, dispatchDefault);
		} else {
			int mode = pickIntSwitchMode(swSlot, base, caseCount);
			if (mode == SW_TABLE) emitTableSwitch(swSlot, base, caseCount, dispatchDefault);
			else if (mode == SW_LOOKUP) emitLookupSwitch(swSlot, base, caseCount, dispatchDefault);
			else emitLinearSwitch(swSlot, base, caseCount, dispatchDefault);
		}
		E.mark(lblEnd);
		C.caseTop = base;
		C.flowSwitchDepth--;
		boolean completes = defaultLabel < 0 || lastGroupCompletes || C.flowSwitchBreak[flowIndex];

		Lexer.expect(Tk.RBRACE);
		C.locCount = savedLocalCount;
		return completes;
	}

	static boolean pStringSwitch() {
		// String value on JVM stack — store in hidden local
		int swSlot = aSwitchLocal(1, C.N_STRING);
		E.eSt(swSlot, 1); E.pop();

		int lblDispatch = E.label();
		int lblEnd = E.label();
		int eqMi = C.ensNat(C.N_STRING, C.N_EQUALS);
		int eqCpIdx = E.aCP(eqMi);

		int base = C.caseTop;
		int caseCount = 0;
		int defaultLabel = -1;
		boolean lastGroupCompletes = true;
		C.chk(C.flowSwitchDepth, 8, 265);
		int flowIndex = C.flowSwitchDepth++;
		C.flowSwitchEnd[flowIndex] = (short)lblEnd;
		C.flowSwitchBreak[flowIndex] = false;

		E.eBr(E.GOTO, lblDispatch);
		Lexer.expect(Tk.LBRACE);
		// continue inside a switch targets the enclosing loop, if any
		int outerCont = C.lpDepth > 0 ? C.lpContLbl[C.lpDepth - 1] : -1;
		E.pushLp(lblEnd, outerCont);
		C.lpContOwn[C.lpDepth - 1] = (byte)(C.lpDepth >= 2 ? C.lpContOwn[C.lpDepth - 2] & 31 : -1);

		while (Tk.type != Tk.RBRACE && Tk.type != Tk.EOF) {
			if (Tk.type == Tk.CASE) {
				lastGroupCompletes = true;
				Lexer.nextToken();
				if (Tk.type != Tk.STR_LIT) Lexer.error(267); // case label needs a string literal
				// Parse string literal — register in CP
				byte[] buf = new byte[Tk.strLen];
				Native.arraycopy(Tk.strBuf, 0, buf, 0, Tk.strLen);
				C.chk(C.caseTop, 64, 266);
				C.caseVals[C.caseTop] = E.aSCP(buf, Tk.strLen);
					Lexer.nextToken(); // skip string
					Lexer.expect(Tk.COLON);

					C.caseLbls[C.caseTop] = (short)E.label();
					E.mark(C.caseLbls[C.caseTop]);
					C.caseTop++;
					caseCount++;
			} else if (Tk.type == Tk.DEFAULT) {
				lastGroupCompletes = true;
				Lexer.nextToken();
				Lexer.expect(Tk.COLON);
				defaultLabel = E.label();
				E.mark(defaultLabel);
			} else {
				boolean stmtCompletes = pStmt();
				if (lastGroupCompletes) lastGroupCompletes = stmtCompletes;
			}
		}

		E.popLp();
		E.eBr(E.GOTO, lblEnd);
		E.mark(lblDispatch);
		for (int i = base; i < base + caseCount; i++) {
			E.eLd(swSlot, 1); E.push();
			E.eLdc(C.caseVals[i]); E.push();
			E.eOp(E.INVOKEVIRTUAL, eqCpIdx); E.pop(); E.pop(); E.push();
			E.eBr(E.IFNE, C.caseLbls[i]);
			E.pop();
		}
		E.eBr(E.GOTO, defaultLabel >= 0 ? defaultLabel : lblEnd);
		E.mark(lblEnd);
		C.caseTop = base;
		C.flowSwitchDepth--;
		boolean completes = defaultLabel < 0 || lastGroupCompletes || C.flowSwitchBreak[flowIndex];

		Lexer.expect(Tk.RBRACE);
		return completes;
	}

	static void pTry() {
		int savedLocalCount = C.locCount;
		Lexer.nextToken(); // skip 'try'

		int lblEnd = E.label();
		int startPC = C.mcLen;

		// Track exits that leave this region: the single-pass emitter cannot
		// inline a finally body at them, so try-finally rejects such code.
		C.chk(C.tryDepth, 8, 265);
		C.tryLpD[C.tryDepth] = (byte)C.lpDepth;
		C.tryEsc[C.tryDepth] = (short)0;
		C.tryDepth++;

		Lexer.expect(Tk.LBRACE);
		pBlock();
		Lexer.expect(Tk.RBRACE);
		C.locCount = savedLocalCount;

		int endPC = C.mcLen;
		E.eBr(E.GOTO, lblEnd); // GOTO after handlers

		// Catch clauses
		while (Tk.type == Tk.CATCH) {
			C.locCount = savedLocalCount;
			Lexer.nextToken();
			Lexer.expect(Tk.LPAREN);

			// Exception type
			int excNm = Catalog.parseTypeNm();

			// Exception variable name
			int varNm = C.iN();
			Lexer.expect(Tk.RPAREN);

			int handlerPC = C.mcLen;

			// Store exception in local
			int slot = C.locCount;
			E.aLoc(varNm, 1); // reference type
			E.eSt(slot, 1); // ASTORE
			E.pop(); // exception ref was on stack
			E.push(); // but we consumed it

			// Record exception table entry
			int catchClassId = Resolver.fClsByNm(excNm);
			if (catchClassId < 0) Lexer.error(202);
			C.chk(C.excC, C.MAX_EXC, 264);
			C.excSPc[C.excC] = (short)startPC;
			C.excEPc[C.excC] = (short)endPC;
			C.excHPc[C.excC] = (short)handlerPC;
			C.excCCls[C.excC] = (byte)catchClassId;
			C.excC++;
			C.mExcC[C.curMi]++;

			Lexer.expect(Tk.LBRACE);
			pBlock();
			Lexer.expect(Tk.RBRACE);
			C.locCount = savedLocalCount;

			E.eBr(E.GOTO, lblEnd); // GOTO end
		}

		// Finally clause — emit body once, use flag variable for normal vs exceptional
		C.tryDepth--;
		if (Tk.type == Tk.FINALLY) {
			// A return/break/continue that left the protected region would
			// silently skip this finally; reject instead of miscompiling.
			if (C.tryEsc[C.tryDepth] != 0) Lexer.error(269);
			Lexer.nextToken();

			int lblFinally = E.label();
			int excSlot = C.locCount;
			E.aLoc(C.iStr("$finally"), 1);
			int finallyLocalCount = C.locCount;

			// Catch-all handler: store exception, goto finally
			int handlerPC = C.mcLen;
			E.eSt(excSlot, 1); // ASTORE exception (from JVM stack)
			E.eBr(E.GOTO, lblFinally);

			C.chk(C.excC, C.MAX_EXC, 264);
			C.excSPc[C.excC] = (short)startPC;
			C.excEPc[C.excC] = (short)endPC;
			C.excHPc[C.excC] = (short)handlerPC;
			C.excCCls[C.excC] = (byte)0xFF; // catch all
			C.excC++;
			C.mExcC[C.curMi]++;

			// Normal path: null means no exception
			E.mark(lblEnd);
			E.eb(E.ACONST_NULL);
			E.push();
			E.eSt(excSlot, 1); // ASTORE null
			E.pop();

			// Finally body (emitted ONCE, parsed in source order)
			E.mark(lblFinally);
			Lexer.expect(Tk.LBRACE);
			pBlock();
			Lexer.expect(Tk.RBRACE);
			C.locCount = finallyLocalCount;

			// If exception was caught, re-throw
			E.eLd(excSlot, 1); // ALOAD excSlot
			E.push();
			int lblDone = E.label();
			E.eBr(E.IFEQ, lblDone); // if null, skip ATHROW
			E.pop();
			E.eLd(excSlot, 1); // ALOAD again
			E.push();
			E.eb(E.ATHROW);
			E.pop();
			E.mark(lblDone);
			E.pop();
		} else {
			E.mark(lblEnd);
		}
		C.locCount = savedLocalCount;
	}

}
