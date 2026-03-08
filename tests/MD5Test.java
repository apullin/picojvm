/*
 * MD5Test.java — Unrolled MD5 hash for pager stress testing.
 *
 * All 64 rounds are fully unrolled inline (no loops), producing
 * several KB of bytecode. This forces real eviction pressure when
 * running with small page sizes (e.g. --page-size=256 --pages=4).
 *
 * Input: "abc"
 * Expected output: 900150983cd24fb0d6963f7d28e17f72
 */
public class MD5Test {

    static void printHexByte(int v) {
        int hi = (v >> 4) & 0xF;
        int lo = v & 0xF;
        Native.putchar(hi < 10 ? '0' + hi : 'a' + hi - 10);
        Native.putchar(lo < 10 ? '0' + lo : 'a' + lo - 10);
    }

    static void printHexLE(int v) {
        printHexByte(v & 0xFF);
        printHexByte((v >>> 8) & 0xFF);
        printHexByte((v >>> 16) & 0xFF);
        printHexByte((v >>> 24) & 0xFF);
    }

    public static void main(String[] args) {
        // MD5("abc") — single 512-bit block
        // Padded message as 16 little-endian 32-bit words:
        int m0  = 0x80636261;  // 'a','b','c', 0x80
        int m1  = 0, m2  = 0, m3  = 0;
        int m4  = 0, m5  = 0, m6  = 0, m7  = 0;
        int m8  = 0, m9  = 0, m10 = 0, m11 = 0;
        int m12 = 0, m13 = 0;
        int m14 = 24;           // bit length = 3*8 = 24
        int m15 = 0;

        int a = 0x67452301;
        int b = (int)0xefcdab89L;
        int c = (int)0x98badcfeL;
        int d = 0x10325476;
        int f;

        // Round 0
        f = (b & c) | ((~b) & d);
        f = f + a + (int)0xd76aa478L + m0;
        a = d; d = c; c = b;
        b = b + ((f << 7) | (f >>> 25));

        // Round 1
        f = (b & c) | ((~b) & d);
        f = f + a + (int)0xe8c7b756L + m1;
        a = d; d = c; c = b;
        b = b + ((f << 12) | (f >>> 20));

        // Round 2
        f = (b & c) | ((~b) & d);
        f = f + a + 0x242070db + m2;
        a = d; d = c; c = b;
        b = b + ((f << 17) | (f >>> 15));

        // Round 3
        f = (b & c) | ((~b) & d);
        f = f + a + (int)0xc1bdceeeL + m3;
        a = d; d = c; c = b;
        b = b + ((f << 22) | (f >>> 10));

        // Round 4
        f = (b & c) | ((~b) & d);
        f = f + a + (int)0xf57c0fafL + m4;
        a = d; d = c; c = b;
        b = b + ((f << 7) | (f >>> 25));

        // Round 5
        f = (b & c) | ((~b) & d);
        f = f + a + 0x4787c62a + m5;
        a = d; d = c; c = b;
        b = b + ((f << 12) | (f >>> 20));

        // Round 6
        f = (b & c) | ((~b) & d);
        f = f + a + (int)0xa8304613L + m6;
        a = d; d = c; c = b;
        b = b + ((f << 17) | (f >>> 15));

        // Round 7
        f = (b & c) | ((~b) & d);
        f = f + a + (int)0xfd469501L + m7;
        a = d; d = c; c = b;
        b = b + ((f << 22) | (f >>> 10));

        // Round 8
        f = (b & c) | ((~b) & d);
        f = f + a + 0x698098d8 + m8;
        a = d; d = c; c = b;
        b = b + ((f << 7) | (f >>> 25));

        // Round 9
        f = (b & c) | ((~b) & d);
        f = f + a + (int)0x8b44f7afL + m9;
        a = d; d = c; c = b;
        b = b + ((f << 12) | (f >>> 20));

        // Round 10
        f = (b & c) | ((~b) & d);
        f = f + a + (int)0xffff5bb1L + m10;
        a = d; d = c; c = b;
        b = b + ((f << 17) | (f >>> 15));

        // Round 11
        f = (b & c) | ((~b) & d);
        f = f + a + (int)0x895cd7beL + m11;
        a = d; d = c; c = b;
        b = b + ((f << 22) | (f >>> 10));

        // Round 12
        f = (b & c) | ((~b) & d);
        f = f + a + 0x6b901122 + m12;
        a = d; d = c; c = b;
        b = b + ((f << 7) | (f >>> 25));

        // Round 13
        f = (b & c) | ((~b) & d);
        f = f + a + (int)0xfd987193L + m13;
        a = d; d = c; c = b;
        b = b + ((f << 12) | (f >>> 20));

        // Round 14
        f = (b & c) | ((~b) & d);
        f = f + a + (int)0xa679438eL + m14;
        a = d; d = c; c = b;
        b = b + ((f << 17) | (f >>> 15));

        // Round 15
        f = (b & c) | ((~b) & d);
        f = f + a + 0x49b40821 + m15;
        a = d; d = c; c = b;
        b = b + ((f << 22) | (f >>> 10));

        // Round 16
        f = (d & b) | ((~d) & c);
        f = f + a + (int)0xf61e2562L + m1;
        a = d; d = c; c = b;
        b = b + ((f << 5) | (f >>> 27));

        // Round 17
        f = (d & b) | ((~d) & c);
        f = f + a + (int)0xc040b340L + m6;
        a = d; d = c; c = b;
        b = b + ((f << 9) | (f >>> 23));

        // Round 18
        f = (d & b) | ((~d) & c);
        f = f + a + 0x265e5a51 + m11;
        a = d; d = c; c = b;
        b = b + ((f << 14) | (f >>> 18));

        // Round 19
        f = (d & b) | ((~d) & c);
        f = f + a + (int)0xe9b6c7aaL + m0;
        a = d; d = c; c = b;
        b = b + ((f << 20) | (f >>> 12));

        // Round 20
        f = (d & b) | ((~d) & c);
        f = f + a + (int)0xd62f105dL + m5;
        a = d; d = c; c = b;
        b = b + ((f << 5) | (f >>> 27));

        // Round 21
        f = (d & b) | ((~d) & c);
        f = f + a + 0x02441453 + m10;
        a = d; d = c; c = b;
        b = b + ((f << 9) | (f >>> 23));

        // Round 22
        f = (d & b) | ((~d) & c);
        f = f + a + (int)0xd8a1e681L + m15;
        a = d; d = c; c = b;
        b = b + ((f << 14) | (f >>> 18));

        // Round 23
        f = (d & b) | ((~d) & c);
        f = f + a + (int)0xe7d3fbc8L + m4;
        a = d; d = c; c = b;
        b = b + ((f << 20) | (f >>> 12));

        // Round 24
        f = (d & b) | ((~d) & c);
        f = f + a + 0x21e1cde6 + m9;
        a = d; d = c; c = b;
        b = b + ((f << 5) | (f >>> 27));

        // Round 25
        f = (d & b) | ((~d) & c);
        f = f + a + (int)0xc33707d6L + m14;
        a = d; d = c; c = b;
        b = b + ((f << 9) | (f >>> 23));

        // Round 26
        f = (d & b) | ((~d) & c);
        f = f + a + (int)0xf4d50d87L + m3;
        a = d; d = c; c = b;
        b = b + ((f << 14) | (f >>> 18));

        // Round 27
        f = (d & b) | ((~d) & c);
        f = f + a + 0x455a14ed + m8;
        a = d; d = c; c = b;
        b = b + ((f << 20) | (f >>> 12));

        // Round 28
        f = (d & b) | ((~d) & c);
        f = f + a + (int)0xa9e3e905L + m13;
        a = d; d = c; c = b;
        b = b + ((f << 5) | (f >>> 27));

        // Round 29
        f = (d & b) | ((~d) & c);
        f = f + a + (int)0xfcefa3f8L + m2;
        a = d; d = c; c = b;
        b = b + ((f << 9) | (f >>> 23));

        // Round 30
        f = (d & b) | ((~d) & c);
        f = f + a + 0x676f02d9 + m7;
        a = d; d = c; c = b;
        b = b + ((f << 14) | (f >>> 18));

        // Round 31
        f = (d & b) | ((~d) & c);
        f = f + a + (int)0x8d2a4c8aL + m12;
        a = d; d = c; c = b;
        b = b + ((f << 20) | (f >>> 12));

        // Round 32
        f = b ^ c ^ d;
        f = f + a + (int)0xfffa3942L + m5;
        a = d; d = c; c = b;
        b = b + ((f << 4) | (f >>> 28));

        // Round 33
        f = b ^ c ^ d;
        f = f + a + (int)0x8771f681L + m8;
        a = d; d = c; c = b;
        b = b + ((f << 11) | (f >>> 21));

        // Round 34
        f = b ^ c ^ d;
        f = f + a + 0x6d9d6122 + m11;
        a = d; d = c; c = b;
        b = b + ((f << 16) | (f >>> 16));

        // Round 35
        f = b ^ c ^ d;
        f = f + a + (int)0xfde5380cL + m14;
        a = d; d = c; c = b;
        b = b + ((f << 23) | (f >>> 9));

        // Round 36
        f = b ^ c ^ d;
        f = f + a + (int)0xa4beea44L + m1;
        a = d; d = c; c = b;
        b = b + ((f << 4) | (f >>> 28));

        // Round 37
        f = b ^ c ^ d;
        f = f + a + 0x4bdecfa9 + m4;
        a = d; d = c; c = b;
        b = b + ((f << 11) | (f >>> 21));

        // Round 38
        f = b ^ c ^ d;
        f = f + a + (int)0xf6bb4b60L + m7;
        a = d; d = c; c = b;
        b = b + ((f << 16) | (f >>> 16));

        // Round 39
        f = b ^ c ^ d;
        f = f + a + (int)0xbebfbc70L + m10;
        a = d; d = c; c = b;
        b = b + ((f << 23) | (f >>> 9));

        // Round 40
        f = b ^ c ^ d;
        f = f + a + 0x289b7ec6 + m13;
        a = d; d = c; c = b;
        b = b + ((f << 4) | (f >>> 28));

        // Round 41
        f = b ^ c ^ d;
        f = f + a + (int)0xeaa127faL + m0;
        a = d; d = c; c = b;
        b = b + ((f << 11) | (f >>> 21));

        // Round 42
        f = b ^ c ^ d;
        f = f + a + (int)0xd4ef3085L + m3;
        a = d; d = c; c = b;
        b = b + ((f << 16) | (f >>> 16));

        // Round 43
        f = b ^ c ^ d;
        f = f + a + 0x04881d05 + m6;
        a = d; d = c; c = b;
        b = b + ((f << 23) | (f >>> 9));

        // Round 44
        f = b ^ c ^ d;
        f = f + a + (int)0xd9d4d039L + m9;
        a = d; d = c; c = b;
        b = b + ((f << 4) | (f >>> 28));

        // Round 45
        f = b ^ c ^ d;
        f = f + a + (int)0xe6db99e5L + m12;
        a = d; d = c; c = b;
        b = b + ((f << 11) | (f >>> 21));

        // Round 46
        f = b ^ c ^ d;
        f = f + a + 0x1fa27cf8 + m15;
        a = d; d = c; c = b;
        b = b + ((f << 16) | (f >>> 16));

        // Round 47
        f = b ^ c ^ d;
        f = f + a + (int)0xc4ac5665L + m2;
        a = d; d = c; c = b;
        b = b + ((f << 23) | (f >>> 9));

        // Round 48
        f = c ^ (b | (~d));
        f = f + a + (int)0xf4292244L + m0;
        a = d; d = c; c = b;
        b = b + ((f << 6) | (f >>> 26));

        // Round 49
        f = c ^ (b | (~d));
        f = f + a + 0x432aff97 + m7;
        a = d; d = c; c = b;
        b = b + ((f << 10) | (f >>> 22));

        // Round 50
        f = c ^ (b | (~d));
        f = f + a + (int)0xab9423a7L + m14;
        a = d; d = c; c = b;
        b = b + ((f << 15) | (f >>> 17));

        // Round 51
        f = c ^ (b | (~d));
        f = f + a + (int)0xfc93a039L + m5;
        a = d; d = c; c = b;
        b = b + ((f << 21) | (f >>> 11));

        // Round 52
        f = c ^ (b | (~d));
        f = f + a + 0x655b59c3 + m12;
        a = d; d = c; c = b;
        b = b + ((f << 6) | (f >>> 26));

        // Round 53
        f = c ^ (b | (~d));
        f = f + a + (int)0x8f0ccc92L + m3;
        a = d; d = c; c = b;
        b = b + ((f << 10) | (f >>> 22));

        // Round 54
        f = c ^ (b | (~d));
        f = f + a + (int)0xffeff47dL + m10;
        a = d; d = c; c = b;
        b = b + ((f << 15) | (f >>> 17));

        // Round 55
        f = c ^ (b | (~d));
        f = f + a + (int)0x85845dd1L + m1;
        a = d; d = c; c = b;
        b = b + ((f << 21) | (f >>> 11));

        // Round 56
        f = c ^ (b | (~d));
        f = f + a + 0x6fa87e4f + m8;
        a = d; d = c; c = b;
        b = b + ((f << 6) | (f >>> 26));

        // Round 57
        f = c ^ (b | (~d));
        f = f + a + (int)0xfe2ce6e0L + m15;
        a = d; d = c; c = b;
        b = b + ((f << 10) | (f >>> 22));

        // Round 58
        f = c ^ (b | (~d));
        f = f + a + (int)0xa3014314L + m6;
        a = d; d = c; c = b;
        b = b + ((f << 15) | (f >>> 17));

        // Round 59
        f = c ^ (b | (~d));
        f = f + a + 0x4e0811a1 + m13;
        a = d; d = c; c = b;
        b = b + ((f << 21) | (f >>> 11));

        // Round 60
        f = c ^ (b | (~d));
        f = f + a + (int)0xf7537e82L + m4;
        a = d; d = c; c = b;
        b = b + ((f << 6) | (f >>> 26));

        // Round 61
        f = c ^ (b | (~d));
        f = f + a + (int)0xbd3af235L + m11;
        a = d; d = c; c = b;
        b = b + ((f << 10) | (f >>> 22));

        // Round 62
        f = c ^ (b | (~d));
        f = f + a + 0x2ad7d2bb + m2;
        a = d; d = c; c = b;
        b = b + ((f << 15) | (f >>> 17));

        // Round 63
        f = c ^ (b | (~d));
        f = f + a + (int)0xeb86d391L + m9;
        a = d; d = c; c = b;
        b = b + ((f << 21) | (f >>> 11));

        // Add initial values
        a = a + 0x67452301;
        b = b + (int)0xefcdab89L;
        c = c + (int)0x98badcfeL;
        d = d + 0x10325476;

        // Output 128-bit digest as 32 hex chars (little-endian byte order)
        printHexLE(a);
        printHexLE(b);
        printHexLE(c);
        printHexLE(d);
        Native.putchar('\n');
        Native.halt();
    }
}
