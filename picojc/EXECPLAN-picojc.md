# picojc: A Java Compiler for picoJVM on Intel 8085

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds. This document must be maintained in accordance with `PLANS.md` at the repository root.

## Purpose / Big Picture

After this work is complete, a developer can write a Java program using a well-defined subset of the language (integer arithmetic, classes, inheritance, interfaces, arrays, strings, exceptions, switch statements), compile it with `picojc` into a `.pjvm` binary, and run it on the picoJVM interpreter -- either on the host desktop or on a simulated Intel 8085 microprocessor. The compiler itself is written in Java and targets picoJVM, meaning it can eventually compile itself (self-host) and run on real 8085 hardware.

To see it working after all milestones are complete, a developer runs:

    cd tooling/picojvm
    make                       # builds host picoJVM interpreter
    cd picojc
    make picojc.pjvm           # builds picojc via javac + pjvmpack.py
    make test                  # compiles all 36 test .java files, runs on picoJVM, checks output

They should observe 36/36 tests passing, each producing the expected byte sequence via `Native.putchar()`.


## Progress

- [x] Milestone 1: Foundation (Lexer, Token, project scaffolding, bootstrap build)
- [x] Milestone 2: Catalog and Resolve (Pass 1 + Pass 2 + Pass 4 skeleton)
- [x] Milestone 3: Core Language Emission (Pass 3 for Tier 1 tests T01-T12)
- [x] Milestone 4: Object-Oriented Emission (Tier 2 tests T13-T19)
- [x] Milestone 5: Advanced Features (Tier 3 tests T20-T30)
- [x] Milestone 6: Compatibility and Polish (Tier 4 tests T31-T38, 36/36 passing)
- [ ] Milestone 7: Self-Hosting (picojc compiles itself)

Note: T23_StringSwitch and T26_Interface are deferred (need hashCode/equals pattern and vmid dispatch respectively). T37_Recursion and T38_LinkedList were added to reach 36 tests.


## Surprises & Discoveries

- **Consolidated architecture**: The original plan called for 7 separate .java files (Token, Lexer, SymbolTable, Resolver, Emitter, PjvmWriter, Compiler). In practice, all compilation logic lives in Compiler.java with Token.java, Lexer.java, and Native.java as supporting files. The flat-array approach with static fields works well for the 8085's memory constraints.

- **Exception class hierarchy double-resolution bug**: `synthesizeExceptionClass()` sets `classParent` to a resolved class index, but the `resolve()` pass re-iterates all classes and reinterprets that index as a name index. Fix: limit the resolution loop to `origClassCount` (classes that existed before synthesis).

- **Finally body duplication**: Java's `finally` clause must emit code on both the normal path and the exception path. The compiler re-lexes the source to parse the finally body twice. Critical: save the source position BEFORE `expect(TOK_LBRACE)` consumes the opening brace, since `expect` calls `nextToken` which advances past the first token of the body.

- **Inline static field initializers**: `static int x = 42;` requires saving the source position of the initializer expression during catalog, then re-lexing to emit bytecodes at the start of `<clinit>`. A synthetic `<clinit>` is created if only field initializers exist (no explicit `static { }` block).

- **Array element type tracking**: Simple `int type` (0=int, 1=ref) is insufficient for arrays. Extended to 0=int, 1=ref, 3=int[], 4=byte[], 5=char[] to emit the correct BALOAD/BASTORE vs IALOAD/IASTORE vs CALOAD/CASTORE opcodes.

- **Implicit this.method() calls**: Instance methods called without `this.` prefix (e.g., `area()` inside a class) need ALOAD_0 + INVOKEVIRTUAL, not INVOKESTATIC. Required a method lookup before falling through to static call.


## Decision Log

- Decision: Write picojc as a multi-pass, syntax-directed compiler with no AST.
  Rationale: The target platform (8085, 64KB address space) cannot hold a full AST in memory. Each pass re-reads source from storage. Peak RAM stays under 16KB. This is specified in the PICOJVM_JAVAC_SPEC.md section 5.
  Date/Author: 2026-03-09 / initial plan

- Decision: Use javac + pjvmpack.py as the bootstrap toolchain for Phase 0.
  Rationale: picojc is written in Java and runs on picoJVM. To compile it the first time, we need an existing Java compiler. Standard javac produces .class files; pjvmpack.py converts them to .pjvm. Once picojc can compile itself, this bootstrap dependency disappears.
  Date/Author: 2026-03-09 / initial plan

- Decision: Use memory-mapped I/O (Option A from spec) for source file input.
  Rationale: No interpreter changes needed. The test harness pre-loads the source file into picoJVM's memory before execution. `Native.peek()` reads bytes from the source buffer at a known address (0xC000, length at 0xBFFE). `Native.poke()` writes output bytes. This keeps the interpreter unchanged and the compiler portable.
  Date/Author: 2026-03-09 / initial plan

- Decision: Structure milestones around test tiers, not compiler passes.
  Rationale: Each pass (Catalog, Resolve, Emit, Link) is useless alone. A milestone that delivers "Pass 1 done" has no observable behavior. Instead, milestones deliver progressively more test programs compiling and running correctly. Each milestone extends all four passes as needed for the new language features.
  Date/Author: 2026-03-09 / initial plan

- Decision: First error halts compilation, no recovery.
  Rationale: Spec section 5.6 explicitly states this is acceptable for a bootstrap compiler. Keeps the compiler simple and small.
  Date/Author: 2026-03-09 / initial plan


## Outcomes & Retrospective

(To be populated at milestone completions and at final completion.)


## Context and Orientation

This section describes everything a novice needs to know to implement picojc from scratch.

