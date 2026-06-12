# Building picoJVM

## Host builds

Requirements: a C compiler, `python3`, and a JDK (`javac` — only needed to
pack test programs and bootstrap picojc).

```bash
make                  # ./picojvm        — v3, host caps
make ./picojvm-large  # v3+v4 loaders, large capacity tables
make ./picojvm-paged  # paged program space (programs > 64K)
```

Host binaries are rebuilt automatically when `HOST_VM_OPTS` change (a flags
stamp in `build/` deletes them on mismatch), so test targets that override
options cannot leave a stale binary behind.

## 8085 builds

Requirements: the outer [llvm-8085](https://github.com/apullin/llvm-8085)
checkout — `clang`/`ld.lld` from `llvm-project/build-clang-8085`, the
sysroot (`crt0`, `libgcc`, `libc`), and the `i8085-trace` simulator. Paths
are derived from this repo's position inside the llvm-8085 tree.

```bash
# Build + run one program on the simulator
make sim PJVM_FILE=tests/Fib.pjvm
make sim-Fib                      # same, by test name

# Smoke suites (build, run, verify output bytes against expected/)
make test-sim-smoke
make test-sim-gc-smoke
```

The simulator target uses the flat 64K map: VM image at 0, output window at
0xE000, trap record at 0xE080, heap between `_end` and 0xE000. The default
linker script (`ldscripts/i8085-64k-flat-boot.ld`) places the image loader
in `.pjvmboot` inside the heap window, where it is reclaimed after parse.

### Toolchain flags

| flag | default | effect |
|---|---|---|
| `TARGET_OUTLINE` | 1 | I8085 MachineOutliner (`-mllvm -enable-machine-outliner=always`); ~−30% interpreter text |
| `TARGET_LTO` | 0 | Full LTO: C compiles to bitcode, codegen at link time over one merged module, so the outliner sees cross-TU repeats (~−2.6K ROM, ~+2.2K heap on the flagship) |
| `TARGET_ASM_HELPERS` | 1 | Hand-written 8085 helpers (`platform/i8085_helpers.S`) replace compiler-generated stack ops and copies |
| `TARGET_OPT` | Oz | optimization level for target builds |

Target objects are guarded by a flags stamp like the host binaries:
toggling `TARGET_LTO` (which changes the object *format*) or any other
target flag removes stale objects automatically.

**LTO caveats:**

- Codegen happens in the linker, so `-mllvm` codegen flags go on the
  `ld.lld` line (the Makefile handles this when `TARGET_LTO=1`).
- `ld.lld` must be built from the same backend tree as `clang`. The ninja
  targets for clang do **not** rebuild lld; after backend changes run
  `ninja -C build-clang-8085 lld`. A stale lld accepts the flags and
  silently codegens with the old backend — if LTO sizes regress
  mysteriously, compare `bin/lld` and `bin/clang-*` mtimes first.
- Use full LTO, not ThinLTO: ThinLTO codegens modules in parallel backends
  and the outliner's view shrinks back to per-module.
- Functions placed in special sections survive whole-program inlining
  because `PJVM_BOOT_FN` carries `noinline`; keep that property if adding
  overlay sections.

### Hand-written assembly and the C ABI

`platform/i8085_helpers.S` implements hot helpers (stack push/pop, locals,
fetch, string primitives, copies) as C-callable functions, and in one place
calls *back into C* (`heap_alloc`) with a hand-built argument frame. If you
change the signature of any C function the helpers call, update the frame:
`grep CALL platform/i8085_helpers.S` and audit anything that isn't a local
label. The C calling convention here: arguments on the stack, 16-bit
returns in BC, no callee-saved registers.

## Bare metal

`platform/i8085_target.c` is the hardware shim (32K ROM split map). See
[DEPLOYMENT.md](DEPLOYMENT.md) for ROM budget planning and the deployment
configuration ladder.

## Porting to a new target

1. Copy `platform/generic.c`.
2. Implement the platform callbacks declared in `src/pjvm.h`:
   - memory: `heap_alloc`, `r8`, `w8`, `r16`, `w16`
   - I/O: `pjvm_platform_putchar`, `pjvm_platform_peek8`/`poke8`,
     `pjvm_platform_out`, `pjvm_platform_trap`, term/key/ticks
   - file I/O (if `PJVM_USE_FILE_NATIVES`): open/read/write/close/delete
3. Link with `src/pjvm.c`, `src/pjvm_heap.c`, `src/pjvm_gc.c`.
4. Set capacity and feature macros for your RAM/ROM budget — see
   [CONFIGURATION.md](CONFIGURATION.md).

Call sequence at boot: `pjvm_parse(image)`, then
`pjvm_heap_init(&ctx, start, limit)` (this binds the context — the heap,
GC, and interpreter all operate on the bound `g_pjvm`), then
`pjvm_run(&ctx)`.
