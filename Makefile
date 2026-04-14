CC      = cc
CFLAGS  = -Wall -Wextra -O2
HOST_VM_DEBUG = -DPJVM_DEBUG_TOOLS
HOST_VM_OPTS ?=
JAVAC   = javac
PYTHON  = python3
PICOJVM = ./picojvm
EXPDIR  = expected
GC_DEMO_MANUAL    = $(BUILDDIR)/gc-policy-demo-manual
GC_DEMO_ALLOCFAIL = $(BUILDDIR)/gc-policy-demo-allocfail
GC_DEMO_WATERMARK = $(BUILDDIR)/gc-policy-demo-watermark75
GC_DEMO_RETURN    = $(BUILDDIR)/gc-policy-demo-return
GC_DEMO_RANDOM    = $(BUILDDIR)/gc-policy-demo-random
GC_COLLECT_TEST   = $(BUILDDIR)/gc-collect-test

# Single-class tests
TESTS_SINGLE = Fib HelloWorld BubbleSort Counter StringTest RomStringTest NativeOpsTest StaticInitTest MultiArrayTest StringSwitchTest ConstTest
TESTS_MULTI  = Shapes Features InterfaceTest ExceptionTest
TESTS_PAGER  = BigSwitch BigLUT
ALL_TESTS    = $(TESTS_SINGLE) $(TESTS_MULTI)
ALL_TESTS_PAGER = $(ALL_TESTS) $(TESTS_PAGER)

# --- 8085 target toolchain ---
ROOT     = $(shell cd ../.. && pwd)
CLANG    = $(ROOT)/llvm-project/build-clang-8085/bin/clang
LLD      = $(ROOT)/llvm-project/build-clang-8085/bin/ld.lld
OBJCOPY  = $(ROOT)/llvm-project/build-clang-8085/bin/llvm-objcopy
SIZE     = $(ROOT)/llvm-project/build-clang-8085/bin/llvm-size
TRACE    = $(ROOT)/i8085-trace/build/i8085-trace
SIM_VERIFY = $(ROOT)/tooling/examples/verify_dump.py
CRT      = $(ROOT)/sysroot/crt/crt0.S
LIBGCC   = $(ROOT)/sysroot/lib/libgcc.a
LIBC     = $(ROOT)/sysroot/lib/libc.a
LDSCRIPT = $(ROOT)/sysroot/ldscripts/i8085-32kram-32krom.ld
TARGET_OPT = Oz
BUILDDIR = build
TARGET_VM_OPTS ?=
TARGET_ASM_HELPERS ?= 1
SIM_DUMP_ADDR ?= 0x7000
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

# Capacity overrides for 8085 target (smaller than host defaults)
SIM_CAPS = -DPJVM_METHOD_CAP=64 -DPJVM_CLASS_CAP=16 -DPJVM_VTABLE_CAP=128 \
           -DPJVM_STATIC_CAP=32 -DPJVM_MAX_STACK=64 -DPJVM_MAX_LOCALS=128 \
           -DPJVM_MAX_FRAMES=16

all: $(PICOJVM)

$(PICOJVM): src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c platform/host.c src/pjvm.h
	$(CC) $(CFLAGS) $(HOST_VM_DEBUG) $(HOST_VM_OPTS) -DPJVM_MAX_FRAMES=128 -o $@ src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c platform/host.c

$(PICOJVM_PAGED): src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c platform/host.c src/pjvm.h
	$(CC) $(CFLAGS) $(HOST_VM_DEBUG) $(HOST_VM_OPTS) -DPJVM_PAGED -o $@ src/pjvm.c src/pjvm_heap.c src/pjvm_gc.c platform/host.c

# Compile all test .java files
tests/%.class: tests/%.java tests/Native.java
	$(JAVAC) -d tests $^

# ConstTest needs Const.java annotation
tests/ConstTest.class: tests/ConstTest.java tests/Native.java tests/Const.java
	$(JAVAC) -d tests $^

