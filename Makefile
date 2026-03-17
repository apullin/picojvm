CC      = cc
CFLAGS  = -Wall -Wextra -O2
HOST_VM_DEBUG = -DPJVM_DEBUG_TOOLS
JAVAC   = javac
PYTHON  = python3
PICOJVM = ./picojvm
EXPDIR  = expected

# Single-class tests
TESTS_SINGLE = Fib HelloWorld BubbleSort Counter StringTest RomStringTest StaticInitTest MultiArrayTest StringSwitchTest ConstTest
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
CRT      = $(ROOT)/sysroot/crt/crt0.S
LIBGCC   = $(ROOT)/sysroot/lib/libgcc.a
LIBC     = $(ROOT)/sysroot/lib/libc.a
LDSCRIPT = $(ROOT)/sysroot/ldscripts/i8085-32kram-32krom.ld
TARGET_OPT = Oz
BUILDDIR = build

PICOJVM_PAGED = ./picojvm-paged

# Capacity overrides for 8085 target (smaller than host defaults)
SIM_CAPS = -DPJVM_METHOD_CAP=64 -DPJVM_CLASS_CAP=16 -DPJVM_VTABLE_CAP=128 \
           -DPJVM_STATIC_CAP=32 -DPJVM_MAX_STACK=64 -DPJVM_MAX_LOCALS=128 \
           -DPJVM_MAX_FRAMES=16

all: $(PICOJVM)

$(PICOJVM): src/pjvm.c platform/host.c src/pjvm.h
	$(CC) $(CFLAGS) $(HOST_VM_DEBUG) -DPJVM_MAX_FRAMES=128 -o $@ src/pjvm.c platform/host.c

$(PICOJVM_PAGED): src/pjvm.c platform/host.c src/pjvm.h
	$(CC) $(CFLAGS) $(HOST_VM_DEBUG) -DPJVM_PAGED -o $@ src/pjvm.c platform/host.c

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

# --- 8085 simulator target ---

$(BUILDDIR):
	mkdir -p $(BUILDDIR)

# Convert .pjvm binary to C source with const array
$(BUILDDIR)/pjvm_data.c: | $(BUILDDIR)
	@echo "// Auto-generated — .pjvm program data" > $@
	@echo "#include <stdint.h>" >> $@
	@echo "const uint8_t pjvm_program[] = {" >> $@
	@$(PYTHON) -c "import sys; d=open(sys.argv[1],'rb').read(); \
		lines=['    '+', '.join(f'0x{b:02x}' for b in d[i:i+16]) for i in range(0,len(d),16)]; \
		print(',\n'.join(lines))" $(PJVM_FILE) >> $@
	@echo "};" >> $@

$(BUILDDIR)/crt0.o: $(CRT) | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) -c $< -o $@

$(BUILDDIR)/pjvm.o: src/pjvm.c src/pjvm.h | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) $(SIM_CAPS) -DPJVM_ASM_HELPERS -c $< -o $@

$(BUILDDIR)/i8085_sim.o: platform/i8085_sim.c src/pjvm.h | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) $(SIM_CAPS) -DPJVM_ASM_HELPERS -c $< -o $@

$(BUILDDIR)/i8085_helpers.o: platform/i8085_helpers.S | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -DPJVM_ASM_HELPERS -c $< -o $@

$(BUILDDIR)/pjvm_data.o: $(BUILDDIR)/pjvm_data.c | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) -c $< -o $@

$(BUILDDIR)/picojvm.elf: $(BUILDDIR)/crt0.o $(BUILDDIR)/pjvm.o $(BUILDDIR)/i8085_sim.o $(BUILDDIR)/i8085_helpers.o $(BUILDDIR)/pjvm_data.o $(LIBGCC) $(LIBC)
	$(LLD) -m i8085elf --gc-sections -T $(LDSCRIPT) -o $@ $^ $(LIBGCC)

$(BUILDDIR)/picojvm.bin: $(BUILDDIR)/picojvm.elf
	$(OBJCOPY) -O binary $< $@

# Build for 8085 simulator: make sim PJVM_FILE=tests/Fib.pjvm
sim: $(BUILDDIR)/picojvm.bin
	@$(SIZE) $(BUILDDIR)/picojvm.elf
	@echo "--- Running on 8085 simulator ---"
	$(TRACE) -e 0x0000 -l 0x0000 -n 50000000 -S -q -d 0x7000:128 $(BUILDDIR)/picojvm.bin

# Build + run a specific test: make sim-Fib
sim-%: tests/%.pjvm
	$(MAKE) sim PJVM_FILE=tests/$*.pjvm

clean:
	rm -f $(PICOJVM) $(PICOJVM_PAGED) tests/*.class tests/*.pjvm tests/*.pjvmmap
	rm -rf $(BUILDDIR)

.PHONY: all test test-paged test-paged-stress clean sim
