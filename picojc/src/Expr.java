public class Expr {
    // Returns: 0=void, 1=int, 2=ref

    static int parseExpression() {
        return parseTernary();
    }

    static int parseTernary() {
        int type = parseOr();
        if (Token.type == Token.TOK_QUESTION) {
            Lexer.nextToken();
            Emit.popStack();
            int lblFalse = Emit.newLabel();
            int lblEnd = Emit.newLabel();
            Emit.emitBranch(0x99, lblFalse); // IFEQ → false
            int tType = parseExpression();
            Emit.emitBranch(0xA7, lblEnd); // GOTO end
            Emit.popStack();
            Lexer.expect(Token.TOK_COLON);
            Emit.setLabel(lblFalse);
            int fType = parseExpression();
            Emit.setLabel(lblEnd);
            type = tType;
        }
        return type;
    }

    static int parseOr() {
        int type = parseAnd();
        while (Token.type == Token.TOK_OR) {
            Lexer.nextToken();
            int lblTrue = Emit.newLabel();
            int lblEnd = Emit.newLabel();
            // Short-circuit: if left is true, skip right
            Emit.emitByte(0x59); // DUP
            Emit.pushStack();
            Emit.emitBranch(0x9A, lblTrue); // IFNE → true
            Emit.popStack();
            Emit.emitByte(0x57); // POP
            Emit.popStack();
            parseAnd();
            Emit.emitBranch(0xA7, lblEnd);
            Emit.setLabel(lblTrue);
            Emit.setLabel(lblEnd);
        }
        return type;
    }

    static int parseAnd() {
        int type = parseBitOr();
        while (Token.type == Token.TOK_AND) {
            Lexer.nextToken();
            int lblFalse = Emit.newLabel();
            int lblEnd = Emit.newLabel();
            Emit.emitByte(0x59); // DUP
            Emit.pushStack();
            Emit.emitBranch(0x99, lblFalse); // IFEQ → false
            Emit.popStack();
            Emit.emitByte(0x57); // POP
            Emit.popStack();
            parseBitOr();
            Emit.emitBranch(0xA7, lblEnd);
            Emit.setLabel(lblFalse);
            Emit.setLabel(lblEnd);
        }
        return type;
    }

    static int parseBitOr() {
        int type = parseBitXor();
        while (Token.type == Token.TOK_PIPE) {
            Lexer.nextToken();
            parseBitXor();
            Emit.emitByte(0x80); // IOR
            Emit.popStack();
        }
        return type;
    }

    static int parseBitXor() {
        int type = parseBitAnd();
        while (Token.type == Token.TOK_CARET) {
            Lexer.nextToken();
            parseBitAnd();
            Emit.emitByte(0x82); // IXOR
            Emit.popStack();
        }
        return type;
    }

    static int parseBitAnd() {
        int type = parseEquality();
        while (Token.type == Token.TOK_AMP) {
            Lexer.nextToken();
            parseEquality();
            Emit.emitByte(0x7E); // IAND
            Emit.popStack();
        }
        return type;
    }

    static int parseEquality() {
        int type = parseComparison();
        while (Token.type == Token.TOK_EQ || Token.type == Token.TOK_NE) {
            int op = Token.type;
            Lexer.nextToken();
            int rtype = parseComparison();
            Emit.popStack(); Emit.popStack();
            if (type == 2 || rtype == 2) {
                // Reference comparison
                int lbl = Emit.newLabel();
                int lblEnd = Emit.newLabel();
                Emit.emitBranch(op == Token.TOK_EQ ? 0xA5 : 0xA6, lbl); // IF_ACMPEQ/NE
                Emit.emitByte(0x03); // ICONST_0
                Emit.pushStack();
                Emit.emitBranch(0xA7, lblEnd);
                Emit.setLabel(lbl);
                Emit.emitByte(0x04); // ICONST_1
                Emit.pushStack();
                Emit.setLabel(lblEnd);
            } else {
                int lbl = Emit.newLabel();
                int lblEnd = Emit.newLabel();
                Emit.emitBranch(op == Token.TOK_EQ ? 0x9F : 0xA0, lbl); // IF_ICMPEQ/NE
                Emit.emitByte(0x03); Emit.pushStack();
                Emit.emitBranch(0xA7, lblEnd);
                Emit.setLabel(lbl);
                Emit.emitByte(0x04); Emit.pushStack();
                Emit.setLabel(lblEnd);
            }
            type = 1;
        }
        return type;
    }

    static int parseComparison() {
        int type = parseShift();
        while (Token.type == Token.TOK_LT || Token.type == Token.TOK_GT ||
               Token.type == Token.TOK_LE || Token.type == Token.TOK_GE ||
               Token.type == Token.TOK_INSTANCEOF) {
            if (Token.type == Token.TOK_INSTANCEOF) {
                Lexer.nextToken();
                int classNm = Compiler.internBuf(Token.strBuf, Token.strLen);
                Lexer.nextToken();
                Emit.popStack();
                int ci = Resolver.findClassByName(classNm);
                int cpIdx = Emit.allocClassCP(ci >= 0 ? ci : 0);
                Emit.emitByte(0xC1); // INSTANCEOF
                Emit.emitShortBE(cpIdx);
                Emit.pushStack();
                type = 1;
                continue;
            }
            int op = Token.type;
            Lexer.nextToken();
            parseShift();
            Emit.popStack(); Emit.popStack();

            int branchOp;
            if (op == Token.TOK_LT) branchOp = 0xA1; // IF_ICMPLT
            else if (op == Token.TOK_GE) branchOp = 0xA2; // IF_ICMPGE
            else if (op == Token.TOK_GT) branchOp = 0xA3; // IF_ICMPGT
            else branchOp = 0xA4; // IF_ICMPLE

            int lbl = Emit.newLabel();
            int lblEnd = Emit.newLabel();
            Emit.emitBranch(branchOp, lbl);
            Emit.emitByte(0x03); Emit.pushStack(); // ICONST_0
            Emit.emitBranch(0xA7, lblEnd);
            Emit.setLabel(lbl);
            Emit.emitByte(0x04); Emit.pushStack(); // ICONST_1
            Emit.setLabel(lblEnd);
            type = 1;
        }
        return type;
    }

    static int parseShift() {
        int type = parseAdditive();
        while (Token.type == Token.TOK_SHL || Token.type == Token.TOK_SHR ||
               Token.type == Token.TOK_USHR) {
            int op = Token.type;
            Lexer.nextToken();
            parseAdditive();
            Emit.popStack();
            if (op == Token.TOK_SHL) Emit.emitByte(0x78); // ISHL
            else if (op == Token.TOK_SHR) Emit.emitByte(0x7A); // ISHR
            else Emit.emitByte(0x7C); // IUSHR
        }
        return type;
    }

    static int parseAdditive() {
        int type = parseMultiplicative();
        while (Token.type == Token.TOK_PLUS || Token.type == Token.TOK_MINUS) {
            int op = Token.type;
            Lexer.nextToken();
            parseMultiplicative();
            Emit.popStack();
            Emit.emitByte(op == Token.TOK_PLUS ? 0x60 : 0x64); // IADD / ISUB
        }
        return type;
    }

    static int parseMultiplicative() {
        int type = parseUnary();
        while (Token.type == Token.TOK_STAR || Token.type == Token.TOK_SLASH ||
               Token.type == Token.TOK_PERCENT) {
            int op = Token.type;
            Lexer.nextToken();
            parseUnary();
            Emit.popStack();
            if (op == Token.TOK_STAR) Emit.emitByte(0x68); // IMUL
            else if (op == Token.TOK_SLASH) Emit.emitByte(0x6C); // IDIV
            else Emit.emitByte(0x70); // IREM
        }
        return type;
    }

    static int parseUnary() {
        if (Token.type == Token.TOK_MINUS) {
            Lexer.nextToken();
            if (Token.type == Token.TOK_INT_LIT) {
                // Negative literal
                Token.intValue = -Token.intValue;
                return parsePrimary();
            }
            parseUnary();
            Emit.emitByte(0x74); // INEG
            return 1;
        }
        if (Token.type == Token.TOK_TILDE) {
            Lexer.nextToken();
            parseUnary();
            // ~x = x ^ (-1)
            Emit.emitByte(0x02); // ICONST_M1
            Emit.pushStack();
            Emit.emitByte(0x82); // IXOR
            Emit.popStack();
            return 1;
        }
        if (Token.type == Token.TOK_BANG) {
            Lexer.nextToken();
            parseUnary();
            // !x: if x == 0, push 1, else push 0
            Emit.popStack();
            int lbl = Emit.newLabel();
            int lblEnd = Emit.newLabel();
            Emit.emitBranch(0x99, lbl); // IFEQ
            Emit.emitByte(0x03); Emit.pushStack(); // ICONST_0
            Emit.emitBranch(0xA7, lblEnd);
            Emit.setLabel(lbl);
            Emit.emitByte(0x04); Emit.pushStack(); // ICONST_1
            Emit.setLabel(lblEnd);
            return 1;
        }
        if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
            int op = Token.type;
            Lexer.nextToken();
            int nm = Compiler.internBuf(Token.strBuf, Token.strLen);
            Lexer.nextToken();
            int li = Emit.findLocal(nm);
            if (li >= 0) {
                int slot = Compiler.localSlot[li];
                // Pre-increment: load, add/sub 1, dup, store
                Emit.emitLoad(slot, 0);
                Emit.pushStack();
                Emit.emitByte(0x04); Emit.pushStack(); // ICONST_1
                Emit.emitByte(op == Token.TOK_INC ? 0x60 : 0x64); // IADD/ISUB
                Emit.popStack();
                Emit.emitByte(0x59); Emit.pushStack(); // DUP
                Emit.emitStore(slot, 0);
                Emit.popStack();
                return 1;
            }
            Lexer.error(201);
            return 1;
        }
        if (Token.type == Token.TOK_LPAREN) {
            // Check for cast: (Type)expr
            Lexer.save();
            Lexer.nextToken();
            if (Stmt.isTypeToken(Token.type) || Token.type == Token.TOK_IDENT) {
                int castType = Token.type;
                int castNm = -1;
                if (Token.type == Token.TOK_IDENT) castNm = Compiler.internBuf(Token.strBuf, Token.strLen);
                Lexer.nextToken();
                if (Token.type == Token.TOK_RPAREN) {
                    // It's a cast
                    Lexer.nextToken();
                    parseUnary();
                    // Emit cast instruction
                    if (castType == Token.TOK_BYTE) Emit.emitByte(0x91); // I2B
                    else if (castType == Token.TOK_CHAR) Emit.emitByte(0x92); // I2C
                    else if (castType == Token.TOK_SHORT) Emit.emitByte(0x93); // I2S
                    else if (castType == Token.TOK_IDENT && castNm >= 0) {
                        // Object cast = CHECKCAST
                        int ci = Resolver.findClassByName(castNm);
                        int cpIdx = Emit.allocClassCP(ci >= 0 ? ci : 0);
                        Emit.emitByte(0xC0); // CHECKCAST
                        Emit.emitShortBE(cpIdx);
                    }
                    return castType == Token.TOK_IDENT ? 2 : 1;
                }
                // Not a cast, restore and parse as parenthesized expression
            }
            Lexer.restore();
            Lexer.nextToken(); // skip (
            int type = parseExpression();
            Lexer.expect(Token.TOK_RPAREN);
            return parsePostfix(type);
        }
        return parsePostfix(parsePrimary());
    }

    static int parsePostfix(int type) {
        while (true) {
            if (Token.type == Token.TOK_DOT) {
                Lexer.nextToken();
                int memberNm = Compiler.internBuf(Token.strBuf, Token.strLen);
                Lexer.nextToken();

                if (memberNm == Compiler.N_LENGTH && Token.type != Token.TOK_LPAREN) {
                    // array.length
                    Emit.emitByte(0xBE); // ARRAYLENGTH
                    type = 1;
                } else if (Token.type == Token.TOK_LPAREN) {
                    // Method call on object
                    type = emitMethodCall(type, memberNm, false);
                } else {
                    // Field access
                    type = emitFieldAccess(memberNm, false);
                }
            }
            else if (Token.type == Token.TOK_LBRACKET) {
                // Array access
                Lexer.nextToken();
                parseExpression();
                Lexer.expect(Token.TOK_RBRACKET);
                Emit.popStack(); // index

                // Check for store
                if (Token.type == Token.TOK_ASSIGN) {
                    Lexer.nextToken();
                    parseExpression();
                    Emit.popStack(); // value
                    Emit.popStack(); // array ref
                    if (type == 4) Emit.emitByte(0x54);       // BASTORE (byte[])
                    else if (type == 5) Emit.emitByte(0x55);  // CASTORE (char[])
                    else Emit.emitByte(0x4F);                  // IASTORE (int[]/ref[])
                    type = 0; // void
                } else if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
                    // Array element post-increment: arr[idx]++
                    // Stack: [arrRef, idx] (idx was popped from tracker but still on JVM stack)
                    int op = Token.type;
                    Lexer.nextToken();
                    Emit.pushStack(); // re-count idx
                    Emit.emitByte(0x5C); Emit.pushStack(); Emit.pushStack(); // DUP2
                    if (type == 4) Emit.emitByte(0x33);       // BALOAD
                    else if (type == 5) Emit.emitByte(0x34);  // CALOAD
                    else Emit.emitByte(0x2E);                  // IALOAD
                    Emit.popStack(); // IALOAD consumes dup'd pair, pushes value: net -1
                    Emit.emitByte(0x5B); Emit.pushStack(); // DUP_X2: old value below arrRef,idx,new
                    Emit.emitByte(0x04); Emit.pushStack(); // ICONST_1
                    Emit.emitByte(op == Token.TOK_INC ? 0x60 : 0x64); Emit.popStack(); // IADD/ISUB
                    if (type == 4) Emit.emitByte(0x54);       // BASTORE
                    else if (type == 5) Emit.emitByte(0x55);  // CASTORE
                    else Emit.emitByte(0x4F);                  // IASTORE
                    Emit.popStack(); Emit.popStack(); Emit.popStack(); // IASTORE consumes arrRef,idx,value
                    type = 1; // old value remains on stack
                } else {
                    if (type == 4) Emit.emitByte(0x33);       // BALOAD (byte[])
                    else if (type == 5) Emit.emitByte(0x34);  // CALOAD (char[])
                    else Emit.emitByte(0x2E);                  // IALOAD (int[]/ref[])
                    // Propagate inner type for multi-dim arrays
                    if (type == 6) type = 4;             // byte[][] elem → byte[]
                    else if (type == 7) type = 5;        // char[][] elem → char[]
                    else type = 1;
                }
            }
            else if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
                // Post-increment/decrement in general postfix position
                Lexer.nextToken();
                type = 1;
            }
            else if (Token.type == Token.TOK_ASSIGN ||
                     (Token.type >= Token.TOK_PLUS_EQ && Token.type <= Token.TOK_USHR_EQ)) {
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

    static int parsePrimary() {
        if (Token.type == Token.TOK_INT_LIT || Token.type == Token.TOK_CHAR_LIT) {
            int val = Token.intValue;
            Lexer.nextToken();
            Emit.emitIntConst(val);
            Emit.pushStack();
            return 1;
        }
        if (Token.type == Token.TOK_STR_LIT) {
            byte[] buf = new byte[Token.strLen];
            Native.arraycopy(Token.strBuf, 0, buf, 0, Token.strLen);
            int cpIdx = Emit.allocStringCP(buf, Token.strLen);
            Lexer.nextToken();
            Emit.emitByte(0x12); // LDC
            Emit.emitByte(cpIdx);
            Emit.pushStack();
            return 2; // reference
        }
        if (Token.type == Token.TOK_TRUE) {
            Lexer.nextToken();
            Emit.emitByte(0x04); // ICONST_1
            Emit.pushStack();
            return 1;
        }
        if (Token.type == Token.TOK_FALSE) {
            Lexer.nextToken();
            Emit.emitByte(0x03); // ICONST_0
            Emit.pushStack();
            return 1;
        }
        if (Token.type == Token.TOK_NULL) {
            Lexer.nextToken();
            Emit.emitByte(0x01); // ACONST_NULL
            Emit.pushStack();
            return 2;
        }
        if (Token.type == Token.TOK_THIS) {
            Lexer.nextToken();
            Emit.emitLoad(0, 1); // ALOAD_0
            Emit.pushStack();
            return 2;
        }
        if (Token.type == Token.TOK_NEW) {
            return parseNew();
        }
        if (Token.type == Token.TOK_IDENT || Token.type == Token.TOK_STRING_KW) {
            int nm = Compiler.internBuf(Token.strBuf, Token.strLen);
            Lexer.nextToken();

            // Check for static method call or field access: Name.member
            if (Token.type == Token.TOK_DOT) {
                // Could be ClassName.method() or ClassName.field
                int ci = Resolver.findClassByName(nm);
                if (ci >= 0 || nm == Compiler.N_NATIVE || nm == Compiler.N_STRING) {
                    Lexer.nextToken();
                    int memberNm = Compiler.internBuf(Token.strBuf, Token.strLen);
                    Lexer.nextToken();

                    if (Token.type == Token.TOK_LPAREN) {
                        // Static method call
                        return emitStaticCall(nm, memberNm);
                    } else {
                        // Static field access
                        return emitStaticFieldAccess(ci, memberNm);
                    }
                }
            }

            // Check for local variable
            int li = Emit.findLocal(nm);
            if (li >= 0) {
                int slot = Compiler.localSlot[li];
                int ltype = Compiler.localType[li];

                // Check for assignment
                if (Token.type == Token.TOK_ASSIGN) {
                    Lexer.nextToken();
                    parseExpression();
                    Emit.emitByte(0x59); Emit.pushStack(); // DUP (keep value on stack)
                    Emit.emitStore(slot, ltype);
                    Emit.popStack();
                    return ltype == 1 ? 2 : 1;
                }
                if (Token.type >= Token.TOK_PLUS_EQ && Token.type <= Token.TOK_USHR_EQ) {
                    int op = Token.type;
                    Lexer.nextToken();
                    Emit.emitLoad(slot, ltype);
                    Emit.pushStack();
                    parseExpression();
                    Emit.emitCompoundOp(op);
                    Emit.popStack();
                    Emit.emitByte(0x59); Emit.pushStack(); // DUP
                    Emit.emitStore(slot, ltype);
                    Emit.popStack();
                    return 1;
                }
                if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
                    // Post-increment: load value, then increment
                    Emit.emitLoad(slot, 0);
                    Emit.pushStack();
                    int op = Token.type;
                    Lexer.nextToken();
                    // Use IINC for efficiency
                    Emit.emitByte(0x84); // IINC
                    Emit.emitByte(slot);
                    Emit.emitByte(op == Token.TOK_INC ? 1 : 0xFF); // +1 or -1
                    return 1;
                }

                Emit.emitLoad(slot, ltype);
                Emit.pushStack();
                // Map local type to expression type
                // 0=int→1, 1=ref→2, 3=int[]→3, 4=byte[]→4, 5=char[]→5
                if (ltype >= 3) return ltype;
                return ltype == 1 ? 2 : 1;
            }

            // Check for instance field (implicit this)
            if (!Compiler.curMethodIsStatic) {
                int fi = Resolver.findField(Compiler.curClass, nm);
                if (fi >= 0 && !Compiler.fieldIsStatic[fi]) {
                    // Check for assignment
                    if (Token.type == Token.TOK_ASSIGN) {
                        Lexer.nextToken();
                        Emit.emitLoad(0, 1); // ALOAD_0 (this)
                        Emit.pushStack();
                        parseExpression();
                        int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
                        Emit.emitByte(0xB5); // PUTFIELD
                        Emit.emitShortBE(cpIdx);
                        Emit.popStack(); Emit.popStack(); // obj + value consumed
                        // Push value back for expression result
                        // Actually for statement context this is void
                        return 0;
                    }
                    if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
                        int op = Token.type;
                        Lexer.nextToken();
                        int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
                        // Post-inc/dec: load old value, then update field
                        Emit.emitLoad(0, 1); Emit.pushStack(); // ALOAD_0
                        Emit.emitByte(0xB4); Emit.emitShortBE(cpIdx); // GETFIELD (old value)
                        // Keep old value for expression result
                        Emit.emitLoad(0, 1); Emit.pushStack(); // ALOAD_0 again
                        Emit.emitLoad(0, 1); Emit.pushStack(); // ALOAD_0 again
                        Emit.emitByte(0xB4); Emit.emitShortBE(cpIdx); // GETFIELD again
                        Emit.emitByte(0x04); Emit.pushStack(); // ICONST_1
                        Emit.emitByte(op == Token.TOK_INC ? 0x60 : 0x64); Emit.popStack(); // IADD/ISUB
                        Emit.emitByte(0xB5); Emit.emitShortBE(cpIdx); // PUTFIELD
                        Emit.popStack(); Emit.popStack(); // obj + value consumed by PUTFIELD
                        return 1; // old value remains on stack
                    }
                    if (Token.type >= Token.TOK_PLUS_EQ && Token.type <= Token.TOK_USHR_EQ) {
                        int op = Token.type;
                        Lexer.nextToken();
                        int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
                        Emit.emitLoad(0, 1); Emit.pushStack(); // ALOAD_0
                        Emit.emitLoad(0, 1); Emit.pushStack(); // ALOAD_0
                        Emit.emitByte(0xB4); Emit.emitShortBE(cpIdx); // GETFIELD
                        parseExpression();
                        Emit.emitCompoundOp(op);
                        Emit.popStack();
                        Emit.emitByte(0xB5); Emit.emitShortBE(cpIdx); // PUTFIELD
                        Emit.popStack(); Emit.popStack();
                        return 0;
                    }
                    Emit.emitLoad(0, 1); // ALOAD_0 (this)
                    Emit.pushStack();
                    int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
                    Emit.emitByte(0xB4); // GETFIELD
                    Emit.emitShortBE(cpIdx);
                    // stack: -1 (obj) +1 (value) = net 0
                    if (Compiler.fieldArrayKind[fi] != 0) return Compiler.fieldArrayKind[fi];
                    return 1;
                }
            }

            // Check for static field (prefer current class first)
            {
                int sfi = -1;
                for (int fi2 = 0; fi2 < Compiler.fieldCount; fi2++) {
                    if (Compiler.fieldName[fi2] == nm && Compiler.fieldIsStatic[fi2]) {
                        if (Compiler.fieldClass[fi2] == Compiler.curClass) { sfi = fi2; break; }
                        if (sfi < 0) sfi = fi2;
                    }
                }
                if (sfi >= 0) {
                    int fi = sfi;
                    if (Token.type == Token.TOK_ASSIGN) {
                        Lexer.nextToken();
                        parseExpression();
                        Emit.emitByte(0x59); Emit.pushStack(); // DUP
                        int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
                        Emit.emitByte(0xB3); // PUTSTATIC
                        Emit.emitShortBE(cpIdx);
                        Emit.popStack();
                        return 1;
                    }
                    if (Token.type >= Token.TOK_PLUS_EQ && Token.type <= Token.TOK_USHR_EQ) {
                        int op = Token.type;
                        Lexer.nextToken();
                        int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
                        Emit.emitByte(0xB2); // GETSTATIC
                        Emit.emitShortBE(cpIdx);
                        Emit.pushStack();
                        parseExpression();
                        Emit.emitCompoundOp(op);
                        Emit.popStack();
                        Emit.emitByte(0x59); Emit.pushStack(); // DUP
                        Emit.emitByte(0xB3); // PUTSTATIC
                        Emit.emitShortBE(cpIdx);
                        Emit.popStack();
                        return 1;
                    }
                    if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
                        int op = Token.type;
                        Lexer.nextToken();
                        int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
                        Emit.emitByte(0xB2); // GETSTATIC
                        Emit.emitShortBE(cpIdx);
                        Emit.pushStack();
                        // Post: value before inc/dec is on stack
                        Emit.emitByte(0x59); Emit.pushStack(); // DUP
                        Emit.emitByte(0x04); Emit.pushStack(); // ICONST_1
                        Emit.emitByte(op == Token.TOK_INC ? 0x60 : 0x64); Emit.popStack(); // IADD/ISUB
                        Emit.emitByte(0xB3); // PUTSTATIC
                        Emit.emitShortBE(cpIdx);
                        Emit.popStack();
                        return 1;
                    }
                    int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
                    Emit.emitByte(0xB2); // GETSTATIC
                    Emit.emitShortBE(cpIdx);
                    Emit.pushStack();
                    if (Compiler.fieldArrayKind[fi] != 0) return Compiler.fieldArrayKind[fi];
                    return 1;
                }
            }

            // Check for method call in same class
            if (Token.type == Token.TOK_LPAREN) {
                // Check if it's an instance method (implicit this.method())
                if (!Compiler.curMethodIsStatic) {
                    for (int mi2 = 0; mi2 < Compiler.methodCount; mi2++) {
                        if (Compiler.methodClass[mi2] == Compiler.curClass && Compiler.methodName[mi2] == nm &&
                            !Compiler.methodIsStatic[mi2] && !Compiler.methodIsNative[mi2] && !Compiler.methodIsConstructor[mi2]) {
                            // Instance method: push this, then INVOKEVIRTUAL
                            Emit.emitLoad(0, 1); // ALOAD_0 (this)
                            Emit.pushStack();
                            return emitMethodCall(2, nm, false);
                        }
                    }
                }
                return emitStaticCall(Compiler.className[Compiler.curClass], nm);
            }

            Lexer.error(202); // Undefined identifier
            return 0;
        }
        Lexer.error(203); // Unexpected token
        return 0;
    }

    // ==================== NEW EXPRESSION ====================

    static int parseNew() {
        Lexer.nextToken(); // skip 'new'

        if (Token.type == Token.TOK_INT || Token.type == Token.TOK_BYTE ||
            Token.type == Token.TOK_CHAR || Token.type == Token.TOK_SHORT ||
            Token.type == Token.TOK_BOOLEAN) {
            // Primitive array: new int[size]
            int elemType = Token.type;
            Lexer.nextToken();
            Lexer.expect(Token.TOK_LBRACKET);
            parseExpression();
            Lexer.expect(Token.TOK_RBRACKET);

            int typeCode = 10; // int
            if (elemType == Token.TOK_BYTE) typeCode = 8;
            else if (elemType == Token.TOK_CHAR) typeCode = 5;
            else if (elemType == Token.TOK_SHORT) typeCode = 9;
            else if (elemType == Token.TOK_BOOLEAN) typeCode = 4;

            // Check for 2D array: new type[N][M] or new type[N][]
            if (Token.type == Token.TOK_LBRACKET) {
                Lexer.nextToken();
                if (Token.type == Token.TOK_RBRACKET) {
                    // new type[N][] — array of references, size N
                    Lexer.nextToken();
                    int cpIdx = Emit.allocClassCP(0);
                    Emit.emitByte(0xBD); // ANEWARRAY
                    Emit.emitShortBE(cpIdx);
                    return 2; // reference array
                }
                parseExpression();
                Lexer.expect(Token.TOK_RBRACKET);
                // MULTIANEWARRAY
                int cpIdx = Emit.allocClassCP(0); // type doesn't matter for int[][]
                Emit.emitByte(0xC5); // MULTIANEWARRAY
                Emit.emitShortBE(cpIdx);
                Emit.emitByte(2); // 2 dimensions
                Emit.popStack(); // second dimension
                // First dim still on stack, result replaces it
                return 2;
            }

            Emit.emitByte(0xBC); // NEWARRAY
            Emit.emitByte(typeCode);
            // Stack: count consumed, array ref pushed = net 0
            // Return specific array type for proper BALOAD/BASTORE emission
            if (typeCode == 8 || typeCode == 4) return 4;  // byte[] or boolean[]
            if (typeCode == 5) return 5;  // char[]
            return 3; // int[] (or short[])
        }

        // Object or reference array: new ClassName(...) or new ClassName[size]
        int classNm = Compiler.internBuf(Token.strBuf, Token.strLen);
        Lexer.nextToken();

        if (Token.type == Token.TOK_LBRACKET) {
            // Reference array: new ClassName[size]
            Lexer.nextToken();
            parseExpression();
            Lexer.expect(Token.TOK_RBRACKET);

            int ci = Resolver.findClassByName(classNm);
            int cpIdx = Emit.allocClassCP(ci >= 0 ? ci : 0);

            // Check for 2D
            if (Token.type == Token.TOK_LBRACKET) {
                Lexer.nextToken();
                parseExpression();
                Lexer.expect(Token.TOK_RBRACKET);
                Emit.emitByte(0xC5); // MULTIANEWARRAY
                Emit.emitShortBE(cpIdx);
                Emit.emitByte(2);
                Emit.popStack();
                return 2;
            }

            Emit.emitByte(0xBD); // ANEWARRAY
            Emit.emitShortBE(cpIdx);
            return 2;
        }

        // Object creation: new ClassName(args)
        int ci = Resolver.findClassByName(classNm);
        if (ci < 0) ci = Resolver.synthesizeExceptionClass(classNm);
        int cpIdx = Emit.allocClassCP(ci);
        Emit.emitByte(0xBB); // NEW
        Emit.emitShortBE(cpIdx);
        Emit.pushStack();
        Emit.emitByte(0x59); // DUP
        Emit.pushStack();

        // Parse constructor arguments
        Lexer.expect(Token.TOK_LPAREN);
        int argc = 1; // 'this' counts
        while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
            parseExpression();
            argc++;
            if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
        }
        Lexer.expect(Token.TOK_RPAREN);

        // Find constructor
        int ctorMi = -1;
        for (int mi = 0; mi < Compiler.methodCount; mi++) {
            if (Compiler.methodClass[mi] == ci && Compiler.methodIsConstructor[mi] && Compiler.methodArgCount[mi] == argc) {
                ctorMi = mi;
                break;
            }
        }
        if (ctorMi < 0) {
            // Try default constructor (0 user args = 1 total with 'this')
            for (int mi = 0; mi < Compiler.methodCount; mi++) {
                if (Compiler.methodClass[mi] == ci && Compiler.methodIsConstructor[mi]) {
                    ctorMi = mi;
                    break;
                }
            }
        }
        if (ctorMi < 0) {
            // Use Object.<init> as fallback
            ctorMi = Compiler.ensureNative(Compiler.N_OBJECT, Compiler.N_INIT);
        }

        int ctorCpIdx = Emit.allocCP(ctorMi);
        Emit.emitByte(0xB7); // INVOKESPECIAL
        Emit.emitShortBE(ctorCpIdx);
        // Pop args + dup from stack, keep original ref
        for (int i = 0; i < argc; i++) Emit.popStack();

        return 2; // reference on stack
    }

    // ==================== METHOD CALLS ====================

    static int emitStaticCall(int classNm, int methodNm) {
        Lexer.expect(Token.TOK_LPAREN);

        // First check native methods
        int nativeMi = Compiler.ensureNative(classNm, methodNm);
        if (nativeMi >= 0) {
            int argc = 0;
            while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
                parseExpression();
                argc++;
                if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
            }
            Lexer.expect(Token.TOK_RPAREN);
            int cpIdx = Emit.allocCP(nativeMi);
            Emit.emitByte(0xB8); // INVOKESTATIC
            Emit.emitShortBE(cpIdx);
            for (int i = 0; i < argc; i++) Emit.popStack();
            int retType = Compiler.methodRetType[nativeMi];
            if (retType != 0) Emit.pushStack();
            return retType;
        }

        // User-defined static method
        int mi = -1;
        for (int m = 0; m < Compiler.methodCount; m++) {
            if (Compiler.methodName[m] == methodNm && !Compiler.methodIsNative[m]) {
                int mc = Compiler.methodClass[m];
                if (mc < Compiler.classCount && Compiler.className[mc] == classNm) {
                    mi = m;
                    break;
                }
            }
        }

        int argc = 0;
        while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
            parseExpression();
            argc++;
            if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
        }
        Lexer.expect(Token.TOK_RPAREN);

        if (mi < 0) {
            Lexer.error(204); // Undefined method
            return 0;
        }

        int cpIdx = Emit.allocCP(mi);
        Emit.emitByte(0xB8); // INVOKESTATIC
        Emit.emitShortBE(cpIdx);
        for (int i = 0; i < argc; i++) Emit.popStack();
        int retType = Compiler.methodRetType[mi];
        if (retType != 0) Emit.pushStack();
        return retType;
    }

    static int emitMethodCall(int objType, int methodNm, boolean isInterface) {
        // Object is already on stack
        Lexer.expect(Token.TOK_LPAREN);

        // Check for String methods
        int nativeMi = Compiler.ensureNative(Compiler.N_STRING, methodNm);

        int argc = 1; // 'this' already on stack
        while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
            parseExpression();
            argc++;
            if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
        }
        Lexer.expect(Token.TOK_RPAREN);

        if (nativeMi >= 0) {
            int cpIdx = Emit.allocCP(nativeMi);
            Emit.emitByte(0xB6); // INVOKEVIRTUAL
            Emit.emitShortBE(cpIdx);
            for (int i = 0; i < argc; i++) Emit.popStack();
            int retType = Compiler.methodRetType[nativeMi];
            if (retType != 0) Emit.pushStack();
            return retType;
        }

        // Find the method in user classes
        int mi = -1;
        for (int m = 0; m < Compiler.methodCount; m++) {
            if (Compiler.methodName[m] == methodNm && !Compiler.methodIsStatic[m] && !Compiler.methodIsNative[m]) {
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
        int mci = Compiler.methodClass[mi];
        if (mci < Compiler.classCount && Compiler.classIsInterface[mci]) {
            useInterface = true;
        }

        if (useInterface) {
            int cpIdx = Emit.allocCP(mi);
            Emit.emitByte(0xB9); // INVOKEINTERFACE
            Emit.emitShortBE(cpIdx);
            Emit.emitByte(argc);
            Emit.emitByte(0);
            for (int i = 0; i < argc; i++) Emit.popStack();
        } else {
            int cpIdx = Emit.allocCP(mi);
            Emit.emitByte(0xB6); // INVOKEVIRTUAL
            Emit.emitShortBE(cpIdx);
            for (int i = 0; i < argc; i++) Emit.popStack();
        }

        int retType = Compiler.methodRetType[mi];
        if (retType != 0) Emit.pushStack();
        return retType;
    }

    // ==================== FIELD ACCESS ====================

    static int emitFieldAccess(int fieldNm, boolean isStore) {
        // Object ref is on stack
        // Find field
        int fi = -1;
        for (int f = 0; f < Compiler.fieldCount; f++) {
            if (Compiler.fieldName[f] == fieldNm && !Compiler.fieldIsStatic[f]) {
                fi = f;
                break;
            }
        }
        if (fi < 0) {
            Lexer.error(206);
            return 0;
        }

        if (Token.type == Token.TOK_ASSIGN) {
            Lexer.nextToken();
            parseExpression();
            int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
            Emit.emitByte(0xB5); // PUTFIELD
            Emit.emitShortBE(cpIdx);
            Emit.popStack(); Emit.popStack();
            return 0;
        }
        if (Token.type >= Token.TOK_PLUS_EQ && Token.type <= Token.TOK_USHR_EQ) {
            int op = Token.type;
            Lexer.nextToken();
            Emit.emitByte(0x59); Emit.pushStack(); // DUP obj ref
            int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
            Emit.emitByte(0xB4); // GETFIELD
            Emit.emitShortBE(cpIdx);
            parseExpression();
            Emit.emitCompoundOp(op);
            Emit.popStack();
            Emit.emitByte(0xB5); // PUTFIELD
            Emit.emitShortBE(cpIdx);
            Emit.popStack(); Emit.popStack();
            return 0;
        }

        int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
        Emit.emitByte(0xB4); // GETFIELD
        Emit.emitShortBE(cpIdx);
        // Stack: obj consumed, value pushed = net 0
        if (Compiler.fieldArrayKind[fi] != 0) return Compiler.fieldArrayKind[fi];
        return 1;
    }

    static int emitStaticFieldAccess(int ci, int fieldNm) {
        int fi = -1;
        for (int f = 0; f < Compiler.fieldCount; f++) {
            if (Compiler.fieldName[f] == fieldNm && Compiler.fieldIsStatic[f]) {
                if (Compiler.fieldClass[f] == ci) { fi = f; break; }
                if (fi < 0) fi = f;
            }
        }
        if (fi < 0) {
            Lexer.error(207);
            return 0;
        }

        if (Token.type == Token.TOK_ASSIGN) {
            Lexer.nextToken();
            parseExpression();
            int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
            Emit.emitByte(0xB3); // PUTSTATIC
            Emit.emitShortBE(cpIdx);
            Emit.popStack();
            return 0;
        }
        if (Token.type >= Token.TOK_PLUS_EQ && Token.type <= Token.TOK_USHR_EQ) {
            int op = Token.type;
            Lexer.nextToken();
            int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
            Emit.emitByte(0xB2); // GETSTATIC
            Emit.emitShortBE(cpIdx);
            Emit.pushStack();
            parseExpression();
            Emit.emitCompoundOp(op);
            Emit.popStack();
            Emit.emitByte(0x59); Emit.pushStack(); // DUP
            Emit.emitByte(0xB3); // PUTSTATIC
            Emit.emitShortBE(cpIdx);
            Emit.popStack();
            return 1;
        }
        if (Token.type == Token.TOK_INC || Token.type == Token.TOK_DEC) {
            int op = Token.type;
            Lexer.nextToken();
            int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
            Emit.emitByte(0xB2); // GETSTATIC
            Emit.emitShortBE(cpIdx);
            Emit.pushStack();
            Emit.emitByte(0x59); Emit.pushStack(); // DUP
            Emit.emitByte(0x04); Emit.pushStack(); // ICONST_1
            Emit.emitByte(op == Token.TOK_INC ? 0x60 : 0x64); Emit.popStack(); // IADD/ISUB
            Emit.emitByte(0xB3); // PUTSTATIC
            Emit.emitShortBE(cpIdx);
            Emit.popStack();
            return 1;
        }

        int cpIdx = Emit.allocFieldCP(Compiler.fieldSlot[fi]);
        Emit.emitByte(0xB2); // GETSTATIC
        Emit.emitShortBE(cpIdx);
        Emit.pushStack();
        if (Compiler.fieldArrayKind[fi] != 0) return Compiler.fieldArrayKind[fi];
        return 1;
    }


    // ==================== EMIT HELPERS ====================





}
