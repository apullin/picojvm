.DELETE_ON_ERROR:

CC      = cc
CFLAGS  = -Wall -Wextra -O2
HOST_VM_DEBUG = -DPJVM_DEBUG_TOOLS
HOST_VM_FEATURES = -DPJVM_USE_CONST_STRING_ARRAYS=1 -DPJVM_USE_CONST_OBJECT_ARRAYS=1
HOST_VM_OPTS ?= $(HOST_VM_FEATURES)
JAVAC   = javac
JAVAC8FLAGS ?= -source 8 -target 8
PYTHON  = python3
PICOJVM = ./picojvm
PICOJVM_LARGE = ./picojvm-large
PJVM_HEADERS = src/pjvm.h src/pjvm_opts.h
EXPDIR  = expected
GC_DEMO_MANUAL    = $(BUILDDIR)/gc-policy-demo-manual
GC_DEMO_ALLOCFAIL = $(BUILDDIR)/gc-policy-demo-allocfail
GC_DEMO_WATERMARK = $(BUILDDIR)/gc-policy-demo-watermark75
GC_DEMO_RETURN    = $(BUILDDIR)/gc-policy-demo-return
GC_DEMO_RANDOM    = $(BUILDDIR)/gc-policy-demo-random
GC_COLLECT_TEST   = $(BUILDDIR)/gc-collect-test
GC_FRAGMENT_TEST  = $(BUILDDIR)/gc-fragment-test
GC_EXACT_TEST     = $(BUILDDIR)/gc-exact-test

# Single-class tests
TESTS_SINGLE = Fib HelloWorld BubbleSort Counter StringTest RomStringTest StringApiSmoke StringShimTest NativeOpsTest StaticInitTest MultiArrayTest StringSwitchTest VmHardeningTest ConstTest ConstStringArrayTest ConstObjectArrayTest ConstNarrowingTest TermSmoke FilesSmoke PicoJseStdSmoke JavaLangSmoke StringConcatSmoke TarSmoke ZipSmoke ZipDeflateSmoke
TESTS_MULTI  = Shapes Features InterfaceTest ExceptionTest EnumBasicTest EnumShimTest ThrowableShimTest
TESTS_PAGER  = BigSwitch BigLUT
ALL_TESTS    = $(TESTS_SINGLE) $(TESTS_MULTI)
ALL_TESTS_PAGER = $(ALL_TESTS) $(TESTS_PAGER)

# picoJSE package rule:
# - standard Java-compatible surfaces live under java.*
# - target/runtime substrate and nonstandard helpers live under pj.*
PICOJSE_JAVA_SRCS = java/lang/Boolean.java \
                    java/lang/Byte.java \
                    java/lang/Character.java \
                    java/lang/Enum.java \
                    java/lang/Exception.java \
                    java/lang/Integer.java \
                    java/lang/Math.java \
                    java/lang/RuntimeException.java \
                    java/lang/Short.java \
                    java/lang/String.java \
                    java/lang/StringBuilder.java \
                    java/lang/System.java \
                    java/lang/Throwable.java

PICOJSE_PJ_SRCS = pj/Native.java \
                  pj/archive/Tar.java \
                  pj/archive/Zip.java \
                  pj/archive/ZipRead.java \
                  pj/archive/ZipWrite.java \
                  pj/archive/ZipDeflateBits.java \
                  pj/archive/ZipDeflate.java \
                  pj/archive/ZipHuffman.java \
                  pj/archive/ZipInflate.java \
                  pj/archive/ZipTables.java \
                  pj/io/Console.java \
                  pj/io/Files.java \
                  pj/io/Binary.java \
                  pj/io/TextWriter.java \
                  pj/term/Keys.java \
                  pj/term/Terminal.java \
                  pj/term/CellSurface.java \
                  pj/term/AnsiTerminal.java \
                  pj/term/Draw.java \
                  pj/util/Bytes.java \
                  pj/util/Ints.java \
                  pj/text/Format.java \
                  pj/text/Parse.java \
                  pj/text/Strings.java

PICOJSE_SRCS = $(PICOJSE_JAVA_SRCS) $(PICOJSE_PJ_SRCS)
PICOJSE_CLASSDIR = $(BUILDDIR)/picojse-classes

# --- 8085 target toolchain ---
ROOT     = $(shell cd ../.. && pwd)
GC_DEFAULT_OPTS = -DPJVM_HEAP_MODE=PJVM_HEAP_FREELIST -DPJVM_GC_TRIGGERS=3 -DPJVM_GC_WATERMARK_PCT=75
GC_HOST_PRESSURE_LIMIT ?= 2049
GC_SIM_OUTPUT_BASE ?= 0xE000
GC_SIM_SMOKE_HEAP_END ?= 0xE000
GC_SIM_HEAP_END ?= 0xE000
GC_SIM_TRAP_BASE ?= 0xE080
GC_SIM_FLAT_LDSCRIPT = ldscripts/i8085-64k-flat-boot.ld
GC_SIM_SMOKE_OPTS = $(GC_DEFAULT_OPTS) -DPJVM_SIM_HEAP_END=$(GC_SIM_SMOKE_HEAP_END) -DPJVM_SIM_OUTPUT_BASE=$(GC_SIM_OUTPUT_BASE) -DPJVM_SIM_TRAP_BASE=$(GC_SIM_TRAP_BASE)
GC_SIM_STRESS_OPTS = $(GC_DEFAULT_OPTS) -DPJVM_SIM_HEAP_END=$(GC_SIM_HEAP_END) -DPJVM_SIM_OUTPUT_BASE=$(GC_SIM_OUTPUT_BASE) -DPJVM_SIM_TRAP_BASE=$(GC_SIM_TRAP_BASE)
CLANG    = $(ROOT)/llvm-project/build-clang-8085/bin/clang
LLD      = $(ROOT)/llvm-project/build-clang-8085/bin/ld.lld
OBJCOPY  = $(ROOT)/llvm-project/build-clang-8085/bin/llvm-objcopy
SIZE     = $(ROOT)/llvm-project/build-clang-8085/bin/llvm-size
TRACE    = $(ROOT)/i8085-trace/build/i8085-trace
SIM_VERIFY = $(ROOT)/tooling/examples/verify_dump.py
CRT      = $(ROOT)/sysroot/crt/crt0.S
LIBGCC   = $(ROOT)/sysroot/lib/libgcc.a
LIBC     = $(ROOT)/sysroot/lib/libc.a
# 64K flat map with the boot overlay: the .pjvm loader links inside the
# heap window and is reclaimed after it runs (it executes once, before
# pjvm_heap_init). The VM image (~35K+ with hardening + GC) no longer
# fits the 32K ROM split; real-hardware footprint validation lives in
# the outer pjvm8085-asm suite.
LDSCRIPT = ldscripts/i8085-64k-flat-boot.ld
TARGET_OPT = Oz
# I8085 MachineOutliner (implemented in the local LLVM backend, stages 1-3:
# conservative + SP-relative repair + tail-outlining). Roughly -30% VM text;
# validated by the sim suites. TARGET_OUTLINE=0 disables.
TARGET_OUTLINE ?= 1
ifeq ($(TARGET_OUTLINE),1)
TARGET_OUTLINE_FLAGS = -mllvm -enable-machine-outliner=always
else
TARGET_OUTLINE_FLAGS =
endif
# Full LTO for 8085 target builds: C objects carry bitcode and codegen runs
# at link time over one merged module, so the outliner sees cross-TU repeats
# (~-2.6K ROM, ~+2.2K heap on the flagship sim). Codegen -mllvm flags must
# reach the LINKER. Requires an lld built from the same backend tree as
# clang: a stale lld silently codegens without the outliner hooks.
TARGET_LTO ?= 0
ifeq ($(TARGET_LTO),1)
TARGET_LTO_CFLAGS = -flto
TARGET_LTO_LDFLAGS = $(TARGET_OUTLINE_FLAGS)
else
TARGET_LTO_CFLAGS =
TARGET_LTO_LDFLAGS =
endif
BUILDDIR = build
TARGET_VM_OPTS ?=
TARGET_ASM_HELPERS ?= 1
SIM_DUMP_ADDR ?= 0xE000
PJVM_TAG = $(if $(PJVM_FILE),$(basename $(notdir $(PJVM_FILE))),pjvm)
PJVM_DATA_C = $(BUILDDIR)/$(PJVM_TAG)_data.c
PJVM_DATA_O = $(BUILDDIR)/$(PJVM_TAG)_data.o
TARGET_ELF = $(BUILDDIR)/$(PJVM_TAG).elf
TARGET_BIN = $(BUILDDIR)/$(PJVM_TAG).bin
ifeq ($(TARGET_ASM_HELPERS),1)
TARGET_ASM_HELPERS_DEF = -DPJVM_ASM_HELPERS
TARGET_HELPER_OBJS = $(BUILDDIR)/i8085_helpers.o
else
TARGET_ASM_HELPERS_DEF =
TARGET_HELPER_OBJS =
endif

