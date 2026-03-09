# picojc — A Java Compiler for picoJVM

## Project Codename: picojc

A multi-pass Java compiler targeting the picoJVM bytecode interpreter, designed
to run on the Intel 8085 microprocessor (64KB address space, ~3 MHz).  Written
in Java so that it can ultimately self-host: compile itself on picoJVM on real
8085 hardware.

---

## Table of Contents

1. [Background: The LLVM-8085 Project](#1-background-the-llvm-8085-project)
2. [Background: picoJVM](#2-background-picojvm)
3. [The .pjvm Binary Format](#3-the-pjvm-binary-format)
4. [Supported Java Language Subset](#4-supported-java-language-subset)
5. [Compiler Architecture: Multi-Pass Design](#5-compiler-architecture-multi-pass-design)
6. [Memory Budget and Paging Strategy](#6-memory-budget-and-paging-strategy)
7. [Bytecode Emission Strategy](#7-bytecode-emission-strategy)
8. [Constant Pool Construction](#8-constant-pool-construction)
9. [Type System and Resolution](#9-type-system-and-resolution)
10. [Exception Table Generation](#10-exception-table-generation)
11. [Native Method Stubs](#11-native-method-stubs)
12. [Test Suite Specification](#12-test-suite-specification)
13. [Test Harness and Run Infrastructure](#13-test-harness-and-run-infrastructure)
14. [Simulator Tools and Setup](#14-simulator-tools-and-setup)
15. [Development Workflow](#15-development-workflow)
16. [Self-Hosting Path](#16-self-hosting-path)
17. [Appendix A: picoJVM Opcode Table](#appendix-a-picojvm-opcode-table)
18. [Appendix B: Native Method ABI](#appendix-b-native-method-abi)
19. [Appendix C: Existing picoJVM Test Programs](#appendix-c-existing-picojvm-test-programs)
20. [Appendix D: .pjvm File Format Reference](#appendix-d-pjvm-file-format-reference)

---

## 1. Background: The LLVM-8085 Project

picoJVM is part of a larger project: a full LLVM compiler backend for the Intel
8085 8-bit microprocessor.  The project includes:

- **LLVM backend** (`llvm-project/`): Clang/LLVM fork with an i8085 target.
  Compiles C, C++ (with STL), and Rust to 8085 machine code.
- **Runtime library** (`sysroot/`): Hand-written 8085 assembly for libgcc
  (integer arithmetic, IEEE 754 softfloat), picolibc (libc), CRT0 startup code,
  and linker scripts.
- **i8085-trace** (`i8085-trace/`): Standalone Intel 8085 CPU simulator with
  per-instruction NDJSON trace output. Used for all testing.
- **picoJVM** (`tooling/picojvm/`): A portable Java bytecode interpreter
  targeting 8-bit systems.  ~1200 lines of C, supports 57 JVM opcodes, with
  optional paging for large programs.
- **FreeRTOS port** (`FreeRTOS/`): Working RTOS port with task scheduling,
  queues, and heap management on simulated 8085.
- **Benchmarks** (`tooling/examples/`): 15 C benchmarks and 8 JVM benchmarks,
  all verified on the 8085 simulator.

The 8085 is an 8-bit CPU with:
- Registers: A, B, C, D, E, H, L (8-bit); pairs BC, DE, HL (16-bit)
- 16-bit address bus (64KB address space)
- No hardware multiply or divide
- No indexed addressing modes
- Stack grows downward; SP is 16-bit
- Typical clock: 3.072 MHz

All testing uses the i8085-trace simulator, which provides deterministic
execution with cycle-accurate(-ish) tracing.

---

## 2. Background: picoJVM

picoJVM is the runtime that picojc targets.  Understanding it deeply is essential
for writing a correct compiler.

### 2.1 Architecture

picoJVM is a stack-based bytecode interpreter executing a subset of JVM
bytecode.  It consists of:

- **core.c** (~1200 lines): Portable interpreter loop.  Single giant `switch`
  on 57 opcodes.  No target-specific code.
- **pjvm.h**: Public header with all types, capacity defaults, and API.
- **pjvmpack.py**: Python tool that converts `.class` files to `.pjvm` binary
  format.  Pre-resolves constant pool references, builds vtables, serializes
  class hierarchy.  **This is the tool that picojc replaces.**
- **Platform shims**: Each platform implements 8 callbacks:
  - `heap_alloc(ctx, size)` — allocate heap memory
  - `r8(addr)`, `w8(addr, val)` — byte read/write to object memory
  - `r16(addr)`, `w16(addr, val)` — 16-bit read/write (little-endian)
  - `pjvm_platform_putchar(ch)` — character output
  - `pjvm_platform_peek8(addr)`, `pjvm_platform_poke8(addr, val)` — raw I/O
  - `pjvm_platform_trap(op, pc)` — fatal error handler

### 2.2 Value Representation

All values on the operand stack and in local variables are stored as
`(uint16_t lo, uint16_t hi)` pairs.  This encodes:

- **int** (32-bit signed): `value = lo | (hi << 16)`.  Negative values have
  `hi = 0xFFFF` for sign extension.
- **Reference** (16-bit heap address): stored in `lo`, `hi = 0`.
- **null**: `lo = 0, hi = 0`.

The interpreter uses separate parallel arrays: `stk_lo[]`/`stk_hi[]` for the
operand stack, `loc_lo[]`/`loc_hi[]` for local variables, `sf_lo[]`/`sf_hi[]`
for static fields.

### 2.3 Object Memory Layout

All objects and arrays live in a flat heap, addressed by 16-bit references.

**Object instance:**
```
Offset  Size  Contents
0       2     Class ID (uint16_t)
2       2     Reserved (0)
4       4     Instance field 0 (lo:2 + hi:2)
8       4     Instance field 1
...
4+n*4   4     Instance field n-1
```

**Integer/reference array:**
```
Offset  Size  Contents
0       2     Element count (uint16_t)
2       2     Reserved (0)
4       4     Element 0 (4 bytes each)
8       4     Element 1
...
```

**Byte/char array:**
```
Offset  Size  Contents
0       2     Element count (uint16_t)
2       2     Reserved (0)
4       1     Element 0 (1 byte each)
5       1     Element 1
...
```

**String** (same layout as byte array — UTF-8 encoded):
```
Offset  Size  Contents
0       2     String length in bytes
2       2     Reserved (0)
4       n     String data (UTF-8, no null terminator)
```

### 2.4 Call Frame Model

Each method invocation pushes a `PJVMFrame`:
```c
typedef struct {
    uint32_t pc;     // Return address (bytecode offset)
    uint16_t cb;     // Caller's constant pool base
    uint8_t  mi;     // Caller's method index
    uint8_t  lb;     // Caller's local variable base
    uint8_t  so;     // Caller's stack pointer (for unwinding)
} PJVMFrame;
```

Method invocation:
1. Save current frame (PC, method index, local base, SP, CP base)
2. Copy arguments from operand stack to new local variable slots
3. Set PC to method's `code_offset`, set CP base to method's `cp_base`

Method return:
1. Pop return value (if any)
2. Restore caller's frame
3. Push return value onto caller's stack

### 2.5 Static Initialization

Before `main()`, picoJVM runs `<clinit>` methods for all classes in declaration
order.  The `<clinit>` method index is stored per-class (0xFF = none).

### 2.6 Exception Handling

On `athrow`:
1. Pop exception reference from stack
2. Search current method's exception table for matching handler:
   - Entry: `(start_pc, end_pc, handler_pc, catch_class_id)`
   - Match if throw PC is in `[start_pc, end_pc)` AND exception class is
     assignable to catch class (walking the inheritance chain)
   - `catch_class_id = 0xFF` matches any exception (finally/catch-all)
3. If match: reset stack to frame's saved offset, push exception ref, jump to
   handler
4. If no match: unwind to caller and retry

### 2.7 Virtual and Interface Dispatch

**invokevirtual**: Look up vtable slot from method metadata.  Read receiver
object's class ID, index into that class's vtable to find actual method index.

**invokeinterface**: Similar but uses a virtual method ID (`vmid`) instead of a
vtable slot.  Linear scan of the receiver's vtable for matching vmid.

### 2.8 Capacity Defaults

```c
PJVM_METHOD_CAP    256   // Max methods across all classes
PJVM_CLASS_CAP      64   // Max classes
PJVM_VTABLE_CAP    256   // Max vtable entries (global)
PJVM_STATIC_CAP     64   // Max static fields
PJVM_MAX_STACK     256   // Operand stack depth (per-thread)
PJVM_MAX_LOCALS   1024   // Local variable array size
PJVM_MAX_FRAMES     64   // Call stack depth
```

On 8085 target, these are reduced:
```c
PJVM_METHOD_CAP     64
PJVM_CLASS_CAP      16
PJVM_VTABLE_CAP    128
PJVM_STATIC_CAP     32
PJVM_MAX_STACK      64
PJVM_MAX_LOCALS    128
PJVM_MAX_FRAMES     16
```

### 2.9 Paging (Optional)

picoJVM supports a fixed-page LRU cache for the `.pjvm` image.  The entire
program is stored externally (e.g., SD card, SPI flash); only a configurable
number of page-sized buffers reside in RAM.  All read-only accesses to the
`.pjvm` image go through a `PROG(offset)` macro that transparently faults pages
in.

This is critical for picojc: the compiler's own code can be large (say, 15KB of
bytecode) but only occupies 2-4KB of RAM as page buffers.

### 2.10 Performance Baseline

| Test | Text Size (asm) | Steps | Cycles |
|------|-----------------|-------|--------|
| Fib | 23,921 | 321,183 | 2,112,486 |
| HelloWorld | 23,963 | 106,834 | 690,755 |
| BubbleSort | 24,007 | 713,528 | 4,731,533 |
| Counter | 23,925 | 41,264 | 260,261 |
| StringTest | 24,007 | 38,918 | 246,730 |
| StaticInitTest | 23,891 | 22,257 | 138,798 |
| Shapes | 24,237 | 88,271 | 563,348 |
| ExceptionTest | 24,151 | 51,048 | 319,897 |

Interpreter floor (NullProg): 23,829 bytes.

---

## 3. The .pjvm Binary Format

picojc must emit exactly this binary format.  This is the contract between
compiler and runtime.

### 3.1 Header

**v1 header** (10 bytes):
```
Offset  Size  Field            Description
0       1     magic_hi         0x85
1       1     magic_lo         0x4A (v1)
2       1     n_methods        Total method count (all classes)
3       1     main_mi          Method index of main()
4       1     n_static         Total static field count
5       1     n_integers       Number of integer constants in CP
6       1     n_classes        Number of classes
7       1     n_strings        Number of string constants
8       2     bytecodes_size   Total bytecode section size (16-bit LE)
```

**v2 header** (14 bytes) — used when bytecode exceeds 64KB:
```
Offset  Size  Field            Description
0       1     magic_hi         0x85
1       1     magic_lo         0x4B (v2)
2       1     n_methods        Total method count
3       1     main_mi          Method index of main()
4       1     n_static         Total static field count
5       1     n_integers       Number of integer constants
6       1     n_classes        Number of classes
7       1     n_strings        Number of string constants
8       4     bytecodes_size   Total bytecode size (32-bit LE)
12      1     pager_flags      Bit 0: pin hints; bits 1-3: page_shift
13      1     reserved         Must be 0
```

For picojc, v1 is sufficient unless compiling very large programs.

### 3.2 Class Table

Immediately follows header.  For each of `n_classes` (variable-length entries):

```
Offset  Size        Field            Description
0       1           parent_id        Parent class ID (0xFF = Object/root)
1       1           n_instance_flds  Number of instance fields
2       1           vtable_size      Number of vtable entries
3       1           clinit_mi        <clinit> method index (0xFF = none)
4       vtable_size vtable[]         Array of method indices
```

### 3.3 Method Table

For each of `n_methods` (12 bytes per entry in v1):

```
Offset  Size  Field       Description
0       1     max_locals  Max local variable slots needed
1       1     max_stack   Max operand stack depth needed
2       1     arg_count   Argument count (incl. 'this' for instance methods)
3       1     flags       Bit 0: is_native; bits 1-7: native_id (if native)
4       2     code_offset Bytecode offset (16-bit LE, v1)
6       2     cp_base     This method's CP resolution table base offset
8       1     vtable_slot Virtual method slot (0xFF = non-virtual)
9       1     vmid        Virtual method ID for interface dispatch (0xFF = none)
10      1     exc_count   Number of exception table entries
11      1     exc_off_idx Exception table base index
```

### 3.4 Constant Pool Resolution Table

```
Offset  Size      Field     Description
0       2         cp_size   Size of resolution array (16-bit LE)
2       cp_size   entries[] Pre-resolved values (1 byte each):
                            - Methodref: resolved method index (u8)
                            - Fieldref (static): static field slot
                            - Fieldref (instance): instance field slot
                            - String: 0x80 | string_index
                            - Class: class_id
                            - Unresolved: 0xFF
```

This is the key optimization in .pjvm: the Java constant pool (complex, multi-
level indirection) is flattened into a simple byte-indexed lookup table at pack
time.  The interpreter never parses constant pool structures at runtime.

### 3.5 Integer Constants

For each of `n_integers` (4 bytes each, little-endian int32):
```
[int32_le value0] [int32_le value1] ...
```

### 3.6 String Constants

For each of `n_strings`:
```
[uint16_le length] [length bytes of UTF-8 data]
```

No null terminator.

### 3.7 Bytecode Section

Raw JVM bytecode stream, `bytecodes_size` bytes.  All methods' bytecodes
concatenated.  Each method's `code_offset` in the method table points into this
section.

### 3.8 Exception Table

For each exception handler (7 bytes per entry):
```
Offset  Size  Field        Description
0       2     start_pc     Start of protected region (bytecode offset, LE)
2       2     end_pc       End of protected region (bytecode offset, LE)
4       2     handler_pc   Handler bytecode offset (LE)
6       1     catch_class  Class ID to catch (0xFF = catch-all/finally)
```

These offsets are relative to the start of the bytecode section (i.e., the same
values that appear in the Java `.class` file's exception table).

---

## 4. Supported Java Language Subset

picojc must handle exactly the language features that picoJVM can execute.

### 4.1 Types

| Type | Width | JVM category | Notes |
|------|-------|-------------|-------|
| `int` | 32-bit | 1 | Primary numeric type |
| `byte` | 8-bit (sign-ext to int) | 1 | Stored as int on stack |
| `char` | 16-bit (zero-ext to int) | 1 | Stored as int on stack |
| `short` | 16-bit (sign-ext to int) | 1 | Stored as int on stack |
| `boolean` | 1-bit (stored as int) | 1 | 0 or 1 |
| `void` | - | - | Return type only |
| Reference | 16-bit | 1 | Object/array/String/null |

**NOT supported**: `long`, `float`, `double`.  No 64-bit values.  No floating
point.  All arithmetic is 32-bit integer.

### 4.2 Declarations

- **Classes**: single inheritance, with `extends`
- **Interfaces**: `implements`, multiple allowed
- **Fields**: instance and static, type `int`, `byte`, `char`, `short`,
  `boolean`, or reference
- **Methods**: instance and static, with `public`/`private`/`protected`
- **Constructors**: `<init>` (instance), `<clinit>` (static initializer)
- **Local variables**: any primitive or reference type
- **Parameters**: same as local variables

### 4.3 Expressions

- Arithmetic: `+`, `-`, `*`, `/`, `%`, unary `-`
- Bitwise: `&`, `|`, `^`, `~`, `<<`, `>>`, `>>>`
- Comparison: `==`, `!=`, `<`, `>`, `<=`, `>=`
- Logical: `&&`, `||`, `!`
- Assignment: `=`, `+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`, `<<=`,
  `>>=`, `>>>=`
- Increment: `++`, `--` (pre and post)
- Cast: `(Type)expr`
- `instanceof`
- Ternary: `cond ? a : b`
- Method call: `obj.method(args)`, `ClassName.method(args)`
- Field access: `obj.field`, `ClassName.field`
- Array access: `arr[i]`
- String literals: `"hello"`
- Integer literals: decimal, hex (`0xFF`), binary (`0b101`), char (`'A'`)
- `null`
- `this`
- `new ClassName(args)`
- `new int[size]`, `new Type[size]`, `new int[r][c]`

### 4.4 Statements

- Variable declaration with optional initializer
- Expression statements
- `if` / `else if` / `else`
- `while`, `do-while`
- `for` (C-style)
- Enhanced for (`for (int x : arr)`) — **optional, can desugar**
- `switch` / `case` / `default` (int and String)
- `return`, `return expr`
- `break`, `continue`
- `throw new ExceptionType()`
- `try` / `catch` / `finally`
- Block: `{ ... }`

### 4.5 Class Features

- Single inheritance (`extends`)
- Interface implementation (`implements`)
- Virtual method dispatch (automatic)
- Constructor chaining (`super()` — implicit)
- Static initializer blocks (`static { ... }`)
- Static fields with initializers (`static int x = 42;`)
- `instanceof` and cast (`checkcast`)

### 4.6 NOT Supported

- Generics (no type erasure needed — picoJVM has no verifier)
- Lambdas / anonymous classes / inner classes
- Annotations
- Enums (could be supported as sugar, but not required)
- `synchronized` / `volatile`
- Multi-catch (`catch (A | B e)`)
- Try-with-resources
- Varargs
- Auto-boxing
- String concatenation with `+` (no `StringBuilder`)
- `assert`
- Package declarations / imports (all classes in single compilation unit)

### 4.7 String Handling

Strings are first-class objects in picoJVM with native method support.  String
constants are interned (same literal = same reference).  Available methods:

- `s.length()` — returns int
- `s.charAt(i)` — returns char (as int)
- `s.equals(other)` — returns boolean
- `s.hashCode()` — returns int (FNV-1a: `31 * h + char`)
- `s.toString()` — returns self
- `Native.print(s)` — output to platform

String switch works through hashCode + lookupswitch + equals (standard javac
pattern).

---

## 5. Compiler Architecture: Multi-Pass Design

The fundamental constraint: **minimize peak RAM usage**.  On an 8085 with paging,
the compiler's code is paged from storage, but all data structures must fit in
~38KB of RAM.  A traditional AST-based compiler would need 50-100 bytes per AST
node; a 500-line program with ~2000 nodes would consume 100-200KB.  Impossible.

Instead, picojc uses **multi-pass syntax-directed translation** — each pass
re-reads the source from storage, and no pass holds the entire program's AST in
memory.

### 5.1 Pass Overview

| Pass | Input | Output | Peak RAM |
|------|-------|--------|----------|
| 1. Catalog | Source text | Symbol directory | ~4KB |
| 2. Resolve | Source + symbols | Resolved symbol table, CP indices | ~8KB |
| 3. Emit | Source + resolved symbols | Bytecode + CP + exception table | ~4KB buffer |
| 4. Link | All sections | .pjvm binary | ~2KB buffer |

Total peak RAM: ~12-16KB.  Well within our 38KB budget.

### 5.2 Pass 1: Catalog (Lex + Survey)

**Goal**: Identify all classes, methods, fields, and their source locations.

**Algorithm**:
1. Stream through source file token by token
2. On `class` keyword: record class name, parent (if `extends`), interfaces
3. On field declaration: record name, type, static/instance
4. On method declaration: record name, descriptor, arg count, static/instance,
   byte offset in source file for method body
5. On `}` closing method: record end offset

**Output**: A compact **symbol directory** — arrays of:
```java
class CatalogEntry {
    // Class entries
    int classId;
    int parentClassId;        // -1 = Object
    String className;
    int sourceOffset;         // byte offset in source

    // Field entries
    int fieldSlot;
    boolean isStatic;
    String fieldName;
    String fieldType;

    // Method entries
    int methodIndex;
    String methodName;
    String descriptor;        // e.g. "(II)I"
    int argCount;
    int bodyStartOffset;      // source byte offset of '{'
    int bodyEndOffset;        // source byte offset of '}'
    boolean isStatic;
    boolean isConstructor;
    boolean isNative;
}
```

**RAM cost**: ~20-30 bytes per symbol.  A program with 100 symbols = ~3KB.

**What this pass does NOT do**: parse method bodies, resolve references, or
generate any code.

### 5.3 Pass 2: Resolve (Type Check + Layout)

**Goal**: Assign concrete indices and offsets to all symbols.

**Algorithm**:
1. Topological sort classes (parents before children)
2. Assign class IDs (0, 1, 2, ...)
3. Compute instance field layouts:
   - Parent fields first, then child fields
   - Each field gets a slot index (offset in object = 4 + slot * 4)
4. Compute static field slots (global array, sequential)
5. Build vtables:
   - Start with parent's vtable
   - Override slots for methods with matching name+descriptor
   - Append new virtual methods
6. Assign method indices (global flat array)
7. Build CP resolution index:
   - For each symbolic reference (method call, field access, class reference),
     assign a CP index resolving to the concrete method/field/class
8. Assign interface method vmids for invokeinterface
9. Determine max_locals and max_stack per method (requires light parsing of
   method body to count local declarations and estimate stack depth)

**Output**: Fully resolved symbol table with:
- Class IDs, field slots, method indices, vtable contents
- CP resolution entries (one byte each)
- Per-method: code_offset (to be filled in Pass 3), cp_base, max_locals,
  max_stack, exc_count

### 5.4 Pass 3: Emit (Code Generation)

**Goal**: Generate bytecode for every method body.

**Algorithm**:
For each method (re-reading its source span):
1. Initialize a per-method bytecode buffer (~512 bytes)
2. Initialize backpatch list (for forward branches)
3. Recursive descent parse of method body:
   - Each expression emits bytecodes directly as it's parsed
   - Local variable declarations update a name→slot map
   - `if`/`while`/`for` emit branch instructions with forward patch targets
   - Method calls emit invokevirtual/invokestatic/invokespecial + CP index
   - Field access emits getfield/putfield/getstatic/putstatic + CP index
   - `try`/`catch`/`finally` record exception table entries
4. Backpatch all forward branch offsets
5. Record method's `code_offset` = current position in bytecode stream
6. Append method bytecodes to global bytecode accumulator

This pass also collects:
- Integer constants for the CP (LDC/LDC_W targets)
- String constants for the CP

**Per-method RAM**: ~512 bytes bytecode buffer + ~64 bytes backpatch list +
~128 bytes local variable map.  Total: ~1KB per method (only one active at a
time).

### 5.5 Pass 4: Link (Pack)

**Goal**: Assemble the final .pjvm binary.

**Algorithm**:
1. Write header (10 or 14 bytes)
2. Write class table (variable-length, includes vtables)
3. Write method table (12 bytes per method)
4. Write CP resolution table
5. Write integer constants
6. Write string constants
7. Write bytecode section
8. Write exception table

This is a straightforward serialization.  All data is already prepared by
Pass 2 and Pass 3.

### 5.6 Error Handling

In a memory-constrained environment, error reporting must be minimal.  Strategy:

- **Lexer errors**: Print line number + "unexpected character" and halt
- **Parser errors**: Print line number + "expected X, got Y" and halt
- **Resolution errors**: Print "undefined: Name" and halt
- **Type errors**: Minimal.  picoJVM has no bytecode verifier, so many type
  errors become runtime failures.  However, picojc should catch obvious errors:
  - Undefined variable/method/class
  - Wrong argument count
  - Missing return statement (detectable during emit)

No fancy error recovery.  First error = compilation stops.  This is acceptable
for a self-hosting bootstrap compiler.

---

## 6. Memory Budget and Paging Strategy

### 6.1 Memory Map (On 8085 Target)

```
Address Range    Size    Contents
0x0000-0x5FFF   24KB    picoJVM interpreter (ROM, via linker)
0x6000-0x67FF    2KB    Page buffer pool (2 × 1KB pages)
0x6800-0xF7FF   ~37KB   Heap (compiler data structures)
0xF800-0xFDFF    1.5KB  Stack
0xFE00-0xFFFF    512B   I/O / reserved
```

### 6.2 What Lives in RAM

| Structure | Size Estimate | Notes |
|-----------|--------------|-------|
| Symbol directory (Pass 1) | ~3KB | 100 symbols × 30 bytes |
| Resolved symbol table (Pass 2) | ~6KB | Adds vtable, CP data |
| Method bytecode buffer | ~512B | One method at a time |
| Backpatch list | ~128B | Max ~64 forward branches |
| Local variable map | ~256B | Max ~64 locals |
| String constant table | ~1KB | Pointers + lengths |
| Integer constant table | ~256B | Max 64 constants |
| CP resolution table | ~512B | Max 512 entries |
| Exception table entries | ~256B | Accumulated across methods |
| Output buffer | ~1KB | For writing .pjvm to storage |
| Lexer state | ~128B | Token buffer, line number |
| **Total peak** | **~13KB** | Well within 37KB heap |

### 6.3 What Gets Paged

The compiler's own `.pjvm` bytecodes are paged from storage.  Only the active
page buffer(s) reside in RAM.  With 2 × 1KB pages, even a 20KB compiler image
works fine — just with more page faults.

The **source file** being compiled is also read from storage, one token at a
time.  The lexer maintains a small read-ahead buffer (~128 bytes), not the
entire source.

### 6.4 Output Strategy

The `.pjvm` output is written to storage sequentially.  Since Pass 4 (Link) has
a defined section order, the output is written section-by-section.  The only
wrinkle: method table entries need `code_offset` values from Pass 3, so the
method table must be held in RAM until Pass 4 writes it.  At 12 bytes × 64
methods = 768 bytes, this is fine.

---

## 7. Bytecode Emission Strategy

### 7.1 Stack Machine = Natural Fit

JVM bytecode is a stack machine.  A recursive-descent parser naturally evaluates
expressions in the correct order for a stack machine:

```java
// Source: a + b * c
// Parse tree (recursive descent):
//   parseExpr → parseTerm → parseFactor (a)
//                          → parseFactor (b)
//                          → parseFactor (c)
//                          → IMUL
//              → IADD

// Emitted bytecodes:
//   ILOAD a
//   ILOAD b
//   ILOAD c
//   IMUL
//   IADD
```

The parser calls `emitByte(opcode)` as it processes each node.  No intermediate
representation needed.

### 7.2 Expression Compilation

```
parseExpression():
    parseTernary()

parseTernary():
    left = parseOr()
    if peek() == '?':
        emit: IFEQ label_false
        parseExpression()    // true branch
        emit: GOTO label_end
      label_false:
        parseExpression()    // false branch
      label_end:

parseOr():
    left = parseAnd()
    while peek() == '||':
        emit: DUP + IFNE short_circuit
        emit: POP
        parseAnd()
      short_circuit:

// ... (standard precedence climbing) ...

parsePrimary():
    if integer literal:
        emit: ICONST_n / BIPUSH / SIPUSH / LDC
    if string literal:
        add to string constant table
        emit: LDC with string CP index
    if identifier:
        resolve to local/field/class
        emit: ILOAD_n / ALOAD_n / GETSTATIC / GETFIELD
    if 'new':
        resolve class
        emit: NEW classid + DUP + INVOKESPECIAL <init>
    if 'new' array:
        emit count expression
        emit: NEWARRAY / ANEWARRAY
    if method call:
        emit arguments
        emit: INVOKESTATIC / INVOKEVIRTUAL / INVOKEINTERFACE
```

### 7.3 Statement Compilation

```
compileStatement():
    if 'if':
        compileExpression(condition)
        emit: IFEQ label_else
        compileBlock(then)
        if 'else':
            emit: GOTO label_end
          label_else:
            compileBlock(else)
          label_end:
        else:
          label_else:

    if 'while':
      label_top:
        compileExpression(condition)
        emit: IFEQ label_end
        compileBlock(body)
        emit: GOTO label_top
      label_end:

    if 'for':
        compileStatement(init)
      label_top:
        compileExpression(condition)
        emit: IFEQ label_end
        compileBlock(body)
        compileStatement(update)
        emit: GOTO label_top
      label_end:

    if 'switch':
        compileExpression(value)
        // For int switch: emit TABLESWITCH or LOOKUPSWITCH
        // For String switch: emit hashCode + LOOKUPSWITCH + equals chain

    if 'return':
        compileExpression(value)  // if non-void
        emit: IRETURN / ARETURN / RETURN

    if 'throw':
        compileExpression(expr)
        emit: ATHROW

    if 'try':
        record exception table start
        compileBlock(try_body)
        record exception table end
        emit: GOTO label_after
        for each catch clause:
          label_handler:
            record handler in exception table
            ASTORE exception_local
            compileBlock(catch_body)
            emit: GOTO label_after
        if finally:
          label_finally:
            record catch-all handler in exception table
            ASTORE exception_local
            compileBlock(finally_body)
            ALOAD exception_local
            ATHROW  // re-throw
      label_after:
        if finally:
            compileBlock(finally_body)  // also runs on normal path
```

### 7.4 Branch Backpatching

Forward branches (if-else, while, for) use a backpatch list:

```java
class BackpatchEntry {
    int bytecodeOffset;   // where the branch operand is
    int targetLabel;      // which label to resolve to
}
```

When emitting a forward branch:
1. Emit the branch opcode
2. Emit 2 placeholder bytes (0x00, 0x00)
3. Record `(currentOffset - 2, label)` in backpatch list

When the label is defined:
1. Record `label → currentOffset`
2. Scan backpatch list for matching label entries
3. Compute offset: `target - branchInstruction`
4. Patch the 2-byte operand

### 7.5 Switch Compilation

**Integer switch**: Analyze case values.
- If dense (range / count < 3): emit `TABLESWITCH`
- If sparse: emit `LOOKUPSWITCH`

**String switch** (following standard javac pattern):
1. Emit `s.hashCode()` (INVOKEVIRTUAL)
2. `LOOKUPSWITCH` on hash values (mapping hash → candidate branch)
3. At each candidate: emit `s.equals("literal")` (INVOKEVIRTUAL)
4. If equals: GOTO case body
5. If not: fall through to next candidate or default

This is exactly what standard javac produces, and picoJVM's StringSwitchTest
already validates this pattern.

---

## 8. Constant Pool Construction

The .pjvm CP resolution table is a pre-resolved flat array.  picojc must build
it during Pass 2-3.

### 8.1 CP Entry Types

Each method has a contiguous range of CP entries starting at its `cp_base`.
When the method's bytecode uses a 2-byte CP index (e.g., in INVOKEVIRTUAL),
that index maps into this range.

| Entry Type | Resolved Value |
|-----------|---------------|
| Methodref | Method index (u8) |
| Static fieldref | Static field slot (u8) |
| Instance fieldref | Instance field slot (u8) |
| String constant | `0x80 \| string_index` (u8) |
| Class ref | Class ID (u8) |
| Integer constant | Integer constant index (u8, for LDC) |
| Unresolved | 0xFF |

### 8.2 Construction Strategy

During Pass 2 (Resolve):
1. For each method, scan its bytecode references (from Pass 1 offsets)
2. Assign sequential CP indices per method
3. Resolve each reference:
   - `ClassName.methodName(desc)` → look up in method table → method index
   - `ClassName.fieldName` (static) → look up in static field table → slot
   - `obj.fieldName` (instance) → look up in class's field layout → slot
   - `"string"` → add to string constant table → `0x80 | index`
   - `ClassName` (for new/instanceof/checkcast) → class ID

### 8.3 Deduplication

String constants and integer constants should be deduplicated:
- Same string literal → same string index
- Same integer value → same integer index

Use simple hash tables (hash → index) during construction.  These are small
(typically <100 entries) and transient.

---

## 9. Type System and Resolution

### 9.1 Type Representation

Minimal type system — just enough for code generation:

```java
class Type {
    enum Kind { INT, BYTE, CHAR, SHORT, BOOLEAN, VOID, CLASS, ARRAY }
    Kind kind;
    String className;   // for CLASS
    Type elementType;   // for ARRAY
}
```

### 9.2 Name Resolution

Three scopes, searched in order:
1. **Local variables** (method scope): name → local slot
2. **Instance fields** (this.field): name → field slot
3. **Static fields** (ClassName.field): name → static slot
4. **Methods**: (className, methodName, descriptor) → method index

For unqualified field/method access, assume `this` if instance method, error if
static method.

### 9.3 Descriptor Strings

picoJVM uses standard JVM method descriptors:
- `()V` — no args, void return
- `(I)I` — int arg, int return
- `(II)I` — two int args, int return
- `(Ljava/lang/String;)V` — String arg, void return

picojc must generate these for method resolution and CP construction.  The
descriptor determines argument count and return type.

---

## 10. Exception Table Generation

### 10.1 Exception Hierarchy

picoJVM requires that exception classes exist in the class table.  pjvmpack.py
auto-synthesizes missing classes in the hierarchy:

```
Throwable (class 0, always synthesized)
  Exception (class 1)
    RuntimeException (class 2)
      [user-defined exceptions extending RuntimeException]
  [user-defined exceptions extending Exception]
```

picojc must do the same: if the user declares `class MyException extends
RuntimeException`, ensure Throwable, Exception, and RuntimeException exist in
the class table (even with no methods or fields).

### 10.2 Table Entry Generation

For each `try-catch-finally` block:
1. Record start PC at the beginning of the try block
2. Record end PC at the end of the try block
3. For each `catch (ExType e)`:
   - Record handler PC
   - Record catch class ID (from resolved class table)
4. For `finally`:
   - Record handler PC with catch class 0xFF (catch-all)
   - Finally handler must re-throw after executing

### 10.3 Nested Exception Handling

Exception table entries are searched in order.  For nested try-catch, inner
handlers must appear before outer handlers in the table.  The emission order
naturally produces this: inner try blocks emit their handlers first.

---

## 11. Native Method Stubs

picoJVM has a fixed set of native methods.  picojc must recognize the `Native`
class and emit the correct native stubs.

### 11.1 Native Method Table

| Method | Native ID | Signature | Description |
|--------|-----------|-----------|-------------|
| `Native.putchar` | 0 | `(I)V` | Output byte |
| `Native.in` | 1 | `(I)I` | Input from port |
| `Native.out` | 2 | `(II)V` | Output to port |
| `Native.peek` | 3 | `(I)I` | Read memory byte |
| `Native.poke` | 4 | `(II)V` | Write memory byte |
| `Native.halt` | 5 | `()V` | Stop interpreter |
| `Object.<init>` | 6 | `()V` | No-op constructor |
| `String.length` | 7 | `()I` | String length |
| `String.charAt` | 8 | `(I)C` | Char at index |
| `String.equals` | 9 | `(Ljava/lang/Object;)Z` | String equality |
| `String.toString` | 10 | `()Ljava/lang/String;` | Identity |
| `Native.print` | 11 | `(Ljava/lang/String;)V` | Print string |
| `String.hashCode` | 12 | `()I` | Hash code |

### 11.2 Encoding

In the method table, native methods have:
- `flags` bit 0 = 1 (is_native)
- `flags` bits 1-7 = native_id
- `code_offset` = 0 (unused)
- `max_locals` = arg_count (for frame setup)
- `max_stack` = max(arg_count, 1)

### 11.3 Native.java as Built-in

picojc should recognize `Native` as a built-in class — not parsed from source.
When the parser sees `Native.putchar(x)`, it resolves to native method index
with the appropriate native ID.

Similarly, `java.lang.String` methods (`length`, `charAt`, `equals`, `hashCode`,
`toString`) are native.  String is a special class that picojc treats as
built-in.

---

## 12. Test Suite Specification

### 12.1 Test Strategy

Tests operate at three levels:
1. **Unit tests**: Verify individual compiler phases (lexer, parser, resolver)
2. **Integration tests**: Compile `.java` → `.pjvm`, run on host picoJVM,
   compare output
3. **Simulator tests**: Compile `.java` → `.pjvm`, run on 8085 simulator via
   picoJVM, compare output

The primary test mechanism is **output comparison**: each test program produces
a known byte sequence via `Native.putchar()`.  The test harness captures this
output and compares against expected values.

### 12.2 Test Program Catalog

Tests are organized by feature complexity.  Each test specifies:
- Source `.java` file(s)
- Expected output bytes (decimal or hex)
- Which language features are exercised

#### Tier 1: Core Language (must pass for basic functionality)

**T01_Empty.java** — Empty main
```java
public class T01_Empty {
    public static void main(String[] args) {
        Native.halt();
    }
}
```
Expected output: (none)
Tests: class declaration, static method, Native.halt

**T02_Literal.java** — Integer literals and putchar
```java
public class T02_Literal {
    public static void main(String[] args) {
        Native.putchar(42);
        Native.putchar(0);
        Native.putchar(255);
        Native.halt();
    }
}
```
Expected output: `[42, 0, 255]`
Tests: BIPUSH, SIPUSH, ICONST, invokestatic

**T03_Arithmetic.java** — Basic arithmetic
```java
public class T03_Arithmetic {
    public static void main(String[] args) {
        Native.putchar(3 + 4);       // 7
        Native.putchar(10 - 3);      // 7
        Native.putchar(6 * 7);       // 42
        Native.putchar(100 / 10);    // 10
        Native.putchar(17 % 5);      // 2
        Native.putchar(-(-5));       // 5
        Native.halt();
    }
}
```
Expected output: `[7, 7, 42, 10, 2, 5]`
Tests: IADD, ISUB, IMUL, IDIV, IREM, INEG

**T04_LocalVars.java** — Local variable load/store
```java
public class T04_LocalVars {
    public static void main(String[] args) {
        int a = 10;
        int b = 20;
        int c = a + b;
        Native.putchar(c);           // 30
        a = c - b;
        Native.putchar(a);           // 10
        Native.halt();
    }
}
```
Expected output: `[30, 10]`
Tests: ISTORE, ILOAD, local variable indexing

**T05_IfElse.java** — Conditional branching
```java
public class T05_IfElse {
    public static void main(String[] args) {
        int x = 5;
        if (x > 3) {
            Native.putchar(1);       // 1
        } else {
            Native.putchar(0);
        }
        if (x < 3) {
            Native.putchar(0);
        } else {
            Native.putchar(2);       // 2
        }
        if (x == 5) {
            Native.putchar(3);       // 3
        }
        Native.halt();
    }
}
```
Expected output: `[1, 2, 3]`
Tests: IF_ICMPGT, IF_ICMPLT, IF_ICMPEQ, GOTO

**T06_WhileLoop.java** — While loop
```java
public class T06_WhileLoop {
    public static void main(String[] args) {
        int i = 0;
        int sum = 0;
        while (i < 5) {
            sum = sum + i;
            i++;
        }
        Native.putchar(sum);         // 10 (0+1+2+3+4)
        Native.putchar(i);           // 5
        Native.halt();
    }
}
```
Expected output: `[10, 5]`
Tests: GOTO, IFEQ/IFNE, IINC, loop structure

**T07_ForLoop.java** — For loop
```java
public class T07_ForLoop {
    public static void main(String[] args) {
        int product = 1;
        for (int i = 1; i <= 5; i++) {
            product = product * i;
        }
        Native.putchar(product);     // 120 (5!)
        Native.halt();
    }
}
```
Expected output: `[120]`
Tests: for loop compilation, IMUL

**T08_MethodCall.java** — Static method calls
```java
public class T08_MethodCall {
    static int add(int a, int b) {
        return a + b;
    }

    static int factorial(int n) {
        if (n <= 1) return 1;
        return n * factorial(n - 1);
    }

    public static void main(String[] args) {
        Native.putchar(add(3, 4));        // 7
        Native.putchar(factorial(5));     // 120
        Native.halt();
    }
}
```
Expected output: `[7, 120]`
Tests: INVOKESTATIC, IRETURN, recursion, argument passing

**T09_IntArray.java** — Integer arrays
```java
public class T09_IntArray {
    public static void main(String[] args) {
        int[] arr = new int[5];
        for (int i = 0; i < 5; i++) {
            arr[i] = (i + 1) * 10;
        }
        Native.putchar(arr[0]);      // 10
        Native.putchar(arr[2]);      // 30
        Native.putchar(arr[4]);      // 50
        Native.putchar(arr.length);  // 5
        Native.halt();
    }
}
```
Expected output: `[10, 30, 50, 5]`
Tests: NEWARRAY, IASTORE, IALOAD, ARRAYLENGTH

**T10_ByteArray.java** — Byte arrays
```java
public class T10_ByteArray {
    public static void main(String[] args) {
        byte[] buf = new byte[4];
        buf[0] = 65;  // 'A'
        buf[1] = 66;  // 'B'
        buf[2] = 67;  // 'C'
        buf[3] = 68;  // 'D'
        for (int i = 0; i < buf.length; i++) {
            Native.putchar(buf[i]);
        }
        Native.halt();
    }
}
```
Expected output: `[65, 66, 67, 68]` = `"ABCD"`
Tests: NEWARRAY (byte), BASTORE, BALOAD

**T11_Bitwise.java** — Bitwise operations
```java
public class T11_Bitwise {
    public static void main(String[] args) {
        Native.putchar(0xFF & 0x0F);      // 15
        Native.putchar(0x0F | 0xF0);      // 255
        Native.putchar(0xFF ^ 0x0F);      // 240
        Native.putchar(1 << 4);           // 16
        Native.putchar(128 >> 2);         // 32
        Native.putchar(128 >>> 2);        // 32
        Native.halt();
    }
}
```
Expected output: `[15, 255, 240, 16, 32, 32]`
Tests: IAND, IOR, IXOR, ISHL, ISHR, IUSHR

**T12_StringLiteral.java** — String constants
```java
public class T12_StringLiteral {
    public static void main(String[] args) {
        String s = "Hello";
        Native.print(s);              // "Hello"
        Native.putchar(s.length());   // 5
        Native.putchar(s.charAt(1));  // 'e' = 101
        Native.halt();
    }
}
```
Expected output: `[72, 101, 108, 108, 111, 5, 101]`
Tests: LDC (string), Native.print, String.length, String.charAt

#### Tier 2: Object-Oriented Features

**T13_SimpleClass.java** — Object creation and fields
```java
class Point {
    int x, y;
}

public class T13_SimpleClass {
    public static void main(String[] args) {
        Point p = new Point();
        p.x = 10;
        p.y = 20;
        Native.putchar(p.x);         // 10
        Native.putchar(p.y);         // 20
        Native.putchar(p.x + p.y);   // 30
        Native.halt();
    }
}
```
Expected output: `[10, 20, 30]`
Tests: NEW, PUTFIELD, GETFIELD

**T14_Constructor.java** — Constructors with args
```java
class Box {
    int w, h;

    Box(int w, int h) {
        this.w = w;
        this.h = h;
    }

    int area() {
        return w * h;
    }
}

public class T14_Constructor {
    public static void main(String[] args) {
        Box b = new Box(3, 7);
        Native.putchar(b.area());     // 21
        Native.halt();
    }
}
```
Expected output: `[21]`
Tests: INVOKESPECIAL with args, this reference, instance method call

**T15_Inheritance.java** — Inheritance and override
```java
class Animal {
    int sound() { return 0; }
}

class Dog extends Animal {
    int sound() { return 68; }  // 'D'
}

class Cat extends Animal {
    int sound() { return 67; }  // 'C'
}

public class T15_Inheritance {
    public static void main(String[] args) {
        Animal a = new Dog();
        Native.putchar(a.sound());    // 68 = 'D'
        a = new Cat();
        Native.putchar(a.sound());    // 67 = 'C'
        Native.halt();
    }
}
```
Expected output: `[68, 67]`
Tests: INVOKEVIRTUAL, vtable dispatch, polymorphism

**T16_StaticFields.java** — Static fields and initializers
```java
public class T16_StaticFields {
    static int count = 0;
    static int base;
    static { base = 100; }

    static void inc() { count++; }

    public static void main(String[] args) {
        inc(); inc(); inc();
        Native.putchar(count);        // 3
        Native.putchar(base);         // 100
        Native.halt();
    }
}
```
Expected output: `[3, 100]`
Tests: GETSTATIC, PUTSTATIC, <clinit>

**T17_InstanceOf.java** — instanceof and checkcast
```java
class Base {}
class Derived extends Base {}

public class T17_InstanceOf {
    public static void main(String[] args) {
        Base b = new Derived();
        Native.putchar(b instanceof Base ? 1 : 0);      // 1
        Native.putchar(b instanceof Derived ? 1 : 0);    // 1
        Derived d = (Derived) b;  // checkcast, should not trap
        Native.putchar(42);           // 42 (survived)
        Native.halt();
    }
}
```
Expected output: `[1, 1, 42]`
Tests: INSTANCEOF, CHECKCAST, class hierarchy traversal

**T18_NullRef.java** — Null references
```java
public class T18_NullRef {
    public static void main(String[] args) {
        String s = null;
        if (s == null) {
            Native.putchar(1);        // 1
        }
        Object o = "hello";
        if (o != null) {
            Native.putchar(2);        // 2
        }
        Native.halt();
    }
}
```
Expected output: `[1, 2]`
Tests: ACONST_NULL, IFNULL, IFNONNULL

**T19_ObjectArray.java** — Reference arrays
```java
class Item {
    int value;
    Item(int v) { this.value = v; }
}

public class T19_ObjectArray {
    public static void main(String[] args) {
        Item[] items = new Item[3];
        items[0] = new Item(10);
        items[1] = new Item(20);
        items[2] = new Item(30);
        int sum = 0;
        for (int i = 0; i < items.length; i++) {
            sum += items[i].value;
        }
        Native.putchar(sum);          // 60
        Native.halt();
    }
}
```
Expected output: `[60]`
Tests: ANEWARRAY, AASTORE, AALOAD, object fields through array refs

#### Tier 3: Advanced Features

**T20_Interface.java** — Interface dispatch
```java
interface Sizeable {
    int size();
}

class SmallThing implements Sizeable {
    public int size() { return 1; }
}

class BigThing implements Sizeable {
    public int size() { return 100; }
}

public class T20_Interface {
    static void printSize(Sizeable s) {
        Native.putchar(s.size());
    }

    public static void main(String[] args) {
        printSize(new SmallThing());   // 1
        printSize(new BigThing());     // 100
        Native.halt();
    }
}
```
Expected output: `[1, 100]`
Tests: INVOKEINTERFACE, interface vtable lookup

**T21_Exception.java** — Exception handling
```java
class TestException extends RuntimeException {}

public class T21_Exception {
    static void thrower() {
        throw new RuntimeException();
    }

    public static void main(String[] args) {
        // Basic catch
        try {
            throw new RuntimeException();
        } catch (RuntimeException e) {
            Native.putchar(1);         // 1
        }

        // Stack unwinding
        try {
            thrower();
        } catch (Exception e) {
            Native.putchar(2);         // 2
        }

        // Wrong type falls through
        try {
            try {
                throw new RuntimeException();
            } catch (TestException e) {
                Native.putchar(99);    // should NOT reach
            }
        } catch (Exception e) {
            Native.putchar(3);         // 3
        }

        // Finally
        try {
            Native.putchar(4);         // 4
        } finally {
            Native.putchar(5);         // 5
        }

        Native.halt();
    }
}
```
Expected output: `[1, 2, 3, 4, 5]`
Tests: ATHROW, exception table, class hierarchy matching, finally (catch-all)

**T22_Switch.java** — Switch statements
```java
public class T22_Switch {
    static int denseSwitch(int x) {
        switch (x) {
            case 0: return 10;
            case 1: return 20;
            case 2: return 30;
            default: return 0;
        }
    }

    static int sparseSwitch(int x) {
        switch (x) {
            case 100: return 1;
            case 500: return 2;
            case 999: return 3;
            default: return 0;
        }
    }

    public static void main(String[] args) {
        Native.putchar(denseSwitch(0));     // 10
        Native.putchar(denseSwitch(2));     // 30
        Native.putchar(denseSwitch(99));    // 0
        Native.putchar(sparseSwitch(500));  // 2
        Native.putchar(sparseSwitch(0));    // 0
        Native.halt();
    }
}
```
Expected output: `[10, 30, 0, 2, 0]`
Tests: TABLESWITCH, LOOKUPSWITCH

**T23_StringSwitch.java** — String switch (hashCode + equals pattern)
```java
public class T23_StringSwitch {
    static int classify(String s) {
        switch (s) {
            case "red":   return 1;
            case "green": return 2;
            case "blue":  return 3;
            default:      return 0;
        }
    }

    public static void main(String[] args) {
        Native.putchar(classify("red"));     // 1
        Native.putchar(classify("green"));   // 2
        Native.putchar(classify("blue"));    // 3
        Native.putchar(classify("other"));   // 0
        Native.halt();
    }
}
```
Expected output: `[1, 2, 3, 0]`
Tests: String hashCode, lookupswitch, String equals, complex control flow

**T24_StringEquals.java** — String comparisons
```java
public class T24_StringEquals {
    public static void main(String[] args) {
        String a = "hello";
        String b = "hello";
        String c = "world";

        Native.putchar(a.equals(b) ? 1 : 0);   // 1 (same content)
        Native.putchar(a.equals(c) ? 1 : 0);   // 0 (different)
        Native.putchar(a == b ? 1 : 0);         // 1 (interned)
        Native.putchar(a.hashCode() == b.hashCode() ? 1 : 0); // 1
        Native.halt();
    }
}
```
Expected output: `[1, 0, 1, 1]`
Tests: String.equals, String.hashCode, reference equality, string interning

**T25_MultiArray.java** — Multi-dimensional arrays
```java
public class T25_MultiArray {
    public static void main(String[] args) {
        int[][] grid = new int[3][4];
        grid[0][0] = 1;
        grid[1][2] = 5;
        grid[2][3] = 9;
        Native.putchar(grid[0][0]);    // 1
        Native.putchar(grid[1][2]);    // 5
        Native.putchar(grid[2][3]);    // 9
        Native.putchar(grid.length);   // 3
        Native.halt();
    }
}
```
Expected output: `[1, 5, 9, 3]`
Tests: MULTIANEWARRAY, nested array access

**T26_DoWhile.java** — Do-while loop
```java
public class T26_DoWhile {
    public static void main(String[] args) {
        int i = 0;
        do {
            i++;
        } while (i < 5);
        Native.putchar(i);            // 5
        Native.halt();
    }
}
```
Expected output: `[5]`
Tests: do-while compilation

**T27_BreakContinue.java** — Break and continue
```java
public class T27_BreakContinue {
    public static void main(String[] args) {
        // Break
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            if (i == 5) break;
            sum += i;
        }
        Native.putchar(sum);          // 10 (0+1+2+3+4)

        // Continue
        sum = 0;
        for (int i = 0; i < 6; i++) {
            if (i == 3) continue;
            sum += i;
        }
        Native.putchar(sum);          // 12 (0+1+2+4+5)

        Native.halt();
    }
}
```
Expected output: `[10, 12]`
Tests: break/continue in for loops, GOTO targets

**T28_Ternary.java** — Ternary expression
```java
public class T28_Ternary {
    public static void main(String[] args) {
        int x = 5;
        Native.putchar(x > 3 ? 1 : 0);    // 1
        Native.putchar(x < 3 ? 1 : 0);    // 0
        int y = x > 0 ? x * 2 : 0;
        Native.putchar(y);                  // 10
        Native.halt();
    }
}
```
Expected output: `[1, 0, 10]`
Tests: ternary expression compilation, nested expressions

**T29_StackOps.java** — Stack manipulation (DUP, SWAP, POP)
```java
public class T29_StackOps {
    static int doubleIt(int x) {
        // This exercises DUP (x used twice)
        return x + x;
    }

    public static void main(String[] args) {
        Native.putchar(doubleIt(21));      // 42
        // Post-increment exercises DUP (value before increment)
        int a = 5;
        int b = a++;
        Native.putchar(b);                 // 5
        Native.putchar(a);                 // 6
        Native.halt();
    }
}
```
Expected output: `[42, 5, 6]`
Tests: DUP, post-increment codegen

**T30_TypeConversion.java** — Type narrowing
```java
public class T30_TypeConversion {
    public static void main(String[] args) {
        int x = 300;
        byte b = (byte) x;
        Native.putchar(b & 0xFF);         // 44 (300 & 0xFF)
        char c = (char) 65;
        Native.putchar(c);                 // 65 = 'A'
        short s = (short) 32767;
        Native.putchar(s & 0xFF);         // 255
        Native.halt();
    }
}
```
Expected output: `[44, 65, 255]`
Tests: I2B, I2C, I2S

#### Tier 4: Compatibility (match existing picoJVM test suite)

**T31_Fib.java** — Fibonacci (identical to existing picoJVM test)
```java
// Same as tests/Fib.java — validates compiler produces equivalent bytecodes
```
Expected output: `[0, 1, 1, 2, 3, 5, 8, 13, 21, 34]`

**T32_BubbleSort.java** — Bubble sort (identical to existing picoJVM test)
Expected output: `[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]`

**T33_Shapes.java** — Inheritance test (identical to existing picoJVM test)
Expected output: `[63, 0, 83, 25, 82, 21]` (`'?', 0, 'S', 25, 'R', 21`)

**T34_ExceptionTest.java** — Exception test (identical to existing picoJVM test)
Expected output: `[1, 2, 3, 10, 4]`

**T35_InterfaceTest.java** — Interface test (identical to existing picoJVM test)
Expected output: `[27, 20, 67, 66, 27, 20]`

**T36_StringSwitchTest.java** — String switch (identical to existing picoJVM test)
Expected output: `[1, 2, 3, 0]`

### 12.3 Comparison Test Strategy

For Tier 4 tests, the test harness should:
1. Compile with picojc: `picojc T31_Fib.java -o T31_Fib.pjvm`
2. Compile with javac + pjvmpack: `javac T31_Fib.java && pjvmpack.py T31_Fib.class -o T31_Fib_ref.pjvm`
3. Run both on host picoJVM
4. Compare outputs byte-for-byte

The bytecodes need not be identical (different compilers may use different local
variable assignments, different branch encodings, etc.), but the **observable
output** must match exactly.

---

## 13. Test Harness and Run Infrastructure

### 13.1 Directory Structure

```
tooling/picojvm/picojc/
    src/                     # picojc source .java files
    tests/                   # Test .java files (T01..T36)
    expected/                # Expected output files (one per test)
    run_tests.py             # Test harness script
    Makefile                 # Build + test targets
```

### 13.2 Expected Output Files

Each test has a corresponding `expected/T01_Empty.out` file containing the
expected raw byte output (binary, not text).  For example:

```
expected/T02_Literal.out:  bytes [42, 0, 255]
expected/T08_MethodCall.out: bytes [7, 120]
```

These are generated once from the reference pjvmpack.py pipeline and checked in.

### 13.3 Test Harness Script: run_tests.py

```python
#!/usr/bin/env python3
"""
picojc test harness.

Compiles each test .java file with picojc, runs the resulting .pjvm on the
host picoJVM interpreter, and compares output against expected byte sequences.

Usage:
    python3 run_tests.py [--picojc PATH] [--picojvm PATH] [--pjvmpack PATH]
                         [--filter PATTERN] [--compare] [--verbose]

Options:
    --picojc PATH     Path to picojc compiler (default: built from src/)
    --picojvm PATH    Path to host picoJVM binary (default: ../picojvm)
    --pjvmpack PATH   Path to pjvmpack.py (default: ../pjvmpack.py)
    --filter PATTERN  Only run tests matching pattern (e.g., "T01" or "Tier1")
    --compare         Also compile with javac+pjvmpack and compare outputs
    --verbose         Print detailed output for each test
    --sim             Also run on 8085 simulator (requires i8085-trace)
    --trace PATH      Path to i8085-trace binary
"""
```

#### Test Execution Steps (per test):

1. **Compile**: Run picojc on the test `.java` file(s), producing a `.pjvm`
2. **Execute**: Run host picoJVM on the `.pjvm`, capture stdout bytes
3. **Compare**: Compare captured bytes against `expected/` file
4. **Report**: PASS / FAIL with details on mismatch

#### Output Format:

```
[PASS] T01_Empty           (0 bytes, 0ms)
[PASS] T02_Literal          (3 bytes, 1ms)
[PASS] T03_Arithmetic       (6 bytes, 1ms)
...
[FAIL] T15_Inheritance      expected [68, 67], got [0, 0]
...
Results: 30/36 passed, 6 failed
```

#### Comparison Mode (`--compare`):

```
[PASS] T31_Fib              picojc=match, pjvmpack=match
[DIFF] T33_Shapes           picojc output differs from pjvmpack output
  picojc:    [63, 0, 83, 25, 82, 21]
  pjvmpack:  [63, 0, 83, 25, 82, 21]
  (outputs match despite different bytecodes — OK)
```

### 13.4 Makefile Targets

```makefile
# Build picojc (host, via javac + pjvmpack, bootstrapping later)
picojc.pjvm: src/*.java
    javac -d build/classes src/*.java
    python3 ../pjvmpack.py build/classes/*.class -o $@

# Run all tests
test: picojc.pjvm ../picojvm
    python3 run_tests.py

# Run single test
test-%: picojc.pjvm ../picojvm
    python3 run_tests.py --filter $*

# Run with comparison against reference pipeline
test-compare: picojc.pjvm ../picojvm
    python3 run_tests.py --compare

# Run on 8085 simulator
test-sim: picojc.pjvm
    python3 run_tests.py --sim --trace ../../i8085-trace/build/i8085-trace

# Generate expected output files from reference pipeline
gen-expected: ../picojvm
    python3 run_tests.py --gen-expected

# Clean
clean:
    rm -rf build/ tests/*.pjvm tests/*.class
```

### 13.5 Capturing picoJVM Output

The host picoJVM binary writes output bytes to stdout.  The test harness
captures this and compares against expected bytes.  The `--quiet` or exit
behavior depends on the interpreter reaching `Native.halt()`.

To capture output programmatically:

```python
import subprocess

result = subprocess.run(
    ['./picojvm', 'test.pjvm'],
    capture_output=True, timeout=10
)
output_bytes = result.stdout  # raw bytes from putchar
```

The picoJVM host binary prints a stats line to stderr, and the actual putchar
output goes to stdout as raw bytes (in the `| ` prefixed display format).  The
test harness should parse the relevant output bytes.

**Important**: The host picoJVM prints output in a human-readable format:
```
picoJVM | Hello, 8085!
```

The test harness should either:
- Parse the `picoJVM | ` prefix and extract the displayed text, OR
- Use the raw byte values from the formatted output, OR
- Modify the host platform to support a `--raw` flag for binary output

The simplest approach: capture stderr/stdout and parse the known format.

### 13.6 8085 Simulator Tests

For simulator-level tests, the flow is:

1. Compile picojc test `.java` → `.pjvm`
2. Convert `.pjvm` to C data array (existing Makefile target)
3. Cross-compile picoJVM + data for 8085
4. Run on i8085-trace with `-d 0x0200:64` to dump output region
5. Parse hex dump for expected output bytes

This is the same flow as the existing `make sim-Fib` target.  The test harness
wraps it:

```python
def run_on_simulator(pjvm_file, trace_binary, expected):
    # Build for 8085
    subprocess.run(['make', 'sim', f'PJVM_FILE={pjvm_file}'],
                   cwd='../', check=True)
    # Run simulator
    result = subprocess.run([
        trace_binary, '-e', '0x0000', '-l', '0x0000',
        '-n', '50000000', '-S', '-q', '-d', '0x0200:64',
        'build/picojvm.bin'
    ], capture_output=True, cwd='../')
    # Parse memory dump from stderr
    dump = parse_hex_dump(result.stderr, 0x0200)
    return dump[:len(expected)] == expected
```

---

## 14. Simulator Tools and Setup

### 14.1 i8085-trace: The 8085 Simulator

**Location**: `/Users/andrewpullin/personal/llvm-8085/i8085-trace/`

**Building**:
```bash
cd i8085-trace
mkdir -p build && cd build
cmake ..
make
```

**Binary**: `i8085-trace/build/i8085-trace`

**What it does**: Runs 8085 machine code binaries on a simulated 8085 CPU with
64KB flat memory and 256-byte I/O space.  Outputs per-instruction trace in
NDJSON format, or a summary JSON line in `-S` mode.

### 14.2 Key Flags for picoJVM Testing

```bash
# Standard picoJVM simulation run:
./i8085-trace \
    -e 0x0000 \           # Entry point
    -l 0x0000 \           # Load address
    -n 50000000 \         # Max 50M instructions
    -S \                  # Summary mode (no per-step trace)
    -q \                  # Quiet (no status messages)
    -d 0x0200:64 \        # Dump output region at exit
    build/picojvm.bin     # Binary to run
```

The picoJVM 8085 platform writes output bytes to memory starting at address
`0x0200`.  The `-d 0x0200:64` flag dumps this region after execution, and the
test harness parses the hex dump to extract output bytes.

### 14.3 Output Formats

**Summary JSON** (stdout with `-S -q`):
```json
{"pc":"0142","sp":"FF00","f":"46","clk":2112486,"steps":321183,"halt":"hlt","r":["22","00","00","00","00","02","0A"]}
```

Key fields:
- `halt`: Termination reason.  Must be `"hlt"` for successful tests.
- `steps`: Instruction count (for performance tracking)
- `clk`: T-state count (cycle estimate)

**Memory dump** (stderr with `-d`):
```
Memory dump 0x0200 - 0x023F (64 bytes):
  0200: 00 01 01 02 03 05 08 0D 15 22 00 00 00 00 00 00  |........."......|
```

### 14.4 Termination Conditions

The simulator stops when:
1. HLT instruction (from `Native.halt()` → picoJVM trap → 8085 HLT)
2. Max steps reached (increase `-n` for complex tests)
3. Infinite loop detected (PC unchanged)

For picoJVM tests, always verify `halt == "hlt"` in the summary JSON.

### 14.5 Cross-Compilation Toolchain

To build picoJVM for the 8085 simulator:

```bash
cd tooling/picojvm

# Build a specific test:
make sim-Fib        # compiles Fib.pjvm + picoJVM for 8085, runs on simulator

# Build with custom .pjvm:
make sim PJVM_FILE=tests/MyTest.pjvm

# Just build the binary (no run):
make build/picojvm.bin PJVM_FILE=tests/MyTest.pjvm
```

The toolchain:
- **Clang**: `llvm-project/build-clang-8085/bin/clang --target=i8085-unknown-elf`
- **LLD**: `llvm-project/build-clang-8085/bin/ld.lld -m i8085elf`
- **Linker script**: `sysroot/ldscripts/i8085-32kram-32krom.ld`
- **Libraries**: `sysroot/lib/libgcc.a`, `sysroot/lib/libc.a`

### 14.6 Host picoJVM Interpreter

For rapid iteration, use the host (desktop) picoJVM binary:

```bash
cd tooling/picojvm

# Build host interpreter
make

# Run a .pjvm file
./picojvm tests/Fib.pjvm

# Output:
# picoJVM | <output bytes displayed>
# --- stats: heap=XX, sp_max=XX, lt_max=XX, fdepth_max=XX ---
```

The host interpreter is a standard POSIX binary (compiled with `cc -O2`).  It
runs .pjvm files directly, with output to stdout and stats to stderr.

### 14.7 pjvmpack.py: The Reference Compiler Pipeline

The existing `.class` → `.pjvm` pipeline serves as the reference implementation:

```bash
# Compile Java to .class
javac -d tests tests/Fib.java tests/Native.java

# Pack .class to .pjvm
python3 pjvmpack.py tests/Fib.class -o tests/Fib.pjvm -v
```

The `-v` flag outputs detailed information about the packing process: class
hierarchy, method table, vtable assignments, CP layout, section sizes.

This pipeline is what picojc replaces.  During development, use pjvmpack.py
output as the reference for comparison.

---

## 15. Development Workflow

### 15.1 Bootstrap Strategy

picojc is written in Java and runs on picoJVM.  But to bootstrap, we need a
working compiler first.  The bootstrap path:

1. **Phase 0**: Write picojc in Java.  Compile with standard javac.  Pack with
   pjvmpack.py.  Test on host picoJVM.
2. **Phase 1**: picojc can compile simple programs (Tier 1 tests).  Still
   compiled by javac + pjvmpack.
3. **Phase 2**: picojc can compile itself.  Use javac-compiled picojc to compile
   picojc source → picojc.pjvm.  Verify the self-compiled version passes all
   tests.
4. **Phase 3**: Self-hosted.  Use picojc.pjvm (running on picoJVM) to compile
   picojc source → picojc2.pjvm.  Verify picojc2.pjvm is byte-identical to
   picojc.pjvm (fixed point).

### 15.2 Iterative Development

During Phase 0-1, the inner development loop is:

```bash
# 1. Edit picojc Java source
vim src/Compiler.java

# 2. Compile with javac + pjvmpack
make picojc.pjvm

# 3. Run a test through picojc
./picojvm picojc.pjvm < tests/T02_Literal.java > T02_Literal.pjvm

# 4. Run the output on picoJVM
./picojvm T02_Literal.pjvm

# 5. Check output
# Expected: [42, 0, 255]
```

Or use the test harness for batch testing:
```bash
make test              # all tests
make test-T08          # single test
make test-compare      # compare against reference pipeline
```

### 15.3 I/O for the Compiler

picojc running on picoJVM needs to:
- **Read source file**: Via `Native.peek()` reading from a memory-mapped buffer,
  or via a custom native method for file I/O
- **Write .pjvm output**: Via `Native.poke()` to an output buffer, or via
  custom native method

For the host platform, the simplest approach: the test harness loads the source
file into memory before launching picoJVM, and the compiler reads from a known
address.  For 8085 target: source and output are in paged storage.

**Design decision**: Define a simple file I/O protocol using `Native.peek/poke`
on a memory-mapped region, or add new native methods:

```java
// Option A: Memory-mapped I/O (works with existing natives)
// Source loaded at 0xC000, length at 0xBFFE
int srcLen = Native.peek(0xBFFE) | (Native.peek(0xBFFF) << 8);
int ch = Native.peek(0xC000 + offset);

// Option B: New native methods (requires interpreter extension)
// Native.openFile(name), Native.readByte(), Native.writeByte(), etc.
```

Option A is simpler and requires no interpreter changes.  The test harness pre-
loads the source file into the picoJVM memory image before execution.

### 15.4 Recommended File Structure

```java
// src/Lexer.java       — Tokenizer
// src/Token.java       — Token types and values
// src/Parser.java      — Recursive descent parser + code emitter
// src/Resolver.java    — Pass 2: symbol resolution and layout
// src/Emitter.java     — Bytecode emission helpers
// src/SymbolTable.java — Symbol directory and resolved table
// src/PjvmWriter.java  — Pass 4: .pjvm binary serialization
// src/Compiler.java    — Main entry point, orchestrates passes
// src/Native.java      — Native method stubs (same as picoJVM tests)
```

---

## 16. Self-Hosting Path

### 16.1 What Self-Hosting Means

picojc is self-hosting when:
1. picojc (compiled by javac + pjvmpack) can compile its own source
2. The resulting picojc.pjvm produces identical output to the javac-compiled
   version on all test inputs
3. Ideally: picojc.pjvm can compile itself (fixed point)

### 16.2 Language Features Required for Self-Hosting

picojc itself will use:
- Classes with fields and methods
- Arrays (byte[], int[], String[])
- String constants and operations (hashCode, equals, charAt, length)
- Loops (for, while)
- Switch statements (on int)
- Exception handling (for parse errors: throw + catch)
- Static methods (most of the compiler)
- Possibly a few instances (Lexer, Parser, Emitter objects)

This is well within picoJVM's supported subset.

### 16.3 On-8085 Compilation

Once self-hosted, picojc.pjvm can run on real 8085 hardware (or simulator) via
picoJVM with paging:

```bash
# On simulator:
# 1. Load picoJVM + picojc.pjvm into ROM (paged)
# 2. Load source.java into RAM (or external storage)
# 3. Run — compiler writes output.pjvm to storage
# 4. Load output.pjvm and run on picoJVM

# Estimated compilation speed: ~100-500 bytes/second
# (dominated by page faults for compiler code)
```

### 16.4 Multi-Pass + Paging Performance

Each pass re-reads the source file from storage.  With a typical source file of
2KB and 4 passes, that's 8KB of source I/O per compilation.  The compiler's own
code (say, 12KB of bytecodes) is paged with an LRU cache.  At 3 MHz with 2
page buffers:

- Page fault cost: ~1000 T-states (read 1KB from SPI flash at 4MHz)
- Compiler code working set per pass: ~3-4KB (subset of methods active)
- Estimated page faults per pass: ~10-20
- Total page fault overhead: ~100-200K T-states (negligible vs. execution)

The bottleneck is interpreter overhead, not paging.

---

## Appendix A: picoJVM Opcode Table

All JVM opcodes supported by picoJVM.  picojc must emit only these opcodes.

| Opcode | Hex | Mnemonic | Stack Effect | Description |
|--------|-----|----------|-------------|-------------|
| 0 | 0x00 | NOP | - | No operation |
| 1 | 0x01 | ACONST_NULL | → null | Push null |
| 2 | 0x02 | ICONST_M1 | → -1 | Push -1 |
| 3 | 0x03 | ICONST_0 | → 0 | Push 0 |
| 4 | 0x04 | ICONST_1 | → 1 | Push 1 |
| 5 | 0x05 | ICONST_2 | → 2 | Push 2 |
| 6 | 0x06 | ICONST_3 | → 3 | Push 3 |
| 7 | 0x07 | ICONST_4 | → 4 | Push 4 |
| 8 | 0x08 | ICONST_5 | → 5 | Push 5 |
| 16 | 0x10 | BIPUSH byte | → value | Push signed byte |
| 17 | 0x11 | SIPUSH s16 | → value | Push signed short |
| 18 | 0x12 | LDC idx | → value | Load constant (1-byte index) |
| 19 | 0x13 | LDC_W idx16 | → value | Load constant (2-byte index) |
| 21 | 0x15 | ILOAD idx | → value | Load int from local |
| 25 | 0x19 | ALOAD idx | → ref | Load ref from local |
| 26 | 0x1A | ILOAD_0 | → value | Load int local 0 |
| 27 | 0x1B | ILOAD_1 | → value | Load int local 1 |
| 28 | 0x1C | ILOAD_2 | → value | Load int local 2 |
| 29 | 0x1D | ILOAD_3 | → value | Load int local 3 |
| 42 | 0x2A | ALOAD_0 | → ref | Load ref local 0 |
| 43 | 0x2B | ALOAD_1 | → ref | Load ref local 1 |
| 44 | 0x2C | ALOAD_2 | → ref | Load ref local 2 |
| 45 | 0x2D | ALOAD_3 | → ref | Load ref local 3 |
| 46 | 0x2E | IALOAD | array, idx → value | Load int array element |
| 50 | 0x32 | AALOAD | array, idx → ref | Load ref array element |
| 51 | 0x33 | BALOAD | array, idx → value | Load byte array element |
| 52 | 0x34 | CALOAD | array, idx → value | Load char array element |
| 53 | 0x35 | SALOAD | array, idx → value | Load short array element |
| 54 | 0x36 | ISTORE idx | value → | Store int to local |
| 58 | 0x3A | ASTORE idx | ref → | Store ref to local |
| 59 | 0x3B | ISTORE_0 | value → | Store int local 0 |
| 60 | 0x3C | ISTORE_1 | value → | Store int local 1 |
| 61 | 0x3D | ISTORE_2 | value → | Store int local 2 |
| 62 | 0x3E | ISTORE_3 | value → | Store int local 3 |
| 75 | 0x4B | ASTORE_0 | ref → | Store ref local 0 |
| 76 | 0x4C | ASTORE_1 | ref → | Store ref local 1 |
| 77 | 0x4D | ASTORE_2 | ref → | Store ref local 2 |
| 78 | 0x4E | ASTORE_3 | ref → | Store ref local 3 |
| 79 | 0x4F | IASTORE | array, idx, val → | Store int array element |
| 83 | 0x53 | AASTORE | array, idx, ref → | Store ref array element |
| 84 | 0x54 | BASTORE | array, idx, val → | Store byte array element |
| 85 | 0x55 | CASTORE | array, idx, val → | Store char array element |
| 86 | 0x56 | SASTORE | array, idx, val → | Store short array element |
| 87 | 0x57 | POP | val → | Pop top value |
| 88 | 0x58 | POP2 | val1, val2 → | Pop top 2 values |
| 89 | 0x59 | DUP | val → val, val | Duplicate top |
| 90 | 0x5A | DUP_X1 | v2, v1 → v1, v2, v1 | Dup and insert 2 down |
| 91 | 0x5B | DUP_X2 | v3, v2, v1 → v1, v3, v2, v1 | Dup and insert 3 down |
| 92 | 0x5C | DUP2 | v2, v1 → v2, v1, v2, v1 | Dup top 2 |
| 95 | 0x5F | SWAP | v2, v1 → v1, v2 | Swap top 2 |
| 96 | 0x60 | IADD | a, b → a+b | Add |
| 100 | 0x64 | ISUB | a, b → a-b | Subtract |
| 104 | 0x68 | IMUL | a, b → a*b | Multiply |
| 108 | 0x6C | IDIV | a, b → a/b | Divide |
| 112 | 0x70 | IREM | a, b → a%b | Remainder |
| 116 | 0x74 | INEG | a → -a | Negate |
| 120 | 0x78 | ISHL | a, n → a<<n | Left shift |
| 122 | 0x7A | ISHR | a, n → a>>n | Arithmetic right shift |
| 124 | 0x7C | IUSHR | a, n → a>>>n | Unsigned right shift |
| 126 | 0x7E | IAND | a, b → a&b | Bitwise AND |
| 128 | 0x80 | IOR | a, b → a\|b | Bitwise OR |
| 130 | 0x82 | IXOR | a, b → a^b | Bitwise XOR |
| 132 | 0x84 | IINC idx, const | - | Increment local by signed byte |
| 145 | 0x91 | I2B | val → byte | Int to byte |
| 146 | 0x92 | I2C | val → char | Int to char |
| 147 | 0x93 | I2S | val → short | Int to short |
| 153 | 0x99 | IFEQ off16 | val → | Branch if == 0 |
| 154 | 0x9A | IFNE off16 | val → | Branch if != 0 |
| 155 | 0x9B | IFLT off16 | val → | Branch if < 0 |
| 156 | 0x9C | IFGE off16 | val → | Branch if >= 0 |
| 157 | 0x9D | IFGT off16 | val → | Branch if > 0 |
| 158 | 0x9E | IFLE off16 | val → | Branch if <= 0 |
| 159 | 0x9F | IF_ICMPEQ off16 | a, b → | Branch if a == b |
| 160 | 0xA0 | IF_ICMPNE off16 | a, b → | Branch if a != b |
| 161 | 0xA1 | IF_ICMPLT off16 | a, b → | Branch if a < b |
| 162 | 0xA2 | IF_ICMPGE off16 | a, b → | Branch if a >= b |
| 163 | 0xA3 | IF_ICMPGT off16 | a, b → | Branch if a > b |
| 164 | 0xA4 | IF_ICMPLE off16 | a, b → | Branch if a <= b |
| 165 | 0xA5 | IF_ACMPEQ off16 | r1, r2 → | Branch if refs equal |
| 166 | 0xA6 | IF_ACMPNE off16 | r1, r2 → | Branch if refs not equal |
| 167 | 0xA7 | GOTO off16 | - | Unconditional branch |
| 170 | 0xAA | TABLESWITCH | key → | Dense switch |
| 171 | 0xAB | LOOKUPSWITCH | key → | Sparse switch |
| 172 | 0xAC | IRETURN | val → [caller] val | Return int |
| 176 | 0xB0 | ARETURN | ref → [caller] ref | Return reference |
| 177 | 0xB1 | RETURN | - | Return void |
| 178 | 0xB2 | GETSTATIC idx16 | → value | Get static field |
| 179 | 0xB3 | PUTSTATIC idx16 | value → | Put static field |
| 180 | 0xB4 | GETFIELD idx16 | obj → value | Get instance field |
| 181 | 0xB5 | PUTFIELD idx16 | obj, value → | Put instance field |
| 182 | 0xB6 | INVOKEVIRTUAL idx16 | obj, args → result | Virtual call |
| 183 | 0xB7 | INVOKESPECIAL idx16 | obj, args → result | Direct call |
| 184 | 0xB8 | INVOKESTATIC idx16 | args → result | Static call |
| 185 | 0xB9 | INVOKEINTERFACE idx16, count, 0 | obj, args → result | Interface call |
| 187 | 0xBB | NEW idx16 | → ref | Create object |
| 188 | 0xBC | NEWARRAY type | count → ref | Create primitive array |
| 189 | 0xBD | ANEWARRAY idx16 | count → ref | Create ref array |
| 190 | 0xBE | ARRAYLENGTH | arr → length | Array length |
| 191 | 0xBF | ATHROW | exc → | Throw exception |
| 192 | 0xC0 | CHECKCAST idx16 | ref → ref | Verify type |
| 193 | 0xC1 | INSTANCEOF idx16 | ref → 0/1 | Type check |
| 197 | 0xC5 | MULTIANEWARRAY idx16, dims | counts → ref | Multi-dim array |
| 198 | 0xC6 | IFNULL off16 | ref → | Branch if null |
| 199 | 0xC7 | IFNONNULL off16 | ref → | Branch if not null |

**NEWARRAY type codes**: 4=boolean(1B), 5=char(2B), 6=float(N/A), 7=double(N/A),
8=byte(1B), 9=short(2B), 10=int(4B), 11=long(N/A).  picoJVM implementation:
types 4,8 → 1 byte/element; types 5,9 → 2 bytes/element; others → 4 bytes/element.

---

## Appendix B: Native Method ABI

### B.1 Native Method Flag Encoding

In the method table, byte offset 3 (`flags`):
```
Bit 0:    is_native (1 = native method)
Bits 1-7: native_id (0-127)
```

So the flags byte = `(native_id << 1) | 1` for native methods.

### B.2 Argument Passing

Native methods receive arguments on the picoJVM operand stack, same as Java
methods.  The `arg_count` field determines how many stack entries the method
consumes.

For instance methods (String.length, etc.), `arg_count` includes `this` as
argument 0.

### B.3 Return Values

Native methods that return a value leave it on the operand stack.  The
interpreter's native dispatch handles this directly.

---

## Appendix C: Existing picoJVM Test Programs

These are the test programs already in the picoJVM test suite.  Tier 4 of the
picojc test suite should produce identical output.

| Test | Source Files | Expected Output (bytes) | Features |
|------|-------------|------------------------|----------|
| Fib | Fib.java | 0,1,1,2,3,5,8,13,21,34 | Recursion, loops, locals |
| HelloWorld | HelloWorld.java | H,e,l,l,o,,,space,8,0,8,5,!,\n | Arrays, loops |
| BubbleSort | BubbleSort.java | 0,1,2,3,4,5,6,7,8,9 | Nested loops, swap |
| Counter | Counter.java | 3 | Objects, fields, methods |
| StringTest | StringTest.java | H,e,l,l,o,5,108,1,0,1,W,o,r,l,d | Strings |
| StaticInitTest | StaticInitTest.java | 42,50 | Static init, clinit |
| MultiArrayTest | MultiArrayTest.java | 1,5,9,3,4 | Multi-dim arrays |
| StringSwitchTest | StringSwitchTest.java | 1,2,3,0 | String switch |
| Shapes | Shapes.java + Shape, Square, Rect | 63,0,83,25,82,21 | Inheritance, vtables |
| Features | Features.java + Shape, Square, Rect | B,Y,T,E,1,2,3,0,10,20,99,1,0,1,O,K | Mixed features |
| InterfaceTest | InterfaceTest.java + interfaces | 27,20,67,66,27,20 | Interfaces |
| ExceptionTest | ExceptionTest.java + MyException | 1,2,3,10,4 | Exceptions |

### C.1 Source Code

All test sources are at `tooling/picojvm/tests/*.java`.  Key files:

**Native.java** (must be available to all tests):
```java
public class Native {
    public static native void putchar(int c);
    public static native int  in(int port);
    public static native void out(int port, int val);
    public static native int  peek(int addr);
    public static native void poke(int addr, int val);
    public static native void halt();
    public static native void print(String s);
}
```

**Fib.java** (reference implementation):
```java
public class Fib {
    static int fib(int n) {
        if (n <= 1) return n;
        int a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            int t = a + b;
            a = b;
            b = t;
        }
        return b;
    }
    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            Native.putchar(fib(i));
        }
        Native.halt();
    }
}
```

---

## Appendix D: .pjvm File Format Reference

### D.1 Section Ordering (v1)

```
Bytes       Section
[0..9]      Header (10 bytes)
[10..]      Class table (variable)
[..]        Method table (n_methods × 12 bytes)
[..]        CP resolution table (2 + cp_size bytes)
[..]        Integer constants (n_integers × 4 bytes)
[..]        String constants (variable)
[..]        Bytecode section (bytecodes_size bytes)
[..]        Exception table (Σ exc_count × 7 bytes)
```

### D.2 Byte Order

All multi-byte values are **little-endian** (LSB first).

### D.3 String Encoding

Strings are UTF-8 encoded.  picoJVM treats them as byte arrays internally.
No null terminator in the binary format.

### D.4 Exception Table Offsets

Exception table `start_pc`, `end_pc`, and `handler_pc` are offsets from the
**start of the bytecode section** (not from the start of the .pjvm file).
They correspond directly to the offsets used in GOTO and branch instructions
within that method.

### D.5 CP Index Encoding in Bytecode

When bytecode uses a 2-byte constant pool index (e.g., INVOKEVIRTUAL, GETFIELD,
NEW), the index is: `(byte1 << 8) | byte2` (big-endian, per JVM spec).  This
indexes into the method's CP resolution slice starting at `cp_base`.

For LDC (1-byte index): the single byte indexes into the same CP slice.

### D.6 Branch Offset Encoding

Branch offsets (GOTO, IF*) are 2-byte **signed** values in big-endian, relative
to the branch instruction's PC.  Specifically: `target_pc = branch_pc + offset`,
where `branch_pc` is the address of the branch opcode itself.

### D.7 TABLESWITCH Layout

After 4-byte alignment padding:
```
[2 bytes] default_offset (signed, relative to switch opcode PC)
[2 bytes] low (signed)
[2 bytes] high (signed)
[(high - low + 1) × 4 bytes] jump offsets (signed, relative to switch PC)
```

Note: picoJVM uses 2-byte default/low/high (not 4-byte as in standard JVM).
The jump table entries are still 4 bytes each.

### D.8 LOOKUPSWITCH Layout

After 4-byte alignment padding:
```
[2 bytes] default_offset (signed, relative to switch opcode PC)
[2 bytes] npairs
[npairs × 6 bytes]:
    [4 bytes] match_key (signed, big-endian)
    [2 bytes] offset (signed, relative to switch PC)
```

Note: picoJVM uses 2-byte npairs (not 4-byte) and 2-byte offsets (not 4-byte
as standard JVM).  Match keys remain 4 bytes.

---

## Appendix E: Glossary

| Term | Definition |
|------|-----------|
| picojc | The Java compiler described in this spec |
| picoJVM | The bytecode interpreter that runs .pjvm programs |
| .pjvm | Binary file format for picoJVM programs |
| pjvmpack.py | Existing Python tool: .class → .pjvm converter |
| i8085-trace | Intel 8085 CPU simulator |
| CP | Constant Pool — pre-resolved lookup table in .pjvm |
| vtable | Virtual method dispatch table |
| vmid | Virtual Method ID — for interface dispatch |
| clinit | Class static initializer method |
| NDJSON | Newline-delimited JSON (trace output format) |
| T-states | Clock cycles (cycle timing unit for 8085) |
| LRU | Least Recently Used (page eviction policy) |
| backpatch | Fill in branch target after it's known |