### The LLVM-8085 Project

This repository (`llvm-8085`) contains a complete LLVM compiler backend for the Intel 8085 8-bit microprocessor. The 8085 has 7 general-purpose 8-bit registers (A, B, C, D, E, H, L), a 16-bit stack pointer and program counter, and a 64KB address space. It runs at approximately 3 MHz. It has no hardware multiply, no hardware divide, no floating point, and no indexed addressing modes.

The relevant parts of the project for picojc are:

- `tooling/picojvm/core.c` (~1200 lines): The picoJVM interpreter. A portable C program that executes `.pjvm` bytecode files. It implements 57 JVM opcodes in a single giant switch statement.
- `tooling/picojvm/pjvm.h`: Public header for picoJVM. Contains type definitions, capacity constants, and the API.
- `tooling/picojvm/pjvmpack.py` (~1000 lines): A Python script that converts Java `.class` files (produced by javac) into `.pjvm` binary files. This is the tool that picojc replaces.
- `tooling/picojvm/tests/*.java`: Existing test programs that run on picoJVM. These include Fib.java, BubbleSort.java, Shapes.java, ExceptionTest.java, and others.
- `tooling/picojvm/tests/Native.java`: The `Native` class that provides built-in methods for I/O and control (putchar, halt, print, peek, poke, etc.).
- `tooling/picojvm/Makefile`: Builds the host picoJVM interpreter and runs tests.
- `tooling/picojvm/platform/`: Platform-specific shim files for picoJVM (host POSIX, 8085 target).
- `i8085-trace/`: The 8085 simulator. Runs 8085 binaries with instruction tracing. Binary at `i8085-trace/build/i8085-trace`.
- `tooling/picojvm/PICOJVM_JAVAC_SPEC.md`: The detailed specification for picojc. Contains the .pjvm binary format, supported language subset, compiler architecture, all 36 test programs with expected outputs, and the self-hosting path. This is the authoritative reference for all picojc behavior.

### What picoJVM Does

picoJVM is a stack-based bytecode interpreter. It executes a subset of JVM bytecode (57 opcodes). All values on its operand stack are stored as `(uint16_t lo, uint16_t hi)` pairs. Integer values use both halves (32-bit signed, `value = lo | (hi << 16)`). References (pointers to heap objects) use only `lo` (16-bit address), with `hi = 0`. Null is `(0, 0)`.

Objects live in a flat heap. Each object starts with a 2-byte class ID, 2 bytes reserved, then 4 bytes per instance field. Arrays start with a 2-byte element count, 2 bytes reserved, then elements (4 bytes each for int/ref arrays, 1 byte each for byte/char arrays).

Method calls use a frame stack. Each frame saves the return PC, constant pool base, method index, local variable base, and operand stack pointer. Arguments are copied from the caller's operand stack into the callee's local variable slots.

### The .pjvm Binary Format

The `.pjvm` file is the output of picojc. It contains these sections in order:

1. **Header** (10 bytes for v1): magic bytes (0x85, 0x4A), method count, main method index, static field count, integer constant count, class count, string count, bytecode section size (16-bit LE).

2. **Class table** (variable length): For each class: parent_id (1 byte, 0xFF = root), instance field count (1 byte), vtable size (1 byte), clinit method index (1 byte, 0xFF = none), then vtable entries (1 byte each = method indices).

3. **Method table** (12 bytes per method): max_locals, max_stack, arg_count, flags (bit 0 = native, bits 1-7 = native_id), code_offset (2 bytes LE), cp_base (2 bytes LE), vtable_slot, vmid, exc_count, exc_off_idx.

4. **CP resolution table**: 2-byte size (LE), then that many bytes. Each byte is a pre-resolved value: method index for Methodref, field slot for Fieldref, `0x80 | string_index` for String, class_id for Class, 0xFF for unresolved.

5. **Integer constants**: 4 bytes each, little-endian int32.

6. **String constants**: For each string: 2-byte length (LE), then UTF-8 bytes (no null terminator).

7. **Bytecode section**: Raw JVM bytecodes, concatenated for all methods. Each method's `code_offset` points into this section.

8. **Exception table**: 7 bytes per entry: start_pc (2 bytes LE), end_pc (2 bytes LE), handler_pc (2 bytes LE), catch_class_id (1 byte, 0xFF = catch-all). Offsets are relative to the start of the bytecode section.