PICOJVM_PAGED = ./picojvm-paged
HOST_VM_LARGE_OPTS ?= -DPJVM_ENABLE_V4=1 -DPJVM_METHOD_CAP=20000 \
                      -DPJVM_CLASS_CAP=2048 -DPJVM_VTABLE_CAP=20000 \
                      -DPJVM_STATIC_CAP=12000 -DPJVM_MAX_STACK=4096 \
                      -DPJVM_MAX_LOCALS=8192 -DPJVM_MAX_FRAMES=512

# Capacity overrides for 8085 target (smaller than host defaults)
SIM_CAPS = -DPJVM_METHOD_CAP=64 -DPJVM_CLASS_CAP=16 -DPJVM_VTABLE_CAP=128 \
           -DPJVM_STATIC_CAP=32 -DPJVM_MAX_STACK=64 -DPJVM_MAX_LOCALS=128 \
           -DPJVM_MAX_FRAMES=16 -DPJVM_BOOT_OVERLAY=1

all: $(PICOJVM)

# Host binaries depend on the flags they were built with: a stamp file is
# rewritten whenever HOST_VM_OPTS change, so test targets that override the
# options (GC pressure configs, etc.) can't leave a stale ./picojvm behind.
HOST_VM_ALLFLAGS = $(CFLAGS) $(HOST_VM_DEBUG) $(HOST_VM_OPTS)
$(BUILDDIR)/.hostvm.flags: FORCE | $(BUILDDIR)
	@printf '%s\n' '$(HOST_VM_ALLFLAGS)' | cmp -s - $@ 2>/dev/null || \
		{ printf '%s\n' '$(HOST_VM_ALLFLAGS)' > $@; \
		  rm -f $(PICOJVM) $(PICOJVM_LARGE) $(PICOJVM_PAGED); }

$(PICOJVM): src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c platform/host.c $(PJVM_HEADERS) $(BUILDDIR)/.hostvm.flags
	$(CC) $(CFLAGS) $(HOST_VM_DEBUG) $(HOST_VM_OPTS) -DPJVM_MAX_FRAMES=128 -o $@ src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c platform/host.c

$(PICOJVM_LARGE): src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c platform/host.c $(PJVM_HEADERS) $(BUILDDIR)/.hostvm.flags
	$(CC) $(CFLAGS) $(HOST_VM_DEBUG) $(HOST_VM_OPTS) $(HOST_VM_LARGE_OPTS) -o $@ src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c platform/host.c

$(PICOJVM_PAGED): src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c platform/host.c $(PJVM_HEADERS) $(BUILDDIR)/.hostvm.flags
	$(CC) $(CFLAGS) $(HOST_VM_DEBUG) $(HOST_VM_OPTS) -DPJVM_PAGED -o $@ src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c platform/host.c

# Test compiles resolve java.lang.* to the shims in java/lang/ via javac's
# implicit sourcepath, so a shim change must recompile every test class.
# All test-class rules depend on tests/Native.java: touching it when a shim
# source changes re-fires them without editing each rule.
tests/Native.java: $(PICOJSE_JAVA_SRCS)
	touch $@

# Compile all test .java files
tests/%.class: tests/%.java tests/Native.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

# ConstTest needs Const.java annotation
tests/ConstTest.class: tests/ConstTest.java tests/Native.java tests/Const.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

tests/ConstStringArrayTest.class: tests/ConstStringArrayTest.java tests/Native.java tests/Const.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

tests/ConstObjectArrayTest.class tests/ConstPoint.class: tests/ConstObjectArrayTest.java tests/Native.java tests/Const.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

tests/ConstObjectArrayTest.pjvm: tests/ConstObjectArrayTest.class tests/ConstPoint.class
	$(PYTHON) pjvmpack.py $^ -o $@ -v

tests/ConstNarrowingTest.class tests/NarrowPoint.class tests/ByteCommand.class: tests/ConstNarrowingTest.java tests/Native.java tests/Const.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

tests/ConstNarrowingTest.pjvm: tests/ConstNarrowingTest.class tests/NarrowPoint.class tests/ByteCommand.class
	$(PYTHON) pjvmpack.py $^ -o $@ -v

# Pack single-class .pjvm
tests/%.pjvm: tests/%.class
	$(PYTHON) pjvmpack.py $< -o $@ -v

# Multi-class Shapes test
tests/Shapes.pjvm: tests/Shape.class tests/Square.class tests/Rect.class tests/Shapes.class
	$(PYTHON) pjvmpack.py $^ -o $@ -v

tests/Shape.class tests/Square.class tests/Rect.class tests/Shapes.class: tests/Shapes.java tests/Native.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

# Multi-class Features test (needs Shape hierarchy)
tests/Features.pjvm: tests/Shape.class tests/Square.class tests/Rect.class tests/Features.class
	$(PYTHON) pjvmpack.py $^ -o $@ -v

tests/Features.class: tests/Features.java tests/Shapes.java tests/Native.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

# Multi-class InterfaceTest
tests/InterfaceTest.pjvm: tests/HasArea.class tests/Describable.class tests/Measurable.class tests/Circle.class tests/Box.class tests/InterfaceTest.class
	$(PYTHON) pjvmpack.py $^ -o $@ -v

tests/HasArea.class tests/Describable.class tests/Measurable.class tests/Circle.class tests/Box.class tests/InterfaceTest.class: tests/InterfaceTest.java tests/Native.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

# Multi-class ExceptionTest
tests/ExceptionTest.pjvm: tests/MyException.class tests/ExceptionTest.class
	$(PYTHON) pjvmpack.py $^ -o $@ -v

tests/MyException.class tests/ExceptionTest.class: tests/ExceptionTest.java tests/Native.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

# javac enums emit a nested class plus java/lang/Enum boilerplate.
tests/EnumBasicTest.pjvm: tests/EnumBasicTest.class tests/EnumBasicTest$$Color.class
	$(PYTHON) pjvmpack.py tests/EnumBasicTest.class 'tests/EnumBasicTest$$Color.class' -o $@ -v

tests/EnumBasicTest.class tests/EnumBasicTest$$Color.class: tests/EnumBasicTest.java tests/Native.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

