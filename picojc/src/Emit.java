public class Emit {
    static void emit() {
        Compiler.codeLen = 0;
        Compiler.cpSize = 0;
        Compiler.excCount = 0;
        autoCtorsEmitted = false;

        // Skip to class bodies and emit methods
        while (Token.type != Token.TOK_EOF) {
            emitClassMethods();
        }

        // Emit auto-generated default constructors after all user methods
        emitAutoConstructors();
    }

    static void emitClassMethods() {
        // Skip to class body
        while (Token.type != Token.TOK_LBRACE && Token.type != Token.TOK_EOF) {
            Lexer.nextToken();
        }
        if (Token.type == Token.TOK_EOF) return;
        Lexer.nextToken(); // skip {

        // We're inside a class body
        int ci = findCurrentClass();
        Compiler.curClass = ci;

        while (Token.type != Token.TOK_RBRACE && Token.type != Token.TOK_EOF) {
            emitMember(ci);
        }
        if (Token.type == Token.TOK_RBRACE) Lexer.nextToken();
    }

    static int emitClassIdx; // tracking which class we're emitting
    static int findCurrentClass() {
        // Match by position - classes appear in source order
        for (int ci = Compiler.userClassStart; ci < Compiler.classCount; ci++) {
            if (Compiler.classBodyStart[ci] >= 0 && Lexer.pos >= Compiler.classBodyStart[ci] &&
                (Compiler.classBodyEnd[ci] < 0 || Lexer.pos <= Compiler.classBodyEnd[ci])) {
                return ci;
            }
        }
        return Compiler.userClassStart;
    }

    static void emitMember(int ci) {
        // Skip modifiers
        boolean isStat = false;
        boolean isNat = false;
        boolean isAbstract = false;
        while (Token.type == Token.TOK_PUBLIC || Token.type == Token.TOK_PRIVATE ||
               Token.type == Token.TOK_PROTECTED || Token.type == Token.TOK_STATIC ||
               Token.type == Token.TOK_FINAL || Token.type == Token.TOK_NATIVE ||
               Token.type == Token.TOK_ABSTRACT) {
            if (Token.type == Token.TOK_STATIC) isStat = true;
            if (Token.type == Token.TOK_NATIVE) isNat = true;
            if (Token.type == Token.TOK_ABSTRACT) isAbstract = true;
            Lexer.nextToken();
        }

        // Static init block
        if (isStat && Token.type == Token.TOK_LBRACE) {
            int mi = findMethod(ci, Compiler.N_CLINIT);
            if (mi >= 0) emitMethodBody(mi);
            else { Lexer.nextToken(); Catalog.skipBlock(); Lexer.expect(Token.TOK_RBRACE); }
            return;
        }

        // Constructor check
        if (Token.type == Token.TOK_IDENT) {
            int nm = Compiler.internBuf(Token.strBuf, Token.strLen);
            if (nm == Compiler.className[ci]) {
                Lexer.save();
                Lexer.nextToken();
                if (Token.type == Token.TOK_LPAREN) {
                    int mi = findConstructor(ci);
                    if (mi >= 0) emitMethodBody(mi);
                    else skipMethodDecl();
                    return;
                }
                Lexer.restore();
                Token.strLen = Compiler.nameLen[nm];
                Native.arraycopy(Compiler.namePool, Compiler.nameOff[nm], Token.strBuf, 0, Token.strLen);
                Token.type = Token.TOK_IDENT;
            }
        }

        // Return type
        Catalog.skipType();

        // Name
        int nm = Compiler.internBuf(Token.strBuf, Token.strLen);
        Lexer.nextToken();

        if (Token.type == Token.TOK_LPAREN) {
            // Method
            int mi = findMethod(ci, nm);
            if (mi >= 0 && !Compiler.methodIsNative[mi] && Compiler.methodBodyStart[mi] >= 0) {
                emitMethodBody(mi);
            } else {
                skipMethodDecl();
            }
        } else {
            // Field - skip to semicolon
            while (Token.type != Token.TOK_SEMI && Token.type != Token.TOK_EOF) {
                Lexer.nextToken();
            }
            Lexer.expect(Token.TOK_SEMI);
        }
    }

    static void skipMethodDecl() {
        // Skip parameter list
        Lexer.expect(Token.TOK_LPAREN);
        while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
            Lexer.nextToken();
        }
        Lexer.expect(Token.TOK_RPAREN);
        if (Token.type == Token.TOK_SEMI) {
            Lexer.nextToken(); // native/abstract method
        } else {
            Lexer.expect(Token.TOK_LBRACE);
            Catalog.skipBlock();
            Lexer.expect(Token.TOK_RBRACE);
        }
    }

    static int findMethod(int ci, int nm) {
        for (int mi = 0; mi < Compiler.methodCount; mi++) {
            if (Compiler.methodClass[mi] == ci && Compiler.methodName[mi] == nm && !Compiler.methodIsNative[mi]) {
                return mi;
            }
        }
        return -1;
    }

    static int findConstructor(int ci) {
        for (int mi = 0; mi < Compiler.methodCount; mi++) {
            if (Compiler.methodClass[mi] == ci && Compiler.methodIsConstructor[mi] && !Compiler.methodIsNative[mi]) {
                return mi;
            }
        }
        return -1;
    }


    static void emitMethodBody(int mi) {
        Compiler.curMethod = mi;
        Compiler.curMethodIsStatic = Compiler.methodIsStatic[mi];
        Compiler.mcodeLen = 0;
        Compiler.patchCount = 0;
        Compiler.labelCount = 0;
        Compiler.localCount = 0;
        Compiler.localNextSlot = 0;
        Compiler.loopDepth = 0;
        Compiler.stackDepth = 0;
        Compiler.maxStack = 0;

        // Init per-method CP
        Compiler.cpMethodCount = 0;
        Compiler.cpMethodBase = Compiler.cpSize;

        // Set up parameters as locals
        if (Compiler.methodIsConstructor[mi]) {
            // Constructor: skip params in source, set up 'this'
            addLocal(Compiler.N_INIT, 0); // 'this'
            // Skip to body
            Lexer.expect(Token.TOK_LPAREN);
            while (Token.type != Token.TOK_RPAREN) {
                // Type
                int pType = parseTypeForLocal();
                // Name
                int pNm = Compiler.internBuf(Token.strBuf, Token.strLen);
                Lexer.nextToken();
                addLocal(pNm, pType);
                if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
            }
            Lexer.expect(Token.TOK_RPAREN);

            // Emit super() call
            emitByte(0x2A); // ALOAD_0 (this)
            pushStack();
            int objInitMi = Compiler.ensureNative(Compiler.N_OBJECT, Compiler.N_INIT);
            int cpIdx = allocCP(objInitMi);
            emitByte(0xB7); // INVOKESPECIAL
            emitShortBE(cpIdx);
            popStack(); // 'this' consumed

            // Parse body
            Lexer.expect(Token.TOK_LBRACE);
            Stmt.parseBlock();
            Lexer.expect(Token.TOK_RBRACE);

            // Emit RETURN
            emitByte(0xB1);
        } else if (Compiler.methodName[mi] == Compiler.N_CLINIT) {
            // Save current lexer position (at '{' of static block)
            int clinitPos = Lexer.pos;
            int clinitLine = Lexer.line;
            int clinitTok = Token.type;
            // Emit inline static field initializers first
            for (int fi = 0; fi < Compiler.fieldCount; fi++) {
                if (Compiler.fieldClass[fi] == Compiler.curClass && Compiler.fieldIsStatic[fi] && Compiler.fieldInitPos[fi] >= 0) {
                    Lexer.pos = Compiler.fieldInitPos[fi];
                    Lexer.line = Compiler.fieldInitLine[fi];
                    Lexer.nextToken();
                    Expr.parseExpression();
                    int sfSlot = Compiler.fieldSlot[fi];
                    int cpIdx2 = allocCP(sfSlot);
                    emitByte(0xB3); // PUTSTATIC
                    emitShortBE(cpIdx2);
                    popStack();
                }
            }
            // Restore lexer to static block body
            Lexer.pos = clinitPos;
            Lexer.line = clinitLine;
            Token.type = clinitTok;
            // Static initializer body: { ... }
            Lexer.nextToken(); // skip {
            Stmt.parseBlock();
            Lexer.expect(Token.TOK_RBRACE);
            emitByte(0xB1); // RETURN
        } else {
            // Regular method
            if (!Compiler.curMethodIsStatic) {
                addLocal(Compiler.internStr("this"), 1); // 'this' is slot 0
            }

            // Parse parameters
            Lexer.expect(Token.TOK_LPAREN);
            while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
                int pType = parseTypeForLocal();
                int pNm = Compiler.internBuf(Token.strBuf, Token.strLen);
                Lexer.nextToken();
                addLocal(pNm, pType);
                if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
            }
            Lexer.expect(Token.TOK_RPAREN);

            // Parse body
            Lexer.expect(Token.TOK_LBRACE);
            Stmt.parseBlock();
            Lexer.expect(Token.TOK_RBRACE);

            // If method doesn't end with return, add implicit return
            if (Compiler.mcodeLen == 0 || (Compiler.mcode[Compiler.mcodeLen - 1] & 0xFF) != 0xB1 &&
                (Compiler.mcode[Compiler.mcodeLen - 1] & 0xFF) != 0xAC && (Compiler.mcode[Compiler.mcodeLen - 1] & 0xFF) != 0xB0) {
                emitByte(0xB1); // RETURN
            }
        }

        // Resolve backpatches
        for (int i = 0; i < Compiler.patchCount; i++) {
            int loc = Compiler.patchLoc[i];
            int lbl = Compiler.patchLabel[i];
            int target = Compiler.labelAddr[lbl];
            int offset = target - (loc - 1); // relative to branch opcode
            Compiler.mcode[loc] = (byte) ((offset >> 8) & 0xFF);
            Compiler.mcode[loc + 1] = (byte) (offset & 0xFF);
        }

        commitMethodCode(mi);
        Compiler.methodMaxLocals[mi] = Compiler.localNextSlot > 0 ? Compiler.localNextSlot : 1;
        Compiler.methodMaxStack[mi] = Compiler.maxStack > 0 ? Compiler.maxStack : 1;
    }

    static void commitMethodCode(int mi) {
        Compiler.methodCodeOff[mi] = Compiler.codeLen;
        for (int i = 0; i < Compiler.mcodeLen; i++) {
            Native.poke(Compiler.codeBase + Compiler.codeLen, Compiler.mcode[i] & 0xFF); Compiler.codeLen++;
        }
        Compiler.methodCpBase[mi] = Compiler.cpMethodBase;
        for (int i = 0; i < Compiler.cpMethodCount; i++) {
            Compiler.cpEntries[Compiler.cpMethodBase + i] = (byte) Compiler.cpMethodVals[i];
        }
        Compiler.cpSize = Compiler.cpMethodBase + Compiler.cpMethodCount;
    }

    static boolean autoCtorsEmitted;

    static void emitAutoConstructors() {
        if (autoCtorsEmitted) return;
        autoCtorsEmitted = true;

        for (int mi = 0; mi < Compiler.methodCount; mi++) {
            if (Compiler.methodIsConstructor[mi] && !Compiler.methodIsNative[mi] && Compiler.methodBodyStart[mi] == -2) {
                // Auto-generated default constructor
                Compiler.curMethod = mi;
                Compiler.mcodeLen = 0;
                Compiler.cpMethodCount = 0;
                Compiler.cpMethodBase = Compiler.cpSize;
                Compiler.patchCount = 0;
                Compiler.labelCount = 0;

                // ALOAD_0, INVOKESPECIAL Object.<init>, RETURN
                emitByte(0x2A); // ALOAD_0
                int objInitMi = Compiler.ensureNative(Compiler.N_OBJECT, Compiler.N_INIT);
                int cpIdx = allocCP(objInitMi);
                emitByte(0xB7); // INVOKESPECIAL
                emitShortBE(cpIdx);
                emitByte(0xB1); // RETURN

                commitMethodCode(mi);
                Compiler.methodMaxLocals[mi] = 1;
                Compiler.methodMaxStack[mi] = 1;
            }
            // Synthetic <clinit>: only field initializers, no explicit body
            if (Compiler.methodName[mi] == Compiler.N_CLINIT && !Compiler.methodIsNative[mi] && Compiler.methodBodyStart[mi] == -2) {
                Compiler.curMethod = mi;
                Compiler.curClass = Compiler.methodClass[mi];
                Compiler.curMethodIsStatic = true;
                Compiler.mcodeLen = 0;
                Compiler.cpMethodCount = 0;
                Compiler.cpMethodBase = Compiler.cpSize;
                Compiler.patchCount = 0;
                Compiler.labelCount = 0;
                Compiler.localCount = 0;
                Compiler.stackDepth = 0;
                Compiler.maxStack = 0;

                // Emit field initializers
                for (int fi = 0; fi < Compiler.fieldCount; fi++) {
                    if (Compiler.fieldClass[fi] == Compiler.curClass && Compiler.fieldIsStatic[fi] && Compiler.fieldInitPos[fi] >= 0) {
                        Lexer.pos = Compiler.fieldInitPos[fi];
                        Lexer.line = Compiler.fieldInitLine[fi];
                        Lexer.nextToken();
                        Expr.parseExpression();
                        int sfSlot = Compiler.fieldSlot[fi];
                        int cpIdx2 = allocCP(sfSlot);
                        emitByte(0xB3); // PUTSTATIC
                        emitShortBE(cpIdx2);
                        popStack();
                    }
                }
                emitByte(0xB1); // RETURN

                commitMethodCode(mi);
                Compiler.methodMaxLocals[mi] = 1;
                Compiler.methodMaxStack[mi] = Compiler.maxStack > 0 ? Compiler.maxStack : 1;
            }
        }
    }

    static int parseTypeForLocal() {
        // Returns: 0=int, 1=ref, 3=int[], 4=byte[], 5=char[]
        int baseType = 0;
        int elemKind = 0; // 0=int-like, 1=byte, 2=char
        if (Token.type == Token.TOK_BYTE || Token.type == Token.TOK_BOOLEAN) {
            elemKind = 1; Lexer.nextToken();
        } else if (Token.type == Token.TOK_CHAR) {
            elemKind = 2; Lexer.nextToken();
        } else if (Token.type == Token.TOK_INT || Token.type == Token.TOK_SHORT) {
            elemKind = 0; Lexer.nextToken();
        } else {
            baseType = 1; // reference
            Lexer.nextToken();
        }
        // Array dimensions
        int dimCount = 0;
        while (Token.type == Token.TOK_LBRACKET) {
            Lexer.nextToken();
            if (Token.type == Token.TOK_RBRACKET) Lexer.nextToken();
            dimCount++;
        }
        if (dimCount > 0) {
            if (baseType == 1 || dimCount > 1) return 1; // Object[]/multi-dim = reference
            if (elemKind == 1) return 4; // byte[]
            if (elemKind == 2) return 5; // char[]
            return 3; // int[]
        }
        return baseType; // 0=int, 1=ref
    }


    static void emitByte(int b) {
        Compiler.mcode[Compiler.mcodeLen++] = (byte)(b & 0xFF);
    }

    static void emitShortBE(int s) {
        Compiler.mcode[Compiler.mcodeLen++] = (byte)((s >> 8) & 0xFF);
        Compiler.mcode[Compiler.mcodeLen++] = (byte)(s & 0xFF);
    }

    static void emitIntBE(int v) {
        Compiler.mcode[Compiler.mcodeLen++] = (byte)((v >> 24) & 0xFF);
        Compiler.mcode[Compiler.mcodeLen++] = (byte)((v >> 16) & 0xFF);
        Compiler.mcode[Compiler.mcodeLen++] = (byte)((v >> 8) & 0xFF);
        Compiler.mcode[Compiler.mcodeLen++] = (byte)(v & 0xFF);
    }

    static int emitBranchPlaceholder() {
        int loc = Compiler.mcodeLen;
        emitByte(0);
        emitByte(0);
        return loc;
    }

    static int newLabel() {
        return Compiler.labelCount++;
    }

    static void setLabel(int lbl) {
        Compiler.labelAddr[lbl] = Compiler.mcodeLen;
    }

    static void emitBranch(int opcode, int label) {
        int branchPC = Compiler.mcodeLen;
        emitByte(opcode);
        int loc = emitBranchPlaceholder();
        Compiler.patchLoc[Compiler.patchCount] = loc;
        Compiler.patchLabel[Compiler.patchCount] = label;
        Compiler.patchCount++;
    }

    static void pushStack() {
        Compiler.stackDepth++;
        if (Compiler.stackDepth > Compiler.maxStack) Compiler.maxStack = Compiler.stackDepth;
    }

    static void popStack() {
        if (Compiler.stackDepth > 0) Compiler.stackDepth--;
    }

    static void addLocal(int nm, int type) {
        Compiler.localName[Compiler.localCount] = nm;
        Compiler.localSlot[Compiler.localCount] = Compiler.localCount;
        Compiler.localType[Compiler.localCount] = type;
        Compiler.localCount++;
        if (Compiler.localCount > Compiler.localNextSlot) Compiler.localNextSlot = Compiler.localCount;
    }

    static int findLocal(int nm) {
        for (int i = Compiler.localCount - 1; i >= 0; i--) {
            if (Compiler.localName[i] == nm) return i;
        }
        return -1;
    }

    static int allocCP(int resolvedVal) {
        // Check for existing entry with same value
        for (int i = 0; i < Compiler.cpMethodCount; i++) {
            if (Compiler.cpMethodVals[i] == resolvedVal && Compiler.cpMethodKeys[i] == resolvedVal) {
                return i;
            }
        }
        int idx = Compiler.cpMethodCount++;
        Compiler.cpMethodVals[idx] = resolvedVal;
        Compiler.cpMethodKeys[idx] = resolvedVal;
        return idx;
    }

    static int allocFieldCP(int fieldSlotVal) {
        return allocCP(fieldSlotVal);
    }

    static int allocClassCP(int classId) {
        return allocCP(classId);
    }

    static int allocStringCP(byte[] buf, int len) {
        int strIdx = -1;
        for (int i = 0; i < Compiler.strConstCount; i++) {
            if (Compiler.strConstLen[i] == len && Native.memcmp(Compiler.strConsts[i], 0, buf, 0, len) == 0) {
                strIdx = i; break;
            }
        }
        if (strIdx < 0) {
            strIdx = Compiler.strConstCount++;
            Compiler.strConsts[strIdx] = new byte[len];
            Native.arraycopy(buf, 0, Compiler.strConsts[strIdx], 0, len);
            Compiler.strConstLen[strIdx] = len;
        }
        return allocCP(0x80 | strIdx);
    }

    static int allocIntConstCP(int val) {
        // Find or add integer constant
        int idx = -1;
        for (int i = 0; i < Compiler.intConstCount; i++) {
            if (Compiler.intConsts[i] == val) { idx = i; break; }
        }
        if (idx < 0) {
            idx = Compiler.intConstCount++;
            Compiler.intConsts[idx] = val;
        }
        return allocCP(idx);
    }


    static void emitIntConst(int val) {
        if (val >= -1 && val <= 5) {
            emitByte(0x03 + val); // ICONST_M1=0x02 .. ICONST_5=0x08
        } else if (val >= -128 && val <= 127) {
            emitByte(0x10); // BIPUSH
            emitByte(val & 0xFF);
        } else if (val >= -32768 && val <= 32767) {
            emitByte(0x11); // SIPUSH
            emitShortBE(val);
        } else {
            // LDC with integer constant
            int cpIdx = allocIntConstCP(val);
            emitByte(0x12); // LDC
            emitByte(cpIdx);
        }
    }

    static void emitLoad(int slot, int type) {
        if (type != 0) {
            // Reference (type 1=ref, 3=int[], 4=byte[], 5=char[])
            if (slot <= 3) emitByte(0x2A + slot); // ALOAD_0..3
            else { emitByte(0x19); emitByte(slot); } // ALOAD
        } else {
            // Int
            if (slot <= 3) emitByte(0x1A + slot); // ILOAD_0..3
            else { emitByte(0x15); emitByte(slot); } // ILOAD
        }
    }

    static void emitStore(int slot, int type) {
        if (type != 0) {
            // Reference (type 1=ref, 3=int[], 4=byte[], 5=char[])
            if (slot <= 3) emitByte(0x4B + slot); // ASTORE_0..3
            else { emitByte(0x3A); emitByte(slot); } // ASTORE
        } else {
            if (slot <= 3) emitByte(0x3B + slot); // ISTORE_0..3
            else { emitByte(0x36); emitByte(slot); } // ISTORE
        }
    }

    static void emitCompoundOp(int tok) {
        if (tok == Token.TOK_PLUS_EQ) emitByte(0x60); // IADD
        else if (tok == Token.TOK_MINUS_EQ) emitByte(0x64); // ISUB
        else if (tok == Token.TOK_STAR_EQ) emitByte(0x68); // IMUL
        else if (tok == Token.TOK_SLASH_EQ) emitByte(0x6C); // IDIV
        else if (tok == Token.TOK_PERCENT_EQ) emitByte(0x70); // IREM
        else if (tok == Token.TOK_AMP_EQ) emitByte(0x7E); // IAND
        else if (tok == Token.TOK_PIPE_EQ) emitByte(0x80); // IOR
        else if (tok == Token.TOK_CARET_EQ) emitByte(0x82); // IXOR
        else if (tok == Token.TOK_SHL_EQ) emitByte(0x78); // ISHL
        else if (tok == Token.TOK_SHR_EQ) emitByte(0x7A); // ISHR
        else if (tok == Token.TOK_USHR_EQ) emitByte(0x7C); // IUSHR
    }
}
