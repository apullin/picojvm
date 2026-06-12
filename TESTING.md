# Testing picoJVM

Suites are layered: host VM correctness, format/pager variants, GC (unit →
host integration → 8085 simulator), 8085 simulator smoke, and the picojc
compiler battery. Test programs are compiled with `javac` + `pjvmpack`
unless noted; harnesses fail loudly (golden outputs in `expected/`, `.pjvm`
magic-byte checks catch silently-failed packs).

## Host VM

```bash
make test                # full host suite (javac + pjvmpack pipeline)
make test-v4             # v4-format paths on ./picojvm-large (wide locals, 16-bit ids)
make test-paged          # paged program space
make test-paged-stress   # pager with 1×128B cache (worst-case thrash)
make test-pjtools        # tar/zip/arc archive tools end-to-end
```

## GC

```bash
make test-gc-suite       # everything below
make test-gc-host-suite  #   policy demo, direct collector unit tests
                         #   (collect/fragment/exact), full host+paged
                         #   suites under GC flags, alloc-heavy pressure,
                         #   graph/temp-root integration
make test-gc-sim-suite   #   8085 simulator GC smoke + alloc-heavy stress
make test-gc-alloc-bitmap  # exact allocation bitmap config
```

The GC host compatibility pass reruns the *entire* host and paged suites
with the collector enabled — every program is also a GC test.

## 8085 simulator

```bash
make test-sim-smoke      # Fib, RomStringTest, NativeOpsTest, StringShimTest
make test-sim-gc-smoke   # the same programs under freelist+GC
make test-sim-smoke TARGET_LTO=1    # any sim target accepts the toolchain flags
```

Sim tests run the real interpreter on `i8085-trace`, dump the output
window, and byte-compare against `expected/*.hex`. The trap record at
0xE080 carries the trap code and a 16-bit operand (for heap-overflow
traps, the failed allocation size).

## Feature configs

```bash
make test-mt-in-image            # PJVM_MT_IN_IMAGE on host (v3, v4, dual)
make test-legacy-string-natives  # the legacy C string tier still works
make size-report                 # canonical core-text size table (8085 -Oz)
```

## picojc (compiler)

```bash
cd picojc
make test-all
```

Runs: 76 positive tests, 36 negative (must-fail) tests, binary-match
against golden images, the self-host fixpoint (gen1 compiles itself to
gen2, byte-identical), and the disk-mode suites (single and multi-file,
including compiling the real `pj.*` package sources). picojc compile
errors print `E<line>:<code>` and exit 0 — the `.pjvm` rules validate the
0x85 magic byte to catch this.

## Conventions

- Shim sources (`java/lang/*.java`) must be passed to `javac` as explicit
  compilation units (sourcepath cannot shadow boot packages); all test
  recipes do this, under `-source 8`.
- Host and target objects are rebuilt automatically when build flags
  change (flag stamps in `build/`). If results ever look impossibly stale
  (this Mac's make compares mtimes at 1-second granularity), `rm -rf
  build` and rerun.
- Before landing changes, the full battery is: host, v4, paged,
  paged-stress, GC suite, sim smoke (LTO on and off), MT-in-image,
  alloc-bitmap, legacy string natives, picojc test-all.
