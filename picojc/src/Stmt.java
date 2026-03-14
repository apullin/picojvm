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
		do {
			int nm = C.intern(Tk.strBuf, Tk.strLen);
			int slot = C.locCount;
			E.aLoc(nm, varType);
			Lexer.nextToken();

			if (Tk.type == Tk.ASSIGN) {
				Lexer.nextToken();
				Expr.pExpr();
				E.eSt(slot, varType);
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

		// Init
		if (Tk.type != Tk.SEMI) {
			if (isTyTok(Tk.type)) {
				pLocal(); // includes semicolon
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

	static void pRet() {
		Lexer.nextToken(); // skip 'return'
		if (Tk.type == Tk.SEMI) {
			Lexer.nextToken();
			E.eb(0xB1); // RETURN
		} else {
			int retType = C.mRetT[C.curMi];
			Expr.pExpr();
			E.pop();
			if (retType == 2) E.eb(0xB0); // ARETURN
			else E.eb(0xAC); // IRETURN
			Lexer.expect(Tk.SEMI);
		}
	}

	static void pThrow() {
		Lexer.nextToken(); // skip 'throw'
		Expr.pExpr();
		E.pop();
		E.eb(0xBF); // ATHROW
		Lexer.expect(Tk.SEMI);
	}

	static void pSwitch() {
		Lexer.nextToken(); // skip 'switch'
		Lexer.expect(Tk.LPAREN);
		Expr.pExpr();
		Lexer.expect(Tk.RPAREN);
		E.pop();

		int lblEnd = E.label();

		// Collect cases
		// Save body start BEFORE expect consumes { and advances past first token
		int bodyStart = Lexer.pos;
		int bodyLine = Lexer.line;
		Lexer.expect(Tk.LBRACE);
		// Static to avoid heap allocation per switch (no nested switches)
		// Allocated once in <clinit>, reused for each switch statement
		int caseCount = 0;
		int defaultLabel = -1;

		// Use LOOKUPSWITCH for all switch statements (simplest)
		int switchPC = C.mcLen;
		E.eb(0xAB); // LOOKUPSWITCH

		// Padding to 4-byte alignment
		while ((switchPC + 1 + (C.mcLen - switchPC - 1)) % 4 != 0) {
			E.eb(0);
		}

		int defaultLoc = C.mcLen;
		E.eIBE(0); // default offset placeholder (4 bytes, standard JVM format)
		int npairsLoc = C.mcLen;
		E.eIBE(0); // npairs placeholder (4 bytes)

		// Scan cases
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

				C.caseLbls[caseCount] = E.label();
				C.caseVals[caseCount] = val;
				caseCount++;
			} else if (Tk.type == Tk.DEFAULT) {
				Lexer.nextToken();
				Lexer.expect(Tk.COLON);
				defaultLabel = E.label();
			} else {
				Lexer.nextToken();
			}
		}

		// Patch npairs
		E.pBE(npairsLoc, caseCount);

		// Emit match-offset pairs (sorted by key, picoJVM requires this)
		// Simple insertion sort on caseVals
		for (int i = 1; i < caseCount; i++) {
			int kv = C.caseVals[i];
			int kl = C.caseLbls[i];
			int j = i - 1;
			while (j >= 0 && C.caseVals[j] > kv) {
				C.caseVals[j + 1] = C.caseVals[j];
				C.caseLbls[j + 1] = C.caseLbls[j];
				j--;
			}
			C.caseVals[j + 1] = kv;
			C.caseLbls[j + 1] = kl;
		}

		int[] pairLocs = new int[caseCount];
		for (int i = 0; i < caseCount; i++) {
			E.eIBE(C.caseVals[i]); // match key (4-byte BE)
			pairLocs[i] = C.mcLen;
			E.eIBE(0);
		}

		// Now re-parse and emit case bodies
		Lexer.pos = bodyStart;
		Lexer.line = bodyLine;
		Lexer.nextToken();

		int curCaseIdx = -1;

		E.pushLp(lblEnd, lblEnd); // continue in switch = break

		while (Tk.type != Tk.RBRACE && Tk.type != Tk.EOF) {
			if (Tk.type == Tk.CASE) {
				Lexer.nextToken();
				boolean neg = false;
				if (Tk.type == Tk.MINUS) { neg = true; Lexer.nextToken(); }
				int val = Tk.intValue;
				if (neg) val = -val;
				Lexer.nextToken();
				Lexer.expect(Tk.COLON);

				// Find which case this is
				for (int i = 0; i < caseCount; i++) {
					if (C.caseVals[i] == val) {
						E.mark(C.caseLbls[i]);
						E.pBE(pairLocs[i], C.mcLen - switchPC);
						break;
					}
				}
			} else if (Tk.type == Tk.DEFAULT) {
				Lexer.nextToken();
				Lexer.expect(Tk.COLON);
				if (defaultLabel >= 0) {
					E.mark(defaultLabel);
					E.pBE(defaultLoc, C.mcLen - switchPC);
				}
			} else {
				pStmt();
			}
		}

		E.popLp();

		// If no default, patch default to end
		if (defaultLabel < 0) {
			E.mark(lblEnd);
			E.pBE(defaultLoc, C.mcLen - switchPC);
		}

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
			int excNm = C.intern(Tk.strBuf, Tk.strLen);
			Lexer.nextToken();

			// Exception variable name
			int varNm = C.intern(Tk.strBuf, Tk.strLen);
			Lexer.nextToken();
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
			C.excSPc[C.excC] = startPC;
			C.excEPc[C.excC] = endPC;
			C.excHPc[C.excC] = handlerPC;
			C.excCCls[C.excC] = catchClassId;
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

			C.excSPc[C.excC] = startPC;
			C.excEPc[C.excC] = endPC;
			C.excHPc[C.excC] = handlerPC;
			C.excCCls[C.excC] = 0xFF; // catch all
			C.excC++;
			C.mExcC[C.curMi]++;

			// Normal path: null means no exception
			E.mark(lblEnd);
			E.eb(0x01); // ACONST_NULL
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
			E.eb(0xBF); // ATHROW
			E.pop();
			E.mark(lblDone);
			E.pop();
		} else {
			E.mark(lblEnd);
		}
	}

}
