#!/usr/bin/env python3
"""Generate FFTTest.java — Q7.8 fixed-point 64-point FFT with twiddle/bitrev tables."""

import math

N = 64
LOG2N = 6

# Q7.8: 8 fractional bits, 16-bit signed range [-128.0, +127.996]
FRAC_BITS = 8
SCALE = 1 << FRAC_BITS  # 256

def to_q78(x):
    """Convert float to Q7.8 (signed 16-bit stored as Java int)."""
    v = int(round(x * SCALE))
    if v > 32767: v = 32767
    if v < -32768: v = -32768
    return v

# Twiddle factors: W_N^k = cos(2πk/N) - j·sin(2πk/N)
# We need N/2 = 32 complex twiddle factors
twiddle_cos = []
twiddle_sin = []
for k in range(N // 2):
    angle = -2.0 * math.pi * k / N
    twiddle_cos.append(to_q78(math.cos(angle)))
    twiddle_sin.append(to_q78(math.sin(angle)))

# Bit-reversal permutation for N=64 (6 bits)
def bitrev(x, bits):
    r = 0
    for _ in range(bits):
        r = (r << 1) | (x & 1)
        x >>= 1
    return r

bitrev_table = [bitrev(i, LOG2N) for i in range(N)]

# Test input: impulse at index 1 → DFT should give twiddle factors
# Actually, let's use a more interesting signal: sum of two sinusoids
# x[n] = cos(2π·4·n/64) + 0.5·cos(2π·11·n/64)
# Bins 4 and 11 (and mirrors 53, 60) should be large
test_input = []
for n in range(N):
    x = math.cos(2 * math.pi * 4 * n / N) + 0.5 * math.cos(2 * math.pi * 11 * n / N)
    test_input.append(to_q78(x))

# Compute reference DFT magnitudes (in Q7.8) for verification
ref_real = [0.0] * N
ref_imag = [0.0] * N
for k in range(N):
    for n in range(N):
        angle = -2.0 * math.pi * k * n / N
        ref_real[k] += (test_input[n] / SCALE) * math.cos(angle)
        ref_imag[k] += (test_input[n] / SCALE) * math.sin(angle)

# Top bins by magnitude
mags = [(math.sqrt(ref_real[k]**2 + ref_imag[k]**2), k) for k in range(N)]
mags.sort(reverse=True)

# Expected output: print magnitude² of bins 0..15 (scaled by N for readability)
# Actually let's just print the real part of all bins — simpler verification

lines = []
lines.append("/*")
lines.append(" * FFTTest.java — 64-point radix-2 DIT FFT in Q7.8 fixed-point.")
lines.append(" *")
lines.append(" * Large constant tables (twiddle factors, bit-reversal permutation)")
lines.append(" * plus substantial computation code for pager stress testing.")
lines.append(" *")
lines.append(f" * Input: cos(2pi*4*n/64) + 0.5*cos(2pi*11*n/64)")
lines.append(" * Expected: strong peaks at bins 4, 11, 53, 60")
lines.append(" */")
lines.append("public class FFTTest {")
lines.append("")
lines.append("    // Q7.8 fixed-point multiply: (a * b) >> 8")
lines.append("    static int qmul(int a, int b) {")
lines.append("        return (a * b) >> 8;")
lines.append("    }")
lines.append("")
lines.append("    static void printInt(int v) {")
lines.append("        if (v < 0) { Native.putchar('-'); v = -v; }")
lines.append("        if (v >= 10000) Native.putchar('0' + (v / 10000) % 10);")
lines.append("        if (v >= 1000) Native.putchar('0' + (v / 1000) % 10);")
lines.append("        if (v >= 100)  Native.putchar('0' + (v / 100) % 10);")
lines.append("        if (v >= 10)   Native.putchar('0' + (v / 10) % 10);")
lines.append("        Native.putchar('0' + v % 10);")
lines.append("    }")
lines.append("")
lines.append("    public static void main(String[] args) {")

# Twiddle tables as local arrays
lines.append("        // Twiddle factors (Q7.8)")
cos_vals = ", ".join(str(v) for v in twiddle_cos)
sin_vals = ", ".join(str(v) for v in twiddle_sin)
lines.append(f"        int[] twCos = new int[]{{ {cos_vals} }};")
lines.append(f"        int[] twSin = new int[]{{ {sin_vals} }};")
lines.append("")

# Bit-reversal table
br_vals = ", ".join(str(v) for v in bitrev_table)
lines.append(f"        int[] bitrev = new int[]{{ {br_vals} }};")
lines.append("")

# Input signal
sig_vals = ", ".join(str(v) for v in test_input)
lines.append("        // Input signal (Q7.8)")
lines.append(f"        int[] xr = new int[]{{ {sig_vals} }};")
lines.append("        int[] xi = new int[64];")  # imaginary part = 0
lines.append("")

# Bit-reversal permutation
lines.append("        // Bit-reversal permutation")
lines.append("        for (int i = 0; i < 64; i++) {")
lines.append("            int j = bitrev[i];")
lines.append("            if (i < j) {")
lines.append("                int tmp = xr[i]; xr[i] = xr[j]; xr[j] = tmp;")
lines.append("                tmp = xi[i]; xi[i] = xi[j]; xi[j] = tmp;")
lines.append("            }")
lines.append("        }")
lines.append("")

# FFT butterfly stages — UNROLLED
lines.append("        // FFT butterfly stages (unrolled)")
lines.append("        int tr, ti, tw_idx;")

for stage in range(LOG2N):
    half = 1 << stage
    step = 2 * half
    tw_step = N // step  # twiddle index step
    lines.append(f"")
    lines.append(f"        // === Stage {stage}: half={half}, step={step} ===")
    for group_start in range(0, N, step):
        for k in range(half):
            tw_idx = k * tw_step
            j = group_start + k
            j2 = j + half
            lines.append(f"        tr = qmul(twCos[{tw_idx}], xr[{j2}]) - qmul(twSin[{tw_idx}], xi[{j2}]);")
            lines.append(f"        ti = qmul(twSin[{tw_idx}], xr[{j2}]) + qmul(twCos[{tw_idx}], xi[{j2}]);")
            lines.append(f"        xr[{j2}] = xr[{j}] - tr; xi[{j2}] = xi[{j}] - ti;")
            lines.append(f"        xr[{j}] = xr[{j}] + tr; xi[{j}] = xi[{j}] + ti;")

lines.append("")

# Output: print magnitude² of first 16 bins (and last few) as decimal
lines.append("        // Output: bin magnitudes (Q7.8 squared >> 8 for readability)")
lines.append("        for (int k = 0; k < 16; k++) {")
lines.append("            int mag2 = qmul(xr[k], xr[k]) + qmul(xi[k], xi[k]);")
lines.append("            printInt(mag2);")
lines.append("            Native.putchar(' ');")
lines.append("        }")
lines.append("        Native.putchar('\\n');")

# Also print a checksum (sum of all |re| values)
lines.append("        int cksum = 0;")
lines.append("        for (int k = 0; k < 64; k++) {")
lines.append("            int v = xr[k] < 0 ? -xr[k] : xr[k];")
lines.append("            cksum = cksum + v;")
lines.append("        }")
lines.append("        printInt(cksum);")
lines.append("        Native.putchar('\\n');")

lines.append("        Native.halt();")
lines.append("    }")
lines.append("}")

with open("tests/FFTTest.java", "w") as f:
    f.write("\n".join(lines) + "\n")

# Count unrolled butterfly lines
butterfly_count = sum(N // (2 << s) * (1 << s) for s in range(LOG2N))
print(f"Generated tests/FFTTest.java ({len(lines)} lines)")
print(f"  {butterfly_count} butterfly operations fully unrolled")
print(f"  Twiddle table: {N//2} complex entries")
print(f"  Bit-reversal table: {N} entries")
