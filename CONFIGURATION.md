# Configuring picoJVM

Everything is a `-D` macro. Defaults live in `src/pjvm.h` (capacities) and
`src/pjvm_opts.h` (features); both are overridable from the command line.
The Makefile's host and target flag stamps rebuild affected objects when
options change.

## Capacity caps

| macro | host default | 8085 target |
|---|---|---|
| `PJVM_METHOD_CAP` | 256 | 64 |
| `PJVM_CLASS_CAP` | 64 | 16 |
| `PJVM_VTABLE_CAP` | 256 | 128 |
| `PJVM_STATIC_CAP` | 1024 | 32 |
| `PJVM_MAX_STACK` | 256 | 64 |
| `PJVM_MAX_LOCALS` | 1024 | 128 |
| `PJVM_MAX_FRAMES` | 64 | 16 |

Caps bound RAM tables and are checked at load (`PJVM_TRAP_CAPACITY`).
Call depth additionally clamps at 127 (`PJVM_FDEPTH_LIMIT` — `fdepth` is an
`int8_t`; the `PJVMCtx` layout is frozen for the 8085 asm helpers).
`PJVM_STACK_HEADROOM` defaults to 32 and is the largest per-method
`max_stack` accepted by the loader; it must not exceed `PJVM_MAX_STACK`.
Raise both when packing unusually stack-heavy javac methods.

## Image format loaders

| macro | default | effect |
|---|---|---|
| `PJVM_ENABLE_V3` | 1 | accept compact v3 images (8-bit metadata) |
| `PJVM_ENABLE_V4` | 0 | accept v4 images (16-bit ids, `wide` locals); v4-only saves ~3.6K vs dual |

`pjvmpack` emits v3 by default and auto-selects v4 when 8-bit limits are
exceeded; `--format v3|v4` forces. `--pack-method-table` (ULEB method
table) is incompatible with `PJVM_MT_IN_IMAGE`.

## Heap and GC

| macro | default | effect |
|---|---|---|
| `PJVM_HEAP_MODE` | `PJVM_HEAP_BUMP` | `PJVM_HEAP_FREELIST` enables the coalescing free-list allocator (required for GC) |
| `PJVM_GC_TRIGGERS` | 0 (GC off) | OR of trigger bits below; nonzero compiles the collector in |
| `PJVM_GC_WATERMARK_PCT` | 75 | watermark threshold (precomputed at init as a 256-byte page count) |
| `PJVM_GC_RANDOM_MASK` | 0x0007 | probability mask for the random trigger |
| `PJVM_GC_ALLOC_BITMAP` | 0 | exact allocation bitmap: O(1) conservative-root validation, costs `PJVM_GC_BITMAP_SPAN/16` bytes of RAM |
| `PJVM_GC_BITMAP_SPAN` | 0x10000 | size it to the real heap window |

Trigger bits: `PJVM_GC_TRIG_ALLOC_FAIL` (0x01), `PJVM_GC_TRIG_WATERMARK`
(0x02), `PJVM_GC_TRIG_RETURN` (0x04),
`PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK` (0x08).

Recommended: `PJVM_HEAP_MODE=PJVM_HEAP_FREELIST PJVM_GC_TRIGGERS=3
PJVM_GC_WATERMARK_PCT=75` — the configuration exercised by the host
compatibility suite and the 8085 GC sim runs. `PJVM_GC_TRIGGERS=1`
(collect-on-full only) is the lean variant, ~0.5K smaller.

Collector design: non-moving mark/sweep over the free-list heap. Roots are
exact (operand stack, locals, statics, native temp roots). Marking is
*trusted* through typed slots — ref-array elements and bitmap-declared
object fields mark directly via the block header — and conservative only
for ambiguous words (an int that aliases a heap address). Images carry
per-class reference bitmaps and a static-ref bitmap; older images fall back
to conservative scanning. Native code pins C-held refs across allocations
with `PJVM_GC_PROTECT`/`PJVM_GC_UNPROTECT` (the collector never moves
objects, so pinning only prevents reclamation).

## Memory layout options

| macro | default | effect |
|---|---|---|
| `PJVM_MT_IN_IMAGE` | 0 | serve method metadata directly from the image's fixed-width tables instead of unpacking ~22 bytes/method to RAM. Saves KBs of RAM and the unpack loops; metadata reads become image reads. Incompatible with `PJVM_PAGED` (compile error). |
| `PJVM_BOOT_OVERLAY` | 0 | place the loader in `.pjvmboot` (see `ldscripts/i8085-64k-flat-boot.ld`); the heap reclaims it after parse. `PJVM_BOOT_FN` carries `noinline` so LTO cannot strand loader code in `.text`. |
| `PJVM_PAGED` | unset | 32-bit paged program space with an LRU chunk cache; programs larger than 64K page in from disk |
| `PJVM_FAST_HALT` | 1 | one-byte halt check in the dispatch loop (pc ≥ 0xFF000000 reads as halt); exact 4-byte compare available with 0 |

## Native tiers (deployment knobs, not feature cuts)

| macro | default | effect |
|---|---|---|
| `PJVM_USE_FILE_NATIVES` | 1 | disk natives; a cartridge-style target compiles them out (~−1.1K with term) |
| `PJVM_USE_TERM_NATIVES` | 1 | terminal/key natives; headless targets drop them |
| `PJVM_USE_ENUM_NATIVES` | 1 | javac-enum support for images packed *without* the `java/lang/Enum` shim; shim-packed images never call these (~−1K when off) |
| `PJVM_USE_EXT_STRING_APIS` | 0 | legacy C tier of the algorithmic String API (~8.5K). Off since the `java/lang/String` shim: string algorithms ship as bytecode over the always-present primitives (length/charAt/equals/hashCode/constructors). Enable only for old images packed without the shim. |
| `PJVM_USE_EXT_JAVA_LANG_APIS` | host 1 / 8085 0 | extended java.lang natives |

Calls into a compiled-out group trap `BAD_NATIVE` loudly.

The shim model: `pjvmpack` prefers real methods on packed `java/lang`
classes and gap-fills natives only for what's missing, so a program packed
with the shims runs its java.lang as ordinary bytecode and the VM's
corresponding C is dead weight you can compile out.

## Reduced-opcode profile

`PJVM_PROFILE_SELFHOST_SET=1` drops opcode handlers the self-hosted
compiler corpus never emits (`pop2`, `dup_x1`, `swap`, the single-operand
`if` family, `ifnull`/`ifnonnull`). Each has an individual
`PJVM_USE_OP_*` override. Not suitable for arbitrary javac output.

## 8085 assembly helpers

`PJVM_ASM_HELPERS` (a target build option, `TARGET_ASM_HELPERS=1` in the
Makefile) enables the hand-written helper pack; individual
`PJVM_USE_ASM_*` gates select stack helpers, copies
(`arraycopy`/`memcmp`/`write_bytes`), and optionally `string_from_bytes`
(off by default because it duplicates the GC-safe C constructor path). All are
non-paged-target only; offsets assume the sim-build capacity values (see
the header comment in `platform/i8085_helpers.S`). Builds using the helper
pack are rejected unless stack=64, locals=128, statics=32, and frames=16;
disable `TARGET_ASM_HELPERS` before changing those capacities.
