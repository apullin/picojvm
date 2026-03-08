# Exec Plan: picoJVM Paged Bytecode VM

## Purpose

Enable picoJVM to execute Java programs larger than available RAM by paging
read-only bytecode from disk on demand. A CP/M machine with 64KB RAM and a
243KB floppy could run a ~200KB Java program, with the interpreter keeping
only 512 bytes of bytecode cached in RAM at any time.

**Observable outcome:** After all phases, `make test` passes with the widened
32-bit PC, and a paged-mode build on the host can execute test programs by
reading bytecode pages from the `.pjvm` file on disk (via `fseek`/`fread`)
rather than loading the entire bytecode section into memory.

## Context and Orientation

**Working directory:** `tooling/picojvm/`

**Key files:**

| File | Role |
|------|------|
| `core.h` | Portable interpreter core (~1000 lines). All bytecode access is `bc[g_pjvm->pc]`. PC is `uint16_t`. |
| `pjvmpack.py` | `.class` → `.pjvm` converter. Emits 2-byte LE method code offsets. |
| `platform/host.c` | Desktop host platform (stdio/fopen). Builds `./picojvm`. |
| `platform/generic.c` | Pure-C reference platform. No stdio. |
| `platform/i8085_sim.c` | 8085 simulator platform. Bytecode embedded as const array. |
| `platform/i8085_helpers.S` | Hand-written 8085 ASM for hot interpreter helpers. |
| `platform/i8085_core_hotcold.h` | Alternate hot/cold split interpreter core. |
| `Makefile` | Builds host binary, packs tests, runs 8085 sim builds. |

**Terminology:**

- **PC**: Program counter — byte offset into the bytecode stream.
- **`bc`**: Pointer to the bytecode array in RAM. All bytecode reads are `bc[pc]`.
- **`m_co[]`**: Method code offset table — `m_co[i]` is the bytecode offset where method `i` starts.
- **`m_cb[]`**: Method CP-base table — index into the constant pool resolution array.
- **`PJVMFrame`**: Saved frame on method call — holds `pc`, `mi`, `lb`, `so`, `cb`, `lt`.
- **`PJVMCtx`**: Interpreter context — holds `pc`, `sp`, stack, locals, frames, heap_ptr.
- **Sentinel**: `pc = 0xFFFF` (currently) signals "interpreter should stop". Will become `0xFFFFFFFF`.
- **Page slot**: A 128-byte buffer in RAM that caches one page of bytecode.
- **Page tag**: The page number currently loaded in a slot. `0xFFFFFFFF` = empty.

**Current bytecode access sites in `core.h`** (6 total):

1. `bcread()` (~line 197): `return bc[g_pjvm->pc++];` — single byte fetch + advance
2. `bread()` (~line 201): `bc[g_pjvm->pc]` and `bc[g_pjvm->pc + 1]` — 2-byte big-endian read
3. `cpread()` (~line 207): `bc[g_pjvm->pc]` and `bc[g_pjvm->pc + 1]` — CP index read
4. Opcode fetch (~line 505): `bc[g_pjvm->pc++]` — main dispatch
5. lookupswitch (~lines 881-882): `bc[g_pjvm->pc]` through `bc[g_pjvm->pc+3]` — 4-byte match key
6. multianewarray (~line 977+): `bc[g_pjvm->pc]` — dimension count read (uses bcread)

**Branch sites that assign `pc`** (always via `g_pjvm->pc = opc + offset` or `g_pjvm->pc = m_co[mi]`):

- Conditional branches: ~20 sites (lines 744-841)
- `goto`: line 845
- tableswitch/lookupswitch: lines 850-891
- Method invoke: line 364 (`pjvm_inv`)
- Method return: line 380 (`pjvm_ret`)
- Exception handler: line 418 (`pjvm_throw`)
- Sentinel stops: lines 293, 375, 430

---

## Plan of Work

The implementation is split into 5 phases. Each phase produces a buildable,
testable state. Phases 1-2 change no behavior (just types and abstraction).
Phase 3 adds the actual paging mechanism behind a compile-time flag. Phase 4
updates the file format. Phase 5 adds the CP/M platform.

