public class Catalog {
    static void catalog() {
        C.uClsStart = C.cCount;
        while (Tk.type != Tk.EOF) {
            catClass();
        }
    }

    static void catClass() {
        // Skip modifiers: public, abstract, final
        while (Tk.type == Tk.PUBLIC || Tk.type == Tk.ABSTRACT ||
               Tk.type == Tk.FINAL) {
            Lexer.nextToken();
        }

        boolean isIface = false;
        if (Tk.type == Tk.INTERFACE) {
            isIface = true;
            Lexer.nextToken();
        } else if (Tk.type == Tk.CLASS) {
            Lexer.nextToken();
        } else {
            Lexer.error(Tk.CLASS);
            return;
        }

        // Class/interface name
        int nm = C.intern(Tk.strBuf, Tk.strLen);
        Lexer.nextToken();

        int ci = C.cCount++;
        C.cName[ci] = nm;
        C.cParent[ci] = -1; // Object by default
        C.cIsIface[ci] = isIface;
        C.cClinit[ci] = 0xFF;
        C.cIfaceS[ci] = C.ifListLen;
        C.cIfaceC[ci] = 0;
        C.cOwnF[ci] = 0;

        // extends?
        if (Tk.type == Tk.EXTENDS) {
            Lexer.nextToken();
            int parentNm = C.intern(Tk.strBuf, Tk.strLen);
            Lexer.nextToken();
            // Resolve parent later; store name for now
            C.cParent[ci] = parentNm; // store as name index, resolve in Pass 2
        }

        // implements?
        if (Tk.type == Tk.IMPLEMENTS) {
            Lexer.nextToken();
            while (Tk.type == Tk.IDENT || Tk.type == Tk.STRING_KW) {
                int ifNm = C.intern(Tk.strBuf, Tk.strLen);
                C.ifList[C.ifListLen++] = ifNm; // store as name, resolve later
                C.cIfaceC[ci]++;
                Lexer.nextToken();
                if (Tk.type == Tk.COMMA) Lexer.nextToken();
                else break;
            }
        }

        Lexer.expect(Tk.LBRACE);
        C.cBodyS[ci] = Lexer.pos;

        // Scan class body for fields and methods
        while (Tk.type != Tk.RBRACE && Tk.type != Tk.EOF) {
            catMember(ci);
        }
        C.cBodyE[ci] = Lexer.pos;
        Lexer.expect(Tk.RBRACE);
    }

