# picoJVM

A portable Java bytecode interpreter for 8-bit and 16-bit microprocessors.

picoJVM runs real Java programs on systems with as little as 32KB of RAM.
The interpreter is written in portable C with a thin platform abstraction
layer, and includes **picojc**, a self-hosting Java compiler that runs on
picoJVM itself.

## Features

- **Near-Java 7 language support** — classes, interfaces, inheritance,
  virtual dispatch, exceptions, enums, for-each, varargs, packages/imports,
  string switch, multi-dimensional arrays, method overloading
- **89 JVM opcodes** — full integer arithmetic, object/array operations,
  virtual and interface dispatch, exception handling, type checking
- **~43KB ROM image on Intel 8085** (interpreter, asm helpers, runtime
  libraries, and an embedded test program; measured with the full feature
  set including bounds hardening — leaner configs shrink with `PJVM_USE_*`
  options)
- **Program-space paging** — LRU page cache allows programs larger than
  available RAM; only active code pages reside in memory
- **Configurable heap backends** — tiny bump allocator by default, optional
  coalescing free-list backend, and an optional non-moving mark-sweep GC
- **Execution context struct** — stack, locals, frames, and heap live in
  a `PJVMCtx` struct passed to all API calls
- **File I/O** — native file operations for disk-backed compilation on
  both host and embedded targets
- **~3,300 lines of C** across the interpreter core, heap backends, and
  GC, plus a few hundred lines per platform shim

## picojc — Self-Hosting Java Compiler

picojc is a multi-pass Java compiler written in Java, targeting picoJVM
bytecode. It compiles itself.

- **Self-hosting fixpoint** — javac builds gen0, gen0 compiles picojc to
  gen1, gen1 compiles itself to gen2, gen1 == gen2 (byte-identical)
- **~45KB compiled bytecode** (self-hosted binary)
- **~180KB source** across 11 Java files (~5,300 lines)
- **76 tests + 36 negative tests**, all passing on host
- **Disk-backed compilation** — reads source and writes output through
  native file I/O; supports single-file and multi-file (sources.lst) modes
- **Bootstrap from any modern JDK** — `javac` compiles picojc source,
  `pjvmpack.py` packs the .class files to .pjvm, then picojc runs on
  picoJVM to compile itself

### Supported Java Subset

Types: `int`, `byte`, `char`, `short`, `boolean`, `void`, references.
No `long`, `float`, `double`.

Classes, interfaces, single inheritance, constructors, static initializers,
`instanceof`, casts.  Enums (desugared to `static final int`).  `final`
field inlining.  Method overloading with arity-aware dispatch.  Varargs.
Packages and imports.

Expressions: full arithmetic, bitwise, shifts, comparisons, logical
short-circuit, ternary, pre/post increment, compound assignment.

Statements: `if`/`else`, `while`, `do-while`, `for`, enhanced `for`,
`switch` (int and String), `break`, `continue`, `return`, `throw`,
`try`/`catch`/`finally`.

Not supported: generics, lambdas, inner classes, annotations,
`synchronized`, auto-boxing, string `+` concatenation.

## Project Structure

```
src/
  pjvm.c              Interpreter core (1,318 lines)
  pjvm.h              Public types and API
platform/
  host.c              macOS / Linux host with file I/O
  i8085_sim.c          Intel 8085 simulator target
  i8085_target.c       Intel 8085 bare-metal target
  i8085_helpers.S      8085 assembly helpers
  generic.c            Portable reference platform
picojc/
  src/                 Compiler source (11 Java files)
  tests/               64 test programs
  tests-negative/      9 must-fail tests
  expected/            Golden output for all tests
  Makefile             Build, test, selfhost, disk modes
pjvmpack.py            .class to .pjvm packer (bootstrap tool)
Makefile               picoJVM host build and picoJVM-level tests
java/                  Standard-name picoJSE facades (`java.lang` today)
pj/                    picoJSE substrate (`pj.archive`, `pj.io`, `pj.text`, `pj.term`, `pj.util`)
```