---

## Phase 1: Widen PC to `uint32_t`

**Goal:** Change all PC-related types from `uint16_t` to `uint32_t` with zero
behavioral change. All 12 tests must still pass identically.

**Edits in `core.h`:**

1. Add sentinel define at the top (near the capacity macros):
   ```c
   #define PJVM_PC_HALT 0xFFFFFFFFu
   ```

2. In `PJVMFrame` struct (~line 89): change `uint16_t pc;` to `uint32_t pc;`

3. In `PJVMCtx` struct (~line 101): change `uint16_t pc, cur_cb;` to
   `uint32_t pc; uint16_t cur_cb;` (only `pc` widens; `cur_cb` stays 16-bit
   since the CP resolution table is always small and resident)

4. In method table (~line 117): change `m_co[PJVM_METHOD_CAP]` from `uint16_t`
   to `uint32_t`

5. In `pjvm_parse()` — the line that reads `m_co[i]` (~line 246): widen the
   read to 4 bytes LE. Currently:
   ```c
   m_co[i] = (uint16_t)p[4] | ((uint16_t)p[5] << 8);
   ```
   Change to:
   ```c
   m_co[i] = (uint32_t)p[4] | ((uint32_t)p[5] << 8)
            | ((uint32_t)p[6] << 16) | ((uint32_t)p[7] << 24);
   ```
   **BUT WAIT** — the method table entry is currently 12 bytes with code_offset
   at bytes [4-5]. We can't just read 4 bytes from a 2-byte field in the
   existing format. So for Phase 1, keep reading 2 bytes but store into
   `uint32_t` (zero-extended). The format change comes in Phase 4.
   ```c
   m_co[i] = (uint32_t)((uint16_t)p[4] | ((uint16_t)p[5] << 8));
   ```

6. Replace all 3 sentinel assignments (`pc = 0xFFFF`) with `pc = PJVM_PC_HALT`:
   - ~line 293: `g_pjvm->pc = PJVM_PC_HALT;`
   - ~line 375: `g_pjvm->pc = PJVM_PC_HALT;`
   - ~line 430: `g_pjvm->pc = PJVM_PC_HALT;`

7. Replace sentinel check in main loop (~line 503):
   ```c
   while (g_pjvm->pc != PJVM_PC_HALT) {
   ```

8. Widen `opc` in `pjvm_exec()` (~line 504): `uint32_t opc = g_pjvm->pc;`

9. Widen `base` in tableswitch (~line 851) and lookupswitch (~line 870):
   `uint32_t base = m_co[g_pjvm->cur_mi];`

10. Widen `rel_pc` in `pjvm_throw()` (~line 391):
    `uint32_t rel_pc = throw_pc - m_co[g_pjvm->cur_mi];`

11. Widen `throw_pc` parameter/local in `pjvm_throw()` to `uint32_t`.

12. In `pjvm_run()` clinit loop (~line 483) and main setup (~line 493):
    `j->pc = m_co[mi];` — already fine since `m_co` is now `uint32_t` and
    `j->pc` is `uint32_t`.

**Edits in `platform/i8085_core_hotcold.h`:**

Same pattern of changes as core.h (this is the alternate interpreter core).
Grep for `uint16_t pc`, `0xFFFF`, `opc`, `base`, `rel_pc` and apply identical
widening.

**Verification:**

```bash
cd tooling/picojvm
make clean && make && make test
```

Expected: all 12 tests produce identical output. The `picoJVM |` header line
and all test outputs unchanged. No compiler warnings about truncation or
comparison between signed/unsigned.

Also verify no warnings:
```bash
make CFLAGS="-Wall -Wextra -O2 -Wconversion" 2>&1 | grep -i warn
```

---

## Phase 2: `BC()` Abstraction Macro

**Goal:** Replace all direct `bc[...]` bytecode accesses with a `BC(addr)`
macro. In non-paged mode, `BC(x)` expands to `bc[(x)]` — zero behavioral
change. This creates the hook point for Phase 3.

**Edits in `core.h`:**