# Pack single-class .pjvm
tests/%.pjvm: tests/%.class
	$(PYTHON) pjvmpack.py $< -o $@ -v

# Multi-class Shapes test
tests/Shapes.pjvm: tests/Shape.class tests/Square.class tests/Rect.class tests/Shapes.class
	$(PYTHON) pjvmpack.py $^ -o $@ -v

tests/Shape.class tests/Square.class tests/Rect.class tests/Shapes.class: tests/Shapes.java tests/Native.java
	$(JAVAC) -d tests $^

# Multi-class Features test (needs Shape hierarchy)
tests/Features.pjvm: tests/Shape.class tests/Square.class tests/Rect.class tests/Features.class
	$(PYTHON) pjvmpack.py $^ -o $@ -v

tests/Features.class: tests/Features.java tests/Shapes.java tests/Native.java
	$(JAVAC) -d tests $^

# Multi-class InterfaceTest
tests/InterfaceTest.pjvm: tests/HasArea.class tests/Describable.class tests/Measurable.class tests/Circle.class tests/Box.class tests/InterfaceTest.class
	$(PYTHON) pjvmpack.py $^ -o $@ -v

tests/HasArea.class tests/Describable.class tests/Measurable.class tests/Circle.class tests/Box.class tests/InterfaceTest.class: tests/InterfaceTest.java tests/Native.java
	$(JAVAC) -d tests $^

# Multi-class ExceptionTest
tests/ExceptionTest.pjvm: tests/MyException.class tests/ExceptionTest.class
	$(PYTHON) pjvmpack.py $^ -o $@ -v

tests/MyException.class tests/ExceptionTest.class: tests/ExceptionTest.java tests/Native.java
	$(JAVAC) -d tests $^

# Pager stress tests (generated)
tests/BigSwitch.java tests/BigLUT.java: tests/gen_big_tests.py
	$(PYTHON) tests/gen_big_tests.py .

# Run a test on host
run-%: $(PICOJVM) tests/%.pjvm
	$(PICOJVM) tests/$*.pjvm

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

# Run all tests on host with golden-output comparison
test: $(PICOJVM) $(addprefix tests/,$(addsuffix .pjvm,$(TESTS_SINGLE))) tests/Shapes.pjvm tests/Features.pjvm tests/InterfaceTest.pjvm tests/ExceptionTest.pjvm
	@for t in $(ALL_TESTS); do \
		$(MAKE) --no-print-directory test-$$t; \
	done

# Paged-mode run and test
run-paged-%: $(PICOJVM_PAGED) tests/%.pjvm
	$(PICOJVM_PAGED) tests/$*.pjvm

test-paged: $(PICOJVM_PAGED) $(addprefix tests/,$(addsuffix .pjvm,$(ALL_TESTS_PAGER)))
	@for t in $(ALL_TESTS_PAGER); do \
		$(MAKE) --no-print-directory test-paged-$$t; \
	done

# Paged stress: 1 page × 128B — forces eviction on every page boundary
test-paged-stress: $(PICOJVM_PAGED) $(addprefix tests/,$(addsuffix .pjvm,$(ALL_TESTS_PAGER)))
	@for t in $(ALL_TESTS_PAGER); do \
		$(MAKE) --no-print-directory test-paged-stress-$$t; \
	done

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

test-alloc-heavy: $(PICOJVM) tests/AllocHeavyTest.pjvm
	$(MAKE) --no-print-directory test-AllocHeavyTest

test-paged-alloc-heavy: $(PICOJVM_PAGED) tests/AllocHeavyTest.pjvm
	$(MAKE) --no-print-directory test-paged-AllocHeavyTest

test-gc-alloc-heavy:
	$(MAKE) --no-print-directory clean
	$(MAKE) --no-print-directory test-AllocHeavyTest \
		HOST_VM_OPTS='-DPJVM_HEAP_MODE=PJVM_HEAP_FREELIST -DPJVM_GC_TRIGGERS=3 -DPJVM_GC_WATERMARK_PCT=75 -DPJVM_HOST_HEAP_LIMIT=2049'

