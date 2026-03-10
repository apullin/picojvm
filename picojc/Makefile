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
SRCS = $(SRCDIR)/Compiler.java $(SRCDIR)/Token.java $(SRCDIR)/Lexer.java $(SRCDIR)/Native.java

# Test programs
TESTS = T01_Empty T02_Literal T03_Arithmetic T04_IfElse T05_WhileLoop \
        T06_ForLoop T07_MethodCall T08_IntArray T09_Bitwise T10_StringLiteral \
        T11_SimpleClass T12_DoWhile T13_BreakContinue T14_Ternary \
        T15_ObjectCreate T16_Constructor T17_Inheritance T18_StaticInit T19_InstanceOf \
        T20_NullRef T21_ObjectArray T22_Switch \
        T24_Exception T25_TypeConversion \
        T27_Fib T28_BubbleSort T29_StringTest T30_MultiClass \
        T31_Shapes T32_ExceptionTest T33_Counter T34_StaticInit T35_ByteArray \
        T36_CharArray T37_Recursion T38_LinkedList
# TODO: T23_StringSwitch (needs hashCode/equals pattern), T26_Interface (vmid dispatch)

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
		-o $@ -v

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
	echo "Results: $$pass passed, $$fail failed out of $$(echo $(TESTS) | wc -w | tr -d ' ') tests"; \
	test $$fail -eq 0

# Quick compile check: just build picojc.pjvm
build: $(BUILDDIR)/picojc.pjvm

# Verify picojc compiles with javac (no runtime errors)
check-javac: $(BUILDDIR)/classes
	@echo "javac compilation successful"

clean:
	rm -rf $(BUILDDIR)

.PHONY: all test build check-javac clean
.PRECIOUS: $(BUILDDIR)/%.pjvm $(BUILDDIR)/%.out