# Java-tier enums: pack the java/lang/Enum shim so no VM natives are used
tests/EnumShimTest.pjvm: tests/EnumShimTest.class tests/EnumShimTest$$Color.class $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py tests/EnumShimTest.class 'tests/EnumShimTest$$Color.class' \
		$(PICOJSE_CLASSDIR)/java/lang/Enum.class -o $@ -v

# Java-tier strings: pack the java/lang/String shim; only primitive string
# natives (length/charAt/equals/construction) are used, so this image runs
# on builds with PJVM_USE_EXT_STRING_APIS compiled out (e.g. 8085)
tests/StringShimTest.pjvm: tests/StringShimTest.class $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py tests/StringShimTest.class \
		$(PICOJSE_CLASSDIR)/java/lang/String.class -o $@ -v

tests/StringShimTest.class: tests/StringShimTest.java tests/Native.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

tests/StringApiSmoke.pjvm: tests/StringApiSmoke.class $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py tests/StringApiSmoke.class \
		$(PICOJSE_CLASSDIR)/java/lang/String.class -o $@ -v

# Java-tier exceptions: real messages via the Throwable chain shims
tests/ThrowableShimTest.pjvm: tests/ThrowableShimTest.class $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py tests/ThrowableShimTest.class tests/CustomFault.class \
		$(PICOJSE_CLASSDIR)/java/lang/Throwable.class \
		$(PICOJSE_CLASSDIR)/java/lang/Exception.class \
		$(PICOJSE_CLASSDIR)/java/lang/RuntimeException.class \
		-o $@ -v

tests/ThrowableShimTest.class tests/CustomFault.class: tests/ThrowableShimTest.java tests/Native.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

# Legacy native string tier: same program packed WITHOUT the shim, run on a
# VM built with the (now default-off) C implementations enabled. Keeps the
# legacy block honest until it is deleted outright.
tests/StringApiSmokeNative.pjvm: tests/StringApiSmoke.class
	$(PYTHON) pjvmpack.py tests/StringApiSmoke.class -o $@ -v

$(EXPDIR)/StringApiSmokeNative.hex: $(EXPDIR)/StringApiSmoke.hex
	cp $< $@

test-legacy-string-natives: $(EXPDIR)/StringApiSmokeNative.hex
	$(MAKE) --no-print-directory picojvm \
		HOST_VM_OPTS='$(HOST_VM_FEATURES) -DPJVM_USE_EXT_STRING_APIS=1'
	$(MAKE) --no-print-directory test-StringApiSmokeNative \
		HOST_VM_OPTS='$(HOST_VM_FEATURES) -DPJVM_USE_EXT_STRING_APIS=1'

tests/EnumShimTest.class tests/EnumShimTest$$Color.class: tests/EnumShimTest.java tests/Native.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

$(BUILDDIR)/picojse.stamp: $(PICOJSE_SRCS) tests/TermSmoke.java tests/FilesSmoke.java tests/TermDemo.java tests/PicoJseStdSmoke.java tests/JavaLangSmoke.java tests/StringConcatSmoke.java tests/TarSmoke.java tests/ZipSmoke.java tests/ZipDeflateSmoke.java tests/PJTar.java tests/PJUntar.java tests/PJZip.java tests/PJUnzip.java tests/PJArc.java | $(BUILDDIR)
	@mkdir -p $(PICOJSE_CLASSDIR)
	$(JAVAC) $(JAVAC8FLAGS) -d $(PICOJSE_CLASSDIR) $(PICOJSE_SRCS) tests/TermSmoke.java tests/FilesSmoke.java tests/TermDemo.java tests/PicoJseStdSmoke.java tests/JavaLangSmoke.java tests/StringConcatSmoke.java tests/TarSmoke.java tests/ZipSmoke.java tests/ZipDeflateSmoke.java tests/PJTar.java tests/PJUntar.java tests/PJZip.java tests/PJUnzip.java tests/PJArc.java
	@touch $@

tests/TermSmoke.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/TermSmoke.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/term/Keys.class \
		$(PICOJSE_CLASSDIR)/pj/term/Terminal.class \
		$(PICOJSE_CLASSDIR)/pj/term/CellSurface.class \
		$(PICOJSE_CLASSDIR)/pj/term/AnsiTerminal.class \
		-o $@ -v

tests/FilesSmoke.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/FilesSmoke.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/io/Files.class \
		-o $@ -v

tests/TermDemo.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/TermDemo.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/term/Keys.class \
		$(PICOJSE_CLASSDIR)/pj/term/Terminal.class \
		$(PICOJSE_CLASSDIR)/pj/term/CellSurface.class \
		$(PICOJSE_CLASSDIR)/pj/term/AnsiTerminal.class \
		$(PICOJSE_CLASSDIR)/pj/term/Draw.class \
		$(PICOJSE_CLASSDIR)/pj/text/Format.class \
		-o $@ -v

tests/PicoJseStdSmoke.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/PicoJseStdSmoke.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/io/Binary.class \
		$(PICOJSE_CLASSDIR)/pj/io/Console.class \
		$(PICOJSE_CLASSDIR)/pj/io/TextWriter.class \
		$(PICOJSE_CLASSDIR)/pj/text/Format.class \
		$(PICOJSE_CLASSDIR)/pj/text/Parse.class \
		$(PICOJSE_CLASSDIR)/pj/text/Strings.class \
		$(PICOJSE_CLASSDIR)/pj/util/Bytes.class \
		$(PICOJSE_CLASSDIR)/pj/util/Ints.class \
		-o $@ -v

tests/JavaLangSmoke.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/JavaLangSmoke.class \
		$(PICOJSE_CLASSDIR)/java/lang/Boolean.class \
		$(PICOJSE_CLASSDIR)/java/lang/Byte.class \
		$(PICOJSE_CLASSDIR)/java/lang/Character.class \
		$(PICOJSE_CLASSDIR)/java/lang/Integer.class \
		$(PICOJSE_CLASSDIR)/java/lang/Math.class \
		$(PICOJSE_CLASSDIR)/java/lang/Short.class \
		$(PICOJSE_CLASSDIR)/java/lang/String.class \
		$(PICOJSE_CLASSDIR)/java/lang/StringBuilder.class \
		$(PICOJSE_CLASSDIR)/java/lang/System.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		-o $@ -v

tests/StringConcatSmoke.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/StringConcatSmoke.class \
		$(PICOJSE_CLASSDIR)/java/lang/StringBuilder.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/io/Console.class \
		$(PICOJSE_CLASSDIR)/pj/io/TextWriter.class \
		-o $@ -v

tests/TarSmoke.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/TarSmoke.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/archive/Tar.class \
		$(PICOJSE_CLASSDIR)/pj/io/Files.class \
		$(PICOJSE_CLASSDIR)/pj/text/Format.class \
		$(PICOJSE_CLASSDIR)/pj/text/Strings.class \
		$(PICOJSE_CLASSDIR)/pj/util/Bytes.class \
		-o $@ -v

tests/ZipSmoke.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/ZipSmoke.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/archive/Zip.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipRead.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipWrite.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipDeflateBits.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipDeflate.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipHuffman.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipInflate.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipTables.class \
		$(PICOJSE_CLASSDIR)/pj/io/Files.class \
		$(PICOJSE_CLASSDIR)/pj/io/Binary.class \
		$(PICOJSE_CLASSDIR)/pj/text/Format.class \
		$(PICOJSE_CLASSDIR)/pj/text/Strings.class \
		$(PICOJSE_CLASSDIR)/pj/util/Bytes.class \
		-o $@ -v