# --- 8085 simulator target ---

$(BUILDDIR):
	mkdir -p $(BUILDDIR)

# Convert .pjvm binary to C source with const array
$(PJVM_DATA_C): FORCE $(PJVM_FILE) | $(BUILDDIR)
	@$(PYTHON) -c "import pathlib, sys; d = pathlib.Path(sys.argv[1]).read_bytes(); \
lines = ',\\n'.join('    ' + ', '.join(f'0x{b:02x}' for b in d[i:i+16]) for i in range(0, len(d), 16)); \
pathlib.Path(sys.argv[2]).write_text('// Auto-generated — .pjvm program data\\n#include <stdint.h>\\nconst uint8_t pjvm_program[] = {\\n' + lines + '\\n};\\n')" \
		$(PJVM_FILE) $@

$(BUILDDIR)/crt0.o: $(CRT) | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) -c $< -o $@

$(BUILDDIR)/pjvm.o: src/pjvm.c src/pjvm.h | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) $(SIM_CAPS) $(TARGET_VM_OPTS) $(TARGET_ASM_HELPERS_DEF) -c $< -o $@

$(BUILDDIR)/pjvm_heap.o: src/pjvm_heap.c src/pjvm.h | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -ffunction-sections -$(TARGET_OPT) $(SIM_CAPS) $(TARGET_VM_OPTS) -c $< -o $@

$(BUILDDIR)/pjvm_gc.o: src/pjvm_gc.c src/pjvm.h | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -ffunction-sections -$(TARGET_OPT) $(SIM_CAPS) $(TARGET_VM_OPTS) -c $< -o $@

$(BUILDDIR)/i8085_sim.o: platform/i8085_sim.c src/pjvm.h | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) $(SIM_CAPS) $(TARGET_VM_OPTS) $(TARGET_ASM_HELPERS_DEF) -c $< -o $@

$(BUILDDIR)/i8085_helpers.o: platform/i8085_helpers.S | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf $(TARGET_VM_OPTS) -DPJVM_ASM_HELPERS -c $< -o $@

$(PJVM_DATA_O): $(PJVM_DATA_C) | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) -c $< -o $@

$(BUILDDIR)/%.sim.dump: tests/%.pjvm | $(BUILDDIR)
	@$(MAKE) --no-print-directory sim-$* TARGET_VM_OPTS="$(TARGET_VM_OPTS)" TARGET_ASM_HELPERS="$(TARGET_ASM_HELPERS)" SIM_DUMP_ADDR="$(SIM_DUMP_ADDR)" > $@ 2>&1

$(TARGET_ELF): $(BUILDDIR)/crt0.o $(BUILDDIR)/pjvm.o $(BUILDDIR)/pjvm_heap.o $(BUILDDIR)/pjvm_gc.o $(BUILDDIR)/i8085_sim.o $(TARGET_HELPER_OBJS) $(PJVM_DATA_O) $(LIBGCC) $(LIBC)
	$(LLD) -m i8085elf --gc-sections -T $(LDSCRIPT) -o $@ $^ $(LIBGCC)

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
	@for t in Fib RomStringTest NativeOpsTest; do \
		$(MAKE) --no-print-directory test-sim-$$t; \
	done

clean:
	rm -f $(PICOJVM) $(PICOJVM_PAGED) tests/*.class tests/*.pjvm tests/*.pjvmmap
	rm -rf $(BUILDDIR)

.PHONY: FORCE
.PHONY: all test test-paged test-paged-stress clean sim
.PHONY: gc-demo-manual gc-demo-allocfail gc-demo-watermark75 gc-demo-return gc-demo-random
.PHONY: gc-policy-test test-gc-collect test-alloc-heavy test-paged-alloc-heavy test-gc-alloc-heavy test-sim-smoke
