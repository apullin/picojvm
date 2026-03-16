#!/usr/bin/env python3
"""Generate BigSwitch.java and BigLUT.java for pager stress testing.

BigSwitch: 256-state machine using lookupswitch. Each state emits a byte
and transitions to a deterministic next state. Runs for N steps.
Stresses paging with scattered jumps across a large switch table.

BigLUT: 256-entry lookup table initialized in <clinit>. Performs a series
of indexed lookups and emits results. Stresses sequential paging during
init, then scattered access during lookup.

Both use a fixed PRNG seed so output is deterministic.
"""

import sys

SEED = 0xDEAD
N_STATES = 256
N_STEPS = 32      # steps to execute (= output bytes)
N_LUT = 256
N_LOOKUPS = 32    # lookups to perform (= output bytes)


def lcg(state):
    """Simple 16-bit LCG for deterministic sequences."""
    return (state * 25173 + 13849) & 0xFFFF


def gen_big_switch(path):
    rng = SEED
    # Build a random permutation for next-state (ensures one big cycle
    # visiting all 256 states before repeating)
    perm = list(range(N_STATES))
    for i in range(N_STATES - 1, 0, -1):
        rng = lcg(rng)
        j = rng % (i + 1)
        perm[i], perm[j] = perm[j], perm[i]
    # Generate emit bytes independently
    table = []
    for i in range(N_STATES):
        rng = lcg(rng)
        emit = rng & 0xFF
        table.append((emit, perm[i]))

    lines = []
    lines.append("class BigSwitch {")
    lines.append("    static int step(int state) {")
    lines.append("        switch (state) {")
    for i, (emit, nxt) in enumerate(table):
        lines.append(f"            case {i}: Native.putchar({emit}); return {nxt};")
    lines.append("            default: return -1;")
    lines.append("        }")
    lines.append("    }")
    lines.append("")
    lines.append("    public static void main(String[] args) {")
    lines.append("        int state = 0;")
    lines.append(f"        for (int i = 0; i < {N_STEPS}; i++) {{")
    lines.append("            state = step(state);")
    lines.append("        }")
    lines.append("        Native.halt();")
    lines.append("    }")
    lines.append("}")

    with open(path, 'w') as f:
        f.write('\n'.join(lines) + '\n')

    # Compute expected output
    state = 0
    output = []
    for _ in range(N_STEPS):
        emit, nxt = table[state]
        output.append(emit)
        state = nxt
    return output


def gen_big_lut(path):
    rng = SEED
    # Generate LUT values
    lut = []
    for _ in range(N_LUT):
        rng = lcg(rng)
        lut.append(rng & 0xFF)

    # Generate lookup indices
    indices = []
    for _ in range(N_LOOKUPS):
        rng = lcg(rng)
        indices.append(rng % N_LUT)

    lines = []
    lines.append("class BigLUT {")
    lines.append(f"    static int[] table = new int[{N_LUT}];")
    lines.append("")
    # Use clinit to fill the table — generates lots of bytecode
    lines.append("    static {")
    for i, v in enumerate(lut):
        lines.append(f"        table[{i}] = {v};")
    lines.append("    }")
    lines.append("")
    lines.append("    public static void main(String[] args) {")
    # Perform lookups with indices baked into bytecode
    for idx in indices:
        lines.append(f"        Native.putchar(table[{idx}]);")
    lines.append("        Native.halt();")
    lines.append("    }")
    lines.append("}")

    with open(path, 'w') as f:
        f.write('\n'.join(lines) + '\n')

    # Compute expected output
    output = [lut[i] for i in indices]
    return output


def write_expected(path, output):
    hex_str = ' '.join(f'{b:02x}' for b in output)
    with open(path, 'w') as f:
        f.write(hex_str + '\n')


if __name__ == '__main__':
    base = sys.argv[1] if len(sys.argv) > 1 else '.'

    out_switch = gen_big_switch(f'{base}/tests/BigSwitch.java')
    write_expected(f'{base}/expected/BigSwitch.hex', out_switch)
    print(f"BigSwitch: {N_STATES} states, {N_STEPS} steps, {len(out_switch)} output bytes")
    print(f"  Expected: {' '.join(f'{b:02x}' for b in out_switch[:16])}...")

    out_lut = gen_big_lut(f'{base}/tests/BigLUT.java')
    write_expected(f'{base}/expected/BigLUT.hex', out_lut)
    print(f"BigLUT: {N_LUT} entries, {N_LOOKUPS} lookups, {len(out_lut)} output bytes")
    print(f"  Expected: {' '.join(f'{b:02x}' for b in out_lut[:16])}...")