tests/ZipDeflateSmoke.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/ZipDeflateSmoke.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/archive/Zip.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipRead.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipWrite.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipDeflateBits.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipDeflate.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipHuffman.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipInflate.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipTables.class \
		$(PICOJSE_CLASSDIR)/pj/io/Files.class \
		$(PICOJSE_CLASSDIR)/pj/io/Binary.class \
		$(PICOJSE_CLASSDIR)/pj/text/Format.class \
		$(PICOJSE_CLASSDIR)/pj/text/Strings.class \
		$(PICOJSE_CLASSDIR)/pj/util/Bytes.class \
		-o $@ -v

tests/PJTar.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/PJTar.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/archive/Tar.class \
		$(PICOJSE_CLASSDIR)/pj/io/Console.class \
		$(PICOJSE_CLASSDIR)/pj/io/Files.class \
		$(PICOJSE_CLASSDIR)/pj/io/TextWriter.class \
		$(PICOJSE_CLASSDIR)/pj/util/Bytes.class \
		-o $@ -v

tests/PJUntar.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/PJUntar.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/archive/Tar.class \
		$(PICOJSE_CLASSDIR)/pj/io/Console.class \
		$(PICOJSE_CLASSDIR)/pj/io/Files.class \
		$(PICOJSE_CLASSDIR)/pj/io/TextWriter.class \
		$(PICOJSE_CLASSDIR)/pj/util/Bytes.class \
		-o $@ -v

tests/PJZip.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/PJZip.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/archive/Zip.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipWrite.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipDeflateBits.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipDeflate.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipHuffman.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipTables.class \
		$(PICOJSE_CLASSDIR)/pj/io/Console.class \
		$(PICOJSE_CLASSDIR)/pj/io/Files.class \
		$(PICOJSE_CLASSDIR)/pj/io/Binary.class \
		$(PICOJSE_CLASSDIR)/pj/io/TextWriter.class \
		$(PICOJSE_CLASSDIR)/pj/util/Bytes.class \
		-o $@ -v

tests/PJUnzip.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/PJUnzip.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/archive/Zip.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipRead.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipHuffman.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipInflate.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipTables.class \
		$(PICOJSE_CLASSDIR)/pj/io/Console.class \
		$(PICOJSE_CLASSDIR)/pj/io/Files.class \
		$(PICOJSE_CLASSDIR)/pj/io/Binary.class \
		$(PICOJSE_CLASSDIR)/pj/io/TextWriter.class \
		$(PICOJSE_CLASSDIR)/pj/util/Bytes.class \
		-o $@ -v

tests/PJArc.pjvm: $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py \
		$(PICOJSE_CLASSDIR)/PJArc.class \
		$(PICOJSE_CLASSDIR)/pj/Native.class \
		$(PICOJSE_CLASSDIR)/pj/archive/Tar.class \
		$(PICOJSE_CLASSDIR)/pj/archive/Zip.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipRead.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipWrite.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipDeflateBits.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipDeflate.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipHuffman.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipInflate.class \
		$(PICOJSE_CLASSDIR)/pj/archive/ZipTables.class \
		$(PICOJSE_CLASSDIR)/pj/io/Console.class \
		$(PICOJSE_CLASSDIR)/pj/io/Files.class \
		$(PICOJSE_CLASSDIR)/pj/io/Binary.class \
		$(PICOJSE_CLASSDIR)/pj/io/TextWriter.class \
		$(PICOJSE_CLASSDIR)/pj/util/Bytes.class \
		-o $@ -v

# Multi-class GC graph stress test
tests/GCGraphTest.pjvm: tests/GCNode.class tests/GCGraphTest.class
	$(PYTHON) pjvmpack.py $^ -o $@ -v

tests/GCNode.class tests/GCGraphTest.class: tests/GCGraphTest.java tests/Native.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

# GC temp-root regression test (shim-packed: the allocation pressure flows
# through the primitive INIT natives, which carry the temp-root protection)
tests/GcTempRootTest.pjvm: tests/GcTempRootTest.class $(BUILDDIR)/picojse.stamp
	$(PYTHON) pjvmpack.py tests/GcTempRootTest.class \
		$(PICOJSE_CLASSDIR)/java/lang/String.class -o $@ -v

tests/GcTempRootTest.class: tests/GcTempRootTest.java tests/Native.java
	$(JAVAC) $(JAVAC8FLAGS) -d tests $^ $(PICOJSE_JAVA_SRCS)

# Pager stress tests (generated)
tests/BigSwitch.java tests/BigLUT.java: tests/gen_big_tests.py
	$(PYTHON) tests/gen_big_tests.py .

# Run a test on host
run-%: $(PICOJVM) tests/%.pjvm
	$(PICOJVM) tests/$*.pjvm

run-term-demo: $(PICOJVM) tests/TermDemo.pjvm
	$(PICOJVM) tests/TermDemo.pjvm

$(BUILDDIR)/%.out: $(PICOJVM) tests/%.pjvm | $(BUILDDIR)
	$(PICOJVM) tests/$*.pjvm > $@

$(BUILDDIR)/%.paged.out: $(PICOJVM_PAGED) tests/%.pjvm | $(BUILDDIR)
	$(PICOJVM_PAGED) tests/$*.pjvm > $@

$(BUILDDIR)/%.paged-stress.out: $(PICOJVM_PAGED) tests/%.pjvm | $(BUILDDIR)
	$(PICOJVM_PAGED) tests/$*.pjvm --pages=1 --page-size=128 > $@

$(BUILDDIR)/%.hex: $(BUILDDIR)/%.out | $(BUILDDIR)
	@od -An -t x1 -v $< | tr '\n' ' ' | tr -s ' ' | sed 's/^ //;s/ $$//' > $@

$(BUILDDIR)/%.paged.hex: $(BUILDDIR)/%.paged.out | $(BUILDDIR)
	@od -An -t x1 -v $< | tr '\n' ' ' | tr -s ' ' | sed 's/^ //;s/ $$//' > $@

$(BUILDDIR)/%.paged-stress.hex: $(BUILDDIR)/%.paged-stress.out | $(BUILDDIR)
	@od -An -t x1 -v $< | tr '\n' ' ' | tr -s ' ' | sed 's/^ //;s/ $$//' > $@

test-%: $(BUILDDIR)/%.hex $(EXPDIR)/%.hex
	@if [ "$$(cat $(BUILDDIR)/$*.hex)" = "$$(cat $(EXPDIR)/$*.hex)" ]; then \
		echo "PASS: $*"; \
	else \
		echo "FAIL: $*"; \
		echo "  Expected: $$(cat $(EXPDIR)/$*.hex)"; \
		echo "  Actual:   $$(cat $(BUILDDIR)/$*.hex)"; \
		exit 1; \
	fi

test-paged-%: $(BUILDDIR)/%.paged.hex $(EXPDIR)/%.hex
	@if [ "$$(cat $(BUILDDIR)/$*.paged.hex)" = "$$(cat $(EXPDIR)/$*.hex)" ]; then \
		echo "PASS: $* [paged]"; \
	else \
		echo "FAIL: $* [paged]"; \
		echo "  Expected: $$(cat $(EXPDIR)/$*.hex)"; \
		echo "  Actual:   $$(cat $(BUILDDIR)/$*.paged.hex)"; \
		exit 1; \
	fi

test-paged-stress-%: $(BUILDDIR)/%.paged-stress.hex $(EXPDIR)/%.hex
	@if [ "$$(cat $(BUILDDIR)/$*.paged-stress.hex)" = "$$(cat $(EXPDIR)/$*.hex)" ]; then \
		echo "PASS: $* [paged-stress 1×128B]"; \
	else \
		echo "FAIL: $* [paged-stress 1×128B]"; \
		echo "  Expected: $$(cat $(EXPDIR)/$*.hex)"; \
		echo "  Actual:   $$(cat $(BUILDDIR)/$*.paged-stress.hex)"; \
		exit 1; \
	fi

