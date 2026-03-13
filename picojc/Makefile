# picojc — Java compiler for picoJVM
# Phase 0: javac compiles picojc, pjvmpack packs it to .pjvm
# Then picojc.pjvm runs on picoJVM to compile test programs

JAVAC   = javac
PYTHON  = python3
PICOJVM = ../picojvm
PJVMPACK = ../pjvmpack.py

SRCDIR  = src
BUILDDIR = build
TESTDIR = tests
EXPDIR  = expected

# All compiler source files
SRCS = $(SRCDIR)/Compiler.java $(SRCDIR)/Token.java $(SRCDIR)/Lexer.java $(SRCDIR)/Native.java \
       $(SRCDIR)/Catalog.java $(SRCDIR)/Resolver.java $(SRCDIR)/Emit.java \
       $(SRCDIR)/Stmt.java $(SRCDIR)/Expr.java $(SRCDIR)/Linker.java

# Test programs
TESTS = T01_Empty T02_Literal T03_Arithmetic T04_IfElse T05_WhileLoop \
        T06_ForLoop T07_MethodCall T08_IntArray T09_Bitwise T10_StringLiteral \
        T11_SimpleClass T12_DoWhile T13_BreakContinue T14_Ternary \
        T15_ObjectCreate T16_Constructor T17_Inheritance T18_StaticInit T19_InstanceOf \
        T20_NullRef T21_ObjectArray T22_Switch \
        T24_Exception T25_TypeConversion \
        T27_Fib T28_BubbleSort T29_StringTest T30_MultiClass \
        T31_Shapes T32_ExceptionTest T33_Counter T34_StaticInit T35_ByteArray \
        T36_CharArray T37_Recursion T38_LinkedList T39_NativeOps
# TODO: T23_StringSwitch (needs hashCode/equals pattern), T26_Interface (vmid dispatch)

# Tests requiring lib/ (platform package)
LIB_SRCS = ../lib/Console.java ../lib/Arrays.java ../lib/IO.java
LIB_TESTS = T40_PlatformPkg

all: $(BUILDDIR)/picojc.pjvm

# Step 1: Compile picojc Java sources with javac
$(BUILDDIR)/classes: $(SRCS)
	@mkdir -p $(BUILDDIR)/classes
	$(JAVAC) -d $(BUILDDIR)/classes $(SRCS)
	@touch $@

# Step 2: Pack into .pjvm using pjvmpack
$(BUILDDIR)/picojc.pjvm: $(BUILDDIR)/classes
	$(PYTHON) $(PJVMPACK) \
		$(BUILDDIR)/classes/Compiler.class \
		$(BUILDDIR)/classes/Token.class \
		$(BUILDDIR)/classes/Lexer.class \
		$(BUILDDIR)/classes/Native.class \
		$(BUILDDIR)/classes/Catalog.class \
		$(BUILDDIR)/classes/Resolver.class \
		$(BUILDDIR)/classes/Emit.class \
		$(BUILDDIR)/classes/Stmt.class \
		$(BUILDDIR)/classes/Expr.class \
		$(BUILDDIR)/classes/Linker.class \
		-o $@ -v

# Compile a lib-dependent test: cat lib/*.java + test.java, then compile
$(BUILDDIR)/lib_%.java: $(TESTDIR)/%.java $(LIB_SRCS)
	@mkdir -p $(BUILDDIR)
	cat $(LIB_SRCS) $< > $@

$(BUILDDIR)/lib_%.pjvm: $(BUILDDIR)/lib_%.java $(BUILDDIR)/picojc.pjvm $(PICOJVM)
	$(PICOJVM) $(BUILDDIR)/picojc.pjvm \
		--preload 0xC000:$< \
		--save-raw 0xC000:/dev/null \
		> $@ 2> $(BUILDDIR)/lib_$*_compile.log
	@echo "Compiled $< -> $@ ($$(wc -c < $@) bytes)"
	@cat $(BUILDDIR)/lib_$*_compile.log | grep -E '^(picoJVM|HALT|PAGE)' || true

$(BUILDDIR)/lib_%.out: $(BUILDDIR)/lib_%.pjvm $(PICOJVM)
	$(PICOJVM) $< > $@ 2> $(BUILDDIR)/lib_$*_run.log || true
	@cat $(BUILDDIR)/lib_$*_run.log | grep -E '^(picoJVM|HALT)' || true

test-lib-%: $(BUILDDIR)/lib_%.out $(EXPDIR)/%.txt
	@if diff -q $(BUILDDIR)/lib_$*.out $(EXPDIR)/$*.txt > /dev/null 2>&1; then \
		echo "PASS: $* (lib)"; \
	else \
		echo "FAIL: $* (lib)"; \
		echo "  Expected:"; cat $(EXPDIR)/$*.txt | head -5; \
		echo "  Got:"; cat $(BUILDDIR)/lib_$*.out | head -5; \
		false; \
	fi

