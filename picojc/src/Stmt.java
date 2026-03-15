public class Stmt {
	static void pBlock() {
		while (Tk.type != Tk.RBRACE && Tk.type != Tk.EOF) {
			pStmt();
		}
	}

	static void pStmt() {
		if (Tk.type == Tk.LBRACE) {
			Lexer.nextToken();
			int savedLocalCount = C.locCount;
			pBlock();
			C.locCount = savedLocalCount; // restore scope
			Lexer.expect(Tk.RBRACE);
		}
		else if (Tk.type == Tk.IF) {
			pIf();
		}
		else if (Tk.type == Tk.WHILE) {
			pWhile();
		}
		else if (Tk.type == Tk.DO) {
			pDoWhile();
		}
		else if (Tk.type == Tk.FOR) {
			pFor();
		}
		else if (Tk.type == Tk.RETURN) {
			pRet();
		}
		else if (Tk.type == Tk.BREAK) {
			Lexer.nextToken();
			Lexer.expect(Tk.SEMI);
			if (C.lpDepth > 0) {
				E.eBr(E.GOTO, C.lpBrkLbl[C.lpDepth - 1]); // GOTO break
			}
		}
		else if (Tk.type == Tk.CONTINUE) {
			Lexer.nextToken();
			Lexer.expect(Tk.SEMI);
			if (C.lpDepth > 0) {
				E.eBr(E.GOTO, C.lpContLbl[C.lpDepth - 1]); // GOTO continue
			}
		}
		else if (Tk.type == Tk.SWITCH) {
			pSwitch();
		}
		else if (Tk.type == Tk.THROW) {
			pThrow();
		}
		else if (Tk.type == Tk.TRY) {
			pTry();
		}
		else if (isTyTok(Tk.type)) {
			pLocal();
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
				 Resolver.fClsByNm(nm) >= 0)) {
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
		}
		else {
			pExprStmt();
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
				Expr.pTypedInit(varType, refNm);
				Expr.chkImplicitNarrow(varNarrow);
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

	static void pIf() {
		Lexer.nextToken(); // skip 'if'
		Lexer.expect(Tk.LPAREN);
		Expr.pExpr();
		Lexer.expect(Tk.RPAREN);
		E.pop();

		int lblElse = E.label();
		E.eBr(E.IFEQ, lblElse); // IFEQ → else

		pStmt();

		if (Tk.type == Tk.ELSE) {
			int lblEnd = E.label();
			E.eBr(E.GOTO, lblEnd); // GOTO end
			E.mark(lblElse);
			Lexer.nextToken(); // skip 'else'
			pStmt();
			E.mark(lblEnd);
		} else {
			E.mark(lblElse);
		}
	}

	static void pWhile() {
		Lexer.nextToken(); // skip 'while'
		int lblTop = E.label();
		int lblEnd = E.label();
		int lblCont = lblTop;
		E.mark(lblTop);

		Lexer.expect(Tk.LPAREN);
		Expr.pExpr();
		Lexer.expect(Tk.RPAREN);
		E.pop();
		E.eBr(E.IFEQ, lblEnd); // IFEQ → end

		E.pushLp(lblEnd, lblCont);
		pStmt();
		E.popLp();

		E.eBr(E.GOTO, lblTop); // GOTO top
		E.mark(lblEnd);
	}

	static void pDoWhile() {
		Lexer.nextToken(); // skip 'do'
		int lblTop = E.label();
		int lblEnd = E.label();
		int lblCont = E.label();
		E.mark(lblTop);

		E.pushLp(lblEnd, lblCont);
		pStmt();
		E.popLp();

		Lexer.expect(Tk.WHILE);
		E.mark(lblCont);
		Lexer.expect(Tk.LPAREN);
		Expr.pExpr();
		Lexer.expect(Tk.RPAREN);
		E.pop();
		E.eBr(E.IFNE, lblTop); // IFNE → top
		E.mark(lblEnd);
		Lexer.expect(Tk.SEMI);
	}

	static void pFor() {
		Lexer.nextToken(); // skip 'for'
		Lexer.expect(Tk.LPAREN);

		int savedLocalCount = C.locCount;

		// Init — check for for-each: for (type name : expr)
		if (Tk.type != Tk.SEMI) {
			if (isTyTok(Tk.type)) {
				int varType = E.pTypeLoc();
				int varRefNm = E.tyRefNm;
				int varNarrow = E.tyNarrow;
				int nm = C.intern(Tk.strBuf, Tk.strLen);
				int slot = C.locCount;
				E.aLoc(nm, varType, varRefNm, varNarrow);
				Lexer.nextToken(); // consume name

				if (Tk.type == Tk.COLON) {
					pForEach(varType, E.tyNarrow, slot);
					C.locCount = savedLocalCount;
					return;
				}

				// Traditional for — already declared the local, handle initializer
				if (Tk.type == Tk.ASSIGN) {
					Lexer.nextToken();
					Expr.pTypedInit(varType, varRefNm);
					Expr.chkImplicitNarrow(varNarrow);
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
						Expr.pTypedInit(varType, varRefNm);
						Expr.chkImplicitNarrow(varNarrow);
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
		if (Tk.type != Tk.SEMI) {
			Expr.pExpr();
			E.pop();
			E.eBr(E.IFEQ, lblEnd); // IFEQ → end
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
		E.pushLp(lblEnd, lblUpdate);
		pStmt();
		E.popLp();

		if (hasUpdate) {
			E.eBr(E.GOTO, lblUpdate); // GOTO update
		} else {
			E.eBr(E.GOTO, lblCond); // GOTO cond (no update to run)
		}
		E.mark(lblEnd);

		C.locCount = savedLocalCount;
	}

	static void pForEach(int elemType, int elemNarrow, int elemSlot) {
		Lexer.nextToken(); // skip ':'

		// Parse array expression
		int arrType = Expr.pExpr(); // array ref on stack

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
		E.eStN(elemSlot, elemType >= 3 ? 1 : 0, elemNarrow); E.pop();

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

	static void pRet() {
		Lexer.nextToken(); // skip 'return'
		if (Tk.type == Tk.SEMI) {
			Lexer.nextToken();
			E.eb(E.RETURN);
		} else {
			int retType = C.mRetT[C.curMi];
			Expr.pExpr();
			E.pop();
			if (retType == 2) E.eb(E.ARETURN);
			else {
				Expr.chkImplicitNarrow(C.mRetNarrow[C.curMi]);
				E.eNarrow(C.mRetNarrow[C.curMi]);
				E.eb(E.IRETURN);
			}
			Lexer.expect(Tk.SEMI);
		}
	}

	static void pThrow() {
		Lexer.nextToken(); // skip 'throw'
		Expr.pExpr();
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

	static void pSwitch() {
		Lexer.nextToken(); // skip 'switch'
		Lexer.expect(Tk.LPAREN);
		int switchType = Expr.pExpr();
		Lexer.expect(Tk.RPAREN);

		if (switchType == 2) {
			// String switch — value still on stack
			pStringSwitch();
			return;
		}

		int swSlot = aSwitchLocal(0, -1);
		E.eSt(swSlot, 0);
		E.pop();

		int lblDispatch = E.label();
		int lblEnd = E.label();
		int caseCount = 0;
		int defaultLabel = -1;

		// Parse the body first, then branch into the selected case block.
		E.eBr(E.GOTO, lblDispatch);
		Lexer.expect(Tk.LBRACE);
		E.pushLp(lblEnd, lblEnd); // continue in switch = break

		while (Tk.type != Tk.RBRACE && Tk.type != Tk.EOF) {
			if (Tk.type == Tk.CASE) {
				Lexer.nextToken();
				int val;
				boolean neg = false;
				if (Tk.type == Tk.MINUS) {
					neg = true;
					Lexer.nextToken();
				}
				val = Tk.intValue;
				if (neg) val = -val;
					Lexer.nextToken();
					Lexer.expect(Tk.COLON);

					C.chk(caseCount, 64, 266);
					C.caseLbls[caseCount] = (short)E.label();
					E.mark(C.caseLbls[caseCount]);
					C.caseVals[caseCount] = val;
				caseCount++;
			} else if (Tk.type == Tk.DEFAULT) {
				Lexer.nextToken();
				Lexer.expect(Tk.COLON);
				defaultLabel = E.label();
				E.mark(defaultLabel);
			} else {
				pStmt();
			}
		}

		E.popLp();
		E.eBr(E.GOTO, lblEnd);
		E.mark(lblDispatch);
		for (int i = 0; i < caseCount; i++) {
			E.eLd(swSlot, 0); E.push();
			E.eIC(C.caseVals[i]); E.push();
			E.eBr(0x9F, C.caseLbls[i]); // IF_ICMPEQ
			E.pop(); E.pop();
		}
		E.eBr(E.GOTO, defaultLabel >= 0 ? defaultLabel : lblEnd);
		E.mark(lblEnd);

		Lexer.expect(Tk.RBRACE);
	}

	static void pStringSwitch() {
		// String value on JVM stack — store in hidden local
		int swSlot = aSwitchLocal(1, C.N_STRING);
		E.eSt(swSlot, 1); E.pop();

		int lblDispatch = E.label();
		int lblEnd = E.label();
		int eqMi = C.ensNat(C.N_STRING, C.N_EQUALS);
		int eqCpIdx = E.aCP(eqMi);

		int caseCount = 0;
		int defaultLabel = -1;

		E.eBr(E.GOTO, lblDispatch);
		Lexer.expect(Tk.LBRACE);
		E.pushLp(lblEnd, lblEnd);

		while (Tk.type != Tk.RBRACE && Tk.type != Tk.EOF) {
			if (Tk.type == Tk.CASE) {
				Lexer.nextToken();
				// Parse string literal — register in CP
				byte[] buf = new byte[Tk.strLen];
				Native.arraycopy(Tk.strBuf, 0, buf, 0, Tk.strLen);
				C.caseVals[caseCount] = E.aSCP(buf, Tk.strLen);
					Lexer.nextToken(); // skip string
					Lexer.expect(Tk.COLON);

					C.chk(caseCount, 64, 266);
					C.caseLbls[caseCount] = (short)E.label();
					E.mark(C.caseLbls[caseCount]);
					caseCount++;
			} else if (Tk.type == Tk.DEFAULT) {
				Lexer.nextToken();
				Lexer.expect(Tk.COLON);
				defaultLabel = E.label();
				E.mark(defaultLabel);
			} else {
				pStmt();
			}
		}

		E.popLp();
		E.eBr(E.GOTO, lblEnd);
		E.mark(lblDispatch);
		for (int i = 0; i < caseCount; i++) {
			E.eLd(swSlot, 1); E.push();
			E.eLdc(C.caseVals[i]); E.push();
			E.eOp(E.INVOKEVIRTUAL, eqCpIdx); E.pop(); E.pop(); E.push();
			E.eBr(E.IFNE, C.caseLbls[i]);
			E.pop();
		}
		E.eBr(E.GOTO, defaultLabel >= 0 ? defaultLabel : lblEnd);
		E.mark(lblEnd);

		Lexer.expect(Tk.RBRACE);
	}

	static void pTry() {
		Lexer.nextToken(); // skip 'try'

		int lblEnd = E.label();
		int startPC = C.mcLen;

		Lexer.expect(Tk.LBRACE);
		pBlock();
		Lexer.expect(Tk.RBRACE);

		int endPC = C.mcLen;
		E.eBr(E.GOTO, lblEnd); // GOTO after handlers

		// Catch clauses
		while (Tk.type == Tk.CATCH) {
			Lexer.nextToken();
			Lexer.expect(Tk.LPAREN);

			// Exception type
			int excNm = C.iN();

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
			if (catchClassId < 0) catchClassId = 0xFF;
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

			E.eBr(E.GOTO, lblEnd); // GOTO end
		}

		// Finally clause — emit body once, use flag variable for normal vs exceptional
		if (Tk.type == Tk.FINALLY) {
			Lexer.nextToken();

			int lblFinally = E.label();
			int excSlot = C.locCount;
			E.aLoc(C.iStr("$finally"), 1);

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
	}

}