All multi-byte values in the .pjvm file are **little-endian** except for bytecode-embedded values (CP indices in INVOKEVIRTUAL etc. are big-endian per JVM spec, and branch offsets are big-endian signed 16-bit relative to the branch opcode's PC).

### The Compiler Architecture

picojc uses 4 passes. Each pass re-reads the source file from storage; no pass holds the entire program in memory.

**Pass 1 (Catalog)**: Scans through the source token by token. Records every class (name, parent, interfaces), field (name, type, static/instance), and method (name, descriptor, arg count, source byte offsets of body start/end, static/instance/constructor/native). Output: a compact symbol directory (~20-30 bytes per symbol).

**Pass 2 (Resolve)**: Uses the symbol directory to assign concrete indices and offsets. Topologically sorts classes (parents before children). Assigns class IDs, computes instance field layouts (parent fields first), assigns static field slots, builds vtables (inherit parent, override matching methods, append new ones), assigns method indices, builds the CP resolution table (one byte per symbolic reference), assigns interface vmids, and estimates max_locals and max_stack per method.

**Pass 3 (Emit)**: For each method, re-reads its source span and generates bytecode via recursive-descent parsing. The parser calls `emitByte(opcode)` directly as it processes each construct. Expressions naturally compile in stack-machine order. Forward branches use a backpatch list: emit 2 placeholder bytes, record the location, and fill in the offset when the target label is defined. This pass also collects integer and string constants for the CP. Per-method RAM: ~1KB (bytecode buffer + backpatch list + local variable map).

**Pass 4 (Link)**: Serializes all sections into the final .pjvm binary in the order specified above. Straightforward byte-by-byte output. All data has been prepared by Passes 2 and 3.

### Supported Java Subset

Types: `int` (32-bit), `byte`, `char`, `short`, `boolean` (all stored as int on the JVM stack), `void`, references (16-bit heap address). NOT supported: `long`, `float`, `double`.

Declarations: classes with single inheritance, interfaces, fields (instance and static), methods (instance and static), constructors, local variables.

Expressions: all arithmetic (`+`, `-`, `*`, `/`, `%`, unary `-`), bitwise (`&`, `|`, `^`, `~`, `<<`, `>>`, `>>>`), comparison (`==`, `!=`, `<`, `>`, `<=`, `>=`), logical (`&&`, `||`, `!`), assignment (plain and compound), increment/decrement (pre and post), casts, `instanceof`, ternary, method calls, field access, array access, literals (int, string, char, hex, binary, null, this), `new` (objects and arrays).

Statements: variable declaration, expression statement, if/else, while, do-while, for, switch/case/default, return, break, continue, throw, try/catch/finally, blocks.

NOT supported: generics, lambdas, inner classes, annotations, enums, synchronized, multi-catch, try-with-resources, varargs, auto-boxing, string concatenation with `+`, assert, packages/imports. All classes exist in a single compilation unit (one source file).

### Native Methods

picoJVM has 13 built-in native methods. picojc recognizes two built-in classes (`Native` and `String`) and emits the correct native stubs without requiring their source. The native IDs are:

| Method | ID | Signature |
|--------|---:|-----------|
| Native.putchar | 0 | (I)V |
| Native.in | 1 | (I)I |
| Native.out | 2 | (II)V |
| Native.peek | 3 | (I)I |
| Native.poke | 4 | (II)V |
| Native.halt | 5 | ()V |
| Object.\<init\> | 6 | ()V |
| String.length | 7 | ()I |
| String.charAt | 8 | (I)C |
| String.equals | 9 | (Ljava/lang/Object;)Z |
| String.toString | 10 | ()Ljava/lang/String; |
| Native.print | 11 | (Ljava/lang/String;)V |
| String.hashCode | 12 | ()I |

In the method table, a native method has `flags = (native_id << 1) | 1`, `code_offset = 0`, `max_locals = arg_count`, `max_stack = max(arg_count, 1)`.

### Branch and Switch Encoding

Branch offsets (GOTO, IF*) are 2-byte signed big-endian, relative to the branch opcode's PC: `target_pc = branch_pc + offset`.

TABLESWITCH (after 4-byte alignment padding): 2-byte default_offset, 2-byte low, 2-byte high, then `(high - low + 1)` entries of 4 bytes each (signed offset relative to switch PC). Note picoJVM uses 2-byte default/low/high, not 4-byte as in standard JVM.

LOOKUPSWITCH (after 4-byte alignment padding): 2-byte default_offset, 2-byte npairs, then npairs entries of 6 bytes each (4-byte match key big-endian, 2-byte offset relative to switch PC). picoJVM uses 2-byte npairs and 2-byte offsets, not 4-byte as standard JVM.

### Existing Files and Build System

The host picoJVM interpreter is built by running `make` in `tooling/picojvm/`. The resulting binary `picojvm` takes a `.pjvm` file as argument and executes it.

The reference pipeline (which picojc replaces) is: `javac *.java` to produce `.class` files, then `python3 pjvmpack.py *.class -o output.pjvm` to produce the `.pjvm` binary.

The 8085 simulator is at `i8085-trace/build/i8085-trace`. For simulation tests, picoJVM is cross-compiled to 8085 with the `.pjvm` data baked in, then run on the simulator. Output bytes go to memory address 0x0200 and are dumped with `-d 0x0200:64`.


## Plan of Work

### Milestone 1: Foundation (Lexer, Tokens, Project Scaffolding, Bootstrap Build)

At the end of this milestone, the picojc project directory exists with source files, a Makefile, and a working Lexer that can tokenize Java source files. The Lexer can be tested by compiling picojc with javac + pjvmpack, running it on picoJVM, and observing it print a token stream from a simple input.

**What to create:**

The directory `tooling/picojvm/picojc/` with this structure:

    tooling/picojvm/picojc/
        src/
            Token.java        -- Token type constants and token value holder
            Lexer.java         -- Tokenizer: reads source bytes, produces tokens
            SymbolTable.java   -- Symbol directory (classes, methods, fields)
            Resolver.java      -- Pass 2: symbol resolution and layout
            Emitter.java       -- Bytecode emission buffer and helpers
            PjvmWriter.java    -- Pass 4: .pjvm binary serialization
            Compiler.java      -- Main entry point, orchestrates all passes
            Native.java        -- Copy of the Native class (for picoJVM built-ins)
        tests/                 -- Test .java files (T01..T36)
        expected/              -- Expected output byte files
        run_tests.py           -- Test harness
        Makefile               -- Build and test targets

**Token.java**: Define integer constants for every token type. Java keywords relevant to the subset: `class`, `extends`, `implements`, `interface`, `static`, `public`, `private`, `protected`, `void`, `int`, `byte`, `char`, `short`, `boolean`, `if`, `else`, `while`, `do`, `for`, `switch`, `case`, `default`, `break`, `continue`, `return`, `new`, `null`, `this`, `throw`, `try`, `catch`, `finally`, `instanceof`, `true`, `false`, `native`, `super`. Punctuation: `{`, `}`, `(`, `)`, `[`, `]`, `;`, `,`, `.`, `=`, `==`, `!=`, `<`, `>`, `<=`, `>=`, `+`, `-`, `*`, `/`, `%`, `&`, `|`, `^`, `~`, `!`, `&&`, `||`, `<<`, `>>`, `>>>`, `?`, `:`, `++`, `--`. Compound assignments: `+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`, `<<=`, `>>=`, `>>>=`. Literals: `TOK_INT_LITERAL`, `TOK_STRING_LITERAL`, `TOK_CHAR_LITERAL`. Special: `TOK_IDENTIFIER`, `TOK_EOF`. Also holds the integer value, string value, and source line number of the current token.

**Lexer.java**: Reads source bytes one at a time via `Native.peek(sourceBase + offset)` where `sourceBase` is a known memory address (0xC000) and the source length is at 0xBFFE (2 bytes LE). Key methods: `nextToken()` returns the next token, `peekToken()` looks ahead without consuming. Handles: whitespace, single-line comments (`//`), multi-line comments (`/* */`), identifiers and keywords, integer literals (decimal, hex `0x`, binary `0b`), character literals (`'A'`, `'\n'`, `'\t'`, `'\\'`, `'\''`), string literals (`"hello"` with escape sequences), all operators and punctuation. On error: print line number and halt.

**Compiler.java**: Main class with `main(String[] args)`. Reads source length from memory, creates Lexer, runs Pass 1 (Catalog), Pass 2 (Resolve), Pass 3 (Emit), Pass 4 (Link). For Milestone 1, only the Lexer is functional; the compiler runs a token-dump mode that prints each token's type and value.

**Makefile**: Targets for building picojc.pjvm (via javac + pjvmpack), running tests, and cleaning.

**Verification**: Build picojc.pjvm, load T02_Literal.java into the picoJVM memory image at 0xC000, run picojc.pjvm on picoJVM, observe token output listing each token (keyword `public`, keyword `class`, identifier `T02_Literal`, etc.). The exact output format is not critical; any recognizable token dump proves the lexer works.

### Milestone 2: Catalog, Resolve, and Link Skeleton (Passes 1, 2, 4)

At the end of this milestone, picojc can survey a Java source file, build a symbol table, resolve all references, lay out the .pjvm metadata, and write a structurally valid .pjvm binary (with empty bytecodes). The binary has the correct header, class table, method table, and CP resolution table.

**What to build:**

**Pass 1 (Catalog)** in `Compiler.java` (or a dedicated `Cataloger` method): After lexing, walk through the token stream at the declaration level. On `class` or `interface`, record the class name, parent name (after `extends`), interface names (after `implements`). On field declarations, record name, type, static/instance. On method declarations, record name, return type, parameter types and count, static/instance/constructor, and the source byte offsets of the method body's `{` and `}`. Skip over method bodies by brace-counting (don't parse them yet). Output: populated `SymbolTable`.