# Compile a test program using picojc running on picoJVM
# Usage: make test-T01_Empty
$(BUILDDIR)/%.pjvm: $(TESTDIR)/%.java $(BUILDDIR)/picojc.pjvm $(PICOJVM)
	$(PICOJVM) $(BUILDDIR)/picojc.pjvm \
		--preload 0xC000:$< \
		--save-raw 0xC000:/dev/null \
		> $@ 2> $(BUILDDIR)/$*_compile.log
	@echo "Compiled $< -> $@ ($$(wc -c < $@) bytes)"
	@cat $(BUILDDIR)/$*_compile.log | grep -E '^(picoJVM|HALT|PAGE)' || true

# Run a compiled test and capture output
$(BUILDDIR)/%.out: $(BUILDDIR)/%.pjvm $(PICOJVM)
	$(PICOJVM) $< > $@ 2> $(BUILDDIR)/$*_run.log || true
	@cat $(BUILDDIR)/$*_run.log | grep -E '^(picoJVM|HALT)' || true

# Test a single program: compile with picojc, run, compare output
test-%: $(BUILDDIR)/%.out $(EXPDIR)/%.txt
	@if diff -q $(BUILDDIR)/$*.out $(EXPDIR)/$*.txt > /dev/null 2>&1; then \
		echo "PASS: $*"; \
	else \
		echo "FAIL: $*"; \
		echo "  Expected:"; cat $(EXPDIR)/$*.txt | head -5; \
		echo "  Got:"; cat $(BUILDDIR)/$*.out | head -5; \
		false; \
	fi

# Run all tests
test: $(PICOJVM) $(BUILDDIR)/picojc.pjvm
	@mkdir -p $(BUILDDIR)
	@pass=0; fail=0; \
	for t in $(TESTS); do \
		echo "--- $$t ---"; \
		if $(MAKE) --no-print-directory test-$$t 2>&1; then \
			pass=$$((pass + 1)); \
		else \
			fail=$$((fail + 1)); \
		fi; \
	done; \
	echo ""; \
	for t in $(LIB_TESTS); do \
		echo "--- $$t (lib) ---"; \
		if $(MAKE) --no-print-directory test-lib-$$t 2>&1; then \
			pass=$$((pass + 1)); \
		else \
			fail=$$((fail + 1)); \
		fi; \
	done; \
	echo ""; \
	total=$$((pass + fail)); \
	echo "Results: $$pass passed, $$fail failed out of $$total tests"; \
	test $$fail -eq 0

# Quick compile check: just build picojc.pjvm
build: $(BUILDDIR)/picojc.pjvm

# Verify picojc compiles with javac (no runtime errors)
check-javac: $(BUILDDIR)/classes
	@echo "javac compilation successful"

# --- Self-hosting regression tests ---
# Pipeline: ref (javac→pjvmpack) → gen1 (ref compiles picojc on picoJVM)
#         → gen2 (gen1 compiles itself) → verify gen1==gen2

# Concatenate compiler sources in the order picojc expects
$(BUILDDIR)/compiler_src.java: $(SRCS)
	@mkdir -p $(BUILDDIR)
	cat $(SRCDIR)/Token.java $(SRCDIR)/Lexer.java $(SRCDIR)/Native.java \
		$(SRCDIR)/Compiler.java $(SRCDIR)/Catalog.java $(SRCDIR)/Resolver.java \
		$(SRCDIR)/Emit.java $(SRCDIR)/Stmt.java $(SRCDIR)/Expr.java $(SRCDIR)/Linker.java > $@

# gen1: reference picojc compiles itself
$(BUILDDIR)/gen1.pjvm: $(BUILDDIR)/picojc.pjvm $(BUILDDIR)/compiler_src.java $(PICOJVM)
	$(PICOJVM) $(BUILDDIR)/picojc.pjvm \
		--preload 0xC000:$(BUILDDIR)/compiler_src.java \
		--save-raw 0xC000:/dev/null \
		> $@ 2> $(BUILDDIR)/gen1_compile.log
	@echo "gen1: $$(wc -c < $@ | tr -d ' ') bytes"

# gen2: gen1 compiles itself
$(BUILDDIR)/gen2.pjvm: $(BUILDDIR)/gen1.pjvm $(BUILDDIR)/compiler_src.java $(PICOJVM)
	$(PICOJVM) $(BUILDDIR)/gen1.pjvm \
		--preload 0xC000:$(BUILDDIR)/compiler_src.java \
		--save-raw 0xC000:/dev/null \
		> $@ 2> $(BUILDDIR)/gen2_compile.log
	@echo "gen2: $$(wc -c < $@ | tr -d ' ') bytes"

