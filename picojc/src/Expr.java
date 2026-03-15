public class Expr {
	// Returns: 0=void, 1=int, 2=ref

	// Lvalue descriptor for unified assign/compound/inc-dec/load
	static int lvK;      // 0=local, 1=this_field, 2=static, 3=obj_field
	static int lvI;      // slot (local) or cpIdx (field)
	static int lvT;      // local type (0=int, 1=ref, 3+=array)
	static int lvArr;    // field array kind
	static boolean lvRV; // assign DUPs and returns value

	static int pExpr() {
		return pTern();
	}

	static int pTern() {
		int type = pBin(1);
		if (Tk.type == Tk.QUESTION) {
			Lexer.nextToken();
			E.pop();
			int lblFalse = E.label();
			int lblEnd = E.label();
			E.eBr(E.IFEQ, lblFalse); // IFEQ → false
			int tType = pExpr();
			E.eBr(E.GOTO, lblEnd); // GOTO end
			E.pop();
			Lexer.expect(Tk.COLON);
			E.mark(lblFalse);
			int fType = pExpr();
			E.mark(lblEnd);
			type = tType;
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
		while (true) {
			int info = binInfo(Tk.type);
			if (info < 0) break;
			int prec = info >> 8;
			if (prec < minPrec) break;
			int opcode = info & 0xFF;
			int tok = Tk.type;
			Lexer.nextToken();
			if (prec <= 2) {
				// Short-circuit: || (prec 1) or && (prec 2)
				int lbl1 = E.label(); int lbl2 = E.label();
				E.edup();
				E.eBr(prec == 1 ? E.IFNE : E.IFEQ, lbl1);
				E.pop(); E.epop();
				pBin(prec + 1);
				E.eBr(E.GOTO, lbl2);
				E.mark(lbl1); E.mark(lbl2);
			} else if (prec == 6) {
				// Equality: ==, !=
				int rtype = pBin(prec + 1);
				E.pop(); E.pop();
				if (type == 2 || rtype == 2)
					E.cmpBool(tok == Tk.EQ ? 0xA5 : 0xA6);
				else
					E.cmpBool(tok == Tk.EQ ? 0x9F : 0xA0);
				type = 1;
			} else if (tok == Tk.INSTANCEOF) {
				int classNm = C.intern(Tk.strBuf, Tk.strLen);
				Lexer.nextToken();
				E.pop();
				int ci = Resolver.fClsByNm(classNm);
				int cpIdx = E.aCCP(ci >= 0 ? ci : 0);
				E.eOp(0xC1, cpIdx); E.push();
				type = 1;
			} else if (prec == 7) {
				// Comparison: <, >, <=, >=
				pBin(prec + 1);
				E.pop(); E.pop();
				E.cmpBool(opcode);
				type = 1;
			} else {
				// Standard: |, ^, &, <<, >>, >>>, +, -, *, /, %
				pBin(prec + 1);
				E.pop();
				E.eb(opcode);
			}
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
			pUnary();
			E.eb(0x74); // INEG
			return 1;
		}
		if (Tk.type == Tk.TILDE) {
			Lexer.nextToken();
			pUnary();
			// ~x = x ^ (-1)
			E.eb(0x02); // ICONST_M1
			E.push();
			E.eb(0x82); // IXOR
			E.pop();
			return 1;
		}
		if (Tk.type == Tk.BANG) {
			Lexer.nextToken();
			pUnary();
			E.pop();
			E.cmpBool(E.IFEQ); // IFEQ: !x
			return 1;
		}
		if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
			int op = Tk.type;
			Lexer.nextToken();
			int nm = C.intern(Tk.strBuf, Tk.strLen);
			Lexer.nextToken();
			int li = E.fLoc(nm);
			if (li >= 0) {
				int slot = C.locSlot[li];
				// Pre-increment: load, add/sub 1, dup, store
				E.eLd(slot, 0);
				E.push();
				E.ic1();
				E.eb(op == Tk.INC ? 0x60 : 0x64); // IADD/ISUB
				E.pop();
				E.edup();
				E.eSt(slot, 0);
				E.pop();
				return 1;
			}
			Lexer.error(201);
			return 1;
		}
		if (Tk.type == Tk.LPAREN) {
			// Check for cast: (Type)expr
			Lexer.save();
			Lexer.nextToken();
			if (Stmt.isTyTok(Tk.type) || Tk.type == Tk.IDENT) {
				int castType = Tk.type;
				int castNm = -1;
				if (Tk.type == Tk.IDENT) castNm = C.intern(Tk.strBuf, Tk.strLen);
				Lexer.nextToken();
				if (Tk.type == Tk.RPAREN) {
					// It's a cast
					Lexer.discardSave();
					Lexer.nextToken();
					pUnary();
					// Emit cast instruction
					if (castType == Tk.BYTE) E.eb(0x91); // I2B
					else if (castType == Tk.CHAR) E.eb(0x92); // I2C
					else if (castType == Tk.SHORT) E.eb(0x93); // I2S
					else if (castType == Tk.IDENT && castNm >= 0) {
						// Object cast = CHECKCAST
						int ci = Resolver.fClsByNm(castNm);
						int cpIdx = E.aCCP(ci >= 0 ? ci : 0);
						E.eOp(0xC0, cpIdx); // CHECKCAST
					}
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
				Lexer.nextToken();
				int memberNm = C.intern(Tk.strBuf, Tk.strLen);
				Lexer.nextToken();

				if (memberNm == C.N_LENGTH && Tk.type != Tk.LPAREN) {
					// array.length
					E.eb(0xBE); // ARRAYLENGTH
					type = 1;
				} else if (Tk.type == Tk.LPAREN) {
					// Method call on object
					type = eMethCall(type, memberNm, false);
				} else {
					// Field access
					type = eFldAcc(memberNm);
				}
			}
			else if (Tk.type == Tk.LBRACKET) {
				// Array access
				Lexer.nextToken();
				pExpr();
				Lexer.expect(Tk.RBRACKET);
				E.pop(); // index

				// Check for store
				if (Tk.type == Tk.ASSIGN) {
					Lexer.nextToken();
					pExpr();
					E.pop(); E.pop();
					E.eASt(type);
					type = 0;
				} else if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
					// Array element post-increment: arr[idx]++
					int op = Tk.type;
					Lexer.nextToken();
					E.push(); // re-count idx
					E.eb(0x5C); E.push(); E.push(); // DUP2
					E.eALd(type);
					E.pop(); // consumes dup'd pair, pushes value: net -1
					E.eb(0x5B); E.push(); // DUP_X2
					E.ic1();
					E.eb(op == Tk.INC ? 0x60 : 0x64); E.pop();
					E.eASt(type);
					E.pop(); E.pop(); E.pop();
					type = 1;
				} else {
					E.eALd(type);
					// Propagate inner type for multi-dim arrays
					if (type == 6) type = 4;
					else if (type == 7) type = 5;
					else type = 1;
				}
			}
			else if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
				// Post-increment/decrement in general postfix position
				Lexer.nextToken();
				type = 1;
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
			Lexer.nextToken();
			E.eIC(val);
			E.push();
			return 1;
		}
		if (Tk.type == Tk.STR_LIT) {
			byte[] buf = new byte[Tk.strLen];
			Native.arraycopy(Tk.strBuf, 0, buf, 0, Tk.strLen);
			int cpIdx = E.aSCP(buf, Tk.strLen);
			Lexer.nextToken();
			E.eb(0x12); // LDC
			E.eb(cpIdx);
			E.push();
			return 2; // reference
		}
		if (Tk.type == Tk.TRUE) { Lexer.nextToken(); E.ic1(); return 1; }
		if (Tk.type == Tk.FALSE) { Lexer.nextToken(); E.ic0(); return 1; }
		if (Tk.type == Tk.NULL) {
			Lexer.nextToken();
			E.eb(0x01); // ACONST_NULL
			E.push();
			return 2;
		}
		if (Tk.type == Tk.THIS) {
			Lexer.nextToken();
			E.eLd(0, 1); // ALOAD_0
			E.push();
			return 2;
		}
		if (Tk.type == Tk.NEW) {
			return pNew();
		}
		if (Tk.type == Tk.IDENT || Tk.type == Tk.STRING_KW) {
			int nm = C.intern(Tk.strBuf, Tk.strLen);
			Lexer.nextToken();

			// Check for static method call or field access: Name.member
			if (Tk.type == Tk.DOT) {
				// Could be ClassName.method() or ClassName.field
				int ci = Resolver.fClsByNm(nm);
				if (ci >= 0 || nm == C.N_NATIVE || nm == C.N_STRING) {
					Lexer.nextToken();
					int memberNm = C.intern(Tk.strBuf, Tk.strLen);
					Lexer.nextToken();

					if (Tk.type == Tk.LPAREN) {
						// Static method call
						return eStatCall(nm, memberNm);
					} else {
						// Static field access
						int fi = Resolver.fStatField(ci, memberNm);
						if (fi < 0) { Lexer.error(207); return 0; }
						// ClassName.field — no return value on assign
						lvK = 2; lvI = E.aFCP(C.fSlot[fi]); lvArr = C.fArrKind[fi]; lvRV = false;
						return lvOps();
					}
				}
			}

			// Check for local variable
			int li = E.fLoc(nm);
			if (li >= 0) {
				// Local var lvalue
				lvK = 0; lvI = C.locSlot[li]; lvT = C.locType[li]; lvArr = 0; lvRV = true;
				return lvOps();
			}

			// Check for instance field (implicit this)
			if (!C.curMStatic) {
				int fi = Resolver.fField(C.curCi, nm);
				if (fi >= 0 && !C.fStatic[fi]) {
					// Implicit this.field lvalue
					lvK = 1; lvI = E.aFCP(C.fSlot[fi]); lvArr = C.fArrKind[fi]; lvRV = false;
					return lvOps();
				}
			}

			// Check for static field (prefer current class first)
			{
				int fi = Resolver.fStatField(C.curCi, nm);
				if (fi >= 0) {
					// Static field lvalue (in-class, returns value on assign)
					lvK = 2; lvI = E.aFCP(C.fSlot[fi]); lvArr = C.fArrKind[fi]; lvRV = true;
					return lvOps();
				}
			}

			// Check for method call in same class
			if (Tk.type == Tk.LPAREN) {
				// Check if it's an instance method (implicit this.method())
				if (!C.curMStatic) {
					for (int mi2 = 0; mi2 < C.mCount; mi2++) {
						if (C.mClass[mi2] == C.curCi && C.mName[mi2] == nm &&
							!C.mStatic[mi2] && !C.mNative[mi2] && !C.mIsCtor[mi2]) {
							// Instance method: push this, then INVOKEVIRTUAL
							E.ethis();
							return eMethCall(2, nm, false);
						}
					}
				}
				return eStatCall(C.cName[C.curCi], nm);
			}

			Lexer.error(202); // Undefined identifier
			return 0;
		}
		Lexer.error(203); // Unexpected token
		return 0;
	}

	// ==================== NEW EXPRESSION ====================

	static int pNew() {
		Lexer.nextToken(); // skip 'new'

		if (Tk.type == Tk.INT || Tk.type == Tk.BYTE ||
			Tk.type == Tk.CHAR || Tk.type == Tk.SHORT ||
			Tk.type == Tk.BOOLEAN) {
			// Primitive array: new int[size]
			int elemType = Tk.type;
			Lexer.nextToken();
			Lexer.expect(Tk.LBRACKET);
			pExpr();
			Lexer.expect(Tk.RBRACKET);

			int typeCode = 10; // int
			if (elemType == Tk.BYTE) typeCode = 8;
			else if (elemType == Tk.CHAR) typeCode = 5;
			else if (elemType == Tk.SHORT) typeCode = 9;
			else if (elemType == Tk.BOOLEAN) typeCode = 4;

			// Check for 2D array: new type[N][M] or new type[N][]
			if (Tk.type == Tk.LBRACKET) {
				Lexer.nextToken();
				if (Tk.type == Tk.RBRACKET) {
					// new type[N][] — array of references, size N
					Lexer.nextToken();
					int cpIdx = E.aCCP(0);
					E.eOp(0xBD, cpIdx); // ANEWARRAY
					return 2; // reference array
				}
				pExpr();
				Lexer.expect(Tk.RBRACKET);
				int cpIdx = E.aCCP(0);
				E.eOp(0xC5, cpIdx); // MULTIANEWARRAY
				E.eb(2); // 2 dimensions
				E.pop(); // second dimension
				// First dim still on stack, result replaces it
				return 2;
			}

			E.eb(0xBC); // NEWARRAY
			E.eb(typeCode);
			// Stack: count consumed, array ref pushed = net 0
			// Return specific array type for proper BALOAD/BASTORE emission
			if (typeCode == 8 || typeCode == 4) return 4;  // byte[] or boolean[]
			if (typeCode == 5) return 5;  // char[]
			if (typeCode == 9) return 8;  // short[]
			return 3; // int[]
		}

		// Object or reference array: new ClassName(...) or new ClassName[size]
		int classNm = C.intern(Tk.strBuf, Tk.strLen);
		Lexer.nextToken();

		if (Tk.type == Tk.LBRACKET) {
			// Reference array: new ClassName[size]
			Lexer.nextToken();
			pExpr();
			Lexer.expect(Tk.RBRACKET);

			int ci = Resolver.fClsByNm(classNm);
			int cpIdx = E.aCCP(ci >= 0 ? ci : 0);

			// Check for 2D
			if (Tk.type == Tk.LBRACKET) {
				Lexer.nextToken();
				pExpr();
				Lexer.expect(Tk.RBRACKET);
				E.eOp(0xC5, cpIdx); // MULTIANEWARRAY
				E.eb(2);
				E.pop();
				return 2;
			}

			E.eOp(0xBD, cpIdx); // ANEWARRAY
			return 2;
		}

		// Object creation: new ClassName(args)
		int ci = Resolver.fClsByNm(classNm);
		if (ci < 0) ci = Resolver.synthExcCls(classNm);
		int cpIdx = E.aCCP(ci);
		E.eOp(0xBB, cpIdx); // NEW
		E.push();
		E.edup();

		Lexer.expect(Tk.LPAREN);
		int argc = pArgs(1); // 'this' counts

		// Find constructor: prefer argc match, fall back to any ctor
		int ctorMi = -1;
		for (int mi = 0; mi < C.mCount; mi++) {
			if (C.mClass[mi] == ci && C.mIsCtor[mi]) {
				if (ctorMi < 0 || C.mArgC[mi] == argc) ctorMi = mi;
				if (C.mArgC[mi] == argc) break;
			}
		}
		if (ctorMi < 0) ctorMi = C.ensNat(C.N_OBJECT, C.N_INIT);

		int ctorCpIdx = E.aCP(ctorMi);
		E.eOp(0xB7, ctorCpIdx); // INVOKESPECIAL
		// Pop args + dup from stack, keep original ref
		for (int i = 0; i < argc; i++) E.pop();

		return 2; // reference on stack
	}

	// ==================== METHOD CALLS ====================

	// Pop args, push return value if non-void
	static int eCallRet(int mi, int argc) {
		for (int j = 0; j < argc; j++) E.pop();
		int rt = C.mRetT[mi]; if (rt != 0) E.push(); return rt;
	}

	static int eStatCall(int classNm, int methodNm) {
		Lexer.expect(Tk.LPAREN);
		// Native or user-defined static method
		int mi = C.ensNat(classNm, methodNm);
		if (mi < 0) {
			for (int m = 0; m < C.mCount; m++) {
				if (C.mName[m] == methodNm && !C.mNative[m]) {
					int mc = C.mClass[m];
					if (mc < C.cCount && C.cName[mc] == classNm) { mi = m; break; }
				}
			}
		}
		int argc = pArgs(0);
		if (mi < 0) { Lexer.error(204); return 0; }
		E.eOp(0xB8, E.aCP(mi)); // INVOKESTATIC
		return eCallRet(mi, argc);
	}

	static int eMethCall(int objType, int methodNm, boolean isInterface) {
		Lexer.expect(Tk.LPAREN);
		// Native String method or user-defined instance method
		int mi = C.ensNat(C.N_STRING, methodNm);
		int argc = pArgs(1); // 'this' already on stack
		if (mi < 0) {
			for (int m = 0; m < C.mCount; m++) {
				if (C.mName[m] == methodNm && !C.mStatic[m] && !C.mNative[m]) { mi = m; break; }
			}
			if (mi < 0) { Lexer.error(205); return 0; }
		}
		int cpIdx = E.aCP(mi);
		int mci = C.mClass[mi];
		// Interface dispatch only for non-native user methods
		if (!C.mNative[mi] && mci < C.cCount && C.cIsIface[mci]) {
			E.eOp(0xB9, cpIdx); E.eb(argc); E.eb(0); // INVOKEINTERFACE
		} else {
			E.eOp(0xB6, cpIdx); // INVOKEVIRTUAL
		}
		return eCallRet(mi, argc);
	}

	// ==================== LVALUE OPS ====================

	// Snapshot descriptors into locals — pExpr() may recurse into lvOps
	static int lvOps() {
		int k = lvK, i = lvI, t = lvT, arr = lvArr; boolean rv = lvRV;
		if (Tk.type == Tk.ASSIGN) {
			Lexer.nextToken();
			if (k == 1) E.ethis();
			pExpr();
			if (k == 0) { E.edup(); E.eSt(i, t); E.pop(); return t == 1 ? 2 : 1; }
			if (k == 2) {
				if (rv) { E.edup(); E.eOp(0xB3, i); E.pop(); return 1; }
				E.eOp(0xB3, i); E.pop(); return 0;
			}
			E.eOp(0xB5, i); E.pop(); E.pop(); return 0; // PUTFIELD
		}
		if (Tk.type >= Tk.PLUS_EQ && Tk.type <= Tk.USHR_EQ) {
			int op = Tk.type; Lexer.nextToken();
			if (k == 0) {
				E.eLd(i, t); E.push(); pExpr(); E.eCO(op); E.pop();
				E.edup(); E.eSt(i, t); E.pop(); return 1;
			}
			if (k == 1) {
				E.ethis(); E.ethis(); E.eOp(0xB4, i);
				pExpr(); E.eCO(op); E.pop();
				E.eOp(0xB5, i); E.pop(); E.pop(); return 0;
			}
			if (k == 2) {
				E.eOp(0xB2, i); E.push(); pExpr(); E.eCO(op); E.pop();
				E.edup(); E.eOp(0xB3, i); E.pop(); return 1;
			}
			// k == 3: obj already on stack
			E.edup(); E.eOp(0xB4, i); pExpr(); E.eCO(op); E.pop();
			E.eOp(0xB5, i); E.pop(); E.pop(); return 0;
		}
		if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
			int op = Tk.type; Lexer.nextToken();
			if (k == 0) {
				E.eLd(i, 0); E.push();
				E.eb(0x84); E.eb(i); E.eb(op == Tk.INC ? 1 : 0xFF); return 1;
			}
			if (k == 1) {
				E.ethis(); E.eOp(0xB4, i);
				E.ethis(); E.ethis(); E.eOp(0xB4, i);
				E.ic1(); E.eb(op == Tk.INC ? 0x60 : 0x64); E.pop();
				E.eOp(0xB5, i); E.pop(); E.pop(); return 1;
			}
			// k == 2: GETSTATIC + DUP + 1 + op + PUTSTATIC
			E.eOp(0xB2, i); E.push(); E.edup();
			E.ic1(); E.eb(op == Tk.INC ? 0x60 : 0x64); E.pop();
			E.eOp(0xB3, i); E.pop(); return 1;
			// k == 3: not supported, falls through to load
		}
		// Load
		if (k == 0) {
			E.eLd(i, t); E.push();
			if (t >= 3) return t; return t == 1 ? 2 : 1;
		}
		if (k == 1) { E.ethis(); E.eOp(0xB4, i); }
		else if (k == 2) { E.eOp(0xB2, i); E.push(); }
		else { E.eOp(0xB4, i); }
		if (arr != 0) return arr; return 1;
	}

	// Explicit obj.field lvalue (obj already on stack)
	static int eFldAcc(int fieldNm) {
		int fi = Resolver.fInstField(fieldNm);
		if (fi < 0) { Lexer.error(206); return 0; }
		lvK = 3; lvI = E.aFCP(C.fSlot[fi]); lvArr = C.fArrKind[fi]; lvRV = false;
		return lvOps();
	}


	static int pArgs(int start) {
		int argc = start;
		while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
			pExpr(); argc++;
			if (Tk.type == Tk.COMMA) Lexer.nextToken();
		}
		Lexer.expect(Tk.RPAREN);
		return argc;
	}


}
