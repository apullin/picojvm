public class Catalog {
    static void catalog() {
        Compiler.userClassStart = Compiler.classCount;
        while (Token.type != Token.TOK_EOF) {
            catalogClassOrInterface();
        }
    }

    static void catalogClassOrInterface() {
        // Skip modifiers: public, abstract, final
        while (Token.type == Token.TOK_PUBLIC || Token.type == Token.TOK_ABSTRACT ||
               Token.type == Token.TOK_FINAL) {
            Lexer.nextToken();
        }

        boolean isIface = false;
        if (Token.type == Token.TOK_INTERFACE) {
            isIface = true;
            Lexer.nextToken();
        } else if (Token.type == Token.TOK_CLASS) {
            Lexer.nextToken();
        } else {
            Lexer.error(Token.TOK_CLASS);
            return;
        }

        // Class/interface name
        int nm = Compiler.internBuf(Token.strBuf, Token.strLen);
        Lexer.nextToken();

        int ci = Compiler.classCount++;
        Compiler.className[ci] = nm;
        Compiler.classParent[ci] = -1; // Object by default
        Compiler.classIsInterface[ci] = isIface;
        Compiler.classClinitMi[ci] = 0xFF;
        Compiler.classIfaceStart[ci] = Compiler.ifaceListLen;
        Compiler.classIfaceCount[ci] = 0;
        Compiler.classOwnFields[ci] = 0;

        // extends?
        if (Token.type == Token.TOK_EXTENDS) {
            Lexer.nextToken();
            int parentNm = Compiler.internBuf(Token.strBuf, Token.strLen);
            Lexer.nextToken();
            // Resolve parent later; store name for now
            Compiler.classParent[ci] = parentNm; // store as name index, resolve in Pass 2
        }

        // implements?
        if (Token.type == Token.TOK_IMPLEMENTS) {
            Lexer.nextToken();
            while (Token.type == Token.TOK_IDENT || Token.type == Token.TOK_STRING_KW) {
                int ifNm = Compiler.internBuf(Token.strBuf, Token.strLen);
                Compiler.ifaceList[Compiler.ifaceListLen++] = ifNm; // store as name, resolve later
                Compiler.classIfaceCount[ci]++;
                Lexer.nextToken();
                if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
                else break;
            }
        }

        Lexer.expect(Token.TOK_LBRACE);
        Compiler.classBodyStart[ci] = Lexer.pos;

        // Scan class body for fields and methods
        while (Token.type != Token.TOK_RBRACE && Token.type != Token.TOK_EOF) {
            catalogMember(ci);
        }
        Compiler.classBodyEnd[ci] = Lexer.pos;
        Lexer.expect(Token.TOK_RBRACE);
    }

    static void catalogMember(int ci) {
        // Collect modifiers
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

        // Static initializer block: static { ... }
        if (isStat && Token.type == Token.TOK_LBRACE) {
            int mi;
            if (Compiler.classClinitMi[ci] != 0xFF && Compiler.methodBodyStart[Compiler.classClinitMi[ci]] == -2) {
                // Reuse synthetic <clinit> (created for field initializers)
                mi = Compiler.classClinitMi[ci];
            } else {
                mi = Compiler.methodCount++;
                Compiler.methodClass[mi] = ci;
                Compiler.methodName[mi] = Compiler.N_CLINIT;
                Compiler.methodArgCount[mi] = 0;
                Compiler.methodIsStatic[mi] = true;
                Compiler.methodIsConstructor[mi] = false;
                Compiler.methodIsNative[mi] = false;
                Compiler.methodRetType[mi] = 0; // void
                Compiler.methodVtableSlot[mi] = 0xFF;
                Compiler.methodVmid[mi] = 0xFF;
                Compiler.methodExcCount[mi] = 0;
                Compiler.classClinitMi[ci] = mi;
            }
            Lexer.nextToken(); // skip {
            Compiler.methodBodyStart[mi] = Lexer.pos;
            skipBlock();
            Compiler.methodBodyEnd[mi] = Lexer.pos;
            Lexer.expect(Token.TOK_RBRACE);
            return;
        }

        // Return type or constructor
        int retType = 0; // 0=void, 1=int, 2=ref
        int arrayKind = 0; // 0=non-array, 4=byte[], 5=char[]
        boolean isCtor = false;
        int retTypeToken = Token.type;

        // Check for constructor: ClassName(
        if (Token.type == Token.TOK_IDENT) {
            int nm = Compiler.internBuf(Token.strBuf, Token.strLen);
            if (nm == Compiler.className[ci]) {
                // Might be constructor or field/method named same as class
                Lexer.save();
                Lexer.nextToken();
                if (Token.type == Token.TOK_LPAREN) {
                    // It's a constructor
                    isCtor = true;
                    retType = 0; // void
                    catalogMethod(ci, nm, isStat, isCtor, isNat, isAbstract, retType);
                    return;
                }
                // Not a constructor, restore
                Lexer.restore();
                Token.type = Token.TOK_IDENT;
                Token.strLen = Compiler.nameLen[nm];
                Native.arraycopy(Compiler.namePool, Compiler.nameOff[nm], Token.strBuf, 0, Token.strLen);
            }
        }

        // Parse return type
        if (Token.type == Token.TOK_VOID) { retType = 0; Lexer.nextToken(); }
        else if (Token.type == Token.TOK_INT || Token.type == Token.TOK_BYTE ||
                 Token.type == Token.TOK_CHAR || Token.type == Token.TOK_SHORT ||
                 Token.type == Token.TOK_BOOLEAN) {
            retType = 1; // int-like
            Lexer.nextToken();
            // Check for array type (supports multi-dimensional)
            {
                int dimCount = 0;
                while (Token.type == Token.TOK_LBRACKET) {
                    Lexer.nextToken();
                    Lexer.expect(Token.TOK_RBRACKET);
                    dimCount++;
                    if (retType == 1) {
                        if (retTypeToken == Token.TOK_BYTE || retTypeToken == Token.TOK_BOOLEAN) arrayKind = 4;
                        else if (retTypeToken == Token.TOK_CHAR) arrayKind = 5;
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
        else if (Token.type == Token.TOK_STRING_KW || Token.type == Token.TOK_IDENT) {
            retType = 2; // reference
            Lexer.nextToken();
            // Check for array type (supports multi-dimensional)
            while (Token.type == Token.TOK_LBRACKET) {
                Lexer.nextToken();
                Lexer.expect(Token.TOK_RBRACKET);
            }
        }
        else {
            Lexer.error(100);
            return;
        }

        // Name
        int nm = Compiler.internBuf(Token.strBuf, Token.strLen);
        Lexer.nextToken();

        // Method or field?
        if (Token.type == Token.TOK_LPAREN) {
            // Method
            catalogMethod(ci, nm, isStat, false, isNat, isAbstract, retType);
        } else {
            // Field
            catalogField(ci, nm, isStat, retType, arrayKind);
        }
    }

    static void catalogField(int ci, int nm, boolean isStat, int retType, int arrKind) {
        int fi = Compiler.fieldCount++;
        Compiler.fieldClass[fi] = ci;
        Compiler.fieldName[fi] = nm;
        Compiler.fieldIsStatic[fi] = isStat;
        Compiler.fieldArrayKind[fi] = arrKind;
        Compiler.fieldSlot[fi] = -1;
        Compiler.fieldInitPos[fi] = -1;
        Compiler.fieldInitLine[fi] = 0;
        if (!isStat) Compiler.classOwnFields[ci]++;

        // Check for initializer
        if (Token.type == Token.TOK_ASSIGN && isStat) {
            // Save position right after '=' (before the value token)
            // Lexer.pos is already past '=' since TOK_ASSIGN was scanned
            Compiler.fieldInitPos[fi] = Lexer.pos;
            Compiler.fieldInitLine[fi] = Lexer.line;
            // Ensure class has a <clinit> for field initializer emission
            if (Compiler.classClinitMi[ci] == 0xFF) {
                // Create synthetic <clinit> (bodyStart=-2 = synthetic marker)
                int smi = Compiler.methodCount++;
                Compiler.methodClass[smi] = ci;
                Compiler.methodName[smi] = Compiler.N_CLINIT;
                Compiler.methodArgCount[smi] = 0;
                Compiler.methodIsStatic[smi] = true;
                Compiler.methodIsConstructor[smi] = false;
                Compiler.methodIsNative[smi] = false;
                Compiler.methodRetType[smi] = 0;
                Compiler.methodVtableSlot[smi] = 0xFF;
                Compiler.methodVmid[smi] = 0xFF;
                Compiler.methodExcCount[smi] = 0;
                Compiler.methodBodyStart[smi] = -2; // synthetic: no explicit body
                Compiler.methodBodyEnd[smi] = -2;
                Compiler.classClinitMi[ci] = smi;
            }
        }

        // Skip initializer and comma-separated declarations
        while (Token.type != Token.TOK_SEMI && Token.type != Token.TOK_EOF) {
            if (Token.type == Token.TOK_COMMA) {
                Lexer.nextToken();
                // Another field with same type
                int nm2 = Compiler.internBuf(Token.strBuf, Token.strLen);
                Lexer.nextToken();
                int fi2 = Compiler.fieldCount++;
                Compiler.fieldClass[fi2] = ci;
                Compiler.fieldName[fi2] = nm2;
                Compiler.fieldIsStatic[fi2] = isStat;
                Compiler.fieldArrayKind[fi2] = arrKind;
                Compiler.fieldSlot[fi2] = -1;
                Compiler.fieldInitPos[fi2] = -1;
                Compiler.fieldInitLine[fi2] = 0;
                if (!isStat) Compiler.classOwnFields[ci]++;
            } else {
                Lexer.nextToken();
            }
        }
        Lexer.expect(Token.TOK_SEMI);
    }

    static void catalogMethod(int ci, int nm, boolean isStat, boolean isCtor,
                               boolean isNat, boolean isAbstract, int retType) {
        int mi = Compiler.methodCount++;
        Compiler.methodClass[mi] = ci;
        Compiler.methodName[mi] = isCtor ? Compiler.N_INIT : nm;
        Compiler.methodIsStatic[mi] = isStat;
        Compiler.methodIsConstructor[mi] = isCtor;
        Compiler.methodIsNative[mi] = isNat;
        Compiler.methodRetType[mi] = retType;
        Compiler.methodVtableSlot[mi] = 0xFF;
        Compiler.methodVmid[mi] = 0xFF;
        Compiler.methodExcCount[mi] = 0;

        // Parse parameters
        Lexer.expect(Token.TOK_LPAREN);
        int argc = isStat ? 0 : 1; // instance methods have 'this' as arg 0
        while (Token.type != Token.TOK_RPAREN && Token.type != Token.TOK_EOF) {
            // Skip type
            skipType();
            // Skip name
            Lexer.nextToken();
            argc++;
            if (Token.type == Token.TOK_COMMA) Lexer.nextToken();
        }
        Lexer.expect(Token.TOK_RPAREN);
        Compiler.methodArgCount[mi] = argc;

        if (isNat || isAbstract || Compiler.classIsInterface[ci]) {
            // Native/abstract/interface method: no body
            Lexer.expect(Token.TOK_SEMI);
            Compiler.methodBodyStart[mi] = -1;
            Compiler.methodBodyEnd[mi] = -1;
        } else {
            // Parse body
            Lexer.expect(Token.TOK_LBRACE);
            Compiler.methodBodyStart[mi] = Lexer.pos;
            skipBlock();
            Compiler.methodBodyEnd[mi] = Lexer.pos;
            Lexer.expect(Token.TOK_RBRACE);
        }
    }

    static void skipType() {
        // Skip a type declaration (int, String, ClassName, arrays)
        Lexer.nextToken(); // consume type keyword/name
        while (Token.type == Token.TOK_LBRACKET) {
            Lexer.nextToken(); // [
            if (Token.type == Token.TOK_RBRACKET) Lexer.nextToken(); // ]
        }
    }

    static void skipBlock() {
        // Skip brace-balanced block content (we're just past the opening {)
        int depth = 1;
        while (depth > 0 && Token.type != Token.TOK_EOF) {
            if (Token.type == Token.TOK_LBRACE) depth++;
            else if (Token.type == Token.TOK_RBRACE) {
                depth--;
                if (depth == 0) return; // don't consume the closing }
            }
            Lexer.nextToken();
        }
    }

}
