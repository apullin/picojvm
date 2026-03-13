public class Expr {
    // Returns: 0=void, 1=int, 2=ref

    static int pExpr() {
        return pTern();
    }

    static int pTern() {
        int type = pOr();
        if (Tk.type == Tk.QUESTION) {
            Lexer.nextToken();
            E.pop();
            int lblFalse = E.label();
            int lblEnd = E.label();
            E.eBr(0x99, lblFalse); // IFEQ → false
            int tType = pExpr();
            E.eBr(0xA7, lblEnd); // GOTO end
            E.pop();
            Lexer.expect(Tk.COLON);
            E.mark(lblFalse);
            int fType = pExpr();
            E.mark(lblEnd);
            type = tType;
        }
        return type;
    }

    static int pOr() {
        int type = pAnd();
        while (Tk.type == Tk.OR) {
            Lexer.nextToken();
            int lblTrue = E.label();
            int lblEnd = E.label();
            // Short-circuit: if left is true, skip right
            E.eb(0x59); // DUP
            E.push();
            E.eBr(0x9A, lblTrue); // IFNE → true
            E.pop();
            E.eb(0x57); // POP
            E.pop();
            pAnd();
            E.eBr(0xA7, lblEnd);
            E.mark(lblTrue);
            E.mark(lblEnd);
        }
        return type;
    }

    static int pAnd() {
        int type = pBOr();
        while (Tk.type == Tk.AND) {
            Lexer.nextToken();
            int lblFalse = E.label();
            int lblEnd = E.label();
            E.eb(0x59); // DUP
            E.push();
            E.eBr(0x99, lblFalse); // IFEQ → false
            E.pop();
            E.eb(0x57); // POP
            E.pop();
            pBOr();
            E.eBr(0xA7, lblEnd);
            E.mark(lblFalse);
            E.mark(lblEnd);
        }
        return type;
    }

    static int pBOr() {
        int type = pBXor();
        while (Tk.type == Tk.PIPE) {
            Lexer.nextToken();
            pBXor();
            E.eb(0x80); // IOR
            E.pop();
        }
        return type;
    }

    static int pBXor() {
        int type = pBAnd();
        while (Tk.type == Tk.CARET) {
            Lexer.nextToken();
            pBAnd();
            E.eb(0x82); // IXOR
            E.pop();
        }
        return type;
    }

    static int pBAnd() {
        int type = pEq();
        while (Tk.type == Tk.AMP) {
            Lexer.nextToken();
            pEq();
            E.eb(0x7E); // IAND
            E.pop();
        }
        return type;
    }

    static int pEq() {
        int type = pCmp();
        while (Tk.type == Tk.EQ || Tk.type == Tk.NE) {
            int op = Tk.type;
            Lexer.nextToken();
            int rtype = pCmp();
            E.pop(); E.pop();
            if (type == 2 || rtype == 2) {
                // Reference comparison
                int lbl = E.label();
                int lblEnd = E.label();
                E.eBr(op == Tk.EQ ? 0xA5 : 0xA6, lbl); // IF_ACMPEQ/NE
                E.eb(0x03); // ICONST_0
                E.push();
                E.eBr(0xA7, lblEnd);
                E.mark(lbl);
                E.eb(0x04); // ICONST_1
                E.push();
                E.mark(lblEnd);
            } else {
                int lbl = E.label();
                int lblEnd = E.label();
                E.eBr(op == Tk.EQ ? 0x9F : 0xA0, lbl); // IF_ICMPEQ/NE
                E.eb(0x03); E.push();
                E.eBr(0xA7, lblEnd);
                E.mark(lbl);
                E.eb(0x04); E.push();
                E.mark(lblEnd);
            }
            type = 1;
        }
        return type;
    }

    static int pCmp() {
        int type = pShift();
        while (Tk.type == Tk.LT || Tk.type == Tk.GT ||
               Tk.type == Tk.LE || Tk.type == Tk.GE ||
               Tk.type == Tk.INSTANCEOF) {
            if (Tk.type == Tk.INSTANCEOF) {
                Lexer.nextToken();
                int classNm = C.intern(Tk.strBuf, Tk.strLen);
                Lexer.nextToken();
                E.pop();
                int ci = Resolver.fClsByNm(classNm);
                int cpIdx = E.aCCP(ci >= 0 ? ci : 0);
                E.eb(0xC1); // INSTANCEOF
                E.eSBE(cpIdx);
                E.push();
                type = 1;
                continue;
            }
            int op = Tk.type;
            Lexer.nextToken();
            pShift();
            E.pop(); E.pop();

            int branchOp;
            if (op == Tk.LT) branchOp = 0xA1; // IF_ICMPLT
            else if (op == Tk.GE) branchOp = 0xA2; // IF_ICMPGE
            else if (op == Tk.GT) branchOp = 0xA3; // IF_ICMPGT
            else branchOp = 0xA4; // IF_ICMPLE

            int lbl = E.label();
            int lblEnd = E.label();
            E.eBr(branchOp, lbl);
            E.eb(0x03); E.push(); // ICONST_0
            E.eBr(0xA7, lblEnd);
            E.mark(lbl);
            E.eb(0x04); E.push(); // ICONST_1
            E.mark(lblEnd);
            type = 1;
        }
        return type;
    }

    static int pShift() {
        int type = pAdd();
        while (Tk.type == Tk.SHL || Tk.type == Tk.SHR ||
               Tk.type == Tk.USHR) {
            int op = Tk.type;
            Lexer.nextToken();
            pAdd();
            E.pop();
            if (op == Tk.SHL) E.eb(0x78); // ISHL
            else if (op == Tk.SHR) E.eb(0x7A); // ISHR
            else E.eb(0x7C); // IUSHR
        }
        return type;
    }

    static int pAdd() {
        int type = pMul();
        while (Tk.type == Tk.PLUS || Tk.type == Tk.MINUS) {
            int op = Tk.type;
            Lexer.nextToken();
            pMul();
            E.pop();
            E.eb(op == Tk.PLUS ? 0x60 : 0x64); // IADD / ISUB
        }
        return type;
    }

    static int pMul() {
        int type = pUnary();
        while (Tk.type == Tk.STAR || Tk.type == Tk.SLASH ||
               Tk.type == Tk.PERCENT) {
            int op = Tk.type;
            Lexer.nextToken();
            pUnary();
            E.pop();
            if (op == Tk.STAR) E.eb(0x68); // IMUL
            else if (op == Tk.SLASH) E.eb(0x6C); // IDIV
            else E.eb(0x70); // IREM
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
            // !x: if x == 0, push 1, else push 0
            E.pop();
            int lbl = E.label();
            int lblEnd = E.label();
            E.eBr(0x99, lbl); // IFEQ
            E.eb(0x03); E.push(); // ICONST_0
            E.eBr(0xA7, lblEnd);
            E.mark(lbl);
            E.eb(0x04); E.push(); // ICONST_1
            E.mark(lblEnd);
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
                E.eb(0x04); E.push(); // ICONST_1
                E.eb(op == Tk.INC ? 0x60 : 0x64); // IADD/ISUB
                E.pop();
                E.eb(0x59); E.push(); // DUP
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
                        E.eb(0xC0); // CHECKCAST
                        E.eSBE(cpIdx);
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
                    type = eFldAcc(memberNm, false);
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
                    E.pop(); // value
                    E.pop(); // array ref
                    if (type == 4) E.eb(0x54);       // BASTORE (byte[])
                    else if (type == 5) E.eb(0x55);  // CASTORE (char[])
                    else E.eb(0x4F);                  // IASTORE (int[]/ref[])
                    type = 0; // void
                } else if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
                    // Array element post-increment: arr[idx]++
                    // Stack: [arrRef, idx] (idx was popped from tracker but still on JVM stack)
                    int op = Tk.type;
                    Lexer.nextToken();
                    E.push(); // re-count idx
                    E.eb(0x5C); E.push(); E.push(); // DUP2
                    if (type == 4) E.eb(0x33);       // BALOAD
                    else if (type == 5) E.eb(0x34);  // CALOAD
                    else E.eb(0x2E);                  // IALOAD
                    E.pop(); // IALOAD consumes dup'd pair, pushes value: net -1
                    E.eb(0x5B); E.push(); // DUP_X2: old value below arrRef,idx,new
                    E.eb(0x04); E.push(); // ICONST_1
                    E.eb(op == Tk.INC ? 0x60 : 0x64); E.pop(); // IADD/ISUB
                    if (type == 4) E.eb(0x54);       // BASTORE
                    else if (type == 5) E.eb(0x55);  // CASTORE
                    else E.eb(0x4F);                  // IASTORE
                    E.pop(); E.pop(); E.pop(); // IASTORE consumes arrRef,idx,value
                    type = 1; // old value remains on stack
                } else {
                    if (type == 4) E.eb(0x33);       // BALOAD (byte[])
                    else if (type == 5) E.eb(0x34);  // CALOAD (char[])
                    else E.eb(0x2E);                  // IALOAD (int[]/ref[])
                    // Propagate inner type for multi-dim arrays
                    if (type == 6) type = 4;             // byte[][] elem → byte[]
                    else if (type == 7) type = 5;        // char[][] elem → char[]
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
        if (Tk.type == Tk.TRUE) {
            Lexer.nextToken();
            E.eb(0x04); // ICONST_1
            E.push();
            return 1;
        }
        if (Tk.type == Tk.FALSE) {
            Lexer.nextToken();
            E.eb(0x03); // ICONST_0
            E.push();
            return 1;
        }
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
                        return eSFldAcc(ci, memberNm);
                    }
                }
            }

            // Check for local variable
            int li = E.fLoc(nm);
            if (li >= 0) {
                int slot = C.locSlot[li];
                int ltype = C.locType[li];

                // Check for assignment
                if (Tk.type == Tk.ASSIGN) {
                    Lexer.nextToken();
                    pExpr();
                    E.eb(0x59); E.push(); // DUP (keep value on stack)
                    E.eSt(slot, ltype);
                    E.pop();
                    return ltype == 1 ? 2 : 1;
                }
                if (Tk.type >= Tk.PLUS_EQ && Tk.type <= Tk.USHR_EQ) {
                    int op = Tk.type;
                    Lexer.nextToken();
                    E.eLd(slot, ltype);
                    E.push();
                    pExpr();
                    E.eCO(op);
                    E.pop();
                    E.eb(0x59); E.push(); // DUP
                    E.eSt(slot, ltype);
                    E.pop();
                    return 1;
                }
                if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
                    // Post-increment: load value, then increment
                    E.eLd(slot, 0);
                    E.push();
                    int op = Tk.type;
                    Lexer.nextToken();
                    // Use IINC for efficiency
                    E.eb(0x84); // IINC
                    E.eb(slot);
                    E.eb(op == Tk.INC ? 1 : 0xFF); // +1 or -1
                    return 1;
                }

                E.eLd(slot, ltype);
                E.push();
                // Map local type to expression type
                // 0=int→1, 1=ref→2, 3=int[]→3, 4=byte[]→4, 5=char[]→5
                if (ltype >= 3) return ltype;
                return ltype == 1 ? 2 : 1;
            }

            // Check for instance field (implicit this)
            if (!C.curMStatic) {
                int fi = Resolver.fField(C.curCi, nm);
                if (fi >= 0 && !C.fStatic[fi]) {
                    // Check for assignment
                    if (Tk.type == Tk.ASSIGN) {
                        Lexer.nextToken();
                        E.eLd(0, 1); // ALOAD_0 (this)
                        E.push();
                        pExpr();
                        int cpIdx = E.aFCP(C.fSlot[fi]);
                        E.eb(0xB5); // PUTFIELD
                        E.eSBE(cpIdx);
                        E.pop(); E.pop(); // obj + value consumed
                        // Push value back for expression result
                        // Actually for statement context this is void
                        return 0;
                    }
                    if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
                        int op = Tk.type;
                        Lexer.nextToken();
                        int cpIdx = E.aFCP(C.fSlot[fi]);
                        // Post-inc/dec: load old value, then update field
                        E.eLd(0, 1); E.push(); // ALOAD_0
                        E.eb(0xB4); E.eSBE(cpIdx); // GETFIELD (old value)
                        // Keep old value for expression result
                        E.eLd(0, 1); E.push(); // ALOAD_0 again
                        E.eLd(0, 1); E.push(); // ALOAD_0 again
                        E.eb(0xB4); E.eSBE(cpIdx); // GETFIELD again
                        E.eb(0x04); E.push(); // ICONST_1
                        E.eb(op == Tk.INC ? 0x60 : 0x64); E.pop(); // IADD/ISUB
                        E.eb(0xB5); E.eSBE(cpIdx); // PUTFIELD
                        E.pop(); E.pop(); // obj + value consumed by PUTFIELD
                        return 1; // old value remains on stack
                    }
                    if (Tk.type >= Tk.PLUS_EQ && Tk.type <= Tk.USHR_EQ) {
                        int op = Tk.type;
                        Lexer.nextToken();
                        int cpIdx = E.aFCP(C.fSlot[fi]);
                        E.eLd(0, 1); E.push(); // ALOAD_0
                        E.eLd(0, 1); E.push(); // ALOAD_0
                        E.eb(0xB4); E.eSBE(cpIdx); // GETFIELD
                        pExpr();
                        E.eCO(op);
                        E.pop();
                        E.eb(0xB5); E.eSBE(cpIdx); // PUTFIELD
                        E.pop(); E.pop();
                        return 0;
                    }
                    E.eLd(0, 1); // ALOAD_0 (this)
                    E.push();
                    int cpIdx = E.aFCP(C.fSlot[fi]);
                    E.eb(0xB4); // GETFIELD
                    E.eSBE(cpIdx);
                    // stack: -1 (obj) +1 (value) = net 0
                    if (C.fArrKind[fi] != 0) return C.fArrKind[fi];
                    return 1;
                }
            }

            // Check for static field (prefer current class first)
            {
                int sfi = -1;
                for (int fi2 = 0; fi2 < C.fCount; fi2++) {
                    if (C.fName[fi2] == nm && C.fStatic[fi2]) {
                        if (C.fClass[fi2] == C.curCi) { sfi = fi2; break; }
                        if (sfi < 0) sfi = fi2;
                    }
                }
                if (sfi >= 0) {
                    int fi = sfi;
                    if (Tk.type == Tk.ASSIGN) {
                        Lexer.nextToken();
                        pExpr();
                        E.eb(0x59); E.push(); // DUP
                        int cpIdx = E.aFCP(C.fSlot[fi]);
                        E.eb(0xB3); // PUTSTATIC
                        E.eSBE(cpIdx);
                        E.pop();
                        return 1;
                    }
                    if (Tk.type >= Tk.PLUS_EQ && Tk.type <= Tk.USHR_EQ) {
                        int op = Tk.type;
                        Lexer.nextToken();
                        int cpIdx = E.aFCP(C.fSlot[fi]);
                        E.eb(0xB2); // GETSTATIC
                        E.eSBE(cpIdx);
                        E.push();
                        pExpr();
                        E.eCO(op);
                        E.pop();
                        E.eb(0x59); E.push(); // DUP
                        E.eb(0xB3); // PUTSTATIC
                        E.eSBE(cpIdx);
                        E.pop();
                        return 1;
                    }
                    if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
                        int op = Tk.type;
                        Lexer.nextToken();
                        int cpIdx = E.aFCP(C.fSlot[fi]);
                        E.eb(0xB2); // GETSTATIC
                        E.eSBE(cpIdx);
                        E.push();
                        // Post: value before inc/dec is on stack
                        E.eb(0x59); E.push(); // DUP
                        E.eb(0x04); E.push(); // ICONST_1
                        E.eb(op == Tk.INC ? 0x60 : 0x64); E.pop(); // IADD/ISUB
                        E.eb(0xB3); // PUTSTATIC
                        E.eSBE(cpIdx);
                        E.pop();
                        return 1;
                    }
                    int cpIdx = E.aFCP(C.fSlot[fi]);
                    E.eb(0xB2); // GETSTATIC
                    E.eSBE(cpIdx);
                    E.push();
                    if (C.fArrKind[fi] != 0) return C.fArrKind[fi];
                    return 1;
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
                            E.eLd(0, 1); // ALOAD_0 (this)
                            E.push();
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
                    E.eb(0xBD); // ANEWARRAY
                    E.eSBE(cpIdx);
                    return 2; // reference array
                }
                pExpr();
                Lexer.expect(Tk.RBRACKET);
                // MULTIANEWARRAY
                int cpIdx = E.aCCP(0); // type doesn't matter for int[][]
                E.eb(0xC5); // MULTIANEWARRAY
                E.eSBE(cpIdx);
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
            return 3; // int[] (or short[])
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
                E.eb(0xC5); // MULTIANEWARRAY
                E.eSBE(cpIdx);
                E.eb(2);
                E.pop();
                return 2;
            }

            E.eb(0xBD); // ANEWARRAY
            E.eSBE(cpIdx);
            return 2;
        }

        // Object creation: new ClassName(args)
        int ci = Resolver.fClsByNm(classNm);
        if (ci < 0) ci = Resolver.synthExcCls(classNm);
        int cpIdx = E.aCCP(ci);
        E.eb(0xBB); // NEW
        E.eSBE(cpIdx);
        E.push();
        E.eb(0x59); // DUP
        E.push();

        // Parse constructor arguments
        Lexer.expect(Tk.LPAREN);
        int argc = 1; // 'this' counts
        while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
            pExpr();
            argc++;
            if (Tk.type == Tk.COMMA) Lexer.nextToken();
        }
        Lexer.expect(Tk.RPAREN);

        // Find constructor
        int ctorMi = -1;
        for (int mi = 0; mi < C.mCount; mi++) {
            if (C.mClass[mi] == ci && C.mIsCtor[mi] && C.mArgC[mi] == argc) {
                ctorMi = mi;
                break;
            }
        }
        if (ctorMi < 0) {
            // Try default constructor (0 user args = 1 total with 'this')
            for (int mi = 0; mi < C.mCount; mi++) {
                if (C.mClass[mi] == ci && C.mIsCtor[mi]) {
                    ctorMi = mi;
                    break;
                }
            }
        }
        if (ctorMi < 0) {
            // Use Object.<init> as fallback
            ctorMi = C.ensNat(C.N_OBJECT, C.N_INIT);
        }

        int ctorCpIdx = E.aCP(ctorMi);
        E.eb(0xB7); // INVOKESPECIAL
        E.eSBE(ctorCpIdx);
        // Pop args + dup from stack, keep original ref
        for (int i = 0; i < argc; i++) E.pop();

        return 2; // reference on stack
    }

    // ==================== METHOD CALLS ====================

    static int eStatCall(int classNm, int methodNm) {
        Lexer.expect(Tk.LPAREN);

        // First check native methods
        int nativeMi = C.ensNat(classNm, methodNm);
        if (nativeMi >= 0) {
            int argc = 0;
            while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
                pExpr();
                argc++;
                if (Tk.type == Tk.COMMA) Lexer.nextToken();
            }
            Lexer.expect(Tk.RPAREN);
            int cpIdx = E.aCP(nativeMi);
            E.eb(0xB8); // INVOKESTATIC
            E.eSBE(cpIdx);
            for (int i = 0; i < argc; i++) E.pop();
            int retType = C.mRetT[nativeMi];
            if (retType != 0) E.push();
            return retType;
        }

        // User-defined static method
        int mi = -1;
        for (int m = 0; m < C.mCount; m++) {
            if (C.mName[m] == methodNm && !C.mNative[m]) {
                int mc = C.mClass[m];
                if (mc < C.cCount && C.cName[mc] == classNm) {
                    mi = m;
                    break;
                }
            }
        }

        int argc = 0;
        while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
            pExpr();
            argc++;
            if (Tk.type == Tk.COMMA) Lexer.nextToken();
        }
        Lexer.expect(Tk.RPAREN);

        if (mi < 0) {
            Lexer.error(204); // Undefined method
            return 0;
        }

        int cpIdx = E.aCP(mi);
        E.eb(0xB8); // INVOKESTATIC
        E.eSBE(cpIdx);
        for (int i = 0; i < argc; i++) E.pop();
        int retType = C.mRetT[mi];
        if (retType != 0) E.push();
        return retType;
    }

    static int eMethCall(int objType, int methodNm, boolean isInterface) {
        // Object is already on stack
        Lexer.expect(Tk.LPAREN);

        // Check for String methods
        int nativeMi = C.ensNat(C.N_STRING, methodNm);

        int argc = 1; // 'this' already on stack
        while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
            pExpr();
            argc++;
            if (Tk.type == Tk.COMMA) Lexer.nextToken();
        }
        Lexer.expect(Tk.RPAREN);

        if (nativeMi >= 0) {
            int cpIdx = E.aCP(nativeMi);
            E.eb(0xB6); // INVOKEVIRTUAL
            E.eSBE(cpIdx);
            for (int i = 0; i < argc; i++) E.pop();
            int retType = C.mRetT[nativeMi];
            if (retType != 0) E.push();
            return retType;
        }

        // Find the method in user classes
        int mi = -1;
        for (int m = 0; m < C.mCount; m++) {
            if (C.mName[m] == methodNm && !C.mStatic[m] && !C.mNative[m]) {
                mi = m;
                break;
            }
        }

        if (mi < 0) {
            Lexer.error(205);
            return 0;
        }

        // Check if this is an interface call
        boolean useInterface = false;
        int mci = C.mClass[mi];
        if (mci < C.cCount && C.cIsIface[mci]) {
            useInterface = true;
        }

        if (useInterface) {
            int cpIdx = E.aCP(mi);
            E.eb(0xB9); // INVOKEINTERFACE
            E.eSBE(cpIdx);
            E.eb(argc);
            E.eb(0);
            for (int i = 0; i < argc; i++) E.pop();
        } else {
            int cpIdx = E.aCP(mi);
            E.eb(0xB6); // INVOKEVIRTUAL
            E.eSBE(cpIdx);
            for (int i = 0; i < argc; i++) E.pop();
        }

        int retType = C.mRetT[mi];
        if (retType != 0) E.push();
        return retType;
    }

    // ==================== FIELD ACCESS ====================

    static int eFldAcc(int fieldNm, boolean isStore) {
        // Object ref is on stack
        // Find field
        int fi = -1;
        for (int f = 0; f < C.fCount; f++) {
            if (C.fName[f] == fieldNm && !C.fStatic[f]) {
                fi = f;
                break;
            }
        }
        if (fi < 0) {
            Lexer.error(206);
            return 0;
        }

        if (Tk.type == Tk.ASSIGN) {
            Lexer.nextToken();
            pExpr();
            int cpIdx = E.aFCP(C.fSlot[fi]);
            E.eb(0xB5); // PUTFIELD
            E.eSBE(cpIdx);
            E.pop(); E.pop();
            return 0;
        }
        if (Tk.type >= Tk.PLUS_EQ && Tk.type <= Tk.USHR_EQ) {
            int op = Tk.type;
            Lexer.nextToken();
            E.eb(0x59); E.push(); // DUP obj ref
            int cpIdx = E.aFCP(C.fSlot[fi]);
            E.eb(0xB4); // GETFIELD
            E.eSBE(cpIdx);
            pExpr();
            E.eCO(op);
            E.pop();
            E.eb(0xB5); // PUTFIELD
            E.eSBE(cpIdx);
            E.pop(); E.pop();
            return 0;
        }

        int cpIdx = E.aFCP(C.fSlot[fi]);
        E.eb(0xB4); // GETFIELD
        E.eSBE(cpIdx);
        // Stack: obj consumed, value pushed = net 0
        if (C.fArrKind[fi] != 0) return C.fArrKind[fi];
        return 1;
    }

    static int eSFldAcc(int ci, int fieldNm) {
        int fi = -1;
        for (int f = 0; f < C.fCount; f++) {
            if (C.fName[f] == fieldNm && C.fStatic[f]) {
                if (C.fClass[f] == ci) { fi = f; break; }
                if (fi < 0) fi = f;
            }
        }
        if (fi < 0) {
            Lexer.error(207);
            return 0;
        }

        if (Tk.type == Tk.ASSIGN) {
            Lexer.nextToken();
            pExpr();
            int cpIdx = E.aFCP(C.fSlot[fi]);
            E.eb(0xB3); // PUTSTATIC
            E.eSBE(cpIdx);
            E.pop();
            return 0;
        }
        if (Tk.type >= Tk.PLUS_EQ && Tk.type <= Tk.USHR_EQ) {
            int op = Tk.type;
            Lexer.nextToken();
            int cpIdx = E.aFCP(C.fSlot[fi]);
            E.eb(0xB2); // GETSTATIC
            E.eSBE(cpIdx);
            E.push();
            pExpr();
            E.eCO(op);
            E.pop();
            E.eb(0x59); E.push(); // DUP
            E.eb(0xB3); // PUTSTATIC
            E.eSBE(cpIdx);
            E.pop();
            return 1;
        }
        if (Tk.type == Tk.INC || Tk.type == Tk.DEC) {
            int op = Tk.type;
            Lexer.nextToken();
            int cpIdx = E.aFCP(C.fSlot[fi]);
            E.eb(0xB2); // GETSTATIC
            E.eSBE(cpIdx);
            E.push();
            E.eb(0x59); E.push(); // DUP
            E.eb(0x04); E.push(); // ICONST_1
            E.eb(op == Tk.INC ? 0x60 : 0x64); E.pop(); // IADD/ISUB
            E.eb(0xB3); // PUTSTATIC
            E.eSBE(cpIdx);
            E.pop();
            return 1;
        }

        int cpIdx = E.aFCP(C.fSlot[fi]);
        E.eb(0xB2); // GETSTATIC
        E.eSBE(cpIdx);
        E.push();
        if (C.fArrKind[fi] != 0) return C.fArrKind[fi];
        return 1;
    }


    // ==================== EMIT HELPERS ====================





}
