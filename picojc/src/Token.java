// Token -> Tk (size)
class Tk {
    // Special
    static int EOF       = 0;
    static int IDENT     = 1;
    static int INT_LIT   = 2;
    static int STR_LIT   = 3;
    static int CHAR_LIT  = 4;

    // Keywords (10-49)
    static int CLASS     = 10;
    static int EXTENDS   = 11;
    static int IMPLEMENTS= 12;
    static int INTERFACE = 13;
    static int STATIC    = 14;
    static int PUBLIC    = 15;
    static int PRIVATE   = 16;
    static int PROTECTED = 17;
    static int VOID      = 18;
    static int INT       = 19;
    static int BYTE      = 20;
    static int CHAR      = 21;
    static int SHORT     = 22;
    static int BOOLEAN   = 23;
    static int IF        = 24;
    static int ELSE      = 25;
    static int WHILE     = 26;
    static int DO        = 27;
    static int FOR       = 28;
    static int SWITCH    = 29;
    static int CASE      = 30;
    static int DEFAULT   = 31;
    static int BREAK     = 32;
    static int CONTINUE  = 33;
    static int RETURN    = 34;
    static int NEW       = 35;
    static int NULL      = 36;
    static int THIS      = 37;
    static int THROW     = 38;
    static int TRY       = 39;
    static int CATCH     = 40;
    static int FINALLY   = 41;
    static int INSTANCEOF= 42;
    static int TRUE      = 43;
    static int FALSE     = 44;
    static int NATIVE    = 45;
    static int SUPER     = 46;
    static int FINAL     = 47;
    static int ABSTRACT  = 48;
    static int STRING_KW = 49;

    // Punctuation (50-79)
    static int LBRACE    = 50;
    static int RBRACE    = 51;
    static int LPAREN    = 52;
    static int RPAREN    = 53;
    static int LBRACKET  = 54;
    static int RBRACKET  = 55;
    static int SEMI      = 56;
    static int COMMA     = 57;
    static int DOT       = 58;

    // Operators (80-119)
    static int ASSIGN    = 80;
    static int EQ        = 81;
    static int NE        = 82;
    static int LT        = 83;
    static int GT        = 84;
    static int LE        = 85;
    static int GE        = 86;
    static int PLUS      = 87;
    static int MINUS     = 88;
    static int STAR      = 89;
    static int SLASH     = 90;
    static int PERCENT   = 91;
    static int AMP       = 92;
    static int PIPE      = 93;
    static int CARET     = 94;
    static int TILDE     = 95;
    static int BANG      = 96;
    static int AND       = 97;
    static int OR        = 98;
    static int SHL       = 99;
    static int SHR       = 100;
    static int USHR      = 101;
    static int QUESTION  = 102;
    static int COLON     = 103;
    static int INC       = 104;
    static int DEC       = 105;

    // Compound assignment (106-118)
    static int PLUS_EQ   = 106;
    static int MINUS_EQ  = 107;
    static int STAR_EQ   = 108;
    static int SLASH_EQ  = 109;
    static int PERCENT_EQ= 110;
    static int AMP_EQ    = 111;
    static int PIPE_EQ   = 112;
    static int CARET_EQ  = 113;
    static int SHL_EQ    = 114;
    static int SHR_EQ    = 115;
    static int USHR_EQ   = 116;

    // Current token state
    static int type;
    static int intValue;
    static int line;
    // String/identifier bytes stored in a temp buffer
    static byte[] strBuf = new byte[256];
    static int strLen;
}
