public class Lexer {
    static int srcBase;
    static int srcLen;
    static int pos;
    static int line;
    static int savedPos;
    static int savedLine;

    static void init(int base, int length) {
        srcBase = base;
        srcLen = length;
        pos = 0;
        line = 1;
    }

    static int ch() {
        if (pos >= srcLen) return -1;
        return Native.peek(srcBase + pos) & 0xFF;
    }

    static void advance() {
        pos++;
    }

    static void save() {
        savedPos = pos;
        savedLine = line;
    }

    static void restore() {
        pos = savedPos;
        line = savedLine;
    }

    static void skipWhitespaceAndComments() {
        while (pos < srcLen) {
            int c = ch();
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
            } else if (c == '\n') {
                advance();
                line++;
            } else if (c == '/') {
                int next = (pos + 1 < srcLen) ? (Native.peek(srcBase + pos + 1) & 0xFF) : -1;
                if (next == '/') {
                    // Single-line comment
                    advance(); advance();
                    while (pos < srcLen && ch() != '\n') advance();
                } else if (next == '*') {
                    // Multi-line comment
                    advance(); advance();
                    while (pos < srcLen) {
                        if (ch() == '*') {
                            advance();
                            if (pos < srcLen && ch() == '/') {
                                advance();
                                break;
                            }
                        } else {
                            if (ch() == '\n') line++;
                            advance();
                        }
                    }
                } else {
                    return;
                }
            } else {
                return;
            }
        }
    }

    static boolean isAlpha(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    static boolean isAlphaNum(int c) {
        return isAlpha(c) || isDigit(c);
    }

    // Check if strBuf[0..strLen) matches a given string
    static boolean strEquals(byte[] a, int aLen, byte[] b, int bLen) {
        if (aLen != bLen) return false;
        for (int i = 0; i < aLen; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    // Keyword tables: stored as byte arrays for matching
    // We use a simple sequential check
    static byte[][] kwNames;
    static int[] kwTokens;
    static int kwCount;

    static void initKeywords() {
        kwCount = 0;
        kwNames = new byte[40][];
        kwTokens = new int[40];
        addKw("class",      Token.TOK_CLASS);
        addKw("extends",    Token.TOK_EXTENDS);
        addKw("implements", Token.TOK_IMPLEMENTS);
        addKw("interface",  Token.TOK_INTERFACE);
        addKw("static",     Token.TOK_STATIC);
        addKw("public",     Token.TOK_PUBLIC);
        addKw("private",    Token.TOK_PRIVATE);
        addKw("protected",  Token.TOK_PROTECTED);
        addKw("void",       Token.TOK_VOID);
        addKw("int",        Token.TOK_INT);
        addKw("byte",       Token.TOK_BYTE);
        addKw("char",       Token.TOK_CHAR);
        addKw("short",      Token.TOK_SHORT);
        addKw("boolean",    Token.TOK_BOOLEAN);
        addKw("if",         Token.TOK_IF);
        addKw("else",       Token.TOK_ELSE);
        addKw("while",      Token.TOK_WHILE);
        addKw("do",         Token.TOK_DO);
        addKw("for",        Token.TOK_FOR);
        addKw("switch",     Token.TOK_SWITCH);
        addKw("case",       Token.TOK_CASE);
        addKw("default",    Token.TOK_DEFAULT);
        addKw("break",      Token.TOK_BREAK);
        addKw("continue",   Token.TOK_CONTINUE);
        addKw("return",     Token.TOK_RETURN);
        addKw("new",        Token.TOK_NEW);
        addKw("null",       Token.TOK_NULL);
        addKw("this",       Token.TOK_THIS);
        addKw("throw",      Token.TOK_THROW);
        addKw("try",        Token.TOK_TRY);
        addKw("catch",      Token.TOK_CATCH);
        addKw("finally",    Token.TOK_FINALLY);
        addKw("instanceof", Token.TOK_INSTANCEOF);
        addKw("true",       Token.TOK_TRUE);
        addKw("false",      Token.TOK_FALSE);
        addKw("native",     Token.TOK_NATIVE);
        addKw("super",      Token.TOK_SUPER);
        addKw("final",      Token.TOK_FINAL);
        addKw("abstract",   Token.TOK_ABSTRACT);
        addKw("String",     Token.TOK_STRING_KW);
    }

    static void addKw(String name, int tok) {
        byte[] b = new byte[name.length()];
        for (int i = 0; i < name.length(); i++) {
            b[i] = (byte) name.charAt(i);
        }
        kwNames[kwCount] = b;
        kwTokens[kwCount] = tok;
        kwCount++;
    }

    static int lookupKeyword() {
        for (int i = 0; i < kwCount; i++) {
            if (strEquals(Token.strBuf, Token.strLen, kwNames[i], kwNames[i].length)) {
                return kwTokens[i];
            }
        }
        return Token.TOK_IDENT;
    }

    static void readIdentifier() {
        Token.strLen = 0;
        while (pos < srcLen && isAlphaNum(ch())) {
            if (Token.strLen < 255) {
                Token.strBuf[Token.strLen] = (byte) ch();
                Token.strLen++;
            }
            advance();
        }
        Token.type = lookupKeyword();
    }

    static void readNumber() {
        int c = ch();
        if (c == '0' && pos + 1 < srcLen) {
            int next = Native.peek(srcBase + pos + 1) & 0xFF;
            if (next == 'x' || next == 'X') {
                // Hex literal
                advance(); advance();
                int val = 0;
                while (pos < srcLen) {
                    c = ch();
                    if (c >= '0' && c <= '9') { val = val * 16 + (c - '0'); advance(); }
                    else if (c >= 'a' && c <= 'f') { val = val * 16 + (c - 'a' + 10); advance(); }
                    else if (c >= 'A' && c <= 'F') { val = val * 16 + (c - 'A' + 10); advance(); }
                    else break;
                }
                Token.type = Token.TOK_INT_LIT;
                Token.intValue = val;
                return;
            } else if (next == 'b' || next == 'B') {
                // Binary literal
                advance(); advance();
                int val = 0;
                while (pos < srcLen) {
                    c = ch();
                    if (c == '0' || c == '1') { val = val * 2 + (c - '0'); advance(); }
                    else break;
                }
                Token.type = Token.TOK_INT_LIT;
                Token.intValue = val;
                return;
            }
        }
        // Decimal literal
        int val = 0;
        while (pos < srcLen && isDigit(ch())) {
            val = val * 10 + (ch() - '0');
            advance();
        }
        // Skip L/l suffix if present
        if (pos < srcLen && (ch() == 'L' || ch() == 'l')) advance();
        Token.type = Token.TOK_INT_LIT;
        Token.intValue = val;
    }

    static int readEscape() {
        advance(); // skip backslash
        int c = ch();
        advance();
        if (c == 'n') return '\n';
        if (c == 't') return '\t';
        if (c == 'r') return '\r';
        if (c == '\\') return '\\';
        if (c == '\'') return '\'';
        if (c == '"') return '"';
        if (c == '0') return 0;
        return c;
    }

    static void readCharLiteral() {
        advance(); // skip opening '
        int val;
        if (ch() == '\\') {
            val = readEscape();
        } else {
            val = ch();
            advance();
        }
        advance(); // skip closing '
        Token.type = Token.TOK_CHAR_LIT;
        Token.intValue = val;
    }

    static void readStringLiteral() {
        advance(); // skip opening "
        Token.strLen = 0;
        while (pos < srcLen && ch() != '"') {
            int c;
            if (ch() == '\\') {
                c = readEscape();
            } else {
                c = ch();
                advance();
            }
            if (Token.strLen < 255) {
                Token.strBuf[Token.strLen] = (byte) c;
                Token.strLen++;
            }
        }
        if (pos < srcLen) advance(); // skip closing "
        Token.type = Token.TOK_STR_LIT;
    }

    static boolean matchChar(int expected) {
        if (pos < srcLen && ch() == expected) {
            advance();
            return true;
        }
        return false;
    }

    static void nextToken() {
        skipWhitespaceAndComments();
        Token.line = line;

        if (pos >= srcLen) {
            Token.type = Token.TOK_EOF;
            return;
        }

        int c = ch();

        if (isAlpha(c)) {
            readIdentifier();
            return;
        }
        if (isDigit(c)) {
            readNumber();
            return;
        }

        advance(); // consume the character
        if (c == '{') { Token.type = Token.TOK_LBRACE; }
        else if (c == '}') { Token.type = Token.TOK_RBRACE; }
        else if (c == '(') { Token.type = Token.TOK_LPAREN; }
        else if (c == ')') { Token.type = Token.TOK_RPAREN; }
        else if (c == '[') { Token.type = Token.TOK_LBRACKET; }
        else if (c == ']') { Token.type = Token.TOK_RBRACKET; }
        else if (c == ';') { Token.type = Token.TOK_SEMI; }
        else if (c == ',') { Token.type = Token.TOK_COMMA; }
        else if (c == '.') { Token.type = Token.TOK_DOT; }
        else if (c == '~') { Token.type = Token.TOK_TILDE; }
        else if (c == '?') { Token.type = Token.TOK_QUESTION; }
        else if (c == ':') { Token.type = Token.TOK_COLON; }
        else if (c == '\'') { pos--; readCharLiteral(); }
        else if (c == '"') { pos--; readStringLiteral(); }
        else if (c == '=') { Token.type = matchChar('=') ? Token.TOK_EQ : Token.TOK_ASSIGN; }
        else if (c == '!') { Token.type = matchChar('=') ? Token.TOK_NE : Token.TOK_BANG; }
        else if (c == '<') {
            if (matchChar('=')) Token.type = Token.TOK_LE;
            else if (matchChar('<')) Token.type = matchChar('=') ? Token.TOK_SHL_EQ : Token.TOK_SHL;
            else Token.type = Token.TOK_LT;
        }
        else if (c == '>') {
            if (matchChar('=')) Token.type = Token.TOK_GE;
            else if (matchChar('>')) {
                if (matchChar('>')) Token.type = matchChar('=') ? Token.TOK_USHR_EQ : Token.TOK_USHR;
                else Token.type = matchChar('=') ? Token.TOK_SHR_EQ : Token.TOK_SHR;
            }
            else Token.type = Token.TOK_GT;
        }
        else if (c == '+') {
            if (matchChar('+')) Token.type = Token.TOK_INC;
            else if (matchChar('=')) Token.type = Token.TOK_PLUS_EQ;
            else Token.type = Token.TOK_PLUS;
        }
        else if (c == '-') {
            if (matchChar('-')) Token.type = Token.TOK_DEC;
            else if (matchChar('=')) Token.type = Token.TOK_MINUS_EQ;
            else Token.type = Token.TOK_MINUS;
        }
        else if (c == '*') { Token.type = matchChar('=') ? Token.TOK_STAR_EQ : Token.TOK_STAR; }
        else if (c == '/') { Token.type = matchChar('=') ? Token.TOK_SLASH_EQ : Token.TOK_SLASH; }
        else if (c == '%') { Token.type = matchChar('=') ? Token.TOK_PERCENT_EQ : Token.TOK_PERCENT; }
        else if (c == '&') {
            if (matchChar('&')) Token.type = Token.TOK_AND;
            else if (matchChar('=')) Token.type = Token.TOK_AMP_EQ;
            else Token.type = Token.TOK_AMP;
        }
        else if (c == '|') {
            if (matchChar('|')) Token.type = Token.TOK_OR;
            else if (matchChar('=')) Token.type = Token.TOK_PIPE_EQ;
            else Token.type = Token.TOK_PIPE;
        }
        else if (c == '^') { Token.type = matchChar('=') ? Token.TOK_CARET_EQ : Token.TOK_CARET; }
        else {
            error(c);
        }
    }

    static void expect(int tok) {
        if (Token.type != tok) {
            error(tok);
        }
        nextToken();
    }

    static void error(int code) {
        // Output: E<line><code> then halt
        Native.putchar('E');
        printNum(Token.line);
        Native.putchar(':');
        printNum(code);
        Native.putchar('\n');
        Native.halt();
    }

    static void printNum(int n) {
        if (n < 0) { Native.putchar('-'); n = -n; }
        if (n >= 10) printNum(n / 10);
        Native.putchar('0' + (n % 10));
    }
}
