class Tk {
	static final int EOF=0, IDENT=1, INT_LIT=2, STR_LIT=3, CHAR_LIT=4;
	static final int CLASS=10, EXTENDS=11, IMPLEMENTS=12, INTERFACE=13, STATIC=14;
	static final int PUBLIC=15, PRIVATE=16, PROTECTED=17, VOID=18, INT=19;
	static final int BYTE=20, CHAR=21, SHORT=22, BOOLEAN=23, IF=24, ELSE=25;
	static final int WHILE=26, DO=27, FOR=28, SWITCH=29, CASE=30, DEFAULT=31;
	static final int BREAK=32, CONTINUE=33, RETURN=34, NEW=35, NULL=36, THIS=37;
	static final int THROW=38, TRY=39, CATCH=40, FINALLY=41, INSTANCEOF=42;
	static final int TRUE=43, FALSE=44, NATIVE=45, SUPER=46, FINAL=47, ABSTRACT=48, STRING_KW=49;
	static final int LBRACE=50, RBRACE=51, LPAREN=52, RPAREN=53;
	static final int LBRACKET=54, RBRACKET=55, SEMI=56, COMMA=57, DOT=58, ENUM=59;
	static final int ASSIGN=80, EQ=81, NE=82, LT=83, GT=84, LE=85, GE=86;
	static final int PLUS=87, MINUS=88, STAR=89, SLASH=90, PERCENT=91;
	static final int AMP=92, PIPE=93, CARET=94, TILDE=95, BANG=96;
	static final int AND=97, OR=98, SHL=99, SHR=100, USHR=101;
	static final int QUESTION=102, COLON=103, INC=104, DEC=105;
	static final int PLUS_EQ=106, MINUS_EQ=107, STAR_EQ=108, SLASH_EQ=109;
	static final int PERCENT_EQ=110, AMP_EQ=111, PIPE_EQ=112, CARET_EQ=113;
	static final int SHL_EQ=114, SHR_EQ=115, USHR_EQ=116;
	static int type, intValue, line;
	static byte[] strBuf = new byte[256];
	static int strLen;
}