# Fixed-point test: gen1 == gen2
test-selfhost-fixpoint: $(BUILDDIR)/gen1.pjvm $(BUILDDIR)/gen2.pjvm
	@if cmp -s $(BUILDDIR)/gen1.pjvm $(BUILDDIR)/gen2.pjvm; then \
		echo "PASS: gen1 == gen2 (fixed point, $$(wc -c < $(BUILDDIR)/gen1.pjvm | tr -d ' ') bytes)"; \
	else \
		echo "FAIL: gen1 != gen2"; \
		echo "  gen1: $$(wc -c < $(BUILDDIR)/gen1.pjvm | tr -d ' ') bytes"; \
		echo "  gen2: $$(wc -c < $(BUILDDIR)/gen2.pjvm | tr -d ' ') bytes"; \
		false; \
	fi

# Compile a test with gen1
$(BUILDDIR)/gen1_%.pjvm: $(TESTDIR)/%.java $(BUILDDIR)/gen1.pjvm $(PICOJVM)
	$(PICOJVM) $(BUILDDIR)/gen1.pjvm \
		--preload 0xC000:$< \
		--save-raw 0xC000:/dev/null \
		> $@ 2> $(BUILDDIR)/gen1_$*_compile.log

# Run a gen1-compiled test
$(BUILDDIR)/gen1_%.out: $(BUILDDIR)/gen1_%.pjvm $(PICOJVM)
	$(PICOJVM) $< > $@ 2> $(BUILDDIR)/gen1_$*_run.log || true

# BIN_MATCH: ref-compiled .pjvm == gen1-compiled .pjvm
test-binmatch-%: $(BUILDDIR)/%.pjvm $(BUILDDIR)/gen1_%.pjvm
	@if cmp -s $(BUILDDIR)/$*.pjvm $(BUILDDIR)/gen1_$*.pjvm; then \
		echo "BIN_MATCH: $*"; \
	else \
		echo "FAIL: $* (ref != gen1, $$(wc -c < $(BUILDDIR)/$*.pjvm | tr -d ' ')B vs $$(wc -c < $(BUILDDIR)/gen1_$*.pjvm | tr -d ' ')B)"; \
		false; \
	fi

# All BIN_MATCH tests
test-binmatch: $(PICOJVM) $(BUILDDIR)/gen1.pjvm
	@pass=0; fail=0; \
	for t in $(TESTS); do \
		if $(MAKE) --no-print-directory test-binmatch-$$t 2>/dev/null; then \
			pass=$$((pass + 1)); \
		else \
			fail=$$((fail + 1)); \
		fi; \
	done; \
	echo ""; \
	echo "BIN_MATCH: $$pass passed, $$fail failed out of $$(echo $(TESTS) | wc -w | tr -d ' ')"; \
	test $$fail -eq 0

# gen1 output correctness: compile with gen1, run, compare expected output
test-gen1-%: $(BUILDDIR)/gen1_%.out $(EXPDIR)/%.txt
	@if diff -q $(BUILDDIR)/gen1_$*.out $(EXPDIR)/$*.txt > /dev/null 2>&1; then \
		echo "PASS: $* (gen1)"; \
	else \
		echo "FAIL: $* (gen1)"; \
		echo "  Expected:"; cat $(EXPDIR)/$*.txt | head -5; \
		echo "  Got:"; cat $(BUILDDIR)/gen1_$*.out | head -5; \
		false; \
	fi

# All gen1 output tests
test-gen1: $(PICOJVM) $(BUILDDIR)/gen1.pjvm
	@pass=0; fail=0; \
	for t in $(TESTS); do \
		if $(MAKE) --no-print-directory test-gen1-$$t 2>&1; then \
			pass=$$((pass + 1)); \
		else \
			fail=$$((fail + 1)); \
		fi; \
	done; \
	echo ""; \
	echo "gen1 output: $$pass passed, $$fail failed out of $$(echo $(TESTS) | wc -w | tr -d ' ')"; \
	test $$fail -eq 0

# Full self-hosting regression suite
test-selfhost: test-selfhost-fixpoint test-binmatch test-gen1
	@echo ""
	@echo "=== Self-hosting regression: ALL PASSED ==="

clean:
	rm -rf $(BUILDDIR)

.PHONY: all test build check-javac clean
.PHONY: test-selfhost test-selfhost-fixpoint test-binmatch test-gen1
.PRECIOUS: $(BUILDDIR)/%.pjvm $(BUILDDIR)/%.out
.PRECIOUS: $(BUILDDIR)/gen1_%.pjvm $(BUILDDIR)/gen1_%.out
.PRECIOUS: $(BUILDDIR)/lib_%.java $(BUILDDIR)/lib_%.pjvm $(BUILDDIR)/lib_%.out