    static void catMember(int ci) {
        // Collect modifiers
        boolean isStat = false;
        boolean isNat = false;
        boolean isAbstract = false;
        while (Tk.type == Tk.PUBLIC || Tk.type == Tk.PRIVATE ||
               Tk.type == Tk.PROTECTED || Tk.type == Tk.STATIC ||
               Tk.type == Tk.FINAL || Tk.type == Tk.NATIVE ||
               Tk.type == Tk.ABSTRACT) {
            if (Tk.type == Tk.STATIC) isStat = true;
            if (Tk.type == Tk.NATIVE) isNat = true;
            if (Tk.type == Tk.ABSTRACT) isAbstract = true;
            Lexer.nextToken();
        }

        // Static initializer block: static { ... }
        if (isStat && Tk.type == Tk.LBRACE) {
            int mi;
            if (C.cClinit[ci] != 0xFF && C.mBodyS[C.cClinit[ci]] == -2) {
                // Reuse synthetic <clinit> (created for field initializers)
                mi = C.cClinit[ci];
            } else {
                mi = C.mCount++;
                C.mClass[mi] = ci;
                C.mName[mi] = C.N_CLINIT;
                C.mArgC[mi] = 0;
                C.mStatic[mi] = true;
                C.mIsCtor[mi] = false;
                C.mNative[mi] = false;
                C.mRetT[mi] = 0; // void
                C.mVtSlot[mi] = 0xFF;
                C.mVmid[mi] = 0xFF;
                C.mExcC[mi] = 0;
                C.cClinit[ci] = mi;
            }
            Lexer.nextToken(); // skip {
            C.mBodyS[mi] = Lexer.pos;
            skipBlk();
            C.mBodyE[mi] = Lexer.pos;
            Lexer.expect(Tk.RBRACE);
            return;
        }

        // Return type or constructor
        int retType = 0; // 0=void, 1=int, 2=ref
        int arrayKind = 0; // 0=non-array, 4=byte[], 5=char[]
        boolean isCtor = false;
        int retTypeToken = Tk.type;

        // Check for constructor: ClassName(
        if (Tk.type == Tk.IDENT) {
            int nm = C.intern(Tk.strBuf, Tk.strLen);
            if (nm == C.cName[ci]) {
                // Might be constructor or field/method named same as class
                Lexer.save();
                Lexer.nextToken();
                if (Tk.type == Tk.LPAREN) {
                    // It's a constructor
                    isCtor = true;
                    retType = 0; // void
                    catMethod(ci, nm, isStat, isCtor, isNat, isAbstract, retType);
                    return;
                }
                // Not a constructor, restore
                Lexer.restore();
                Tk.type = Tk.IDENT;
                Tk.strLen = C.nLen[nm];
                Native.arraycopy(C.nPool, C.nOff[nm], Tk.strBuf, 0, Tk.strLen);
            }
        }

        // Parse return type
        if (Tk.type == Tk.VOID) { retType = 0; Lexer.nextToken(); }
        else if (Tk.type == Tk.INT || Tk.type == Tk.BYTE ||
                 Tk.type == Tk.CHAR || Tk.type == Tk.SHORT ||
                 Tk.type == Tk.BOOLEAN) {
            retType = 1; // int-like
            Lexer.nextToken();
            // Check for array type (supports multi-dimensional)
            {
                int dimCount = 0;
                while (Tk.type == Tk.LBRACKET) {
                    Lexer.nextToken();
                    Lexer.expect(Tk.RBRACKET);
                    dimCount++;
                    if (retType == 1) {
                        if (retTypeToken == Tk.BYTE || retTypeToken == Tk.BOOLEAN) arrayKind = 4;
                        else if (retTypeToken == Tk.CHAR) arrayKind = 5;
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
        else if (Tk.type == Tk.STRING_KW || Tk.type == Tk.IDENT) {
            retType = 2; // reference
            Lexer.nextToken();
            // Check for array type (supports multi-dimensional)
            while (Tk.type == Tk.LBRACKET) {
                Lexer.nextToken();
                Lexer.expect(Tk.RBRACKET);
            }
        }
        else {
            Lexer.error(100);
            return;
        }

        // Name
        int nm = C.intern(Tk.strBuf, Tk.strLen);
        Lexer.nextToken();

        // Method or field?
        if (Tk.type == Tk.LPAREN) {
            // Method
            catMethod(ci, nm, isStat, false, isNat, isAbstract, retType);
        } else {
            // Field
            catField(ci, nm, isStat, retType, arrayKind);
        }
    }

    static void catField(int ci, int nm, boolean isStat, int retType, int arrKind) {
        int fi = C.fCount++;
        C.fClass[fi] = ci;
        C.fName[fi] = nm;
        C.fStatic[fi] = isStat;
        C.fArrKind[fi] = arrKind;
        C.fSlot[fi] = -1;
        C.fInitPos[fi] = -1;
        C.fInitLn[fi] = 0;
        if (!isStat) C.cOwnF[ci]++;

        // Check for initializer
        if (Tk.type == Tk.ASSIGN && isStat) {
            // Save position right after '=' (before the value token)
            // Lexer.pos is already past '=' since ASSIGN was scanned
            C.fInitPos[fi] = Lexer.pos;
            C.fInitLn[fi] = Lexer.line;
            // Ensure class has a <clinit> for field initializer emission
            if (C.cClinit[ci] == 0xFF) {
                // Create synthetic <clinit> (bodyStart=-2 = synthetic marker)
                int smi = C.mCount++;
                C.mClass[smi] = ci;
                C.mName[smi] = C.N_CLINIT;
                C.mArgC[smi] = 0;
                C.mStatic[smi] = true;
                C.mIsCtor[smi] = false;
                C.mNative[smi] = false;
                C.mRetT[smi] = 0;
                C.mVtSlot[smi] = 0xFF;
                C.mVmid[smi] = 0xFF;
                C.mExcC[smi] = 0;
                C.mBodyS[smi] = -2; // synthetic: no explicit body
                C.mBodyE[smi] = -2;
                C.cClinit[ci] = smi;
            }
        }

        // Skip initializer and comma-separated declarations
        while (Tk.type != Tk.SEMI && Tk.type != Tk.EOF) {
            if (Tk.type == Tk.COMMA) {
                Lexer.nextToken();
                // Another field with same type
                int nm2 = C.intern(Tk.strBuf, Tk.strLen);
                Lexer.nextToken();
                int fi2 = C.fCount++;
                C.fClass[fi2] = ci;
                C.fName[fi2] = nm2;
                C.fStatic[fi2] = isStat;
                C.fArrKind[fi2] = arrKind;
                C.fSlot[fi2] = -1;
                C.fInitPos[fi2] = -1;
                C.fInitLn[fi2] = 0;
                if (!isStat) C.cOwnF[ci]++;
            } else {
                Lexer.nextToken();
            }
        }
        Lexer.expect(Tk.SEMI);
    }

    static void catMethod(int ci, int nm, boolean isStat, boolean isCtor,
                               boolean isNat, boolean isAbstract, int retType) {
        int mi = C.mCount++;
        C.mClass[mi] = ci;
        C.mName[mi] = isCtor ? C.N_INIT : nm;
        C.mStatic[mi] = isStat;
        C.mIsCtor[mi] = isCtor;
        C.mNative[mi] = isNat;
        C.mRetT[mi] = retType;
        C.mVtSlot[mi] = 0xFF;
        C.mVmid[mi] = 0xFF;
        C.mExcC[mi] = 0;

        // Parse parameters
        Lexer.expect(Tk.LPAREN);
        int argc = isStat ? 0 : 1; // instance methods have 'this' as arg 0
        while (Tk.type != Tk.RPAREN && Tk.type != Tk.EOF) {
            // Skip type
            skipTy();
            // Skip name
            Lexer.nextToken();
            argc++;
            if (Tk.type == Tk.COMMA) Lexer.nextToken();
        }
        Lexer.expect(Tk.RPAREN);
        C.mArgC[mi] = argc;

        if (isNat || isAbstract || C.cIsIface[ci]) {
            // Native/abstract/interface method: no body
            Lexer.expect(Tk.SEMI);
            C.mBodyS[mi] = -1;
            C.mBodyE[mi] = -1;
        } else {
            // Parse body
            Lexer.expect(Tk.LBRACE);
            C.mBodyS[mi] = Lexer.pos;
            skipBlk();
            C.mBodyE[mi] = Lexer.pos;
            Lexer.expect(Tk.RBRACE);
        }
    }

    static void skipTy() {
        // Skip a type declaration (int, String, ClassName, arrays)
        Lexer.nextToken(); // consume type keyword/name
        while (Tk.type == Tk.LBRACKET) {
            Lexer.nextToken(); // [
            if (Tk.type == Tk.RBRACKET) Lexer.nextToken(); // ]
        }
    }

    static void skipBlk() {
        // Skip brace-balanced block content (we're just past the opening {)
        int depth = 1;
        while (depth > 0 && Tk.type != Tk.EOF) {
            if (Tk.type == Tk.LBRACE) depth++;
            else if (Tk.type == Tk.RBRACE) {
                depth--;
                if (depth == 0) return; // don't consume the closing }
            }
            Lexer.nextToken();
        }
    }

}