## picoJSE Packages

`picoJSE` is the Java-side standard environment layer for `picoJVM`.

Naming convention: if picoJSE exposes a standard Java API, it should live under
the matching `java.*` name so stock `javac` bytecode resolves unchanged. The
`pj.*` root is for nonstandard picoJSE substrate and target helpers.

Current package roots:

- `java.lang.StringBuilder` — minimal Java-8 string-concat helper
- `pj.Native` — package-visible native bridge
- `pj.archive` — archive/container helpers (`Tar` and stored-entry `Zip` today)
- `pj.io` — substrate for routed console/text output plus file and byte/binary helpers
- `pj.util` — array, byte, and integer helpers
- `pj.text` — formatting, parsing, and small string utilities
- `pj.term` — retained cell-surface / terminal abstraction and drawing helpers

Planned standard I/O and collection shims should be added as `java.io.*` and
`java.util.*` facades layered on these helpers, not as parallel `pj.*` APIs.

The current `pj.term` implementation is intentionally text-first:

- host ANSI terminal
- serial-terminal / TUI route
- later direct character-display presenters such as SOL-20

Current tiny utility front-ends built on top of `pj.archive`:

- `PJTar`
- `PJUntar`
- `PJZip`
- `PJUnzip`
- `PJArc`

Useful commands:

```bash
# Build and run host-side package smoke tests
make test-TermSmoke test-FilesSmoke test-PicoJseStdSmoke test-TarSmoke test-ZipSmoke

# Run the standalone archive CLI tool tests
make test-pjtools

# Manual terminal demo (ANSI/raw-key capable host terminal)
make run-term-demo

# Prove the self-hosted compiler can build the real pj.* package sources
cd picojc
make test-disk-picojse
```

See [PICOJSE.md](PICOJSE.md) for the package split and current design direction.

## Quick Start

### Build and Test (Host)

```bash
# Build the host interpreter
make

# Run the picoJVM test suite (12 tests, javac + pjvmpack pipeline)
make test

# Build and test picojc (67 tests, self-hosting, disk modes)
cd picojc
make test

# Self-hosting fixpoint verification
make test-selfhost-fixpoint

# Everything
make test-all
```

### Porting to a New Target

1. Copy `platform/generic.c`
2. Implement the platform callbacks declared in `src/pjvm.h`:
   - Memory: `heap_alloc`, `r8`, `w8`, `r16`, `w16`
   - I/O: `pjvm_platform_putchar`, `pjvm_platform_peek8`,
     `pjvm_platform_poke8`, `pjvm_platform_out`, `pjvm_platform_trap`
   - File I/O: `pjvm_platform_file_open`, `pjvm_platform_file_read_byte`,
     `pjvm_platform_file_write_byte`, `pjvm_platform_file_close`,
     `pjvm_platform_file_delete`
3. Link with `src/pjvm.c`
4. Override capacity macros with `-D` flags as needed for your RAM budget

### Heap Backends and GC

The default build uses a simple bump allocator for minimum size.

Optional heap backends are selected with `PJVM_HEAP_MODE`:

- `PJVM_HEAP_BUMP` — default, smallest code size
- `PJVM_HEAP_FREELIST` — coalescing free-list allocator

The free-list backend also supports a non-moving mark-sweep collector. The
current collector uses:

- exact roots from operand stack, locals, and statics
- a non-moving sweep over the free-list heap
- exact ref-array element scanning
- exact object-field scanning when `.pjvm` class metadata includes per-class
  reference bitmaps
- conservative object fallback for older `.pjvm` binaries that do not yet emit
  that metadata

GC trigger policy is controlled by `PJVM_GC_TRIGGERS`, combining any of:

- `PJVM_GC_TRIG_ALLOC_FAIL`
- `PJVM_GC_TRIG_WATERMARK`
- `PJVM_GC_TRIG_RETURN`
- `PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK`

