# picoJVM Improvements — Platform Package & Native Kernel Extensions

## Architecture

Three layers:

```
User code (Compiler.java, application code)
    |
Platform package (Java source, ships with picojc)
    |
Native kernel (core.c, per-platform callbacks)
```

The native kernel stays minimal — only operations that can't be expressed in
Java. The platform package is pure Java built on native ops. It ships as `.java`
source files alongside picojc and gets concatenated onto user source before
compilation.

Platform callbacks (`pjvm_platform_putchar`, `r8`, `w8`, etc.) remain the
per-platform abstraction — identical to today. The native kernel handlers in
core.c are platform-independent bytecode semantics.

---

## 1. Native Kernel Extensions (core.c)

### 1a. `arraycopy` — bulk byte[] copy

**STATUS: DONE**

Native ID: 13

```java
// Native.java
public static native void arraycopy(byte[] src, int srcOff, byte[] dst, int dstOff, int len);
```

```c
// core.c
case NATIVE_ARRAYCOPY: {
    uint16_t len    = spop_lo(); spop_hi();
    uint16_t dstOff = spop_lo(); spop_hi();
    uint16_t dst    = spop_lo();
    uint16_t srcOff = spop_lo(); spop_hi();
    uint16_t src    = spop_lo();
    for (uint16_t i = 0; i < len; i++)
        w8(dst + 4 + dstOff + i, r8(src + 4 + srcOff + i));
    break;
}
```

Eliminates ~12 manual byte-copy loops in the compiler (name interning, string
constant copying, Token.strBuf save/restore, keyword init, mcode-to-raw
transfer).

**NOTES:** Added comment noting forward-copy only — overlapping regions with
dst > src will corrupt. All expected use cases are non-overlapping. Also
registered in pjvmpack.py NATIVE_IDS as `"arraycopy": 13`.

### 1b. `memcmp` — bulk byte[] comparison

**STATUS: DONE**

Native ID: 14

```java
// Native.java
public static native int memcmp(byte[] a, int aOff, byte[] b, int bOff, int len);
```

Returns signed difference of first mismatched byte (< 0, 0, or > 0).

```c
// core.c
case NATIVE_MEMCMP: {
    uint16_t len  = spop_lo(); spop_hi();
    uint16_t bOff = spop_lo(); spop_hi();
    uint16_t bref = spop_lo();
    uint16_t aOff = spop_lo(); spop_hi();
    uint16_t aref = spop_lo();
    int32_t result = 0;
    for (uint16_t i = 0; i < len; i++) {
        uint8_t av = r8(aref + 4 + aOff + i);
        uint8_t bv = r8(bref + 4 + bOff + i);
        if (av != bv) { result = (int32_t)av - (int32_t)bv; break; }
    }
    pjvm_push32(result);
    break;
}
```

Eliminates ~4 manual byte-comparison loops (internStr, internBuf,
allocStringCP, Lexer.strEquals).

**NOTES:** Updated doc from "returns 0 if equal, nonzero otherwise" to
accurately describe the three-way signed difference semantics. Uses existing
`pjvm_push32()` helper (already existed in core.c for hashCode). Registered
in pjvmpack.py as `"memcmp": 14`.

### 1c. `writeBytes` — bulk byte[] to stdout

**STATUS: DONE**

Native ID: 15

```java
// Native.java
public static native void writeBytes(byte[] buf, int off, int len);
```

```c
// core.c
case NATIVE_WRITE_BYTES: {
    uint16_t len = spop_lo(); spop_hi();
    uint16_t off = spop_lo(); spop_hi();
    uint16_t ref = spop_lo();
    for (uint16_t i = 0; i < len; i++)
        pjvm_platform_putchar(r8(ref + 4 + off + i));
    break;
}
```

Uses the existing `pjvm_platform_putchar` callback — no new platform
abstraction needed. Replaces byte-at-a-time output loops in writeOutput()
(~19K putchar calls when self-hosting → 1 native call per section).

**NOTES:** Implemented as specified. Registered in pjvmpack.py as
`"writeBytes": 15`.

---

## 2. `new String(byte[], int, int)` Constructor

**STATUS: DONE**

Native ID: 16

Exposed as a static native method `Native.stringFromBytes(byte[], int, int)`
rather than a constructor:

```java
// Native.java
public static native String stringFromBytes(byte[] src, int off, int len);
```

```java
// Usage:
String s = Native.stringFromBytes(data, 0, len);
```

Implementation in core.c — allocate a string object on the heap and copy bytes:

