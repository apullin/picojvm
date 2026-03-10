public class Token {
    // Special
    static int TOK_EOF       = 0;
    static int TOK_IDENT     = 1;
    static int TOK_INT_LIT   = 2;
    static int TOK_STR_LIT   = 3;
    static int TOK_CHAR_LIT  = 4;

    // Keywords (10-49)
    static int TOK_CLASS     = 10;
    static int TOK_EXTENDS   = 11;
    static int TOK_IMPLEMENTS= 12;
    static int TOK_INTERFACE = 13;
    static int TOK_STATIC    = 14;
    static int TOK_PUBLIC    = 15;
    static int TOK_PRIVATE   = 16;
    static int TOK_PROTECTED = 17;
    static int TOK_VOID      = 18;
    static int TOK_INT       = 19;
    static int TOK_BYTE      = 20;
    static int TOK_CHAR      = 21;
    static int TOK_SHORT     = 22;
    static int TOK_BOOLEAN   = 23;
    static int TOK_IF        = 24;
    static int TOK_ELSE      = 25;
    static int TOK_WHILE     = 26;
    static int TOK_DO        = 27;
    static int TOK_FOR       = 28;
    static int TOK_SWITCH    = 29;
    static int TOK_CASE      = 30;
    static int TOK_DEFAULT   = 31;
    static int TOK_BREAK     = 32;
    static int TOK_CONTINUE  = 33;
    static int TOK_RETURN    = 34;
    static int TOK_NEW       = 35;
    static int TOK_NULL      = 36;
    static int TOK_THIS      = 37;
    static int TOK_THROW     = 38;
    static int TOK_TRY       = 39;
    static int TOK_CATCH     = 40;
    static int TOK_FINALLY   = 41;
    static int TOK_INSTANCEOF= 42;
    static int TOK_TRUE      = 43;
    static int TOK_FALSE     = 44;
    static int TOK_NATIVE    = 45;
    static int TOK_SUPER     = 46;
    static int TOK_FINAL     = 47;
    static int TOK_ABSTRACT  = 48;
    static int TOK_STRING_KW = 49;

    // Punctuation (50-79)
    static int TOK_LBRACE    = 50;
    static int TOK_RBRACE    = 51;
    static int TOK_LPAREN    = 52;
    static int TOK_RPAREN    = 53;
    static int TOK_LBRACKET  = 54;
    static int TOK_RBRACKET  = 55;
    static int TOK_SEMI      = 56;
    static int TOK_COMMA     = 57;
    static int TOK_DOT       = 58;

    // Operators (80-119)
    static int TOK_ASSIGN    = 80;
    static int TOK_EQ        = 81;
    static int TOK_NE        = 82;
    static int TOK_LT        = 83;
    static int TOK_GT        = 84;
    static int TOK_LE        = 85;
    static int TOK_GE        = 86;
    static int TOK_PLUS      = 87;
    static int TOK_MINUS     = 88;
    static int TOK_STAR      = 89;
    static int TOK_SLASH     = 90;
    static int TOK_PERCENT   = 91;
    static int TOK_AMP       = 92;
    static int TOK_PIPE      = 93;
    static int TOK_CARET     = 94;
    static int TOK_TILDE     = 95;
    static int TOK_BANG      = 96;
    static int TOK_AND       = 97;
    static int TOK_OR        = 98;
    static int TOK_SHL       = 99;
    static int TOK_SHR       = 100;
    static int TOK_USHR      = 101;
    static int TOK_QUESTION  = 102;
    static int TOK_COLON     = 103;
    static int TOK_INC       = 104;
    static int TOK_DEC       = 105;

    // Compound assignment (106-118)
    static int TOK_PLUS_EQ   = 106;
    static int TOK_MINUS_EQ  = 107;
    static int TOK_STAR_EQ   = 108;
    static int TOK_SLASH_EQ  = 109;
    static int TOK_PERCENT_EQ= 110;
    static int TOK_AMP_EQ    = 111;
    static int TOK_PIPE_EQ   = 112;
    static int TOK_CARET_EQ  = 113;
    static int TOK_SHL_EQ    = 114;
    static int TOK_SHR_EQ    = 115;
    static int TOK_USHR_EQ   = 116;

    // Current token state
    static int type;
    static int intValue;
    static int line;
    // String/identifier bytes stored in a temp buffer
    static byte[] strBuf = new byte[256];
    static int strLen;
}