**SymbolTable.java**: Arrays holding class entries, field entries, and method entries. Each class entry: classId, className, parentClassName, interfaceNames, sourceOffset. Each field entry: owningClassId, fieldName, fieldType, isStatic, slot (assigned in Pass 2). Each method entry: owningClassId, methodName, descriptor, argCount, bodyStartOffset, bodyEndOffset, isStatic, isConstructor, isNative, methodIndex (assigned in Pass 2), vtableSlot, vmid, maxLocals, maxStack. Built-in classes (Object, String, Native, Throwable, Exception, RuntimeException) are pre-populated with their native methods.

**Pass 2 (Resolve)** in `Resolver.java`: Topologically sorts classes (parent before child). Assigns class IDs (0, 1, 2, ...). Computes instance field layouts: parent fields come first, then child fields; each field gets a slot (object offset = 4 + slot * 4). Assigns static field slots (sequential). Builds vtables: start from parent's vtable, override matching methods, append new virtual methods. Assigns global method indices. Builds the CP resolution table: for each method, scans its body (via a lightweight second lex pass) to find all symbolic references (method calls, field accesses, class names for `new`/`instanceof`/`checkcast`, string literals, integer constants); assigns each a CP index and resolves it to a concrete value. Assigns interface vmids.

**Pass 4 (Link)** in `PjvmWriter.java`: Serializes the header, class table, method table, CP resolution table, integer constants, string constants, bytecodes (empty for now), and exception table (empty for now) into a byte sequence. Writes via `Native.poke()` to an output memory region (e.g., starting at 0x8000, with length written at 0x7FFE).

**Verification**: Compile T02_Literal.java with picojc. The resulting .pjvm should have: correct header (magic 0x85 0x4A, method count, main_mi, etc.), a class table with the user class and built-in classes, a method table with entries for main and native methods, and a CP resolution table. Run the .pjvm on picoJVM -- it will not produce output (bytecodes are empty), but it should not crash. Compare the header bytes against what pjvmpack.py produces for the same program.

### Milestone 3: Core Language Emission (Tier 1 Tests T01-T12)

At the end of this milestone, picojc compiles Tier 1 test programs and they produce correct output on picoJVM. This covers: empty programs, integer literals, arithmetic, local variables, if/else, while, for, static method calls (including recursion), integer arrays, byte arrays, bitwise operations, and string literals.

**What to build:**

**Pass 3 (Emit)** in `Emitter.java` and `Compiler.java` (or a `CodeGen` class): For each method, read its source span (from `bodyStartOffset` to `bodyEndOffset`), create a Lexer for that span, and perform recursive-descent parsing that directly emits bytecodes.