```c
case NATIVE_STRING_FROM_BYTES: {
    uint16_t len = spop_lo(); spop_hi();
    uint16_t off = spop_lo(); spop_hi();
    uint16_t src = spop_lo();
    uint16_t a = heap_alloc(g_pjvm, (uint16_t)(4 + len));
    w16(a, len); w16((uint16_t)(a + 2), 0);
    for (uint16_t i = 0; i < len; i++)
        w8(a + 4 + i, r8(src + 4 + off + i));
    spush(a, 0);
    break;
}
```

### Impact on Compiler.java

This is the single highest-value addition. It enables replacing the entire
name interning system:

**Before** (~50 lines, 3 arrays):
```java
static byte[] namePool = new byte[10240];
static int[] nameOff = new int[MAX_NAMES];
static int[] nameLen = new int[MAX_NAMES];

static int internStr(String s) {
    int len = s.length();
    for (int i = 0; i < nameCount; i++) {
        if (nameLen[i] == len) {
            boolean match = true;
            for (int j = 0; j < len; j++) {
                if (namePool[nameOff[i] + j] != (byte)s.charAt(j)) {
                    match = false; break;
                }
            }
            if (match) return i;
        }
    }
    // ... copy into namePool ...
}

static int internBuf(byte[] buf, int len) {
    // ... nearly identical to internStr ...
}
```

**After** (~10 lines, 1 array):
```java
static String[] names = new String[MAX_NAMES];

static int intern(String s) {
    for (int i = 0; i < nameCount; i++)
        if (names[i].equals(s)) return i;
    names[nameCount] = s;
    return nameCount++;
}
```

Call sites change from `internBuf(Token.strBuf, Token.strLen)` to
`intern(Native.stringFromBytes(Token.strBuf, 0, Token.strLen))`.

The 5 occurrences of the Token.strBuf restore pattern:
```java
Token.strLen = nameLen[nm];
for (int j = 0; j < Token.strLen; j++)
    Token.strBuf[j] = namePool[nameOff[nm] + j];
```
Each becomes: `Token.str = names[nm];` (one line, no loop).

**Estimated savings: ~70 lines**, plus elimination of namePool/nameOff/nameLen
arrays (~30KB of heap saved).

### Compiler support

picojc needs to recognize `Native.stringFromBytes()` the same way it handles
`Native.putchar()` etc. — `ensureNative` adds a native method entry when the
call is encountered.

**NOTES:** Changed from `new String(byte[], int, int)` constructor form to
`Native.stringFromBytes()` static method. This avoids needing special
constructor-interception logic in pjvmpack.py and picojc — it's just another
entry in NATIVE_IDS, same dispatch path as all other natives. The call syntax
is `Native.stringFromBytes(buf, off, len)` instead of
`new String(buf, off, len)`. Marginally less pretty but vastly simpler to
implement in both the packer and the compiler. Registered in pjvmpack.py as
`"stringFromBytes": 16`.

---

## 3. Platform Package (Java Source)

**STATUS: DONE**

These are pure Java files that ship with picoJVM programs. They get compiled
alongside user source. No JVM changes needed — they're ordinary Java built on
the native kernel.

Located in `lib/` directory (not `platform/` which holds C platform shims).

### Console.java

```java
public class Console {
    public static void print(int n) {
        if (n == -2147483648) {
            Native.print("-2147483648");
            return;
        }
        if (n < 0) { Native.putchar('-'); n = -n; }
        if (n >= 10) print(n / 10);
        Native.putchar('0' + (n % 10));
    }

    public static void println(int n) {
        print(n);
        Native.putchar('\n');
    }

    public static void print(String s) {
        Native.print(s);
    }

    public static void println(String s) {
        Native.print(s);
        Native.putchar('\n');
    }

    public static void println() {
        Native.putchar('\n');
    }

    public static void printHex(int v, int digits) {
        for (int i = digits - 1; i >= 0; i--) {
            int nib = (v >> (i * 4)) & 0xF;
            Native.putchar(nib < 10 ? '0' + nib : 'A' + nib - 10);
        }
    }
}
```

Replaces `Lexer.printNum()` and ad-hoc print loops in user code.

**NOTES:** Added `Integer.MIN_VALUE` guard to `print(int n)` — the original
spec's `n = -n` overflows for `-2147483648`. Uses `Native.print()` for the
string fallback. `Console.print(String s)` delegates to `Native.print(s)`
(native bulk output) rather than char-at-a-time loop.

### Arrays.java

```java
public class Arrays {
    public static boolean equals(byte[] a, int aOff, byte[] b, int bOff, int len) {
        return Native.memcmp(a, aOff, b, bOff, len) == 0;
    }

    public static int compare(byte[] a, int aOff, byte[] b, int bOff, int len) {
        return Native.memcmp(a, aOff, b, bOff, len);
    }

    public static void copy(byte[] src, int srcOff, byte[] dst, int dstOff, int len) {
        Native.arraycopy(src, srcOff, dst, dstOff, len);
    }

    public static void fill(byte[] a, int off, int len, byte val) {
        for (int i = 0; i < len; i++) a[off + i] = val;
    }

    public static void fill(int[] a, int off, int len, int val) {
        for (int i = 0; i < len; i++) a[off + i] = val;
    }
}
```

