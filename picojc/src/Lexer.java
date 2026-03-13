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

    // Keyword tables
    static String[] kwNames;
    static int[] kwTokens;
    static int kwCount;

    static void initKeywords() {
        kwCount = 0;
        kwNames = new String[40];
        kwTokens = new int[40];
        addKw("class",      Tk.CLASS);
        addKw("extends",    Tk.EXTENDS);
        addKw("implements", Tk.IMPLEMENTS);
        addKw("interface",  Tk.INTERFACE);
        addKw("static",     Tk.STATIC);
        addKw("public",     Tk.PUBLIC);
        addKw("private",    Tk.PRIVATE);
        addKw("protected",  Tk.PROTECTED);
        addKw("void",       Tk.VOID);
        addKw("int",        Tk.INT);
        addKw("byte",       Tk.BYTE);
        addKw("char",       Tk.CHAR);
        addKw("short",      Tk.SHORT);
        addKw("boolean",    Tk.BOOLEAN);
        addKw("if",         Tk.IF);
        addKw("else",       Tk.ELSE);
        addKw("while",      Tk.WHILE);
        addKw("do",         Tk.DO);
        addKw("for",        Tk.FOR);
        addKw("switch",     Tk.SWITCH);
        addKw("case",       Tk.CASE);
        addKw("default",    Tk.DEFAULT);
        addKw("break",      Tk.BREAK);
        addKw("continue",   Tk.CONTINUE);
        addKw("return",     Tk.RETURN);
        addKw("new",        Tk.NEW);
        addKw("null",       Tk.NULL);
        addKw("this",       Tk.THIS);
        addKw("throw",      Tk.THROW);
        addKw("try",        Tk.TRY);
        addKw("catch",      Tk.CATCH);
        addKw("finally",    Tk.FINALLY);
        addKw("instanceof", Tk.INSTANCEOF);
        addKw("true",       Tk.TRUE);
        addKw("false",      Tk.FALSE);
        addKw("native",     Tk.NATIVE);
        addKw("super",      Tk.SUPER);
        addKw("final",      Tk.FINAL);
        addKw("abstract",   Tk.ABSTRACT);
        addKw("String",     Tk.STRING_KW);
    }

    static void addKw(String name, int tok) {
        kwNames[kwCount] = name;
        kwTokens[kwCount] = tok;
        kwCount++;
    }

    static int lookupKeyword() {
        for (int i = 0; i < kwCount; i++) {
            String kw = kwNames[i];
            if (kw.length() == Tk.strLen) {
                boolean match = true;
                for (int j = 0; j < Tk.strLen; j++) {
                    if ((byte) kw.charAt(j) != Tk.strBuf[j]) { match = false; break; }
                }
                if (match) return kwTokens[i];
            }
        }
        return Tk.IDENT;
    }

    static void readIdentifier() {
        Tk.strLen = 0;
        while (pos < srcLen && isAlphaNum(ch())) {
            if (Tk.strLen < 255) {
                Tk.strBuf[Tk.strLen] = (byte) ch();
                Tk.strLen++;
            }
            advance();
        }
        Tk.type = lookupKeyword();
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
                Tk.type = Tk.INT_LIT;
                Tk.intValue = val;
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
                Tk.type = Tk.INT_LIT;
                Tk.intValue = val;
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
        Tk.type = Tk.INT_LIT;
        Tk.intValue = val;
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
        Tk.type = Tk.CHAR_LIT;
        Tk.intValue = val;
    }

    static void readStringLiteral() {
        advance(); // skip opening "
        Tk.strLen = 0;
        while (pos < srcLen && ch() != '"') {
            int c;
            if (ch() == '\\') {
                c = readEscape();
            } else {
                c = ch();
                advance();
            }
            if (Tk.strLen < 255) {
                Tk.strBuf[Tk.strLen] = (byte) c;
                Tk.strLen++;
            }
        }
        if (pos < srcLen) advance(); // skip closing "
        Tk.type = Tk.STR_LIT;
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
        Tk.line = line;

        if (pos >= srcLen) {
            Tk.type = Tk.EOF;
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
        if (c == '{') { Tk.type = Tk.LBRACE; }
        else if (c == '}') { Tk.type = Tk.RBRACE; }
        else if (c == '(') { Tk.type = Tk.LPAREN; }
        else if (c == ')') { Tk.type = Tk.RPAREN; }
        else if (c == '[') { Tk.type = Tk.LBRACKET; }
        else if (c == ']') { Tk.type = Tk.RBRACKET; }
        else if (c == ';') { Tk.type = Tk.SEMI; }
        else if (c == ',') { Tk.type = Tk.COMMA; }
        else if (c == '.') { Tk.type = Tk.DOT; }
        else if (c == '~') { Tk.type = Tk.TILDE; }
        else if (c == '?') { Tk.type = Tk.QUESTION; }
        else if (c == ':') { Tk.type = Tk.COLON; }
        else if (c == '\'') { pos--; readCharLiteral(); }
        else if (c == '"') { pos--; readStringLiteral(); }
        else if (c == '=') { Tk.type = matchChar('=') ? Tk.EQ : Tk.ASSIGN; }
        else if (c == '!') { Tk.type = matchChar('=') ? Tk.NE : Tk.BANG; }
        else if (c == '<') {
            if (matchChar('=')) Tk.type = Tk.LE;
            else if (matchChar('<')) Tk.type = matchChar('=') ? Tk.SHL_EQ : Tk.SHL;
            else Tk.type = Tk.LT;
        }
        else if (c == '>') {
            if (matchChar('=')) Tk.type = Tk.GE;
            else if (matchChar('>')) {
                if (matchChar('>')) Tk.type = matchChar('=') ? Tk.USHR_EQ : Tk.USHR;
                else Tk.type = matchChar('=') ? Tk.SHR_EQ : Tk.SHR;
            }
            else Tk.type = Tk.GT;
        }
        else if (c == '+') {
            if (matchChar('+')) Tk.type = Tk.INC;
            else if (matchChar('=')) Tk.type = Tk.PLUS_EQ;
            else Tk.type = Tk.PLUS;
        }
        else if (c == '-') {
            if (matchChar('-')) Tk.type = Tk.DEC;
            else if (matchChar('=')) Tk.type = Tk.MINUS_EQ;
            else Tk.type = Tk.MINUS;
        }
        else if (c == '*') { Tk.type = matchChar('=') ? Tk.STAR_EQ : Tk.STAR; }
        else if (c == '/') { Tk.type = matchChar('=') ? Tk.SLASH_EQ : Tk.SLASH; }
        else if (c == '%') { Tk.type = matchChar('=') ? Tk.PERCENT_EQ : Tk.PERCENT; }
        else if (c == '&') {
            if (matchChar('&')) Tk.type = Tk.AND;
            else if (matchChar('=')) Tk.type = Tk.AMP_EQ;
            else Tk.type = Tk.AMP;
        }
        else if (c == '|') {
            if (matchChar('|')) Tk.type = Tk.OR;
            else if (matchChar('=')) Tk.type = Tk.PIPE_EQ;
            else Tk.type = Tk.PIPE;
        }
        else if (c == '^') { Tk.type = matchChar('=') ? Tk.CARET_EQ : Tk.CARET; }
        else {
            error(c);
        }
    }

    static void expect(int tok) {
        if (Tk.type != tok) {
            error(tok);
        }
        nextToken();
    }

    static void error(int code) {
        // Output: E<line><code> then halt
        Native.putchar('E');
        printNum(Tk.line);
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
