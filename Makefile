CC      = cc
CFLAGS  = -Wall -Wextra -O2
JAVAC   = javac
PYTHON  = python3
PICOJVM = ./picojvm

# Single-class tests
TESTS_SINGLE = tests/Fib tests/HelloWorld tests/BubbleSort tests/Counter tests/StringTest tests/StaticInitTest tests/MultiArrayTest tests/StringSwitchTest

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
TARGET_OPT = Os
BUILDDIR = build

PICOJVM_PAGED = ./picojvm-paged

# Capacity overrides for 8085 target (smaller than host defaults)
SIM_CAPS = -DPJVM_METHOD_CAP=64 -DPJVM_CLASS_CAP=16 -DPJVM_VTABLE_CAP=128 \
           -DPJVM_STATIC_CAP=32 -DPJVM_MAX_STACK=64 -DPJVM_MAX_LOCALS=128 \
           -DPJVM_MAX_FRAMES=16

all: $(PICOJVM)

$(PICOJVM): core.c platform/host.c pjvm.h
	$(CC) $(CFLAGS) -o $@ core.c platform/host.c

$(PICOJVM_PAGED): core.c platform/host.c pjvm.h
	$(CC) $(CFLAGS) -DPJVM_PAGED -o $@ core.c platform/host.c

# Compile all test .java files
tests/%.class: tests/%.java tests/Native.java
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

# Run a test on host
run-%: $(PICOJVM) tests/%.pjvm
	$(PICOJVM) tests/$*.pjvm

# Run all tests on host
test: $(PICOJVM) $(addsuffix .pjvm,$(TESTS_SINGLE)) tests/Shapes.pjvm tests/Features.pjvm tests/InterfaceTest.pjvm tests/ExceptionTest.pjvm
	@for t in $(TESTS_SINGLE); do \
		echo "=== $$(basename $$t) ==="; \
		$(PICOJVM) $$t.pjvm; \
		echo ""; \
	done
	@echo "=== Shapes ==="
	$(PICOJVM) tests/Shapes.pjvm
	@echo ""
	@echo "=== Features ==="
	$(PICOJVM) tests/Features.pjvm
	@echo ""
	@echo "=== InterfaceTest ==="
	$(PICOJVM) tests/InterfaceTest.pjvm
	@echo ""
	@echo "=== ExceptionTest ==="
	$(PICOJVM) tests/ExceptionTest.pjvm
	@echo ""

# Paged-mode run and test
run-paged-%: $(PICOJVM_PAGED) tests/%.pjvm
	$(PICOJVM_PAGED) tests/$*.pjvm

test-paged: $(PICOJVM_PAGED) $(addsuffix .pjvm,$(TESTS_SINGLE)) tests/Shapes.pjvm tests/Features.pjvm tests/InterfaceTest.pjvm tests/ExceptionTest.pjvm
	@for t in $(TESTS_SINGLE); do \
		echo "=== $$(basename $$t) [paged] ==="; \
		$(PICOJVM_PAGED) $$t.pjvm; \
		echo ""; \
	done
	@echo "=== Shapes [paged] ==="
	$(PICOJVM_PAGED) tests/Shapes.pjvm
	@echo ""
	@echo "=== Features [paged] ==="
	$(PICOJVM_PAGED) tests/Features.pjvm
	@echo ""
	@echo "=== InterfaceTest [paged] ==="
	$(PICOJVM_PAGED) tests/InterfaceTest.pjvm
	@echo ""
	@echo "=== ExceptionTest [paged] ==="
	$(PICOJVM_PAGED) tests/ExceptionTest.pjvm
	@echo ""

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

$(BUILDDIR)/core.o: core.c pjvm.h | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) $(SIM_CAPS) -DPJVM_ASM_HELPERS -c $< -o $@

$(BUILDDIR)/i8085_sim.o: platform/i8085_sim.c pjvm.h | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) $(SIM_CAPS) -DPJVM_ASM_HELPERS -c $< -o $@

$(BUILDDIR)/i8085_helpers.o: platform/i8085_helpers.S | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -c $< -o $@

$(BUILDDIR)/pjvm_data.o: $(BUILDDIR)/pjvm_data.c | $(BUILDDIR)
	$(CLANG) --target=i8085-unknown-elf -ffreestanding -fno-builtin -$(TARGET_OPT) -c $< -o $@

$(BUILDDIR)/picojvm.elf: $(BUILDDIR)/crt0.o $(BUILDDIR)/core.o $(BUILDDIR)/i8085_sim.o $(BUILDDIR)/i8085_helpers.o $(BUILDDIR)/pjvm_data.o $(LIBGCC) $(LIBC)
	$(LLD) -m i8085elf --gc-sections -T $(LDSCRIPT) -o $@ $^ $(LIBGCC)

$(BUILDDIR)/picojvm.bin: $(BUILDDIR)/picojvm.elf
	$(OBJCOPY) -O binary $< $@

# Build for 8085 simulator: make sim PJVM_FILE=tests/Fib.pjvm
sim: $(BUILDDIR)/picojvm.bin
	@$(SIZE) $(BUILDDIR)/picojvm.elf
	@echo "--- Running on 8085 simulator ---"
	$(TRACE) -e 0x0000 -l 0x0000 -n 50000000 -S -q -d 0x0200:64 $(BUILDDIR)/picojvm.bin

# Build + run a specific test: make sim-Fib
sim-%: tests/%.pjvm
	$(MAKE) sim PJVM_FILE=tests/$*.pjvm

clean:
	rm -f $(PICOJVM) $(PICOJVM_PAGED) tests/*.class tests/*.pjvm tests/*.pjvmmap
	rm -rf $(BUILDDIR)

.PHONY: all test test-paged clean sim