1. After the `bc` pointer declaration, add:
   ```c
   #ifdef PJVM_PAGED
   static uint8_t bc_fetch(uint32_t addr);
   #define BC(a) bc_fetch(a)
   #else
   #define BC(a) bc[(a)]
   #endif
   ```

2. In `bcread()` (~line 197):
   ```c
   static uint8_t bcread(void) { return BC(g_pjvm->pc++); }
   ```

3. In `bread()` (~line 201): replace `bc[g_pjvm->pc]` and `bc[g_pjvm->pc + 1]`
   with `BC(g_pjvm->pc)` and `BC(g_pjvm->pc + 1)`.

4. In `cpread()` (~line 207): replace both `bc[g_pjvm->pc]` and
   `bc[g_pjvm->pc + 1]` with `BC(...)`.

5. Opcode fetch (~line 505): replace `bc[g_pjvm->pc++]` with `BC(g_pjvm->pc++)`.

6. lookupswitch match key reads (~lines 881-882): replace all 4 `bc[g_pjvm->pc]`
   through `bc[g_pjvm->pc+3]` with `BC(...)`.

**Do NOT change:**

- `heap_mem[]` accesses (r8/w8/r16/w16) — these are data, not code
- `prog_data[]` accesses in `pjvm_parse()` — the parser reads headers/tables
  from the resident portion, not from paged bytecode
- `cpr[]` (CP resolution table) accesses — resident data

**Verification:**

```bash
make clean && make && make test
```

Same output as Phase 1. `PJVM_PAGED` is not defined, so `BC(x)` = `bc[(x)]`.

---

## Phase 3: Page Cache Implementation

**Goal:** When `PJVM_PAGED` is defined, bytecode is read from disk page-by-page
instead of being fully resident in RAM. The host platform implements disk I/O
via `fseek`/`fread`.

**New platform shim function signature:**
```c
static void pjvm_platform_read_page(uint32_t file_offset, uint8_t *buf, uint16_t len);
```

**Edits in `core.h`:**

1. Add page cache structures inside `#ifdef PJVM_PAGED`:
   ```c
   #ifdef PJVM_PAGED

   #ifndef PJVM_PAGE_SHIFT
   #define PJVM_PAGE_SHIFT 7     /* 128 bytes per page */
   #endif
   #define PJVM_PAGE_SIZE  (1u << PJVM_PAGE_SHIFT)
   #define PJVM_PAGE_MASK  (PJVM_PAGE_SIZE - 1u)

   #ifndef PJVM_PAGE_SLOTS
   #define PJVM_PAGE_SLOTS 4
   #endif

   static uint8_t  pjvm_page_data[PJVM_PAGE_SLOTS][PJVM_PAGE_SIZE];
   static uint32_t pjvm_page_tag[PJVM_PAGE_SLOTS];  /* 0xFFFFFFFF = empty */
   static uint8_t  pjvm_page_ref[PJVM_PAGE_SLOTS];  /* clock reference bit */
   static uint8_t  pjvm_page_hand;                   /* clock hand */
   static uint32_t pjvm_bc_file_offset;              /* byte offset of bytecodes in .pjvm file */
   static uint8_t  pjvm_hot_slot;                    /* last-accessed slot (fast path) */

   #ifdef PJVM_TRACK_STATS
   static uint32_t pjvm_page_hits, pjvm_page_misses;
   #endif

   #endif /* PJVM_PAGED */
   ```

2. Add forward declaration for platform shim:
   ```c
   #ifdef PJVM_PAGED
   static void pjvm_platform_read_page(uint32_t file_offset, uint8_t *buf, uint16_t len);
   #endif
   ```