The recursive-descent parser implements these grammar productions:

`parseBlock()`: Parses `{ statement* }`. Each statement is dispatched by the leading token.

`parseStatement()`: Dispatches to specific handlers:
- Variable declaration (type + identifier): allocate local slot, parse optional initializer, emit ISTORE/ASTORE.
- `if`: parse condition, emit IFEQ to false branch, parse then-block, optionally emit GOTO + else-block.
- `while`: label_top, parse condition, emit IFEQ to end, parse body, emit GOTO to top.
- `for`: parse init-statement, label_top, parse condition, emit IFEQ to end, parse body, parse update, emit GOTO to top.
- `return`: parse expression (if non-void), emit IRETURN/ARETURN/RETURN.
- Expression statement: parse expression, emit POP if value is unused.

`parseExpression()`: Precedence-climbing for all operators. From lowest to highest precedence: ternary (`?:`), logical OR (`||`), logical AND (`&&`), bitwise OR (`|`), bitwise XOR (`^`), bitwise AND (`&`), equality (`==`, `!=`), comparison (`<`, `>`, `<=`, `>=`), shift (`<<`, `>>`, `>>>`), additive (`+`, `-`), multiplicative (`*`, `/`, `%`), unary (`-`, `~`, `!`, `++`, `--`, cast), postfix (`++`, `--`, `.field`, `.method()`, `[index]`), primary (literal, identifier, `this`, `null`, `new`).

For each binary operator, emit the left operand, then the right operand, then the operator bytecode (IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR). For comparisons, emit IF_ICMPXX with appropriate branching to push 0 or 1.

Integer literal emission: values -1 to 5 use ICONST_M1..ICONST_5; values -128 to 127 use BIPUSH; values -32768 to 32767 use SIPUSH; others use LDC with an integer constant pool entry.

Local variables: maintain a name-to-slot mapping per method. Slots 0..argCount-1 are parameters (slot 0 is `this` for instance methods). New declarations get the next available slot. Emit ILOAD_0..ILOAD_3 for slots 0-3, ILOAD idx for higher slots (similarly ISTORE, ALOAD, ASTORE).

Static method calls: push arguments, emit INVOKESTATIC with CP index resolving to the target method index.

Array operations: `new int[size]` emits size expression + NEWARRAY 10 (type code for int). `new byte[size]` emits NEWARRAY 8. `arr[i]` emits array ref + index + IALOAD/BALOAD. `arr[i] = val` emits array ref + index + value + IASTORE/BASTORE. `arr.length` emits ARRAYLENGTH.

String constants: add to string constant table (deduplicated), emit LDC with CP index `0x80 | string_index`. String method calls (length, charAt, equals, hashCode, toString) emit INVOKEVIRTUAL. `Native.print(s)` emits INVOKESTATIC.

Backpatching: forward branches (if-else, loop exits) emit 2 placeholder bytes and record the location in a backpatch list. When the target label is defined, compute `offset = target - branchOpPC` and write it as 2-byte signed big-endian into the placeholder.

**Emitter.java**: Holds a byte array buffer (~512 bytes) for the current method's bytecodes. Methods: `emitByte(int b)`, `emitShort(int s)` (big-endian), `currentPC()`, `patchBranch(int location, int targetPC)`. After each method, the buffer contents are appended to the global bytecode accumulator and the method's `code_offset` is set.

**max_locals and max_stack**: Pass 3 tracks these per method. max_locals = highest slot used + 1. max_stack is estimated conservatively: start at 0, increment for each push-like opcode, decrement for each pop-like opcode, track the maximum.

**Verification**: For each of T01 through T12, compile with picojc, run the resulting .pjvm on host picoJVM, and compare output against expected bytes:

- T01_Empty: (no output, just halts cleanly)
- T02_Literal: [42, 0, 255]
- T03_Arithmetic: [7, 7, 42, 10, 2, 5]
- T04_LocalVars: [30, 10]
- T05_IfElse: [1, 2, 3]
- T06_WhileLoop: [10, 5]
- T07_ForLoop: [120]
- T08_MethodCall: [7, 120]
- T09_IntArray: [10, 30, 50, 5]
- T10_ByteArray: [65, 66, 67, 68]
- T11_Bitwise: [15, 255, 240, 16, 32, 32]
- T12_StringLiteral: [72, 101, 108, 108, 111, 5, 101]

### Milestone 4: Object-Oriented Emission (Tier 2 Tests T13-T19)

At the end of this milestone, picojc handles object creation, instance fields, constructors with arguments, single inheritance with virtual method dispatch, static fields with static initializers, instanceof/checkcast, null references, and reference arrays.

**What to extend:**

**Object creation**: `new ClassName()` emits NEW with the class's CP index, DUP (so the reference is available after the constructor returns), then INVOKESPECIAL for the `<init>` constructor.