Current recommended GC configuration:

- `PJVM_HEAP_MODE=PJVM_HEAP_FREELIST`
- `PJVM_GC_TRIGGERS=3` (`PJVM_GC_TRIG_ALLOC_FAIL | PJVM_GC_TRIG_WATERMARK`)
- `PJVM_GC_WATERMARK_PCT=75`

This is the configuration exercised by the current host compatibility suite,
direct collector tests, and 8085 simulator GC smoke/stress runs.

The 8085 simulator uses two GC configs:

- smoke runs use a roomier simulated heap to validate semantics on the larger
  GC-enabled runtime image
- `AllocHeavyTest` keeps a tighter heap cap to force real collection pressure

Host examples:

```bash
# Free-list allocator only
make clean test HOST_VM_OPTS=-DPJVM_HEAP_MODE=PJVM_HEAP_FREELIST

# Experimental GC policy demos and direct collector test
make clean gc-policy-test test-gc-collect

# Force GC on the alloc-heavy integration test with a small host heap
make test-gc-alloc-heavy

# Run the broader host GC compatibility/stress matrix
make test-gc-host-suite

# Run the broader 8085 simulator GC matrix
make test-gc-sim-suite

# Everything above
make test-gc-suite
```

### Capacity Defaults

| Macro | Host Default | 8085 Target |
|-------|-------------|-------------|
| `PJVM_METHOD_CAP` | 256 | 64 |
| `PJVM_CLASS_CAP` | 64 | 16 |
| `PJVM_VTABLE_CAP` | 256 | 128 |
| `PJVM_STATIC_CAP` | 1024 | 32 |
| `PJVM_MAX_STACK` | 256 | 64 |
| `PJVM_MAX_LOCALS` | 1024 | 128 |
| `PJVM_MAX_FRAMES` | 64 | 16 |

## The .pjvm Binary Format

picoJVM uses a custom binary format that pre-resolves the Java constant
pool at pack time. The interpreter never parses class file structures at
runtime — all symbolic references are flattened to resolved lookup entries.
`pjvmpack.py` also compacts each class's resolution slice by remapping CP
operands in bytecode to dense local indices without changing instruction
sizes or branch offsets.

Sections (in order): header, class table, method table, constant pool
resolution table, integer constants, string constants, bytecodes,
exception table. All multi-byte values are little-endian.

`pjvmpack.py` emits compact v3 images by default and auto-selects v4 when
8-bit metadata limits are exceeded. Use `--format v3` or `--format v4` to
force a version. The normal `picojvm` host/target build is intentionally
v3-only; `make ./picojvm-large` builds a host VM with `PJVM_ENABLE_V4=1` and
larger capacity tables for large-program experiments. v4-capable builds also
enable the JVM `wide` prefix for local variable indexes above 255. Passing
`--pack-method-table` to `pjvmpack.py` enables the compact v4 method-table
encoding for large method-count images.

See [PICOJVM_JAVAC_SPEC.md](PICOJVM_JAVAC_SPEC.md) for the full format
specification.

## Context

picoJVM is part of the [LLVM-8085](https://github.com/apullin/llvm-8085)
project — a full LLVM compiler backend for the Intel 8085. The larger
project includes C/C++/Rust compilation, an 8085 simulator, a FreeRTOS
port, and IEEE 754 softfloat in hand-written assembly.

## TODO

- **Full internal context / reentrant / thread-safe** — Program metadata
  (`pjvm_prog`, `n_methods`, `main_mi`, section offsets, etc.) is
  currently global. Must move into `PJVMCtx` (or a `PJVMProg` struct
  pointed to by `PJVMCtx`) so multiple threads (e.g. MP/M or FreeRTOS tasks) can each run an
  independent JVM instance concurrently. The execution state (stack,
  locals, frames, heap) is already per-context; only the program
  descriptor remains global.

## License

MIT
