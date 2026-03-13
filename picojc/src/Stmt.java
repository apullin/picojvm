public class Stmt {
    static void parseBlock() {
        while (Token.type != Token.TOK_RBRACE && Token.type != Token.TOK_EOF) {
            parseStatement();
        }
    }

    static void parseStatement() {
        if (Token.type == Token.TOK_LBRACE) {
            Lexer.nextToken();
            int savedLocalCount = Compiler.localCount;
            parseBlock();
            Compiler.localCount = savedLocalCount; // restore scope
            Lexer.expect(Token.TOK_RBRACE);
        }
        else if (Token.type == Token.TOK_IF) {
            parseIf();
        }
        else if (Token.type == Token.TOK_WHILE) {
            parseWhile();
        }
        else if (Token.type == Token.TOK_DO) {
            parseDoWhile();
        }
        else if (Token.type == Token.TOK_FOR) {
            parseFor();
        }
        else if (Token.type == Token.TOK_RETURN) {
            parseReturn();
        }
        else if (Token.type == Token.TOK_BREAK) {
            Lexer.nextToken();
            Lexer.expect(Token.TOK_SEMI);
            if (Compiler.loopDepth > 0) {
                Emit.emitBranch(0xA7, Compiler.loopBreakLabel[Compiler.loopDepth - 1]); // GOTO break
            }
        }
        else if (Token.type == Token.TOK_CONTINUE) {
            Lexer.nextToken();
            Lexer.expect(Token.TOK_SEMI);
            if (Compiler.loopDepth > 0) {
                Emit.emitBranch(0xA7, Compiler.loopContLabel[Compiler.loopDepth - 1]); // GOTO continue
            }
        }
        else if (Token.type == Token.TOK_SWITCH) {
            parseSwitch();
        }
        else if (Token.type == Token.TOK_THROW) {
            parseThrow();
        }
        else if (Token.type == Token.TOK_TRY) {
            parseTryCatch();
        }
        else if (isTypeToken(Token.type)) {
            parseLocalDecl();
        }
        else if (Token.type == Token.TOK_IDENT) {
            // Could be local declaration (ClassName var) or expression statement
            // Peek ahead: if next is ident (or [ ]), it's a declaration
            Lexer.save();
            int nm = Compiler.internBuf(Token.strBuf, Token.strLen);
            int savedType = Token.type;
            Lexer.nextToken();
            if (Token.type == Token.TOK_IDENT ||
                (Token.type == Token.TOK_LBRACKET &&
                 Resolver.findClassByName(nm) >= 0)) {
                // It's a type name followed by a variable name — declaration
                Lexer.restore();
                Token.type = savedType;
                Token.strLen = Compiler.nameLen[nm];
                Native.arraycopy(Compiler.namePool, Compiler.nameOff[nm], Token.strBuf, 0, Token.strLen);
                parseLocalDecl();
            } else {
                // Expression statement
                Lexer.restore();
                Token.type = savedType;
                Token.strLen = Compiler.nameLen[nm];
                Native.arraycopy(Compiler.namePool, Compiler.nameOff[nm], Token.strBuf, 0, Token.strLen);
                parseExpressionStatement();
            }
        }
        else {
            parseExpressionStatement();
        }
    }

    static boolean isTypeToken(int t) {
        return t == Token.TOK_INT || t == Token.TOK_BYTE || t == Token.TOK_CHAR ||
               t == Token.TOK_SHORT || t == Token.TOK_BOOLEAN || t == Token.TOK_VOID ||
               t == Token.TOK_STRING_KW;
    }

    static void parseExpressionStatement() {
        int type = Expr.parseExpression();
        if (type != 0) { // non-void expression, pop result
            Emit.emitByte(0x57); // POP
            Emit.popStack();
        }
        Lexer.expect(Token.TOK_SEMI);
    }

    // ==================== LOCAL VARIABLE DECLARATION ====================

    static void parseLocalDecl() {
        int varType = Emit.parseTypeForLocal(); // 0=int, 1=ref
        do {
            int nm = Compiler.internBuf(Token.strBuf, Token.strLen);
            int slot = Compiler.localCount;
            Emit.addLocal(nm, varType);
            Lexer.nextToken();

            if (Token.type == Token.TOK_ASSIGN) {
                Lexer.nextToken();
                Expr.parseExpression();
                Emit.emitStore(slot, varType);
                Emit.popStack();
            }

            if (Token.type == Token.TOK_COMMA) {
                Lexer.nextToken();
            } else {
                break;
            }
        } while (Token.type == Token.TOK_IDENT);
        Lexer.expect(Token.TOK_SEMI);
    }

    // ==================== CONTROL FLOW ====================

    static void parseIf() {
        Lexer.nextToken(); // skip 'if'
        Lexer.expect(Token.TOK_LPAREN);
        Expr.parseExpression();
        Lexer.expect(Token.TOK_RPAREN);
        Emit.popStack();

        int lblElse = Emit.newLabel();
        Emit.emitBranch(0x99, lblElse); // IFEQ → else

        parseStatement();

        if (Token.type == Token.TOK_ELSE) {
            int lblEnd = Emit.newLabel();
            Emit.emitBranch(0xA7, lblEnd); // GOTO end
            Emit.setLabel(lblElse);
            Lexer.nextToken(); // skip 'else'
            parseStatement();
            Emit.setLabel(lblEnd);
        } else {
            Emit.setLabel(lblElse);
        }
    }

    static void parseWhile() {
        Lexer.nextToken(); // skip 'while'
        int lblTop = Emit.newLabel();
        int lblEnd = Emit.newLabel();
        int lblCont = lblTop;
        Emit.setLabel(lblTop);

        Lexer.expect(Token.TOK_LPAREN);
        Expr.parseExpression();
        Lexer.expect(Token.TOK_RPAREN);
        Emit.popStack();
        Emit.emitBranch(0x99, lblEnd); // IFEQ → end

        Compiler.loopBreakLabel[Compiler.loopDepth] = lblEnd;
        Compiler.loopContLabel[Compiler.loopDepth] = lblCont;
        Compiler.loopDepth++;
        parseStatement();
        Compiler.loopDepth--;

        Emit.emitBranch(0xA7, lblTop); // GOTO top
        Emit.setLabel(lblEnd);
    }

    static void parseDoWhile() {
        Lexer.nextToken(); // skip 'do'
        int lblTop = Emit.newLabel();
        int lblEnd = Emit.newLabel();
        int lblCont = Emit.newLabel();
        Emit.setLabel(lblTop);

        Compiler.loopBreakLabel[Compiler.loopDepth] = lblEnd;
        Compiler.loopContLabel[Compiler.loopDepth] = lblCont;
        Compiler.loopDepth++;
        parseStatement();
        Compiler.loopDepth--;

        Lexer.expect(Token.TOK_WHILE);
        Emit.setLabel(lblCont);
        Lexer.expect(Token.TOK_LPAREN);
        Expr.parseExpression();
        Lexer.expect(Token.TOK_RPAREN);
        Emit.popStack();
        Emit.emitBranch(0x9A, lblTop); // IFNE → top
        Emit.setLabel(lblEnd);
        Lexer.expect(Token.TOK_SEMI);
    }

    static void parseFor() {
        Lexer.nextToken(); // skip 'for'
        Lexer.expect(Token.TOK_LPAREN);

        int savedLocalCount = Compiler.localCount;

        // Init
        if (Token.type != Token.TOK_SEMI) {
            if (isTypeToken(Token.type)) {
                parseLocalDecl(); // includes semicolon
            } else {
                int type = Expr.parseExpression();
                if (type != 0) { Emit.emitByte(0x57); Emit.popStack(); }
                Lexer.expect(Token.TOK_SEMI);
            }
        } else {
            Lexer.nextToken(); // skip ;
        }

        int lblTop = Emit.newLabel();
        int lblEnd = Emit.newLabel();
        int lblUpdate = Emit.newLabel();
        Emit.setLabel(lblTop);

        // Condition
        if (Token.type != Token.TOK_SEMI) {
            Expr.parseExpression();
            Emit.popStack();
            Emit.emitBranch(0x99, lblEnd); // IFEQ → end
        }
        // Save update expression position BEFORE consuming the semicolon
        // Lexer.pos is right after ';', before the update tokens
        int updateStart = Lexer.pos;
        int updateLine = Lexer.line;
        Lexer.expect(Token.TOK_SEMI);

        // Skip update for now — emit it after body
        int parenDepth = 0;
        while (Token.type != Token.TOK_EOF) {
            if (Token.type == Token.TOK_LPAREN) parenDepth++;
            else if (Token.type == Token.TOK_RPAREN) {
                if (parenDepth == 0) break;
                parenDepth--;
            }
            Lexer.nextToken();
        }
        Lexer.expect(Token.TOK_RPAREN);

        // Body
        Compiler.loopBreakLabel[Compiler.loopDepth] = lblEnd;
        Compiler.loopContLabel[Compiler.loopDepth] = lblUpdate;
        Compiler.loopDepth++;
        parseStatement();
        Compiler.loopDepth--;

        // Update
        Emit.setLabel(lblUpdate);
        if (updateStart < Lexer.pos) {
            // Save full lexer + token state
            int savePos = Lexer.pos;
            int saveLine = Lexer.line;
            int saveTokType = Token.type;
            int saveTokInt = Token.intValue;
            int saveTokLine = Token.line;
            int saveTokStrLen = Token.strLen;
            byte[] saveTokStr = new byte[saveTokStrLen];
            Native.arraycopy(Token.strBuf, 0, saveTokStr, 0, saveTokStrLen);

            // Re-lex the update expression
            Lexer.pos = updateStart;
            Lexer.line = updateLine;
            Lexer.nextToken();
            if (Token.type != Token.TOK_RPAREN) {
                int type = Expr.parseExpression();
                if (type != 0) { Emit.emitByte(0x57); Emit.popStack(); }
            }

            // Restore full lexer + token state
            Lexer.pos = savePos;
            Lexer.line = saveLine;
            Token.type = saveTokType;
            Token.intValue = saveTokInt;
            Token.line = saveTokLine;
            Token.strLen = saveTokStrLen;
            Native.arraycopy(saveTokStr, 0, Token.strBuf, 0, saveTokStrLen);
        }

        Emit.emitBranch(0xA7, lblTop); // GOTO top
        Emit.setLabel(lblEnd);

        Compiler.localCount = savedLocalCount;
    }

    static void parseReturn() {
        Lexer.nextToken(); // skip 'return'
        if (Token.type == Token.TOK_SEMI) {
            Lexer.nextToken();
            Emit.emitByte(0xB1); // RETURN
        } else {
            int retType = Compiler.methodRetType[Compiler.curMethod];
            Expr.parseExpression();
            Emit.popStack();
            if (retType == 2) Emit.emitByte(0xB0); // ARETURN
            else Emit.emitByte(0xAC); // IRETURN
            Lexer.expect(Token.TOK_SEMI);
        }
    }

    static void parseThrow() {
        Lexer.nextToken(); // skip 'throw'
        Expr.parseExpression();
        Emit.popStack();
        Emit.emitByte(0xBF); // ATHROW
        Lexer.expect(Token.TOK_SEMI);
    }

    static void parseSwitch() {
        Lexer.nextToken(); // skip 'switch'
        Lexer.expect(Token.TOK_LPAREN);
        Expr.parseExpression();
        Lexer.expect(Token.TOK_RPAREN);
        Emit.popStack();

        int lblEnd = Emit.newLabel();

        // Collect cases
        // Save body start BEFORE expect consumes { and advances past first token
        int bodyStart = Lexer.pos;
        int bodyLine = Lexer.line;
        Lexer.expect(Token.TOK_LBRACE);
        // Static to avoid heap allocation per switch (no nested switches)
        // Allocated once in <clinit>, reused for each switch statement
        int caseCount = 0;
        int defaultLabel = -1;

        // Use LOOKUPSWITCH for all switch statements (simplest)
        int switchPC = Compiler.mcodeLen;
        Emit.emitByte(0xAB); // LOOKUPSWITCH

        // Padding to 4-byte alignment
        while ((switchPC + 1 + (Compiler.mcodeLen - switchPC - 1)) % 4 != 0) {
            Emit.emitByte(0);
        }

        int defaultLoc = Compiler.mcodeLen;
        Emit.emitIntBE(0); // default offset placeholder (4 bytes, standard JVM format)
        int npairsLoc = Compiler.mcodeLen;
        Emit.emitIntBE(0); // npairs placeholder (4 bytes)

        // Scan cases
        while (Token.type != Token.TOK_RBRACE && Token.type != Token.TOK_EOF) {
            if (Token.type == Token.TOK_CASE) {
                Lexer.nextToken();
                int val;
                boolean neg = false;
                if (Token.type == Token.TOK_MINUS) {
                    neg = true;
                    Lexer.nextToken();
                }
                val = Token.intValue;
                if (neg) val = -val;
                Lexer.nextToken();
                Lexer.expect(Token.TOK_COLON);

                Compiler.caseLabels[caseCount] = Emit.newLabel();
                Compiler.caseVals[caseCount] = val;
                caseCount++;
            } else if (Token.type == Token.TOK_DEFAULT) {
                Lexer.nextToken();
                Lexer.expect(Token.TOK_COLON);
                defaultLabel = Emit.newLabel();
            } else {
                Lexer.nextToken();
            }
        }

        // Now fill in the lookupswitch table
        // Patch npairs (4-byte BE, value fits in low 16 bits)
        Compiler.mcode[npairsLoc]     = 0;
        Compiler.mcode[npairsLoc + 1] = 0;
        Compiler.mcode[npairsLoc + 2] = (byte) ((caseCount >> 8) & 0xFF);
        Compiler.mcode[npairsLoc + 3] = (byte) (caseCount & 0xFF);

        // Emit match-offset pairs (sorted by key, picoJVM requires this)
        // Simple insertion sort on caseVals
        for (int i = 1; i < caseCount; i++) {
            int kv = Compiler.caseVals[i];
            int kl = Compiler.caseLabels[i];
            int j = i - 1;
            while (j >= 0 && Compiler.caseVals[j] > kv) {
                Compiler.caseVals[j + 1] = Compiler.caseVals[j];
                Compiler.caseLabels[j + 1] = Compiler.caseLabels[j];
                j--;
            }
            Compiler.caseVals[j + 1] = kv;
            Compiler.caseLabels[j + 1] = kl;
        }

        int[] pairLocs = new int[caseCount];
        for (int i = 0; i < caseCount; i++) {
            // 4-byte match key (big-endian)
            Emit.emitByte((Compiler.caseVals[i] >> 24) & 0xFF);
            Emit.emitByte((Compiler.caseVals[i] >> 16) & 0xFF);
            Emit.emitByte((Compiler.caseVals[i] >> 8) & 0xFF);
            Emit.emitByte(Compiler.caseVals[i] & 0xFF);
            // 4-byte offset placeholder (standard JVM format)
            pairLocs[i] = Compiler.mcodeLen;
            Emit.emitIntBE(0);
        }

        // Now re-parse and emit case bodies
        Lexer.pos = bodyStart;
        Lexer.line = bodyLine;
        Lexer.nextToken();

        int curCaseIdx = -1;

        Compiler.loopBreakLabel[Compiler.loopDepth] = lblEnd;
        Compiler.loopContLabel[Compiler.loopDepth] = lblEnd; // continue in switch = break
        Compiler.loopDepth++;

        while (Token.type != Token.TOK_RBRACE && Token.type != Token.TOK_EOF) {
            if (Token.type == Token.TOK_CASE) {
                Lexer.nextToken();
                boolean neg = false;
                if (Token.type == Token.TOK_MINUS) { neg = true; Lexer.nextToken(); }
                int val = Token.intValue;
                if (neg) val = -val;
                Lexer.nextToken();
                Lexer.expect(Token.TOK_COLON);

                // Find which case this is
                for (int i = 0; i < caseCount; i++) {
                    if (Compiler.caseVals[i] == val) {
                        Emit.setLabel(Compiler.caseLabels[i]);
                        // Patch offset (4-byte BE)
                        int offset = Compiler.mcodeLen - switchPC;
                        Compiler.mcode[pairLocs[i]]     = (byte) ((offset >> 24) & 0xFF);
                        Compiler.mcode[pairLocs[i] + 1] = (byte) ((offset >> 16) & 0xFF);
                        Compiler.mcode[pairLocs[i] + 2] = (byte) ((offset >> 8) & 0xFF);
                        Compiler.mcode[pairLocs[i] + 3] = (byte) (offset & 0xFF);
                        break;
                    }
                }
            } else if (Token.type == Token.TOK_DEFAULT) {
                Lexer.nextToken();
                Lexer.expect(Token.TOK_COLON);
                if (defaultLabel >= 0) {
                    Emit.setLabel(defaultLabel);
                    int offset = Compiler.mcodeLen - switchPC;
                    Compiler.mcode[defaultLoc]     = (byte) ((offset >> 24) & 0xFF);
                    Compiler.mcode[defaultLoc + 1] = (byte) ((offset >> 16) & 0xFF);
                    Compiler.mcode[defaultLoc + 2] = (byte) ((offset >> 8) & 0xFF);
                    Compiler.mcode[defaultLoc + 3] = (byte) (offset & 0xFF);
                }
            } else {
                parseStatement();
            }
        }

        Compiler.loopDepth--;

        // If no default, patch default to end
        if (defaultLabel < 0) {
            Emit.setLabel(lblEnd);
            int offset = Compiler.mcodeLen - switchPC;
            Compiler.mcode[defaultLoc]     = (byte) ((offset >> 24) & 0xFF);
            Compiler.mcode[defaultLoc + 1] = (byte) ((offset >> 16) & 0xFF);
            Compiler.mcode[defaultLoc + 2] = (byte) ((offset >> 8) & 0xFF);
            Compiler.mcode[defaultLoc + 3] = (byte) (offset & 0xFF);
        }

        Emit.setLabel(lblEnd);
        Lexer.expect(Token.TOK_RBRACE);
    }

    static void parseTryCatch() {
        Lexer.nextToken(); // skip 'try'

        int lblEnd = Emit.newLabel();
        int startPC = Compiler.mcodeLen;

        Lexer.expect(Token.TOK_LBRACE);
        parseBlock();
        Lexer.expect(Token.TOK_RBRACE);

        int endPC = Compiler.mcodeLen;
        Emit.emitBranch(0xA7, lblEnd); // GOTO after handlers

        // Catch clauses
        while (Token.type == Token.TOK_CATCH) {
            Lexer.nextToken();
            Lexer.expect(Token.TOK_LPAREN);

            // Exception type
            int excNm = Compiler.internBuf(Token.strBuf, Token.strLen);
            Lexer.nextToken();

            // Exception variable name
            int varNm = Compiler.internBuf(Token.strBuf, Token.strLen);
            Lexer.nextToken();
            Lexer.expect(Token.TOK_RPAREN);

            int handlerPC = Compiler.mcodeLen;

            // Store exception in local
            int slot = Compiler.localCount;
            Emit.addLocal(varNm, 1); // reference type
            Emit.emitStore(slot, 1); // ASTORE
            Emit.popStack(); // exception ref was on stack
            Emit.pushStack(); // but we consumed it

            // Record exception table entry
            int catchClassId = Resolver.findClassByName(excNm);
            if (catchClassId < 0) catchClassId = 0xFF;
            Compiler.excStartPc[Compiler.excCount] = startPC;
            Compiler.excEndPc[Compiler.excCount] = endPC;
            Compiler.excHandlerPc[Compiler.excCount] = handlerPC;
            Compiler.excCatchClass[Compiler.excCount] = catchClassId;
            Compiler.excCount++;
            Compiler.methodExcCount[Compiler.curMethod]++;

            Lexer.expect(Token.TOK_LBRACE);
            parseBlock();
            Lexer.expect(Token.TOK_RBRACE);

            Emit.emitBranch(0xA7, lblEnd); // GOTO end
        }

        // Finally clause
        int finallyBodyPos = -1;
        int finallyBodyLine = -1;
        if (Token.type == Token.TOK_FINALLY) {
            Lexer.nextToken();

            int handlerPC = Compiler.mcodeLen;
            int excSlot = Compiler.localCount;
            Emit.addLocal(Compiler.internStr("$finally"), 1);
            Emit.emitStore(excSlot, 1); // ASTORE exception

            // Record catch-all handler
            Compiler.excStartPc[Compiler.excCount] = startPC;
            Compiler.excEndPc[Compiler.excCount] = endPC;
            Compiler.excHandlerPc[Compiler.excCount] = handlerPC;
            Compiler.excCatchClass[Compiler.excCount] = 0xFF; // catch all
            Compiler.excCount++;
            Compiler.methodExcCount[Compiler.curMethod]++;

            // Save position for re-lexing on normal path (before { is consumed)
            finallyBodyPos = Lexer.pos;
            finallyBodyLine = Lexer.line;
            Lexer.expect(Token.TOK_LBRACE);
            parseBlock();
            Lexer.expect(Token.TOK_RBRACE);

            // Re-throw
            Emit.emitLoad(excSlot, 1); // ALOAD
            Emit.pushStack();
            Emit.emitByte(0xBF); // ATHROW
            Emit.popStack();
        }

        Emit.setLabel(lblEnd);

        // Emit finally body on normal path too (duplicate via re-lex)
        if (finallyBodyPos >= 0) {
            int savedPos = Lexer.pos;
            int savedLine = Lexer.line;
            int savedTok = Token.type;
            Lexer.pos = finallyBodyPos;
            Lexer.line = finallyBodyLine;
            Lexer.nextToken();
            parseBlock();
            // Restore lexer to after the finally block
            Lexer.pos = savedPos;
            Lexer.line = savedLine;
            Token.type = savedTok;
        }
    }

}