3. Implement `bc_fetch()`:
   ```c
   #ifdef PJVM_PAGED
   static uint8_t bc_fetch(uint32_t addr) {
       uint32_t pn = addr >> PJVM_PAGE_SHIFT;
       /* Hot-slot fast path */
       if (pjvm_page_tag[pjvm_hot_slot] == pn) {
   #ifdef PJVM_TRACK_STATS
           pjvm_page_hits++;
   #endif
           return pjvm_page_data[pjvm_hot_slot][addr & PJVM_PAGE_MASK];
       }
       /* Search all slots */
       for (uint8_t s = 0; s < PJVM_PAGE_SLOTS; s++) {
           if (pjvm_page_tag[s] == pn) {
               pjvm_page_ref[s] = 1;
               pjvm_hot_slot = s;
   #ifdef PJVM_TRACK_STATS
               pjvm_page_hits++;
   #endif
               return pjvm_page_data[s][addr & PJVM_PAGE_MASK];
           }
       }
       /* Miss — evict using clock algorithm */
   #ifdef PJVM_TRACK_STATS
       pjvm_page_misses++;
   #endif
       for (;;) {
           uint8_t h = pjvm_page_hand;
           if (!pjvm_page_ref[h]) {
               /* Evict this slot */
               pjvm_page_tag[h] = pn;
               pjvm_page_ref[h] = 1;
               pjvm_hot_slot = h;
               pjvm_page_hand = (h + 1) % PJVM_PAGE_SLOTS;
               pjvm_platform_read_page(
                   pjvm_bc_file_offset + (pn << PJVM_PAGE_SHIFT),
                   pjvm_page_data[h],
                   PJVM_PAGE_SIZE);
               return pjvm_page_data[h][addr & PJVM_PAGE_MASK];
           }
           pjvm_page_ref[h] = 0;
           pjvm_page_hand = (h + 1) % PJVM_PAGE_SLOTS;
       }
   }
   #endif /* PJVM_PAGED */
   ```

4. In `pjvm_parse()`: record `pjvm_bc_file_offset` — the byte offset from the
   start of the .pjvm data where the bytecodes section begins. Currently the
   parser walks through headers, class table, method table, CP resolution,
   int constants, string constants, then bytecodes. Track the running offset
   and store it when we reach the bytecodes section.

5. In `pjvm_parse()`: when `PJVM_PAGED`, do NOT set `bc = &prog_data[offset]`.
   Instead, leave `bc` unused (or NULL), and set `pjvm_bc_file_offset = offset`.
   Initialize all page tags to `0xFFFFFFFF` (empty).

6. In `pjvm_parse()`: when `PJVM_PAGED`, skip reading the bytecode section
   into RAM entirely. The parser still needs to read headers, method tables,
   class tables, CP resolution, and string constants into RAM (these are small
   and stay resident).

**Edits in `platform/host.c`:**

1. Add a file handle that stays open:
   ```c
   static FILE *pjvm_file_handle;
   ```

2. In the `main()` function, after opening the file and reading it, keep the
   handle open (or reopen it) for paged reads.

3. Implement the shim:
   ```c
   #ifdef PJVM_PAGED
   static void pjvm_platform_read_page(uint32_t file_offset, uint8_t *buf, uint16_t len) {
       fseek(pjvm_file_handle, (long)file_offset, SEEK_SET);
       fread(buf, 1, len, pjvm_file_handle);
   }
   #endif
   ```

4. In `main()`, after `pjvm_parse()`, print page stats after `pjvm_run()`:
   ```c
   #if defined(PJVM_PAGED) && defined(PJVM_TRACK_STATS)
   printf("pages: %u hits, %u misses (%.1f%% hit rate)\n",
          pjvm_page_hits, pjvm_page_misses,
          100.0 * pjvm_page_hits / (pjvm_page_hits + pjvm_page_misses));
   #endif
   ```

**Edits in `Makefile`:**

1. Add a new target for the paged host build:
   ```makefile
   picojvm-paged: platform/host.c core.h
   	$(CC) $(CFLAGS) -DPJVM_PAGED -o $@ $<
   ```

2. Add a `test-paged` target that runs all tests with the paged binary.

**Verification:**

```bash
# Non-paged build — must still work identically
make clean && make && make test

# Paged build
make picojvm-paged
./picojvm-paged tests/Fib.pjvm
# Expected: same Fibonacci output + page hit/miss stats line
# Expected: high hit rate (>95%) for small tests

# Run all tests paged
for t in tests/*.pjvm; do echo "=== $(basename $t) ==="; ./picojvm-paged $t; echo; done
```

All 12 tests must produce the same computational output (Fibonacci values,
sorted arrays, etc.) in both paged and non-paged modes. The only difference
is the page stats line in paged mode.

---