**NOTES:** Added `Arrays.compare()` — exposes the full three-way comparison
result from `memcmp`, not just equality. Useful for sorted data structures.

### IO.java

```java
public class IO {
    public static void writeShortLE(int v) {
        Native.putchar(v & 0xFF);
        Native.putchar((v >> 8) & 0xFF);
    }

    public static void writeIntLE(int v) {
        writeShortLE(v);
        writeShortLE(v >> 16);
    }

    public static void writeBytes(byte[] buf, int off, int len) {
        Native.writeBytes(buf, off, len);
    }

    public static void writeByte(int b) {
        Native.putchar(b & 0xFF);
    }
}
```

Replaces `Compiler.writeByte()`, `Compiler.writeShortLE()`, and the inline
endian-conversion patterns throughout writeOutput().

**NOTES:** Implemented as specified. No changes from plan.

---

## Implementation Order

1. **Add native ops to core.c**: arraycopy (13), memcmp (14), writeBytes (15).
   These are ~20 lines of C each, use existing r8/w8/putchar callbacks.
   No platform changes needed.
   **STATUS: DONE**

2. **Add `stringFromBytes`** (16) to core.c.
   **STATUS: DONE** — implemented as `Native.stringFromBytes()` static native
   rather than `new String(byte[], int, int)` constructor.

3. **Create platform package**: Console.java, Arrays.java, IO.java as source
   files in `lib/`. Available for user code to compile against.
   **STATUS: DONE** — placed in `lib/` (not `picojc/platform/` — that path
   collides with the C platform shims in `platform/`).

4. **Refactor Compiler.java** to use the new natives and deduplicate code.
   **STATUS: DONE** — internStr delegates to internBuf via temp byte[],
   internBuf/allocStringCP use memcmp+arraycopy, Token.strBuf restores use
   arraycopy, writeOutput uses writeBytes for bulk sections, commitMethodCode
   helper eliminates 3x duplicated mcode/CP commit blocks. namePool kept as
   flat byte pool (String[]-based interning rejected: no GC means every
   stringFromBytes in hot internBuf path would leak permanent heap objects).

5. **Verify self-hosting**: gen1==gen2 must still hold after refactoring.
   **STATUS: DONE** — gen1==gen2 at 29,630 bytes.

---

## Test Coverage

Two test files exercise all new functionality:

- **picojc/tests/T39_NativeOps.java** — directly tests arraycopy, memcmp,
  writeBytes, and stringFromBytes. Runs as a standard picojc test.
- **picojc/tests/T40_PlatformPkg.java** — tests Console.print/println/printHex,
  Arrays.equals/copy/compare/fillBytes/fillInts, and IO.writeByte/writeBytes.
  Compiled with lib/*.java concatenated (via `make test-lib-T40_PlatformPkg`).

38/38 tests pass (36 original + T39_NativeOps + T40_PlatformPkg).
Self-hosting gen1==gen2 at 29,630 bytes.

---

## Notes

- The native kernel (Native.java) should stay minimal — only operations that
  can't be done in Java. Everything else belongs in the platform package.

- The platform package is pure Java. It compiles to picoJVM bytecodes and runs
  on any picoJVM target (host, 8085 sim, 8085 hardware). No per-platform
  ifdefs.

- The existing Native methods (putchar, peek, poke, in, out, halt, print, and
  the String methods length/charAt/equals/toString/hashCode) remain unchanged.

- On the 8085 target, the bulk native ops (arraycopy, memcmp, writeBytes)
  will be especially valuable — they execute as tight C loops compiled to
  native code, not interpreted JVM bytecodes. A single writeBytes call
  replacing 19K interpreted putchar calls is a major performance win.

- `stringFromBytes` was changed from constructor to static native. This is a
  deliberate simplification — pjvmpack.py's native method resolution works by
  matching `ref_class == "Native"` and looking up the method name in
  `NATIVE_IDS`. A constructor form would require special-casing
  `java/lang/String.<init>([BII)V` in the methodref resolution path, which is
  more complex and fragile.

- **No method overloading in platform package or user code.** picojc resolves
  methods by name only (no signature-based overload resolution). All method
  names must be unique within a class. Console uses `print(int)` and
  `println(int)` for integers, `newline()` for bare newline. Arrays uses
  `fillBytes(byte[])` and `fillInts(int[])` instead of overloaded `fill()`.
  String output uses `Native.print(s)` directly.
