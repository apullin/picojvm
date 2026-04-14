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
- **~24KB on Intel 8085** (including runtime), ~10KB estimated on TMS9900
- **Program-space paging** — LRU page cache allows programs larger than
  available RAM; only active code pages reside in memory
- **Configurable heap backends** — tiny bump allocator by default, optional
  coalescing free-list backend, and an experimental non-moving mark-sweep GC
- **Execution context struct** — stack, locals, frames, and heap live in
  a `PJVMCtx` struct passed to all API calls
- **File I/O** — native file operations for disk-backed compilation on
  both host and embedded targets
- **~1,300 lines of C** (interpreter core + header), plus ~150 lines per
  platform shim

## picojc — Self-Hosting Java Compiler

picojc is a multi-pass Java compiler written in Java, targeting picoJVM
bytecode. It compiles itself.

- **Self-hosting fixpoint** — javac builds gen0, gen0 compiles picojc to
  gen1, gen1 compiles itself to gen2, gen1 == gen2 (byte-identical)
- **38KB compiled bytecode** (self-hosted binary)
- **124KB source** across 11 Java files (~4,400 lines)
- **64 tests + 9 negative tests**, all passing on host
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
```

## Quick Start

### Build and Test (Host)

```bash
# Build the host interpreter
make

# Run the picoJVM test suite (12 tests, javac + pjvmpack pipeline)
make test

# Build and test picojc (64 tests, self-hosting, disk modes)
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

### Heap Backends and Experimental GC

The default build uses a simple bump allocator for minimum size.

Optional heap backends are selected with `PJVM_HEAP_MODE`:

- `PJVM_HEAP_BUMP` — default, smallest code size
- `PJVM_HEAP_FREELIST` — coalescing free-list allocator

On the `gc-experiment` branch, the free-list backend also supports an
experimental non-moving mark-sweep collector. The current collector uses:

- exact roots from operand stack, locals, and statics
- a non-moving sweep over the free-list heap
- conservative scanning of object/ref-array 4-byte slots

GC trigger policy is controlled by `PJVM_GC_TRIGGERS`, combining any of:

- `PJVM_GC_TRIG_ALLOC_FAIL`
- `PJVM_GC_TRIG_WATERMARK`
- `PJVM_GC_TRIG_RETURN`
- `PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK`

Host examples:

```bash
# Free-list allocator only
make clean test HOST_VM_OPTS=-DPJVM_HEAP_MODE=PJVM_HEAP_FREELIST

# Experimental GC policy demos and direct collector test
make clean gc-policy-test test-gc-collect

# Force GC on the alloc-heavy integration test with a small host heap
make test-gc-alloc-heavy
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
runtime — all symbolic references are flattened to byte-indexed lookups.

Sections (in order): header, class table, method table, constant pool
resolution table, integer constants, string constants, bytecodes,
exception table. All multi-byte values are little-endian.

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
