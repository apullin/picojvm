public class Expr {
	// Returns: 0=void, 1=int, 2=ref

	// Lvalue descriptor for unified assign/compound/inc-dec/load
	static int lvK;      // 0=local, 1=this_field, 2=static, 3=obj_field
	static int lvI;      // slot (local) or cpIdx (field)
	static int lvT;      // local type (0=int, 1=ref, 3+=array)
	static int lvArr;    // field array kind
	static int lvRefNm;  // declared ref type name, -1 if unknown/non-ref
	static boolean lvRV; // assign DUPs and returns value
	static int exprRefNm = -1; // declared ref type name for the last parsed ref expression
	static int exprArrRefNm = -1; // element ref type when the last parsed expression is object[]

	static void clearRefInfo() {
		exprRefNm = -1;
		exprArrRefNm = -1;
	}

	static void setObjRef(int refNm) {
		exprRefNm = refNm;
		exprArrRefNm = -1;
	}

	static void setObjArrayRef(int refNm) {
		exprRefNm = -1;
		exprArrRefNm = refNm;
	}

	static boolean callMatches(int mi, int argc, boolean isStatic) {
		if (mi < 0 || C.mStatic[mi] != isStatic || C.mIsCtor[mi]) return false;
		if (!C.mVarargs[mi]) return C.mArgC[mi] == argc;
		int minArgc = (isStatic ? 0 : 1) + C.mFixedArgs[mi];
		return argc >= minArgc && argc <= minArgc + C.MAX_VA_SLOTS;
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

	static int fStaticCallTarget(int classNm, int methodNm, int argc) {
		int mi = C.ensNat(classNm, methodNm);
		if (callMatches(mi, argc, true)) return mi;
		int ci = Resolver.fClsByNm(classNm);
		if (ci < 0) return -1;
		return Resolver.fMethodExact(ci, methodNm, true, argc);
	}

	static int fVirtCallTarget(int recvNm, int methodNm, int argc) {
		if (recvNm == C.N_STRING) {
			int mi = C.ensNat(C.N_STRING, methodNm);
			if (callMatches(mi, argc, false)) return mi;
		}
		int ci = Resolver.fClsByNm(recvNm);
		if (ci < 0) return -1;
		return Resolver.fMethodExact(ci, methodNm, false, argc);
	}

	static int fInstFieldTarget(int recvNm, int fieldNm) {
		int ci = Resolver.fClsByNm(recvNm);
		if (ci < 0) return -1;
		return Resolver.fInstField(ci, fieldNm);
	}

	static int fRetType(int t, int refNm) {
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
		clearRefInfo();
		return 1;
	}

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
			int tRefNm = exprRefNm;
			int tArrRefNm = exprArrRefNm;
			E.eBr(E.GOTO, lblEnd); // GOTO end
			E.pop();
			Lexer.expect(Tk.COLON);
			E.mark(lblFalse);
			int fType = pExpr();
			int fRefNm = exprRefNm;
			int fArrRefNm = exprArrRefNm;
			E.mark(lblEnd);
			type = tType;
			if (tType == 2 && fType == 2 && tRefNm == fRefNm && tArrRefNm == fArrRefNm) {
				exprRefNm = tRefNm;
				exprArrRefNm = tArrRefNm;
			} else clearRefInfo();
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
					clearRefInfo();
				} else if (tok == Tk.INSTANCEOF) {
					int classNm = C.iN();
					E.pop();
					int ci = Resolver.fClsByNm(classNm);
					int cpIdx = E.aCP(ci >= 0 ? ci : 0);
					E.eOp(E.INSTANCEOF, cpIdx); E.push();
					type = 1;
					clearRefInfo();
				} else if (prec == 7) {
					// Comparison: <, >, <=, >=
					pBin(prec + 1);
					E.pop(); E.pop();
					E.cmpBool(opcode);
					type = 1;
					clearRefInfo();
				} else {
					// Standard: |, ^, &, <<, >>, >>>, +, -, *, /, %
					pBin(prec + 1);
					E.pop();
					E.eb(opcode);
					clearRefInfo();
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
				E.eb(E.INEG);
				clearRefInfo();
				return 1;
			}
		if (Tk.type == Tk.TILDE) {
			Lexer.nextToken();
			pUnary();
			// ~x = x ^ (-1)
			E.eb(E.ICONST_0 - 1); // ICONST_M1
				E.push();
				E.eb(E.IXOR);
				E.pop();
				clearRefInfo();
				return 1;
			}
		if (Tk.type == Tk.BANG) {
			Lexer.nextToken();
				pUnary();
				E.pop();
				E.cmpBool(E.IFEQ); // IFEQ: !x
				clearRefInfo();
				return 1;
			}
		if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
			int op = Tk.type;
			Lexer.nextToken();
			int nm = C.iN();
			int li = E.fLoc(nm);
			if (li >= 0) {
				int slot = C.locSlot[li];
				// Pre-increment: load, add/sub 1, dup, store
				E.eLd(slot, 0);
				E.push();
				E.ic1();
				E.eb(op == Tk.INC ? E.IADD : E.ISUB);
				E.pop();
					E.edup();
					E.eSt(slot, 0);
					E.pop();
					clearRefInfo();
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
					if (castType == Tk.BYTE) E.eb(E.I2B);
					else if (castType == Tk.CHAR) E.eb(E.I2C);
					else if (castType == Tk.SHORT) E.eb(E.I2S);
						else if (castType == Tk.IDENT && castNm >= 0) {
							int ci = Resolver.fClsByNm(castNm);
							int cpIdx = E.aCP(ci >= 0 ? ci : 0);
							E.eOp(E.CHECKCAST, cpIdx);
						}
						if (castType == Tk.IDENT) setObjRef(castNm);
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
				Lexer.nextToken();
				int memberNm = C.iN();

				if (memberNm == C.N_LENGTH && Tk.type != Tk.LPAREN) {
					// array.length
					E.eb(E.ARRAYLENGTH);
					type = 1;
					clearRefInfo();
				} else if (Tk.type == Tk.LPAREN) {
					// Method call on object
					type = eMethCall(recvRefNm, memberNm);
				} else {
					// Field access
					type = eFldAcc(recvRefNm, memberNm);
				}
			}
				else if (Tk.type == Tk.LBRACKET) {
					// Array access
					int arrElemRefNm = exprArrRefNm;
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
						clearRefInfo();
					} else if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
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
						E.eALd(type);
						// Propagate inner type for multi-dim arrays
						if (type == 6) type = 4;
						else if (type == 7) type = 5;
						else if (type == 2) {
							type = 2;
							setObjRef(arrElemRefNm);
						} else {
							type = 1;
							clearRefInfo();
						}
					}
				}
				else if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
					// Post-increment/decrement in general postfix position
					Lexer.nextToken();
					type = 1;
					clearRefInfo();
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
			clearRefInfo();
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
		if (Tk.type == Tk.TRUE) { Lexer.nextToken(); E.ic1(); clearRefInfo(); return 1; }
		if (Tk.type == Tk.FALSE) { Lexer.nextToken(); E.ic0(); clearRefInfo(); return 1; }
		if (Tk.type == Tk.NULL) {
			Lexer.nextToken();
			E.eb(E.ACONST_NULL);
			E.push();
			clearRefInfo();
			return 2;
		}
		if (Tk.type == Tk.THIS) {
			Lexer.nextToken();
			E.eLd(0, 1); // ALOAD_0
			E.push();
			setObjRef(C.cName[C.curCi]);
			return 2;
		}
		if (Tk.type == Tk.NEW) {
			return pNew();
		}
		if (Tk.type == Tk.IDENT || Tk.type == Tk.STRING_KW) {
			int nm = C.iN();

			// Check for static method call or field access: Name.member
				if (Tk.type == Tk.DOT) {
					// Could be ClassName.method() or ClassName.field
					int ci = Resolver.fClsByNm(nm);
					if (ci >= 0 || nm == C.N_NATIVE || nm == C.N_STRING) {
						Lexer.nextToken();
						int memberNm = C.iN();

						if (Tk.type == Tk.LPAREN) {
							// Static method call
							return eStatCall(nm, memberNm);
						} else {
							// Static field access
							int fi = Resolver.fStatField(ci, memberNm);
							if (fi < 0) { Lexer.error(207); return 0; }
							if (C.fFinal[fi] && C.fHasConst[fi]) {
								E.eIC(C.fConstVal[fi]); E.push(); clearRefInfo(); return 1;
							}
							// ClassName.field — no return value on assign
							lvK = 2; lvI = E.aCP(C.fSlot[fi]); lvT = C.fType[fi];
							lvArr = C.fArrKind[fi]; lvRefNm = C.fRefNm[fi]; lvRV = false;
							return lvOps();
						}
					}
				}

				// Check for local variable
				int li = E.fLoc(nm);
				if (li >= 0) {
					// Local var lvalue
					lvK = 0; lvI = C.locSlot[li]; lvT = C.locType[li];
					lvArr = 0; lvRefNm = C.locRefNm[li]; lvRV = true;
					return lvOps();
				}

				// Check for instance field (implicit this)
				if (!C.curMStatic) {
					int fi = Resolver.fField(C.curCi, nm);
					if (fi >= 0 && !C.fStatic[fi]) {
						// Implicit this.field lvalue
						lvK = 1; lvI = E.aCP(C.fSlot[fi]); lvT = C.fType[fi];
						lvArr = C.fArrKind[fi]; lvRefNm = C.fRefNm[fi]; lvRV = false;
						return lvOps();
					}
				}

				// Check for static field (prefer current class first)
				{
					int fi = Resolver.fStatField(C.curCi, nm);
						if (fi >= 0) {
							if (C.fFinal[fi] && C.fHasConst[fi]) {
								E.eIC(C.fConstVal[fi]); E.push(); clearRefInfo(); return 1;
							}
						// Static field lvalue (in-class, returns value on assign)
						lvK = 2; lvI = E.aCP(C.fSlot[fi]); lvT = C.fType[fi];
						lvArr = C.fArrKind[fi]; lvRefNm = C.fRefNm[fi]; lvRV = true;
						return lvOps();
					}
				}

				// Check for method call in same class
				if (Tk.type == Tk.LPAREN) {
					// Check if it's an instance method (implicit this.method())
					if (!C.curMStatic && Resolver.fMethod(C.curCi, nm, false) >= 0) {
						E.ethis();
						setObjRef(C.cName[C.curCi]);
						return eMethCall(C.cName[C.curCi], nm);
					}
					return eStatCall(C.cName[C.curCi], nm);
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
					int cpIdx = E.aCP(0);
					E.eOp(E.ANEWARRAY, cpIdx);
					clearRefInfo();
					return 2; // reference array
				}
				pExpr();
				Lexer.expect(Tk.RBRACKET);
				int cpIdx = E.aCP(0);
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
			if (typeCode == 8 || typeCode == 4) return 4;  // byte[] or boolean[]
			if (typeCode == 5) return 5;  // char[]
			if (typeCode == 9) return 8;  // short[]
			return 3; // int[]
		}

		// Object or reference array: new ClassName(...) or new ClassName[size]
		int classNm = C.iN();

		if (Tk.type == Tk.LBRACKET) {
			// Reference array: new ClassName[size]
			Lexer.nextToken();
			pExpr();
			Lexer.expect(Tk.RBRACKET);

			int ci = Resolver.fClsByNm(classNm);
			int cpIdx = E.aCP(ci >= 0 ? ci : 0);

			// Check for 2D
			if (Tk.type == Tk.LBRACKET) {
				Lexer.nextToken();
				pExpr();
				Lexer.expect(Tk.RBRACKET);
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
		if (ci < 0) ci = Resolver.synthExcCls(classNm);
		int cpIdx = E.aCP(ci);
		E.eOp(E.NEW, cpIdx);
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
		E.eOp(E.INVOKESPECIAL, ctorCpIdx);
		// Pop args + dup from stack, keep original ref
		for (int i = 0; i < argc; i++) E.pop();

		setObjRef(classNm);
		return 2; // reference on stack
	}

	// ==================== METHOD CALLS ====================

	// Pop args, push return value if non-void
	static int eCallRet(int mi, int argc) {
		for (int j = 0; j < argc; j++) E.pop();
		int rt = C.mRetT[mi];
		if (rt == 2) setObjRef(C.mRetRefNm[mi]);
		else clearRefInfo();
		if (rt != 0) E.push();
		return rt;
	}

	static int eStatCall(int classNm, int methodNm) {
		Lexer.expect(Tk.LPAREN);
		int argc = pArgs(0);
		int mi = fStaticCallTarget(classNm, methodNm, argc);
		argc = mi >= 0 ? packVarargs(mi, argc) : -1;
		if (mi < 0 || argc < 0) { Lexer.error(204); return 0; }
		E.eOp(E.INVOKESTATIC, E.aCP(mi));
		return eCallRet(mi, argc);
	}

	static int eMethCall(int recvNm, int methodNm) {
		Lexer.expect(Tk.LPAREN);
		int argc = pArgs(1);
		int mi = recvNm >= 0 ? fVirtCallTarget(recvNm, methodNm, argc) : -1;
		argc = mi >= 0 ? packVarargs(mi, argc) : -1;
		if (mi < 0 || argc < 0) { Lexer.error(205); return 0; }
		int cpIdx = E.aCP(mi);
		int mci = C.mClass[mi];
		// Interface dispatch only for non-native user methods
		if (!C.mNative[mi] && mci < C.cCount && C.cIsIface[mci]) {
			E.eOp(E.INVOKEINTERFACE, cpIdx); E.eb(argc); E.eb(0);
		} else {
			E.eOp(E.INVOKEVIRTUAL, cpIdx);
		}
		return eCallRet(mi, argc);
	}

	// ==================== LVALUE OPS ====================

	// Snapshot descriptors into locals — pExpr() may recurse into lvOps
	static int lvOps() {
		int k = lvK, i = lvI, t = lvT, arr = lvArr, refNm = lvRefNm; boolean rv = lvRV;
		if (Tk.type == Tk.ASSIGN) {
			Lexer.nextToken();
			if (k == 1) E.ethis();
			pExpr();
			if (k == 0) { E.edup(); E.eSt(i, t); E.pop(); return fRetType(t, refNm); }
			if (k == 2) {
				if (rv) { E.edup(); E.eOp(E.PUTSTATIC, i); E.pop(); return fRetType(arr != 0 ? arr : t, arr != 0 ? -1 : refNm); }
				E.eOp(E.PUTSTATIC, i); E.pop(); return 0;
			}
			E.eOp(E.PUTFIELD, i); E.pop(); E.pop(); return 0; // PUTFIELD
		}
		if (Tk.type >= Tk.PLUS_EQ && Tk.type <= Tk.USHR_EQ) {
			int op = Tk.type; Lexer.nextToken();
			if (k == 0) {
				E.eLd(i, t); E.push(); pExpr(); E.eCO(op); E.pop();
				E.edup(); E.eSt(i, t); E.pop(); return 1;
			}
			if (k == 1) {
				E.ethis(); E.ethis(); E.eOp(E.GETFIELD, i);
				pExpr(); E.eCO(op); E.pop();
				E.eOp(E.PUTFIELD, i); E.pop(); E.pop(); return 0;
			}
			if (k == 2) {
				E.eOp(E.GETSTATIC, i); E.push(); pExpr(); E.eCO(op); E.pop();
				E.edup(); E.eOp(E.PUTSTATIC, i); E.pop(); return 1;
			}
			// k == 3: obj already on stack
			E.edup(); E.eOp(E.GETFIELD, i); pExpr(); E.eCO(op); E.pop();
			E.eOp(E.PUTFIELD, i); E.pop(); E.pop(); return 0;
		}
		if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
			int op = Tk.type; Lexer.nextToken();
			if (k == 0) {
				E.eLd(i, 0); E.push();
				E.eb(E.IINC); E.eb(i); E.eb(op == Tk.INC ? 1 : 0xFF); return 1;
			}
			if (k == 1) {
				E.ethis(); E.eOp(E.GETFIELD, i);
				E.ethis(); E.ethis(); E.eOp(E.GETFIELD, i);
				E.ic1(); E.eb(op == Tk.INC ? E.IADD : E.ISUB); E.pop();
				E.eOp(E.PUTFIELD, i); E.pop(); E.pop(); return 1;
			}
			// k == 2: GETSTATIC + DUP + 1 + op + PUTSTATIC
			E.eOp(E.GETSTATIC, i); E.push(); E.edup();
			E.ic1(); E.eb(op == Tk.INC ? E.IADD : E.ISUB); E.pop();
			E.eOp(E.PUTSTATIC, i); E.pop(); return 1;
			// k == 3: not supported, falls through to load
		}
		// Load
		if (k == 0) {
			E.eLd(i, t); E.push();
			return fRetType(t, refNm);
		}
		if (k == 1) { E.ethis(); E.eOp(E.GETFIELD, i); }
		else if (k == 2) { E.eOp(E.GETSTATIC, i); E.push(); }
		else { E.eOp(E.GETFIELD, i); }
		if (arr != 0) return fRetType(arr, -1);
		return fRetType(t, refNm);
	}

	// Explicit obj.field lvalue (obj already on stack)
	static int eFldAcc(int recvNm, int fieldNm) {
		int fi = recvNm >= 0 ? fInstFieldTarget(recvNm, fieldNm) : -1;
		if (fi < 0) { Lexer.error(206); return 0; }
		lvK = 3; lvI = E.aCP(C.fSlot[fi]); lvT = C.fType[fi];
		lvArr = C.fArrKind[fi]; lvRefNm = C.fRefNm[fi]; lvRV = false;
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