test-pjtools-tar: $(PICOJVM) tests/PJTar.pjvm tests/PJUntar.pjvm $(EXPDIR)/PJArchiveCreate.txt $(EXPDIR)/PJArchiveExtract.txt | $(BUILDDIR)
	@rm -rf $(BUILDDIR)/pjtools_tar
	@mkdir -p $(BUILDDIR)/pjtools_tar
	@printf 'ALPHA\n' > $(BUILDDIR)/pjtools_tar/a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/pjtools_tar/b.txt
	@cd $(BUILDDIR)/pjtools_tar && $(abspath $(PICOJVM)) $(abspath tests/PJTar.pjvm) -- out.tar a.txt b.txt > create.out 2> create.log
	@if diff -q $(BUILDDIR)/pjtools_tar/create.out $(EXPDIR)/PJArchiveCreate.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: PJTar create"; \
		echo "  Expected:"; cat $(EXPDIR)/PJArchiveCreate.txt; \
		echo "  Got:"; cat $(BUILDDIR)/pjtools_tar/create.out; \
		exit 1; \
	fi
	@rm -f $(BUILDDIR)/pjtools_tar/a.txt $(BUILDDIR)/pjtools_tar/b.txt
	@cd $(BUILDDIR)/pjtools_tar && $(abspath $(PICOJVM)) $(abspath tests/PJUntar.pjvm) -- out.tar > extract.out 2> extract.log
	@if diff -q $(BUILDDIR)/pjtools_tar/extract.out $(EXPDIR)/PJArchiveExtract.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: PJUntar extract"; \
		echo "  Expected:"; cat $(EXPDIR)/PJArchiveExtract.txt; \
		echo "  Got:"; cat $(BUILDDIR)/pjtools_tar/extract.out; \
		exit 1; \
	fi
	@printf 'ALPHA\n' > $(BUILDDIR)/pjtools_tar/exp_a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/pjtools_tar/exp_b.txt
	@if diff -q $(BUILDDIR)/pjtools_tar/a.txt $(BUILDDIR)/pjtools_tar/exp_a.txt > /dev/null 2>&1 && \
	    diff -q $(BUILDDIR)/pjtools_tar/b.txt $(BUILDDIR)/pjtools_tar/exp_b.txt > /dev/null 2>&1; then \
		echo "PASS: pjtools tar"; \
	else \
		echo "FAIL: pjtools tar contents"; \
		exit 1; \
	fi

test-pjtools-zip: $(PICOJVM) tests/PJZip.pjvm tests/PJUnzip.pjvm $(EXPDIR)/PJArchiveCreate.txt $(EXPDIR)/PJArchiveExtract.txt | $(BUILDDIR)
	@rm -rf $(BUILDDIR)/pjtools_zip
	@mkdir -p $(BUILDDIR)/pjtools_zip
	@printf 'ALPHA\n' > $(BUILDDIR)/pjtools_zip/a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/pjtools_zip/b.txt
	@cd $(BUILDDIR)/pjtools_zip && $(abspath $(PICOJVM)) $(abspath tests/PJZip.pjvm) -- out.zip a.txt b.txt > create.out 2> create.log
	@if diff -q $(BUILDDIR)/pjtools_zip/create.out $(EXPDIR)/PJArchiveCreate.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: PJZip create"; \
		echo "  Expected:"; cat $(EXPDIR)/PJArchiveCreate.txt; \
		echo "  Got:"; cat $(BUILDDIR)/pjtools_zip/create.out; \
		exit 1; \
	fi
	@rm -f $(BUILDDIR)/pjtools_zip/a.txt $(BUILDDIR)/pjtools_zip/b.txt
	@cd $(BUILDDIR)/pjtools_zip && $(abspath $(PICOJVM)) $(abspath tests/PJUnzip.pjvm) -- out.zip > extract.out 2> extract.log
	@if diff -q $(BUILDDIR)/pjtools_zip/extract.out $(EXPDIR)/PJArchiveExtract.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: PJUnzip extract"; \
		echo "  Expected:"; cat $(EXPDIR)/PJArchiveExtract.txt; \
		echo "  Got:"; cat $(BUILDDIR)/pjtools_zip/extract.out; \
		exit 1; \
	fi
	@printf 'ALPHA\n' > $(BUILDDIR)/pjtools_zip/exp_a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/pjtools_zip/exp_b.txt
	@if diff -q $(BUILDDIR)/pjtools_zip/a.txt $(BUILDDIR)/pjtools_zip/exp_a.txt > /dev/null 2>&1 && \
	    diff -q $(BUILDDIR)/pjtools_zip/b.txt $(BUILDDIR)/pjtools_zip/exp_b.txt > /dev/null 2>&1; then \
		echo "PASS: pjtools zip"; \
	else \
		echo "FAIL: pjtools zip contents"; \
		exit 1; \
	fi
test-pjtools-arc: $(PICOJVM) tests/PJArc.pjvm $(EXPDIR)/PJArchiveCreate.txt $(EXPDIR)/PJArchiveExtract.txt | $(BUILDDIR)
	@rm -rf $(BUILDDIR)/pjtools_arc
	@mkdir -p $(BUILDDIR)/pjtools_arc
	@printf 'ALPHA\n' > $(BUILDDIR)/pjtools_arc/a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/pjtools_arc/b.txt
	@cd $(BUILDDIR)/pjtools_arc && $(abspath $(PICOJVM)) $(abspath tests/PJArc.pjvm) -- tar out.tar a.txt b.txt > create-tar.out 2> create-tar.log
	@if diff -q $(BUILDDIR)/pjtools_arc/create-tar.out $(EXPDIR)/PJArchiveCreate.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: PJArc tar create"; \
		echo "  Expected:"; cat $(EXPDIR)/PJArchiveCreate.txt; \
		echo "  Got:"; cat $(BUILDDIR)/pjtools_arc/create-tar.out; \
		exit 1; \
	fi
	@rm -f $(BUILDDIR)/pjtools_arc/a.txt $(BUILDDIR)/pjtools_arc/b.txt
	@cd $(BUILDDIR)/pjtools_arc && $(abspath $(PICOJVM)) $(abspath tests/PJArc.pjvm) -- untar out.tar > extract-tar.out 2> extract-tar.log
	@if diff -q $(BUILDDIR)/pjtools_arc/extract-tar.out $(EXPDIR)/PJArchiveExtract.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: PJArc untar extract"; \
		echo "  Expected:"; cat $(EXPDIR)/PJArchiveExtract.txt; \
		echo "  Got:"; cat $(BUILDDIR)/pjtools_arc/extract-tar.out; \
		exit 1; \
	fi
	@printf 'ALPHA\n' > $(BUILDDIR)/pjtools_arc/exp_a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/pjtools_arc/exp_b.txt
	@if diff -q $(BUILDDIR)/pjtools_arc/a.txt $(BUILDDIR)/pjtools_arc/exp_a.txt > /dev/null 2>&1 && \
	    diff -q $(BUILDDIR)/pjtools_arc/b.txt $(BUILDDIR)/pjtools_arc/exp_b.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: pjtools arc tar contents"; \
		exit 1; \
	fi
	@rm -f $(BUILDDIR)/pjtools_arc/a.txt $(BUILDDIR)/pjtools_arc/b.txt $(BUILDDIR)/pjtools_arc/out.tar
	@printf 'ALPHA\n' > $(BUILDDIR)/pjtools_arc/a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/pjtools_arc/b.txt
	@cd $(BUILDDIR)/pjtools_arc && $(abspath $(PICOJVM)) $(abspath tests/PJArc.pjvm) -- zip out.zip a.txt b.txt > create-zip.out 2> create-zip.log
	@if diff -q $(BUILDDIR)/pjtools_arc/create-zip.out $(EXPDIR)/PJArchiveCreate.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: PJArc zip create"; \
		echo "  Expected:"; cat $(EXPDIR)/PJArchiveCreate.txt; \
		echo "  Got:"; cat $(BUILDDIR)/pjtools_arc/create-zip.out; \
		exit 1; \
	fi
	@rm -f $(BUILDDIR)/pjtools_arc/a.txt $(BUILDDIR)/pjtools_arc/b.txt
	@cd $(BUILDDIR)/pjtools_arc && $(abspath $(PICOJVM)) $(abspath tests/PJArc.pjvm) -- unzip out.zip > extract-zip.out 2> extract-zip.log
	@if diff -q $(BUILDDIR)/pjtools_arc/extract-zip.out $(EXPDIR)/PJArchiveExtract.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: PJArc unzip extract"; \
		echo "  Expected:"; cat $(EXPDIR)/PJArchiveExtract.txt; \
		echo "  Got:"; cat $(BUILDDIR)/pjtools_arc/extract-zip.out; \
		exit 1; \
	fi
	@if diff -q $(BUILDDIR)/pjtools_arc/a.txt $(BUILDDIR)/pjtools_arc/exp_a.txt > /dev/null 2>&1 && \
	    diff -q $(BUILDDIR)/pjtools_arc/b.txt $(BUILDDIR)/pjtools_arc/exp_b.txt > /dev/null 2>&1; then \
		echo "PASS: pjtools arc"; \
	else \
		echo "FAIL: pjtools arc zip contents"; \
		exit 1; \
	fi
