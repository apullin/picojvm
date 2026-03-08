#!/usr/bin/env python3
"""Generate MD5Test.java with all 64 rounds fully unrolled."""

import math

K = [int(abs(math.sin(i + 1)) * (2**32)) & 0xFFFFFFFF for i in range(64)]

s = [
    7, 12, 17, 22,  7, 12, 17, 22,  7, 12, 17, 22,  7, 12, 17, 22,
    5,  9, 14, 20,  5,  9, 14, 20,  5,  9, 14, 20,  5,  9, 14, 20,
    4, 11, 16, 23,  4, 11, 16, 23,  4, 11, 16, 23,  4, 11, 16, 23,
    6, 10, 15, 21,  6, 10, 15, 21,  6, 10, 15, 21,  6, 10, 15, 21,
]

def g_val(i):
    if i < 16: return i
    elif i < 32: return (5*i + 1) % 16
    elif i < 48: return (3*i + 5) % 16
    else: return (7*i) % 16

def f_expr(i):
    if i < 16: return "(b & c) | ((~b) & d)"
    elif i < 32: return "(d & b) | ((~d) & c)"
    elif i < 48: return "b ^ c ^ d"
    else: return "c ^ (b | (~d))"

def hex_literal(v):
    """Java int literal for unsigned 32-bit value."""
    if v > 0x7FFFFFFF:
        return f"(int)0x{v:08x}L"
    return f"0x{v:08x}"

lines = []
lines.append("/*")
lines.append(" * MD5Test.java — Unrolled MD5 hash for pager stress testing.")
lines.append(" *")
lines.append(" * All 64 rounds are fully unrolled inline (no loops), producing")
lines.append(" * several KB of bytecode. This forces real eviction pressure when")
lines.append(" * running with small page sizes (e.g. --page-size=256 --pages=4).")
lines.append(" *")
lines.append(" * Input: \"abc\"")
lines.append(" * Expected output: 900150983cd24fb0d6963f7d28e17f72")
lines.append(" */")
lines.append("public class MD5Test {")
lines.append("")
lines.append("    static void printHexByte(int v) {")
lines.append("        int hi = (v >> 4) & 0xF;")
lines.append("        int lo = v & 0xF;")
lines.append("        Native.putchar(hi < 10 ? '0' + hi : 'a' + hi - 10);")
lines.append("        Native.putchar(lo < 10 ? '0' + lo : 'a' + lo - 10);")
lines.append("    }")
lines.append("")
lines.append("    static void printHexLE(int v) {")
lines.append("        printHexByte(v & 0xFF);")
lines.append("        printHexByte((v >>> 8) & 0xFF);")
lines.append("        printHexByte((v >>> 16) & 0xFF);")
lines.append("        printHexByte((v >>> 24) & 0xFF);")
lines.append("    }")
lines.append("")
lines.append("    public static void main(String[] args) {")
lines.append("        // MD5(\"abc\") — single 512-bit block")
lines.append("        // Padded message as 16 little-endian 32-bit words:")
lines.append("        int m0  = 0x80636261;  // 'a','b','c', 0x80")
lines.append("        int m1  = 0, m2  = 0, m3  = 0;")
lines.append("        int m4  = 0, m5  = 0, m6  = 0, m7  = 0;")
lines.append("        int m8  = 0, m9  = 0, m10 = 0, m11 = 0;")
lines.append("        int m12 = 0, m13 = 0;")
lines.append("        int m14 = 24;           // bit length = 3*8 = 24")
lines.append("        int m15 = 0;")
lines.append("")
lines.append("        int a = 0x67452301;")
lines.append("        int b = (int)0xefcdab89L;")
lines.append("        int c = (int)0x98badcfeL;")
lines.append("        int d = 0x10325476;")
lines.append("        int f;")
lines.append("")

for i in range(64):
    g = g_val(i)
    lines.append(f"        // Round {i}")
    lines.append(f"        f = {f_expr(i)};")
    lines.append(f"        f = f + a + {hex_literal(K[i])} + m{g};")
    lines.append(f"        a = d; d = c; c = b;")
    lines.append(f"        b = b + ((f << {s[i]}) | (f >>> {32 - s[i]}));")
    lines.append("")

lines.append("        // Add initial values")
lines.append("        a = a + 0x67452301;")
lines.append("        b = b + (int)0xefcdab89L;")
lines.append("        c = c + (int)0x98badcfeL;")
lines.append("        d = d + 0x10325476;")
lines.append("")
lines.append("        // Output 128-bit digest as 32 hex chars (little-endian byte order)")
lines.append("        printHexLE(a);")
lines.append("        printHexLE(b);")
lines.append("        printHexLE(c);")
lines.append("        printHexLE(d);")
lines.append("        Native.putchar('\\n');")
lines.append("        Native.halt();")
lines.append("    }")
lines.append("}")

with open("tests/MD5Test.java", "w") as f:
    f.write("\n".join(lines) + "\n")

print(f"Generated tests/MD5Test.java ({len(lines)} lines)")
