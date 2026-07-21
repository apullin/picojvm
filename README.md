# picoJVM

A real Java virtual machine for 8-bit microprocessors.

picoJVM runs Java bytecode — with objects, virtual dispatch, exceptions,
strings, enums, and a tracing garbage collector — on an Intel 8085 inside a
64K address space. The full-featured VM links to **~31.5KB of ROM** and
leaves **~26KB of RAM for the Java heap** on a flat 64K machine. It ships
with **picojc**, a self-hosting Java compiler that runs on picoJVM itself,
and **picoJSE**, a Java-side standard library packed as bytecode.

The design thesis is *Java is the system*: the C interpreter stays minimal —
a bytecode engine plus a small tier of primitive natives for physical memory
and I/O — and everything above that line (string algorithms, enums,
exceptions with messages, formatting, archives, tooling) is Java, compiled
to bytecode and run by the VM like any other program.

## Headline numbers

| | |
|---|---|
| Full-featured VM ROM (GC, exceptions, strings, runtime guards; LTO) | **31.5KB** |
| Java heap on a flat 64K 8085, same build | **26.2KB** |
| Minimal interpreter core (no GC), outlined | 24.2KB |
| Interpreter + heap + GC source | ~4,400 lines of C |
| picojc self-hosted compiler binary | ~45KB of bytecode |
| Programs larger than 64K | supported (paged program space) |

## Features

- **Near-Java 7 language support** — classes, interfaces, inheritance,
  virtual dispatch, exceptions, enums, for-each, varargs, packages/imports,
  string switch, multi-dimensional arrays, method overloading
- **Real allocation and GC** — coalescing free-list heap with a non-moving
  mark/sweep collector: exact roots, trusted marking through typed slots,
  per-class reference bitmaps, static-ref bitmaps, temp-root pinning for
  native code, and a configurable trigger policy
- **java.lang in bytecode** — `String`, `Enum`, `Throwable`/`Exception`
  algorithms ship as packed shims over a small native primitive tier; the
  legacy C implementations are compiled out by default
- **Guarded interpreter** — frame/stack/array/index/division guards and
  loud traps for runtime faults and declared-capacity mismatches. Packed
  `.pjvm` images are currently trusted; hostile-image validation is future work
- **Program-space paging** — 32-bit program space with an LRU chunk cache;
  bytecode beyond 64K pages in from disk
- **Boot overlay** — the image loader links inside the heap window and is
  reclaimed as heap after it runs once
- **Pay-once size engineering** — method metadata served directly from the
  ROM image (`PJVM_MT_IN_IMAGE`), an I8085 MachineOutliner in the LLVM
  backend (~30% interpreter text), and full LTO with link-time outlining
- **Portable** — the core is plain C with a thin platform layer; host
  (macOS/Linux), 8085 simulator, and 8085 bare-metal targets are included

## picojc — self-hosting Java compiler

picojc is a multi-pass Java compiler written in Java, targeting picoJVM
bytecode. It compiles itself, on picoJVM.

- **Self-hosting fixpoint** — javac builds gen0, gen0 compiles picojc to
  gen1, gen1 compiles itself to gen2, gen1 == gen2 byte-identical
- **77 positive + 38 negative tests**, plus binary-match and disk-mode
  suites, all passing on the host VM
- **Disk-backed compilation** — reads source and writes images through the
  file natives; single-file and multi-file (`sources.lst`) modes
- **Bootstrap from any modern JDK** — `javac` + `pjvmpack` produce the
  first binary; after that the JDK is optional

Supported subset: `int`/`byte`/`char`/`short`/`boolean` and references (no
`long`/`float`/`double`); classes, interfaces, single inheritance,
constructors, static initializers, `instanceof`, casts, enums (desugared),
overloading, varargs, packages; full integer expression grammar; all
structured statements including `try`/`catch`/`finally` and string switch.
Not supported: generics, lambdas, inner classes, annotations,
`synchronized`, auto-boxing, string `+` concatenation.

## picoJSE — the Java-side platform

If picoJSE exposes a standard Java API it lives under the matching `java.*`
name so stock `javac` bytecode resolves unchanged; the `pj.*` root holds
nonstandard substrate (`pj.io`, `pj.text`, `pj.term`, `pj.util`,
`pj.archive`, and the `pj.Native` bridge). Archive tools (`PJTar`, `PJZip`,
`PJArc`, …) are ordinary picoJSE programs. See [PICOJSE.md](PICOJSE.md).

## Quick start

```bash
make            # host interpreter
make test       # host suite (javac + pjvmpack pipeline)
cd picojc
make test-all   # compiler: positive, negative, binmatch, self-host fixpoint
```

For 8085 simulator and bare-metal builds, toolchain flags (`TARGET_LTO`,
`TARGET_OUTLINE`), and porting to a new platform, see
[BUILDING.md](BUILDING.md).

## Documentation

| | |
|---|---|
| [BUILDING.md](BUILDING.md) | toolchains, build targets, 8085 sim/bare-metal, porting |
| [CONFIGURATION.md](CONFIGURATION.md) | every `PJVM_*` option: caps, heap, GC, formats, native tiers |
| [TESTING.md](TESTING.md) | the test suites and what each one proves |
| [DEPLOYMENT.md](DEPLOYMENT.md) | ROM budgets, size ladder, fitting a 32K/36K ROM part |
| [PICOJVM_JAVAC_SPEC.md](PICOJVM_JAVAC_SPEC.md) | the `.pjvm` image format specification |
| [PICOJSE.md](PICOJSE.md) | picoJSE package design |

## Project structure

```
src/
  pjvm.c              Interpreter core
  pjvm_heap.c         Heap backends (bump, free-list)
  pjvm_gc.c           Mark/sweep collector and trigger policy
  pjvm.h, pjvm_opts.h Public types, API, build options
platform/
  host.c              macOS / Linux host with file I/O
  i8085_sim.c         Intel 8085 simulator target
  i8085_target.c      Intel 8085 bare-metal target
  i8085_helpers.S     Hand-written 8085 helpers (stack ops, copies)
  generic.c           Portable reference platform
ldscripts/            8085 linker scripts (flat 64K + boot overlay)
java/, pj/            picoJSE: java.* facades and pj.* substrate
picojc/               The self-hosting compiler and its suites
pjvmpack/             .class → .pjvm packer (bootstrap tool)
```

## The .pjvm format

picoJVM does not parse class files at runtime. `pjvmpack` pre-resolves the
constant pool at pack time and flattens all symbolic references into dense
resolved tables; the interpreter executes against fixed-width structures it
can read directly out of ROM. Compact v3 images (8-bit metadata) are the
default; v4 (16-bit) is auto-selected for large programs, and the two
loaders are independent build options. See
[PICOJVM_JAVAC_SPEC.md](PICOJVM_JAVAC_SPEC.md).

## Context

picoJVM is part of the [LLVM-8085](https://github.com/apullin/llvm-8085)
project — a full LLVM compiler backend for the Intel 8085. The larger
project includes C/C++/Rust compilation, an 8085 simulator, a FreeRTOS
port, and IEEE 754 softfloat in hand-written assembly. The I8085
MachineOutliner that picoJVM leans on lives in that backend.

## TODO

- **Optional multiple VM instances** — the supported runtime contract is one
  active VM per process or machine. Execution storage lives in `PJVMCtx`, but
  program metadata and GC roots are global; `pjvm_heap_init` initializes the
  singleton rather than switching contexts. A future `PJVMProg` descriptor
  could provide independent instances when a target actually needs them.
- **JDOS** — a resident shell; the term/file natives and archive tools are
  the substrate.

## License

MIT