## Phase 4: `.pjvm` Format v2 (32-bit Code Offsets)

**Goal:** Update `pjvmpack.py` to emit 32-bit method code offsets, and update
`pjvm_parse()` to read them. This enables bytecode sections larger than 64KB.

**File format changes:**

Current method table entry (12 bytes):
```
[0]    max_locals
[1]    max_stack
[2]    arg_count
[3]    flags (static/native/interface)
[4-5]  code_offset (uint16_t LE)
[6-7]  code_bytes (uint16_t LE)
[8-9]  reserved
[10]   line_table_size
[11]   native_id (for native methods)
```

New method table entry (14 bytes):
```
[0]    max_locals
[1]    max_stack
[2]    arg_count
[3]    flags
[4-7]  code_offset (uint32_t LE)  ← widened
[8-9]  code_bytes (uint16_t LE)
[10-11] reserved
[12]   line_table_size
[13]   native_id
```

Current header (10 bytes):
```
[0-1]  magic: 0x85 0x4A
[2-9]  fields as documented
```

New header (14 bytes):
```
[0-1]  magic: 0x85 0x4B  ← version bump
[2]    n_methods
[3]    main_mi
[4]    n_static_fields
[5]    n_int_constants
[6]    n_classes
[7]    n_string_constants
[8-11] bytecodes_size (uint32_t LE)  ← widened
[12]   page_shift (0 = not paged, 7 = 128B)
[13]   reserved
```

**Edits in `pjvmpack.py`:**

1. Add `--v2` flag (default off, so existing tooling keeps working).
2. When `--v2`: emit magic `0x85, 0x4B`, 14-byte header, 14-byte method entries.
3. When not `--v2`: emit current format unchanged.

**Edits in `core.h` `pjvm_parse()`:**

1. Check magic byte [1]: `0x4A` = v1, `0x4B` = v2.
2. v1: read 10-byte header, 12-byte method entries, 2-byte code offsets (zero-extend to uint32_t).
3. v2: read 14-byte header, 14-byte method entries, 4-byte code offsets.
4. This gives full backward compatibility — old `.pjvm` files still work.

**Verification:**

```bash
# v1 format (default) — still works
make clean && make && make test

# v2 format
python3 pjvmpack.py tests/Fib.class -o tests/Fib.pjvm --v2 -v
./picojvm tests/Fib.pjvm
# Expected: same output

# v2 + paged
./picojvm-paged tests/Fib.pjvm
# Expected: same output + page stats
```

---

## Phase 5: CP/M Platform (Future)

**Goal:** Create `platform/cpm.c` that runs on a real CP/M system (or CP/M
emulator). This phase is documented here for completeness but may be
implemented later when a CP/M test environment is available.

**New file: `platform/cpm.c`:**

- `pjvm_platform_putchar()`: BDOS function 2 (console output)
- `pjvm_platform_read_page()`: BDOS function 33 (random read) — one sector per page
- `pjvm_platform_trap()`: BDOS function 0 (warm boot / exit)
- Memory layout: interpreter + resident data in low TPA, page cache + JVM
  state in upper TPA, BDOS at top of memory
- FCB for the .pjvm file kept open throughout execution
- The .pjvm filename passed via CP/M command tail (0x0080)

**Verification:** Run on a CP/M emulator (e.g., `cpm` or `RunCPM`) with a
disk image containing a `.pjvm` file larger than available TPA.

---

## Validation and Acceptance

After Phases 1-3 are complete, the following must all pass:

1. `make clean && make && make test` — all 12 tests pass, non-paged mode, identical output to baseline
2. `make picojvm-paged` — compiles without warnings
3. `./picojvm-paged tests/Fib.pjvm` — correct Fibonacci output + page stats showing >95% hit rate
4. All 12 tests produce correct output in paged mode
5. `make sim-Fib` — 8085 simulator build still works (non-paged, 32-bit PC has no effect on 8085 build since `m_co` values fit in 16 bits)
6. No compiler warnings with `-Wall -Wextra -Wconversion`

After Phase 4:

7. v2 format `.pjvm` files work in both paged and non-paged modes
8. v1 format `.pjvm` files still work (backward compatibility)