test-pjtools: test-pjtools-tar test-pjtools-zip test-pjtools-arc

# Run all tests on host with golden-output comparison
test: $(PICOJVM) $(addprefix tests/,$(addsuffix .pjvm,$(TESTS_SINGLE))) tests/Shapes.pjvm tests/Features.pjvm tests/InterfaceTest.pjvm tests/ExceptionTest.pjvm
	@fail=0; for t in $(ALL_TESTS); do \
		$(MAKE) --no-print-directory test-$$t || fail=1; \
	done; exit $$fail

# Paged-mode run and test
run-paged-%: $(PICOJVM_PAGED) tests/%.pjvm
	$(PICOJVM_PAGED) tests/$*.pjvm

test-paged: $(PICOJVM_PAGED) $(addprefix tests/,$(addsuffix .pjvm,$(ALL_TESTS_PAGER)))
	@fail=0; for t in $(ALL_TESTS_PAGER); do \
		$(MAKE) --no-print-directory test-paged-$$t || fail=1; \
	done; exit $$fail

# Paged stress: 1 page × 128B — forces eviction on every page boundary
test-paged-stress: $(PICOJVM_PAGED) $(addprefix tests/,$(addsuffix .pjvm,$(ALL_TESTS_PAGER)))
	@fail=0; for t in $(ALL_TESTS_PAGER); do \
		$(MAKE) --no-print-directory test-paged-stress-$$t || fail=1; \
	done; exit $$fail

$(GC_DEMO_MANUAL): tests/gc_policy_demo.c src/pjvm_gc.c src/pjvm.h | $(BUILDDIR)
	$(CC) $(CFLAGS) -DPJVM_GC_TRIGGERS=0 -o $@ tests/gc_policy_demo.c src/pjvm_gc.c

$(GC_DEMO_ALLOCFAIL): tests/gc_policy_demo.c src/pjvm_gc.c src/pjvm.h | $(BUILDDIR)
	$(CC) $(CFLAGS) -DPJVM_GC_TRIGGERS=PJVM_GC_TRIG_ALLOC_FAIL -o $@ tests/gc_policy_demo.c src/pjvm_gc.c

$(GC_DEMO_WATERMARK): tests/gc_policy_demo.c src/pjvm_gc.c src/pjvm.h | $(BUILDDIR)
	$(CC) $(CFLAGS) '-DPJVM_GC_TRIGGERS=PJVM_GC_TRIG_WATERMARK' -DPJVM_GC_WATERMARK_PCT=75 -o $@ tests/gc_policy_demo.c src/pjvm_gc.c

$(GC_DEMO_RETURN): tests/gc_policy_demo.c src/pjvm_gc.c src/pjvm.h | $(BUILDDIR)
	$(CC) $(CFLAGS) -DPJVM_GC_TRIGGERS=PJVM_GC_TRIG_RETURN -o $@ tests/gc_policy_demo.c src/pjvm_gc.c

$(GC_DEMO_RANDOM): tests/gc_policy_demo.c src/pjvm_gc.c src/pjvm.h | $(BUILDDIR)
	$(CC) $(CFLAGS) '-DPJVM_GC_TRIGGERS=(PJVM_GC_TRIG_WATERMARK|PJVM_GC_TRIG_RANDOM_ABOVE_WATERMARK)' -DPJVM_GC_WATERMARK_PCT=75 -o $@ tests/gc_policy_demo.c src/pjvm_gc.c

$(GC_COLLECT_TEST): tests/gc_collect_test.c src/pjvm_heap.c src/pjvm_gc.c src/pjvm.h | $(BUILDDIR)
	$(CC) $(CFLAGS) -DPJVM_HEAP_MODE=PJVM_HEAP_FREELIST -DPJVM_GC_TRIGGERS=PJVM_GC_TRIG_ALLOC_FAIL -o $@ tests/gc_collect_test.c src/pjvm_heap.c src/pjvm_gc.c

$(GC_FRAGMENT_TEST): tests/gc_fragment_test.c src/pjvm_heap.c src/pjvm_gc.c src/pjvm.h | $(BUILDDIR)
	$(CC) $(CFLAGS) -DPJVM_HEAP_MODE=PJVM_HEAP_FREELIST -DPJVM_GC_TRIGGERS=PJVM_GC_TRIG_ALLOC_FAIL -o $@ tests/gc_fragment_test.c src/pjvm_heap.c src/pjvm_gc.c

$(GC_EXACT_TEST): tests/gc_exact_test.c src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c src/pjvm.h | $(BUILDDIR)
	$(CC) $(CFLAGS) -DPJVM_HEAP_MODE=PJVM_HEAP_FREELIST -DPJVM_GC_TRIGGERS=PJVM_GC_TRIG_ALLOC_FAIL -o $@ tests/gc_exact_test.c src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c

gc-demo-manual: $(GC_DEMO_MANUAL)
	$(GC_DEMO_MANUAL)

gc-demo-allocfail: $(GC_DEMO_ALLOCFAIL)
	$(GC_DEMO_ALLOCFAIL)

gc-demo-watermark75: $(GC_DEMO_WATERMARK)
	$(GC_DEMO_WATERMARK)

gc-demo-return: $(GC_DEMO_RETURN)
	$(GC_DEMO_RETURN)

gc-demo-random: $(GC_DEMO_RANDOM)
	$(GC_DEMO_RANDOM)

gc-policy-test: gc-demo-manual gc-demo-allocfail gc-demo-watermark75 gc-demo-return gc-demo-random

test-gc-collect: $(GC_COLLECT_TEST)
	$(GC_COLLECT_TEST)

test-gc-fragment: $(GC_FRAGMENT_TEST)
	$(GC_FRAGMENT_TEST)

test-gc-exact: $(GC_EXACT_TEST)
	$(GC_EXACT_TEST)

test-v4: $(PICOJVM_LARGE)
	$(PYTHON) tests/run_v4_tests.py --picojvm $(abspath $(PICOJVM_LARGE))