**Constructor chaining**: Constructors implicitly call `super()` (Object's no-op `<init>`, native ID 6). If the constructor has explicit `this.field = value` assignments, emit ALOAD_0 (this) + value + PUTFIELD.

**Instance field access**: `obj.field` emits the object reference + GETFIELD with the field's CP index (resolving to the field slot). `obj.field = value` emits object ref + value + PUTFIELD.

**Virtual method dispatch**: Instance method calls emit the receiver + arguments + INVOKEVIRTUAL with the method's CP index. The CP entry resolves to the method index; the interpreter uses the vtable at runtime.

**Static fields**: GETSTATIC and PUTSTATIC with CP indices resolving to the static field slot.

**Static initializers**: Classes with `static { ... }` or `static int x = 42;` get a `<clinit>` method (method index recorded in the class table entry). The Emit pass generates bytecode for the initializer body. picoJVM runs all `<clinit>` methods before main.

**instanceof**: Emit the object reference + INSTANCEOF with the class's CP index. Result is 0 or 1 on the stack.

**checkcast**: Emit the object reference + CHECKCAST with the class's CP index. Reference stays on stack (or traps if incompatible).

**Null references**: `null` literal emits ACONST_NULL. `if (x == null)` emits IFNULL. `if (x != null)` emits IFNONNULL.

**Reference arrays**: `new ClassName[size]` emits ANEWARRAY with class CP index. Element access uses AALOAD/AASTORE.

**Verification**: Tests T13 through T19 compile and produce correct output:

- T13_SimpleClass: [10, 20, 30]
- T14_Constructor: [21]
- T15_Inheritance: [68, 67]
- T16_StaticFields: [3, 100]
- T17_InstanceOf: [1, 1, 42]
- T18_NullRef: [1, 2]
- T19_ObjectArray: [60]

### Milestone 5: Advanced Features (Tier 3 Tests T20-T30)

At the end of this milestone, picojc handles interfaces, exceptions, switch statements (int and String), multi-dimensional arrays, do-while loops, break/continue, ternary expressions, stack manipulation patterns, and type conversions.

**What to extend:**

**Interface dispatch**: `implements` clauses are recorded in Pass 1. Pass 2 assigns vmids to interface methods and builds vtable entries accordingly. Calls through an interface reference emit INVOKEINTERFACE (4-byte instruction: opcode, CP index high, CP index low, arg count, 0).

**Exception handling**: `throw new ExType()` emits NEW + DUP + INVOKESPECIAL + ATHROW. `try { ... } catch (ExType e) { ... }` records exception table entries during emission: start_pc (beginning of try body), end_pc (end of try body), handler_pc (beginning of catch body), catch_class_id (from resolved class table). The catch handler stores the exception reference with ASTORE and parses the catch body. `finally` blocks emit a catch-all handler (catch_class = 0xFF) that ASTOREs the exception, executes the finally body, then ALOADs and ATHROWs to re-throw. The finally body is also emitted on the normal (non-exception) path.

Exception class hierarchy: If the user declares `class MyException extends RuntimeException`, picojc must ensure Throwable (class 0), Exception (class 1), and RuntimeException (class 2) exist in the class table, even if they have no user-declared methods or fields. These are synthesized as built-in classes alongside Object, String, and Native.

**Switch statements**: Integer switch with dense case values (range/count < 3) emits TABLESWITCH. Sparse cases emit LOOKUPSWITCH. picoJVM's TABLESWITCH format: padding to 4-byte alignment, 2-byte default offset, 2-byte low, 2-byte high, then (high-low+1) entries of 4 bytes each. LOOKUPSWITCH: padding, 2-byte default offset, 2-byte npairs, then npairs entries of 6 bytes each (4-byte key, 2-byte offset).

String switch follows the standard javac pattern: emit `s.hashCode()` (INVOKEVIRTUAL), LOOKUPSWITCH on hash values to candidate branches, at each candidate emit `s.equals("literal")` (INVOKEVIRTUAL), IFEQ to next candidate or default, GOTO to case body.

**Multi-dimensional arrays**: `new int[r][c]` emits dimensions + MULTIANEWARRAY (5 bytes: opcode, CP index high, CP index low, dimensions count). Nested access `grid[i][j]` is two successive AALOAD/IALOAD operations.

**Do-while**: label_top, parse body, parse condition, emit IFNE to top.

**Break/continue**: Maintain a stack of loop contexts (break target label, continue target label). `break` emits GOTO to break target. `continue` emits GOTO to continue target.

**Ternary**: Parse condition, IFEQ to false branch, parse true expression, GOTO to end, false label, parse false expression, end label.

**Pre/post increment**: `++x` emits ILOAD x, ICONST_1, IADD, DUP, ISTORE x (value after increment). `x++` emits ILOAD x, DUP, ICONST_1, IADD, ISTORE x (value before increment). For local variables, can use IINC as optimization.

**Type conversions**: `(byte)x` emits I2B. `(char)x` emits I2C. `(short)x` emits I2S.

**Verification**: Tests T20 through T30 compile and produce correct output:

- T20_Interface: [1, 100]
- T21_Exception: [1, 2, 3, 4, 5]
- T22_Switch: [10, 30, 0, 2, 0]
- T23_StringSwitch: [1, 2, 3, 0]
- T24_StringEquals: [1, 0, 1, 1]
- T25_MultiArray: [1, 5, 9, 3]
- T26_DoWhile: [5]
- T27_BreakContinue: [10, 12]
- T28_Ternary: [1, 0, 10]
- T29_StackOps: [42, 5, 6]
- T30_TypeConversion: [44, 65, 255]

### Milestone 6: Compatibility, Test Harness, and Simulator Integration (Tier 4 Tests T31-T36)

At the end of this milestone, picojc produces correct output for all 36 test programs, including the 6 compatibility tests that must match the existing picoJVM test suite output. The test harness (`run_tests.py`) automates compilation, execution, and output comparison.

**What to build:**

**Test programs**: Copy the spec's T01-T30 test sources into `tests/`. For T31-T36, use the existing picoJVM test sources (Fib.java, BubbleSort.java, Shapes.java, ExceptionTest.java, InterfaceTest.java, StringSwitchTest.java) directly from `tooling/picojvm/tests/`.

**Expected output files**: Generate `expected/TXX_Name.out` files containing the expected raw bytes for each test. For T31-T36, these match the existing picoJVM test outputs.

**run_tests.py**: For each test: (1) build picojc.pjvm if not up to date, (2) prepare a picoJVM memory image with the test source loaded at 0xC000, (3) run picoJVM with picojc.pjvm to produce the compiled .pjvm, (4) run picoJVM with the compiled .pjvm to produce output, (5) compare output against expected. Report PASS/FAIL with details.

**Comparison mode**: For T31-T36, also compile via javac + pjvmpack.py and compare outputs.

**Simulator integration**: For optional `--sim` mode, cross-compile picoJVM + .pjvm for 8085, run on i8085-trace, parse memory dump.

**Multi-class compilation**: Several Tier 4 tests (Shapes, ExceptionTest, InterfaceTest) have multiple classes in a single file or across files. picojc must handle multiple class declarations in a single source file (all classes in the spec's test programs are in one file).

**Verification**: `python3 run_tests.py` reports 36/36 passed. Comparison mode (`--compare`) confirms T31-T36 outputs match the reference pjvmpack.py pipeline.

### Milestone 7: Self-Hosting (picojc Compiles Itself)

At the end of this milestone, picojc can compile its own source code. The self-compiled version passes all tests identically to the javac-compiled version.

**What to verify:**

1. **Phase 2**: Use javac-compiled picojc.pjvm (running on host picoJVM) to compile picojc's source files, producing picojc_self.pjvm.
2. **Equivalence**: Run all 36 tests through picojc_self.pjvm and verify outputs match.
3. **Phase 3 (stretch goal)**: Use picojc_self.pjvm to compile picojc source again, producing picojc_self2.pjvm. Verify it is byte-identical to picojc_self.pjvm (fixed point).

This milestone requires no new language features -- it validates that the compiler correctly handles all the patterns used in its own source code (classes, arrays, strings, switches, static methods, etc.). Any discrepancies reveal compiler bugs.

**Verification**: `make self-host` succeeds, producing a self-compiled picojc that passes all tests.


## Concrete Steps

The following commands assume the working directory is `tooling/picojvm/picojc/` unless stated otherwise.

### Building picojc

    # Ensure host picoJVM is built
    cd tooling/picojvm && make && cd picojc

    # Build picojc.pjvm via javac + pjvmpack (bootstrap)
    make picojc.pjvm

This compiles `src/*.java` with javac, then runs pjvmpack.py on the resulting .class files, producing `picojc.pjvm`.

### Running a Single Test (Manual)

    # 1. Prepare the test: load source into memory image and run picojc
    #    (run_tests.py automates this, but manually:)

    # Compile a test Java file through picojc
    ./run_tests.py --filter T02 --verbose

    # Expected output:
    #   [PASS] T02_Literal    expected [42, 0, 255], got [42, 0, 255]

### Running All Tests

    make test
    # Expected: 36/36 passed

### Running Comparison Tests

    make test-compare
    # Expected: All Tier 4 tests show matching outputs between picojc and pjvmpack

### Running on 8085 Simulator

    make test-sim
    # Expected: All tests show halt="hlt" and correct output bytes in memory dump

These commands will be updated as each milestone is completed and the exact invocations are verified.


## Validation and Acceptance

The ultimate acceptance criterion is: `make test` in `tooling/picojvm/picojc/` runs all 36 test programs through picojc and picoJVM, and all 36 produce the exact expected byte sequences.

For each milestone, intermediate acceptance:

- **Milestone 1**: Lexer tokenizes T02_Literal.java. Running picojc in token-dump mode on picoJVM prints recognizable tokens (keywords, identifiers, literals, punctuation).

- **Milestone 2**: picojc produces a .pjvm file for T02_Literal.java. The file has a valid header (hex dump shows magic 0x85 0x4A, correct field values). Running it on picoJVM does not crash (but produces no output since bytecodes are empty).

- **Milestone 3**: Tests T01-T12 produce correct output. Run each on picoJVM and compare bytes.

- **Milestone 4**: Tests T13-T19 produce correct output.

- **Milestone 5**: Tests T20-T30 produce correct output.

- **Milestone 6**: All 36 tests pass. Test harness reports 36/36. Tier 4 comparison mode shows outputs match pjvmpack.py reference.

- **Milestone 7**: Self-compiled picojc passes all 36 tests. Fixed-point test (compiling itself twice produces identical output) passes.


## Idempotence and Recovery

All build steps are idempotent. `make clean && make picojc.pjvm` rebuilds from scratch. `make test` can be re-run at any time. Test files are read-only inputs; output .pjvm files are generated into a build directory and can be safely deleted.

If a milestone is partially complete, the test harness reports which tests pass and which fail. Work can resume from any point by examining the test results and fixing the next failing test.

The source of truth is always the Java source files in `src/`. The bootstrap artifacts (picojc.pjvm) can be regenerated from source at any time via `make picojc.pjvm`.


## Artifacts and Notes

### Key Constants

Source buffer memory address: 0xC000 (source bytes loaded here by test harness)
Source length address: 0xBFFE (2 bytes LE)
Output buffer memory address: 0x8000 (picojc writes .pjvm here)
Output length address: 0x7FFE (2 bytes LE, written by picojc after completion)

### .pjvm Header Example (T02_Literal)

Expected header for T02_Literal.java (approximate, verify against pjvmpack.py):

    Offset  Value   Field
    0       0x85    magic_hi
    1       0x4A    magic_lo
    2       0x08    n_methods (main + 7 native methods for Native class + Object.<init>)
    3       0x07    main_mi (index of main method)
    4       0x00    n_static
    5       0x00    n_integers
    6       0x03    n_classes (Object, Native, T02_Literal)
    7       0x00    n_strings
    8-9     ????    bytecodes_size (depends on emitted code)

The exact values depend on how built-in classes and methods are laid out. Verify against `pjvmpack.py -v` output for the same program.

### picoJVM Output Format

When picoJVM runs a .pjvm file on the host, `Native.putchar(42)` outputs byte value 42. The host platform shim displays output as text: `picoJVM | *` (42 = ASCII `*`). The test harness must parse these output lines to extract the actual byte values.


## Interfaces and Dependencies

### Token.java

    public class Token {
        // Token type constants (static final int)
        public static final int TOK_EOF = 0;
        public static final int TOK_IDENTIFIER = 1;
        public static final int TOK_INT_LITERAL = 2;
        public static final int TOK_STRING_LITERAL = 3;
        public static final int TOK_CHAR_LITERAL = 4;
        // Keywords: TOK_CLASS, TOK_EXTENDS, TOK_IMPLEMENTS, TOK_INTERFACE, ...
        // Operators: TOK_PLUS, TOK_MINUS, TOK_STAR, TOK_SLASH, ...
        // Punctuation: TOK_LBRACE, TOK_RBRACE, TOK_LPAREN, TOK_RPAREN, ...

        // Current token state
        public static int type;
        public static int intValue;
        public static int line;
        // String value stored in a byte array (no java.lang.String manipulation)
        public static byte[] stringValue;
        public static int stringLength;
    }

Using static fields (not instance fields) because picoJVM's memory model favors static access patterns and there is only ever one token being processed at a time.

### Lexer.java

    public class Lexer {
        static int srcBase;      // Memory address of source start (0xC000)
        static int srcLength;    // Source length in bytes
        static int pos;          // Current read position
        static int line;         // Current line number

        public static void init(int base, int length);
        public static void nextToken();   // Advances to next token, sets Token fields
        public static int peekChar();     // Look at next byte without consuming
        public static void error(int messageCode);  // Print error and halt
    }

### SymbolTable.java

    public class SymbolTable {
        // Class table
        static int classCount;
        static int[] classParentId;     // indexed by classId
        static int[] classVtableSize;
        static int[] classClinitMi;     // 0xFF = none
        static int[] classFieldCount;   // instance fields

        // Field table
        static int fieldCount;
        static int[] fieldClassId;
        static int[] fieldSlot;         // assigned in Pass 2
        static boolean[] fieldIsStatic;

        // Method table
        static int methodCount;
        static int[] methodClassId;
        static int[] methodArgCount;
        static int[] methodMaxLocals;
        static int[] methodMaxStack;
        static int[] methodFlags;       // native encoding
        static int[] methodCodeOffset;
        static int[] methodCpBase;
        static int[] methodVtableSlot;
        static int[] methodVmid;
        static int[] methodExcCount;
        static int[] methodExcIdx;
        static int[] methodBodyStart;   // source offset
        static int[] methodBodyEnd;     // source offset

        // Name storage: parallel byte arrays for class/field/method names
        // (no String objects to save memory)
    }

### Emitter.java

    public class Emitter {
        static byte[] code;             // Current method bytecode buffer
        static int codeLen;             // Current length
        static byte[] globalCode;       // All methods' bytecodes concatenated
        static int globalCodeLen;

        public static void beginMethod();
        public static void endMethod(int methodIndex);  // Copies to global, sets code_offset
        public static void emitByte(int b);
        public static void emitShort(int s);  // Big-endian
        public static int currentPC();
        public static int emitBranchPlaceholder();  // Returns patch location
        public static void patchBranch(int location);  // Patches to currentPC
    }

### PjvmWriter.java

    public class PjvmWriter {
        static int outBase;    // Output memory address (0x8000)
        static int outPos;     // Current write position

        public static void init(int base);
        public static void writeByte(int b);
        public static void writeShortLE(int s);   // Little-endian
        public static void writeHeader();
        public static void writeClassTable();
        public static void writeMethodTable();
        public static void writeCPTable();
        public static void writeIntConstants();
        public static void writeStringConstants();
        public static void writeBytecodes();
        public static void writeExceptionTable();
        public static void writeAll();  // Calls all of the above in order
    }

### Compiler.java

    public class Compiler {
        public static void main(String[] args) {
            // Read source length from 0xBFFE
            int srcLen = Native.peek(0xBFFE) | (Native.peek(0xBFFF) << 8);
            Lexer.init(0xC000, srcLen);

            // Pass 1: Catalog
            catalog();

            // Pass 2: Resolve
            Resolver.resolve();

            // Pass 3: Emit
            emit();

            // Pass 4: Link
            PjvmWriter.init(0x8000);
            PjvmWriter.writeAll();

            // Write output length at 0x7FFE
            int outLen = PjvmWriter.outPos - PjvmWriter.outBase;
            Native.poke(0x7FFE, outLen & 0xFF);
            Native.poke(0x7FFF, (outLen >> 8) & 0xFF);

            Native.halt();
        }
    }

### run_tests.py (Python)

    Key function signatures:

    def compile_with_picojc(java_source_path, picojc_pjvm_path, picojvm_binary) -> bytes:
        """Compile a .java file with picojc, returns the .pjvm bytes."""

    def run_pjvm(pjvm_bytes, picojvm_binary) -> bytes:
        """Run a .pjvm file on host picoJVM, returns output bytes."""

    def run_test(test_name, expected_bytes) -> bool:
        """Compile and run a test, compare output against expected."""

### Dependencies

- Java 8+ (javac) for bootstrap compilation
- Python 3 for pjvmpack.py and run_tests.py
- Host C compiler (cc) for building picoJVM host binary
- i8085-trace simulator (optional, for --sim tests)
- No external Java libraries (picojc uses only the picoJVM-supported Java subset)