---

## Idempotence and Recovery

Each phase is independently buildable and testable. If something goes wrong:

- `git stash` or `git checkout -- core.h` to revert core.h
- `make clean` removes all generated artifacts
- Phases are cumulative but each one leaves the code in a working state
- The `#ifdef PJVM_PAGED` guard ensures non-paged builds are never affected

---

## Progress

- [x] Phase 1: Widen PC to `uint32_t` in core.h
- [x] Phase 1: Widen PC in i8085_core_hotcold.h
- [x] Phase 1: Verify all 12 tests pass (non-paged, host)
- [x] Phase 1: Verify 8085 sim build works (ASM helpers disabled; C-only correct)
- [x] Phase 2: Add `BC()` macro, replace all `bc[...]` accesses
- [x] Phase 2: Verify all 12 tests pass (non-paged, host)
- [x] Phase 3: Add page cache structures and `bc_fetch()`
- [x] Phase 3: Add `pjvm_platform_read_page()` to host.c
- [x] Phase 3: Record `pjvm_bc_file_offset` in `pjvm_parse()`
- [x] Phase 3: Add `picojvm-paged` Makefile target
- [x] Phase 3: Verify all 12 tests pass in paged mode
- [x] Phase 3: Verify page hit rate stats are printed
- [x] Phase 4: Add `--v2` flag to pjvmpack.py
- [x] Phase 4: Update `pjvm_parse()` for v1/v2 auto-detect
- [x] Phase 4: Verify v2 format works in both modes
- [x] Phase 4: Verify v1 backward compatibility
- [ ] Phase 5: CP/M platform (deferred)

## Surprises & Discoveries

- **2026-03-07**: ASM helpers (`i8085_helpers.S`) are incompatible with `uint32_t pc`. They read/write pc as 16-bit, and `bcread` increments only the low 16 bits. The C dispatch loop does 32-bit pc++, causing conflicts. Additionally, 32-bit array indexing (`bc[uint32_t]`) generates different code than the ASM helpers expect. Fix: disable `PJVM_ASM_HELPERS` in i8085_sim.c. Code size goes from 18.3KB→20.6KB (+2.3KB), speed from 1.04M→1.93M T-states (~2x slower). ASM helpers can be updated later with 32-bit pc support if needed.
- **2026-03-07**: The offsetof() test initially gave wrong results because it used `PJVM_STATIC_CAP=16` while the sim build uses `32`. Always verify offsets against the exact CAP values from the target platform file.

## Decision Log

- **2026-03-07**: Use `uint32_t` for PC, not `uint24_t`. Same codegen cost on 8085, no awkward 3-byte packing, natural C type. — AP + Claude
- **2026-03-07**: Page size = 128 bytes (matches CP/M sector). 4 slots = 512B RAM. Clock replacement policy. — Claude
- **2026-03-07**: Compile-time `#ifdef PJVM_PAGED`, not runtime dispatch. Zero overhead for non-paged builds. — Claude
- **2026-03-07**: Disable ASM helpers under `PJVM_PAGED` initially. The paged fetch path is C-only; ASM helpers would need significant rework to call `bc_fetch()`. — Claude
- **2026-03-07**: v2 format is opt-in (`--v2` flag in pjvmpack.py). Parser auto-detects v1 vs v2 by magic byte. Full backward compatibility. — Claude

## Outcomes & Retrospective

**Phases 1-4 complete (2026-03-07):**

- uint32_t PC widening: all types widened, PJVM_PC_HALT=0xFFFFFFFF, zero behavioral change
- BC() macro: clean abstraction for paged/non-paged bytecode access
- Page cache: 4 slots × 128B, clock eviction, hot-slot fast path. Hit rates 96.7%-99.9% on test suite
- v2 format: 14-byte header (32-bit bytecodes_size), 14-byte method entries (32-bit code_offset). Auto-detected by magic byte. Full v1 backward compatibility.
- ASM helpers disabled under uint32_t PC (C-only +2.3KB, ~2x slower on 8085 sim)
- All 12 tests pass in all 4 configurations: v1/non-paged, v1/paged, v2/non-paged, v2/paged