test-pjvmpack-package: tests/Fib.class tests/ConstTest.class tests/ConstStringArrayTest.class tests/InterfaceTest.pjvm tests/ExceptionTest.pjvm
	$(PYTHON) tests/run_pjvmpack_package_tests.py

test-alloc-heavy: $(PICOJVM) tests/AllocHeavyTest.pjvm
	$(MAKE) --no-print-directory test-AllocHeavyTest

test-paged-alloc-heavy: $(PICOJVM_PAGED) tests/AllocHeavyTest.pjvm
	$(MAKE) --no-print-directory test-paged-AllocHeavyTest

test-gc-graph:
	$(MAKE) --no-print-directory clean
	$(MAKE) --no-print-directory test-GCGraphTest \
		HOST_VM_OPTS='$(HOST_VM_FEATURES) $(GC_DEFAULT_OPTS) -DPJVM_HOST_HEAP_LIMIT=$(GC_HOST_PRESSURE_LIMIT)'

test-gc-temp-roots:
	$(MAKE) --no-print-directory clean
	$(MAKE) --no-print-directory test-GcTempRootTest \
		HOST_VM_OPTS='$(HOST_VM_FEATURES) $(GC_DEFAULT_OPTS) -DPJVM_HOST_HEAP_LIMIT=$(GC_HOST_PRESSURE_LIMIT)'

# Method-table read-through: metadata served from the image, ~22B/method
# of RAM saved; full suite must behave identically
test-mt-in-image:
	$(MAKE) --no-print-directory clean
	$(MAKE) --no-print-directory test \
		HOST_VM_OPTS='$(HOST_VM_FEATURES) -DPJVM_MT_IN_IMAGE=1'

# Exact-allocation-bitmap config: O(1) conservative root validation
test-gc-alloc-bitmap:
	$(MAKE) --no-print-directory clean
	$(MAKE) --no-print-directory test-GcTempRootTest test-GCGraphTest test-AllocHeavyTest \
		HOST_VM_OPTS='$(HOST_VM_FEATURES) $(GC_DEFAULT_OPTS) -DPJVM_GC_ALLOC_BITMAP=1 -DPJVM_HOST_HEAP_LIMIT=$(GC_HOST_PRESSURE_LIMIT)'

test-gc-alloc-heavy:
	$(MAKE) --no-print-directory clean
	$(MAKE) --no-print-directory test-AllocHeavyTest \
		HOST_VM_OPTS='$(HOST_VM_FEATURES) $(GC_DEFAULT_OPTS) -DPJVM_HOST_HEAP_LIMIT=$(GC_HOST_PRESSURE_LIMIT)'

test-gc-host-compat:
	$(MAKE) --no-print-directory clean
	$(MAKE) --no-print-directory test test-paged HOST_VM_OPTS='$(HOST_VM_FEATURES) $(GC_DEFAULT_OPTS)'

test-gc-host-suite:
	$(MAKE) --no-print-directory gc-policy-test
	$(MAKE) --no-print-directory test-gc-collect
	$(MAKE) --no-print-directory test-gc-fragment
	$(MAKE) --no-print-directory test-gc-exact
	$(MAKE) --no-print-directory test-gc-host-compat
	$(MAKE) --no-print-directory test-gc-alloc-heavy
	$(MAKE) --no-print-directory test-gc-graph
	$(MAKE) --no-print-directory test-gc-temp-roots
	$(MAKE) --no-print-directory test-gc-alloc-bitmap

# --- 8085 simulator target ---

$(BUILDDIR):
	mkdir -p $(BUILDDIR)

# Target objects depend on the flags they were built with, like the host
# stamp above. TARGET_LTO additionally changes the object FORMAT (bitcode
# vs native), so stale objects must be removed, not just relinked.
TARGET_VM_ALLFLAGS = $(TARGET_OPT) $(TARGET_LTO_CFLAGS) $(TARGET_OUTLINE_FLAGS) $(SIM_CAPS) $(TARGET_VM_OPTS) $(TARGET_ASM_HELPERS_DEF)
$(BUILDDIR)/.targetvm.flags: FORCE | $(BUILDDIR)
	@printf '%s\n' '$(TARGET_VM_ALLFLAGS)' | cmp -s - $@ 2>/dev/null || \
		{ printf '%s\n' '$(TARGET_VM_ALLFLAGS)' > $@; \
		  rm -f $(BUILDDIR)/pjvm.o $(BUILDDIR)/pjvm_heap.o \
		        $(BUILDDIR)/pjvm_gc.o $(BUILDDIR)/i8085_sim.o \
		        $(BUILDDIR)/i8085_helpers.o $(BUILDDIR)/*.elf $(BUILDDIR)/*.bin; }

# Convert .pjvm binary to C source with const array
$(PJVM_DATA_C): FORCE $(PJVM_FILE) | $(BUILDDIR)
	@$(PYTHON) -c "import pathlib, sys; d = pathlib.Path(sys.argv[1]).read_bytes(); \
lines = ',\\n'.join('    ' + ', '.join(f'0x{b:02x}' for b in d[i:i+16]) for i in range(0, len(d), 16)); \
pathlib.Path(sys.argv[2]).write_text('// Auto-generated — .pjvm program data\\n#include <stdint.h>\\nconst uint8_t pjvm_program[] = {\\n' + lines + '\\n};\\n')" \
		$(PJVM_FILE) $@

$(BUILDDIR)/crt0.o: $(CRT) | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) -c $< -o $@

$(BUILDDIR)/pjvm.o: src/pjvm.c $(PJVM_HEADERS) $(BUILDDIR)/.targetvm.flags | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -ffunction-sections $(TARGET_LTO_CFLAGS) $(TARGET_OUTLINE_FLAGS) -$(TARGET_OPT) $(SIM_CAPS) $(TARGET_VM_OPTS) $(TARGET_ASM_HELPERS_DEF) -c $< -o $@

$(BUILDDIR)/pjvm_heap.o: src/pjvm_heap.c $(PJVM_HEADERS) $(BUILDDIR)/.targetvm.flags | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -ffunction-sections $(TARGET_LTO_CFLAGS) $(TARGET_OUTLINE_FLAGS) -$(TARGET_OPT) $(SIM_CAPS) $(TARGET_VM_OPTS) -c $< -o $@

$(BUILDDIR)/pjvm_gc.o: src/pjvm_gc.c $(PJVM_HEADERS) $(BUILDDIR)/.targetvm.flags | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -ffunction-sections $(TARGET_LTO_CFLAGS) $(TARGET_OUTLINE_FLAGS) -$(TARGET_OPT) $(SIM_CAPS) $(TARGET_VM_OPTS) -c $< -o $@

$(BUILDDIR)/i8085_sim.o: platform/i8085_sim.c $(PJVM_HEADERS) $(BUILDDIR)/.targetvm.flags | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -ffunction-sections $(TARGET_LTO_CFLAGS) $(TARGET_OUTLINE_FLAGS) -$(TARGET_OPT) $(SIM_CAPS) $(TARGET_VM_OPTS) $(TARGET_ASM_HELPERS_DEF) -c $< -o $@

$(BUILDDIR)/i8085_helpers.o: platform/i8085_helpers.S src/pjvm_opts.h $(BUILDDIR)/.targetvm.flags | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf $(TARGET_VM_OPTS) -DPJVM_ASM_HELPERS -c $< -o $@

$(PJVM_DATA_O): $(PJVM_DATA_C) | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) -c $< -o $@

$(BUILDDIR)/%.sim.dump: FORCE tests/%.pjvm | $(BUILDDIR)
	@$(MAKE) --no-print-directory sim-$* LDSCRIPT="$(LDSCRIPT)" TARGET_VM_OPTS="$(TARGET_VM_OPTS)" TARGET_ASM_HELPERS="$(TARGET_ASM_HELPERS)" SIM_DUMP_ADDR="$(SIM_DUMP_ADDR)" > $@ 2>&1

$(BUILDDIR)/AllocHeavyTest.gc-sim.dump: FORCE tests/AllocHeavyTest.pjvm | $(BUILDDIR)
	@$(MAKE) --no-print-directory sim-AllocHeavyTest \
		LDSCRIPT="$(GC_SIM_FLAT_LDSCRIPT)" \
		SIM_DUMP_ADDR="$(GC_SIM_OUTPUT_BASE)" \
		TARGET_VM_OPTS="$(GC_SIM_STRESS_OPTS)" > $@ 2>&1

$(TARGET_ELF): $(BUILDDIR)/crt0.o $(BUILDDIR)/pjvm.o $(BUILDDIR)/pjvm_heap.o $(BUILDDIR)/pjvm_gc.o $(BUILDDIR)/i8085_sim.o $(TARGET_HELPER_OBJS) $(PJVM_DATA_O) $(LIBGCC) $(LIBC)
	$(LLD) $(TARGET_LTO_LDFLAGS) -m i8085elf --gc-sections -T $(LDSCRIPT) -o $@ $^ $(LIBGCC)

$(TARGET_BIN): $(TARGET_ELF)
	$(OBJCOPY) -O binary $< $@

# Build for 8085 simulator: make sim PJVM_FILE=tests/Fib.pjvm
sim: $(TARGET_BIN)
	@$(SIZE) $(TARGET_ELF)
	@echo "--- Running on 8085 simulator ---"
	$(TRACE) -e 0x0000 -l 0x0000 -n 50000000 -S -q -d $(SIM_DUMP_ADDR):128 $(TARGET_BIN)

# Build + run a specific test: make sim-Fib
sim-%: tests/%.pjvm
	$(MAKE) sim PJVM_FILE=tests/$*.pjvm TARGET_VM_OPTS="$(TARGET_VM_OPTS)" TARGET_ASM_HELPERS="$(TARGET_ASM_HELPERS)"

test-sim-%: $(BUILDDIR)/%.sim.dump $(EXPDIR)/%.hex
	@$(PYTHON) $(SIM_VERIFY) --dump $< --expected $(EXPDIR)/$*.hex >/dev/null
	@echo "PASS: $* [sim]"

test-sim-smoke:
	$(MAKE) --no-print-directory test-sim-Fib
	$(MAKE) --no-print-directory test-sim-RomStringTest
	$(MAKE) --no-print-directory test-sim-NativeOpsTest
	$(MAKE) --no-print-directory test-sim-StringShimTest

test-sim-gc-smoke:
	$(MAKE) --no-print-directory test-sim-Fib \
		LDSCRIPT='$(GC_SIM_FLAT_LDSCRIPT)' \
		SIM_DUMP_ADDR='$(GC_SIM_OUTPUT_BASE)' \
		TARGET_VM_OPTS='$(GC_SIM_SMOKE_OPTS)'
	$(MAKE) --no-print-directory test-sim-RomStringTest \
		LDSCRIPT='$(GC_SIM_FLAT_LDSCRIPT)' \
		SIM_DUMP_ADDR='$(GC_SIM_OUTPUT_BASE)' \
		TARGET_VM_OPTS='$(GC_SIM_SMOKE_OPTS)'
	$(MAKE) --no-print-directory test-sim-NativeOpsTest \
		LDSCRIPT='$(GC_SIM_FLAT_LDSCRIPT)' \
		SIM_DUMP_ADDR='$(GC_SIM_OUTPUT_BASE)' \
		TARGET_VM_OPTS='$(GC_SIM_SMOKE_OPTS)'

test-sim-gc-alloc-heavy: $(BUILDDIR)/AllocHeavyTest.gc-sim.dump $(EXPDIR)/AllocHeavyTest.hex
	@$(PYTHON) $(SIM_VERIFY) --dump $< --expected $(EXPDIR)/AllocHeavyTest.hex >/dev/null
	@echo "PASS: AllocHeavyTest [sim-gc]"

test-gc-sim-suite:
	$(MAKE) --no-print-directory test-sim-gc-smoke
	$(MAKE) --no-print-directory clean
	$(MAKE) --no-print-directory test-sim-gc-alloc-heavy

test-gc-suite:
	$(MAKE) --no-print-directory test-gc-host-suite
	$(MAKE) --no-print-directory test-gc-sim-suite

# 8085 code-size table for the principal VM configs (needs the outer
# llvm-8085 toolchain). Sums pjvm.c + pjvm_heap.c + pjvm_gc.c text so
# format/feature growth gets noticed when it lands, not months later.
SIZEREP_BIG = -DPJVM_ENABLE_V4=1 -DPJVM_METHOD_CAP=320 -DPJVM_CLASS_CAP=32 \
              -DPJVM_VTABLE_CAP=320 -DPJVM_STATIC_CAP=128 -DPJVM_MAX_STACK=96 \
              -DPJVM_MAX_LOCALS=256 -DPJVM_MAX_FRAMES=20
SIZEREP_GC = -DPJVM_HEAP_MODE=PJVM_HEAP_FREELIST -DPJVM_GC_TRIGGERS=3 \
             -DPJVM_GC_WATERMARK_PCT=75

size-report: | $(BUILDDIR)
	@echo "VM core text bytes (pjvm.c+heap+gc, i8085 -Oz):"; \
	for cfg in \
	  "small-v3|$(SIM_CAPS) -DPJVM_ASM_HELPERS" \
	  "small-v3+GC|$(SIM_CAPS) -DPJVM_ASM_HELPERS $(SIZEREP_GC)" \
	  "big-v3+v4|$(SIZEREP_BIG)" \
	  "big-v4only|$(SIZEREP_BIG) -DPJVM_ENABLE_V3=0" \
	  "big-v4only+GC|$(SIZEREP_BIG) -DPJVM_ENABLE_V3=0 $(SIZEREP_GC)"; do \
	  name=$${cfg%%|*}; flags=$${cfg#*|}; total=0; \
	  for f in src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c; do \
	    $(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin $(TARGET_OUTLINE_FLAGS) -Oz \
	      $$flags -c $$f -o $(BUILDDIR)/.sizerep.o 2>/dev/null || exit 1; \
	    t=$$($(SIZE) $(BUILDDIR)/.sizerep.o | awk 'NR==2{print $$1}'); \
	    total=$$((total + t)); \
	  done; \
	  printf "  %-16s %6d\n" "$$name" "$$total"; \
	done; rm -f $(BUILDDIR)/.sizerep.o

.PHONY: size-report

clean:
	rm -f $(PICOJVM) $(PICOJVM_PAGED) tests/*.class tests/*.pjvm tests/*.pjvmmap
	rm -rf $(BUILDDIR)

.PHONY: FORCE
.PHONY: all test test-paged test-paged-stress clean sim
.PHONY: gc-demo-manual gc-demo-allocfail gc-demo-watermark75 gc-demo-return gc-demo-random
.PHONY: gc-policy-test test-gc-collect test-gc-fragment test-gc-exact test-v4 test-pjvmpack-package test-alloc-heavy test-paged-alloc-heavy test-gc-alloc-heavy
.PHONY: test-gc-graph test-gc-temp-roots test-gc-alloc-bitmap test-gc-host-compat test-gc-host-suite test-sim-smoke test-sim-gc-smoke test-legacy-string-natives test-mt-in-image
.PHONY: test-sim-gc-alloc-heavy test-gc-sim-suite test-gc-suite
