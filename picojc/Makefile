# picojc — Java compiler for picoJVM
# Phase 0: javac compiles picojc, pjvmpack packs it to .pjvm
# Then picojc.pjvm runs on picoJVM to compile test programs

.DELETE_ON_ERROR:

JAVAC   = javac
PYTHON  = python3
CC      = cc
CFLAGS  = -Wall -Wextra -O2
PICOJVM = ../picojvm
PJVMPACK = ../pjvmpack.py

# Build the host VM via the parent Makefile so the suite runs from a clean
# checkout (the parent's flags stamp keeps it fresh across option changes).
$(PICOJVM): FORCE
	@$(MAKE) --no-print-directory -C .. picojvm

.PHONY: FORCE

SRCDIR  = src
BUILDDIR = build
TESTDIR = tests
EXPDIR  = expected
NEGDIR  = tests-negative
PKGDIR  = tests-pkg
PICOJSE_DIR = ../pj
JAVA_LANG_DIR = ../java/lang
PICOJSE_TESTDIR = ../tests

# All compiler source files
SRCS = $(SRCDIR)/Compiler.java $(SRCDIR)/Token.java $(SRCDIR)/Lexer.java $(SRCDIR)/Native.java \
       $(SRCDIR)/Catalog.java $(SRCDIR)/Resolver.java $(SRCDIR)/Emit.java \
       $(SRCDIR)/Stmt.java $(SRCDIR)/Expr.java $(SRCDIR)/Linker.java \
       $(SRCDIR)/DiskMain.java

# Test programs
TESTS = T01_Empty T02_Literal T03_Arithmetic T04_IfElse T05_WhileLoop \
        T06_ForLoop T07_MethodCall T08_IntArray T09_Bitwise T10_StringLiteral \
        T11_SimpleClass T12_DoWhile T13_BreakContinue T14_Ternary \
        T15_ObjectCreate T16_Constructor T17_Inheritance T18_StaticInit T19_InstanceOf \
        T20_NullRef T21_ObjectArray T22_Switch T23_StringSwitch \
        T24_Exception T25_TypeConversion T26_Interface \
        T27_Fib T28_BubbleSort T29_StringTest T30_MultiClass \
        T31_Shapes T32_ExceptionTest T33_Counter T34_StaticInit T35_ByteArray \
        T36_CharArray T37_Recursion T38_LinkedList T39_NativeOps T41_FileIO \
        T42_ShortArray T43_FinalInline T44_Enum T45_ForEach T46_Varargs \
        T47_MultiDimArray T48_CompoundAssign T49_MultiInterface T50_SwitchEdge \
        T51_ObjectCast T52_CommaLocal T53_Precedence T54_InheritField \
        T55_ExactDispatch T56_OverloadDispatch T57_InheritedOverload \
        T58_Super T59_NarrowWrap T60_NarrowAssign T62_ArrayInit \
	        T63_PackageLocal T65_BigArrayInit T66_Const T67_HybridSwitch \
	        T68_ArrayField T69_StaticIntArrayInit T70_ArrayElemCompound \
	        T71_ObjectFieldCompound T72_BooleanOps T73_MethodArrayReturn \
	        T74_ParenIdent T75_SwitchControl T76_ConstFieldInit \
	        T77_InstanceFieldInit T78_FieldHiding

NEGTESTS = N01_InvalidStaticCall N02_OverloadSameClass \
           N03_OverloadInherited N04_SwitchTooManyCases \
           N05_InvalidLocalNarrowing N06_InvalidStaticInitNarrowing \
           N07_InvalidFieldAssignNarrowing N08_InvalidReturnNarrowing \
           N09_InvalidMainSig N10_FieldAssignExprValue \
           N11_ArrayAssignExprValue N12_InvalidRefLocalPostInc \
           N13_InvalidRefLocalCompound N14_InvalidFieldPostInc \
           N15_InvalidObjArrayElemCompound N16_InvalidLiteralPostInc \
	           N17_InvalidBooleanLocalUpdate N18_InvalidBooleanArrayUpdate \
	           N19_InvalidBooleanArithmetic N20_InvalidBooleanLogicalMixed \
	           N21_InvalidBooleanNot N22_InvalidEqualityKinds \
	           N23_InvalidInstanceofScalar N24_InvalidIfCondition \
	           N25_InvalidTernaryCondition N26_InvalidRefLocalInit \
	           N27_InvalidStaticRefInit N28_InvalidRefAssign \
	           N29_InvalidObjArrayStore N30_InvalidRefReturn \
	           N31_InvalidThrowScalar N32_InvalidObjArrayInit \
	           N33_CaseIdent N34_ContinueOutsideLoop \
	           N35_ReturnInFinally N36_ComplexInstanceInit \
	           N37_HiddenInstanceAsStatic N38_HiddenStaticViaObject

# Tests requiring lib/ (platform package)
LIB_SRCS = ../lib/Console.java ../lib/Arrays.java ../lib/IO.java
LIB_TESTS = T40_PlatformPkg
ARGV_TESTS = T61_MainArgs

all: $(BUILDDIR)/picojc.pjvm

# Step 1: Compile picojc Java sources with javac
$(BUILDDIR)/classes: $(SRCS)
	@mkdir -p $(BUILDDIR)/classes
	$(JAVAC) -d $(BUILDDIR)/classes $(SRCS)
	@touch $@

# Step 2: Pack into .pjvm using pjvmpack
$(BUILDDIR)/picojc.pjvm: $(BUILDDIR)/classes
	$(PYTHON) $(PJVMPACK) \
		$(BUILDDIR)/classes/C.class \
		$(BUILDDIR)/classes/Tk.class \
		$(BUILDDIR)/classes/Lexer.class \
		$(BUILDDIR)/classes/Native.class \
		$(BUILDDIR)/classes/Catalog.class \
		$(BUILDDIR)/classes/Resolver.class \
		$(BUILDDIR)/classes/E.class \
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

$(BUILDDIR)/T61_MainArgs.out: $(BUILDDIR)/T61_MainArgs.pjvm $(PICOJVM)
	$(PICOJVM) $< -- alpha beta z > $@ 2> $(BUILDDIR)/T61_MainArgs_run.log || true
	@cat $(BUILDDIR)/T61_MainArgs_run.log | grep -E '^(picoJVM|HALT)' || true

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

# Compile a negative test and capture the compiler error text
$(BUILDDIR)/neg_%.err: $(NEGDIR)/%.java $(BUILDDIR)/picojc.pjvm $(PICOJVM)
	$(PICOJVM) $(BUILDDIR)/picojc.pjvm \
		--preload 0xC000:$< \
		--save-raw 0xC000:/dev/null \
		> $@ 2> $(BUILDDIR)/neg_$*_compile.log || true
	@cat $(BUILDDIR)/neg_$*_compile.log | grep -E '^(picoJVM|HALT|PAGE)' || true

# Test a negative program: compile should fail with the expected error code regex
test-negative-%: $(BUILDDIR)/neg_%.err $(NEGDIR)/%.re
	@if grep -Eq -f $(NEGDIR)/$*.re $(BUILDDIR)/neg_$*.err; then \
		echo "PASS: $* (negative)"; \
	else \
		echo "FAIL: $* (negative)"; \
		echo "  Expected regex:"; cat $(NEGDIR)/$*.re; \
		echo "  Got:"; cat $(BUILDDIR)/neg_$*.err | head -5; \
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
	for t in $(ARGV_TESTS); do \
		echo "--- $$t (argv) ---"; \
		if $(MAKE) --no-print-directory test-$$t 2>&1; then \
			pass=$$((pass + 1)); \
		else \
			fail=$$((fail + 1)); \
		fi; \
	done; \
	echo ""; \
	total=$$((pass + fail)); \
	echo "Results: $$pass passed, $$fail failed out of $$total tests"; \
	test $$fail -eq 0

test-negative: $(PICOJVM) $(BUILDDIR)/picojc.pjvm
	@mkdir -p $(BUILDDIR)
	@pass=0; fail=0; \
	for t in $(NEGTESTS); do \
		echo "--- $$t ---"; \
		if $(MAKE) --no-print-directory test-negative-$$t 2>&1; then \
			pass=$$((pass + 1)); \
		else \
			fail=$$((fail + 1)); \
		fi; \
	done; \
	echo ""; \
	echo "Negative tests: $$pass passed, $$fail failed out of $$(echo $(NEGTESTS) | wc -w | tr -d ' ')"; \
	test $$fail -eq 0

# --- Disk-backed compiler ---
# picojc-disk.pjvm: same compiler, but reads "input.java" from disk instead of --preload

$(BUILDDIR)/picojc-disk.pjvm: $(BUILDDIR)/classes
	$(PYTHON) $(PJVMPACK) \
		$(BUILDDIR)/classes/DiskMain.class \
		$(BUILDDIR)/classes/C.class \
		$(BUILDDIR)/classes/Tk.class \
		$(BUILDDIR)/classes/Lexer.class \
		$(BUILDDIR)/classes/Native.class \
		$(BUILDDIR)/classes/Catalog.class \
		$(BUILDDIR)/classes/Resolver.class \
		$(BUILDDIR)/classes/E.class \
		$(BUILDDIR)/classes/Stmt.class \
		$(BUILDDIR)/classes/Expr.class \
		$(BUILDDIR)/classes/Linker.class \
		-o $@ -v

# End-to-end disk test: compile a test program via disk I/O, run it, verify output
DISK_PICOJVM = $(abspath $(PICOJVM))
DISK_PICOJVM_BIG = $(abspath $(BUILDDIR)/picojvm-bigheap)
DISK_COMPILER = $(abspath $(BUILDDIR)/picojc-disk.pjvm)

$(BUILDDIR)/picojvm-bigheap: ../src/pjvm.c ../src/pjvm_heap.c ../src/pjvm_gc.c ../platform/host.c ../src/pjvm.h | $(BUILDDIR)
	$(CC) $(CFLAGS) -DPJVM_DEBUG_TOOLS -DPJVM_MAX_FRAMES=128 -DPJVM_HEAP_MODE=PJVM_HEAP_FREELIST -DPJVM_GC_TRIGGERS=3 -DPJVM_GC_WATERMARK_PCT=75 -o $@ ../src/pjvm.c ../src/pjvm_heap.c ../src/pjvm_gc.c ../platform/host.c

define disk_test
	@echo "--- Disk compile: $(1) ---"
	@cp $(TESTDIR)/$(1).java $(BUILDDIR)/disk_test/input.java
	@cd $(BUILDDIR)/disk_test && $(DISK_PICOJVM) $(DISK_COMPILER) > $(1).pjvm \
		2> $(1)_compile.log
	@$(PICOJVM) $(BUILDDIR)/disk_test/$(1).pjvm > $(BUILDDIR)/disk_test/$(1).out \
		2> $(BUILDDIR)/disk_test/$(1)_run.log || true
	@if diff -q $(BUILDDIR)/disk_test/$(1).out $(EXPDIR)/$(1).txt > /dev/null 2>&1; then \
		echo "PASS: $(1) (disk)"; \
	else \
		echo "FAIL: $(1) (disk)"; \
		echo "  Expected:"; cat $(EXPDIR)/$(1).txt | head -3; \
		echo "  Got:"; cat $(BUILDDIR)/disk_test/$(1).out | head -3; \
		false; \
	fi
endef

test-disk: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm
	@mkdir -p $(BUILDDIR)/disk_test
	@rm -f $(BUILDDIR)/disk_test/sources.lst
	@pass=0; fail=0; \
	for t in $(TESTS); do \
		echo "--- $$t (disk) ---"; \
		cp $(TESTDIR)/$$t.java $(BUILDDIR)/disk_test/input.java; \
		cd $(BUILDDIR)/disk_test && $(DISK_PICOJVM) $(DISK_COMPILER) > $$t.pjvm \
			2> $${t}_compile.log; \
		cd $(CURDIR); \
		$(PICOJVM) $(BUILDDIR)/disk_test/$$t.pjvm > $(BUILDDIR)/disk_test/$$t.out \
			2> $(BUILDDIR)/disk_test/$${t}_run.log || true; \
		if diff -q $(BUILDDIR)/disk_test/$$t.out $(EXPDIR)/$$t.txt > /dev/null 2>&1; then \
			echo "PASS: $$t (disk)"; \
			pass=$$((pass + 1)); \
		else \
			echo "FAIL: $$t (disk)"; \
			echo "  Expected:"; cat $(EXPDIR)/$$t.txt | head -3; \
			echo "  Got:"; cat $(BUILDDIR)/disk_test/$$t.out | head -3; \
			fail=$$((fail + 1)); \
		fi; \
	done; \
	echo ""; \
	echo "Disk single-file: $$pass passed, $$fail failed"; \
	test $$fail -eq 0

# Multi-file disk test: compile T40 with platform libs via sources.lst
test-disk-multi: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm
	@mkdir -p $(BUILDDIR)/disk_multi_test
	@cp ../lib/Console.java $(BUILDDIR)/disk_multi_test/
	@cp ../lib/Arrays.java $(BUILDDIR)/disk_multi_test/
	@cp ../lib/IO.java $(BUILDDIR)/disk_multi_test/
	@cp $(TESTDIR)/T40_PlatformPkg.java $(BUILDDIR)/disk_multi_test/
	@printf 'Console.java\nArrays.java\nIO.java\nT40_PlatformPkg.java\n' > $(BUILDDIR)/disk_multi_test/sources.lst
	@echo "--- T40_PlatformPkg (disk multi-file) ---"
	@cd $(BUILDDIR)/disk_multi_test && $(DISK_PICOJVM) $(DISK_COMPILER) > T40.pjvm \
		2> T40_compile.log
	@$(PICOJVM) $(BUILDDIR)/disk_multi_test/T40.pjvm > $(BUILDDIR)/disk_multi_test/T40.out \
		2> $(BUILDDIR)/disk_multi_test/T40_run.log || true
	@if diff -q $(BUILDDIR)/disk_multi_test/T40.out $(EXPDIR)/T40_PlatformPkg.txt > /dev/null 2>&1; then \
		echo "PASS: T40_PlatformPkg (disk multi-file)"; \
	else \
		echo "FAIL: T40_PlatformPkg (disk multi-file)"; \
		echo "  Expected:"; cat $(EXPDIR)/T40_PlatformPkg.txt | head -3; \
		echo "  Got:"; cat $(BUILDDIR)/disk_multi_test/T40.out | head -3; \
		false; \
	fi

# Multi-file package/import test: same-package lookup plus explicit import.
test-disk-package: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm
	@mkdir -p $(BUILDDIR)/disk_pkg_test
	@cp $(PKGDIR)/Helper.java $(BUILDDIR)/disk_pkg_test/
	@cp $(PKGDIR)/Local.java $(BUILDDIR)/disk_pkg_test/
	@cp $(PKGDIR)/T64_PackageImport.java $(BUILDDIR)/disk_pkg_test/
	@printf 'Helper.java\nLocal.java\nT64_PackageImport.java\n' > $(BUILDDIR)/disk_pkg_test/sources.lst
	@echo "--- T64_PackageImport (disk package) ---"
	@cd $(BUILDDIR)/disk_pkg_test && $(DISK_PICOJVM) $(DISK_COMPILER) > T64.pjvm \
		2> T64_compile.log
	@$(PICOJVM) $(BUILDDIR)/disk_pkg_test/T64.pjvm > $(BUILDDIR)/disk_pkg_test/T64.out \
		2> $(BUILDDIR)/disk_pkg_test/T64_run.log || true
	@if diff -q $(BUILDDIR)/disk_pkg_test/T64.out $(EXPDIR)/T64_PackageImport.txt > /dev/null 2>&1; then \
		echo "PASS: T64_PackageImport (disk package)"; \
	else \
		echo "FAIL: T64_PackageImport (disk package)"; \
		echo "  Expected:"; cat $(EXPDIR)/T64_PackageImport.txt | head -3; \
		echo "  Got:"; cat $(BUILDDIR)/disk_pkg_test/T64.out | head -3; \
		false; \
	fi

# Multi-file package/library tests against the real picoJSE sources.
test-disk-picojse-term: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm
	@mkdir -p $(BUILDDIR)/disk_picojse_term
	@cp $(PICOJSE_DIR)/Native.java $(BUILDDIR)/disk_picojse_term/
	@cp $(PICOJSE_DIR)/term/Keys.java $(BUILDDIR)/disk_picojse_term/
	@cp $(PICOJSE_DIR)/term/Terminal.java $(BUILDDIR)/disk_picojse_term/
	@cp $(PICOJSE_DIR)/term/CellSurface.java $(BUILDDIR)/disk_picojse_term/
	@cp $(PICOJSE_DIR)/term/AnsiTerminal.java $(BUILDDIR)/disk_picojse_term/
	@cp $(PICOJSE_TESTDIR)/TermSmoke.java $(BUILDDIR)/disk_picojse_term/
	@printf 'Native.java\nKeys.java\nTerminal.java\nCellSurface.java\nAnsiTerminal.java\nTermSmoke.java\n' > $(BUILDDIR)/disk_picojse_term/sources.lst
	@echo "--- picoJSE TermSmoke (disk package) ---"
	@cd $(BUILDDIR)/disk_picojse_term && $(DISK_PICOJVM) $(DISK_COMPILER) > TermSmoke.pjvm \
		2> TermSmoke_compile.log
	@$(PICOJVM) $(BUILDDIR)/disk_picojse_term/TermSmoke.pjvm > $(BUILDDIR)/disk_picojse_term/TermSmoke.out \
		2> $(BUILDDIR)/disk_picojse_term/TermSmoke_run.log || true
	@if diff -q $(BUILDDIR)/disk_picojse_term/TermSmoke.out $(EXPDIR)/PicoJseTerm.txt > /dev/null 2>&1; then \
		echo "PASS: picoJSE TermSmoke (disk package)"; \
	else \
		echo "FAIL: picoJSE TermSmoke (disk package)"; \
		echo "  Expected:"; cat $(EXPDIR)/PicoJseTerm.txt | head -5; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_term/TermSmoke.out | head -5; \
		false; \
	fi

test-disk-picojse-io: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm
	@mkdir -p $(BUILDDIR)/disk_picojse_io
	@cp $(PICOJSE_DIR)/Native.java $(BUILDDIR)/disk_picojse_io/
	@cp $(PICOJSE_DIR)/io/Files.java $(BUILDDIR)/disk_picojse_io/
	@cp $(PICOJSE_TESTDIR)/FilesSmoke.java $(BUILDDIR)/disk_picojse_io/
	@printf 'Native.java\nFiles.java\nFilesSmoke.java\n' > $(BUILDDIR)/disk_picojse_io/sources.lst
	@echo "--- picoJSE FilesSmoke (disk package) ---"
	@cd $(BUILDDIR)/disk_picojse_io && $(DISK_PICOJVM) $(DISK_COMPILER) > FilesSmoke.pjvm \
		2> FilesSmoke_compile.log
	@$(PICOJVM) $(BUILDDIR)/disk_picojse_io/FilesSmoke.pjvm > $(BUILDDIR)/disk_picojse_io/FilesSmoke.out \
		2> $(BUILDDIR)/disk_picojse_io/FilesSmoke_run.log || true
	@if [ "$$(cat $(BUILDDIR)/disk_picojse_io/FilesSmoke.out)" = "PJIO" ]; then \
		echo "PASS: picoJSE FilesSmoke (disk package)"; \
	else \
		echo "FAIL: picoJSE FilesSmoke (disk package)"; \
		echo "  Expected:"; cat $(EXPDIR)/PicoJseIo.txt | head -5; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_io/FilesSmoke.out | head -5; \
		false; \
	fi

test-disk-picojse-std: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm
	@mkdir -p $(BUILDDIR)/disk_picojse_std
	@cp $(PICOJSE_DIR)/Native.java $(BUILDDIR)/disk_picojse_std/
	@cp $(PICOJSE_DIR)/io/Binary.java $(BUILDDIR)/disk_picojse_std/
	@cp $(PICOJSE_DIR)/io/Console.java $(BUILDDIR)/disk_picojse_std/
	@cp $(PICOJSE_DIR)/io/TextWriter.java $(BUILDDIR)/disk_picojse_std/
	@cp $(PICOJSE_DIR)/util/Bytes.java $(BUILDDIR)/disk_picojse_std/
	@cp $(PICOJSE_DIR)/util/Ints.java $(BUILDDIR)/disk_picojse_std/
	@cp $(PICOJSE_DIR)/text/Format.java $(BUILDDIR)/disk_picojse_std/
	@cp $(PICOJSE_DIR)/text/Parse.java $(BUILDDIR)/disk_picojse_std/
	@cp $(PICOJSE_DIR)/text/Strings.java $(BUILDDIR)/disk_picojse_std/
	@cp $(PICOJSE_TESTDIR)/PicoJseStdSmoke.java $(BUILDDIR)/disk_picojse_std/
	@printf 'Native.java\nBinary.java\nConsole.java\nTextWriter.java\nBytes.java\nInts.java\nFormat.java\nParse.java\nStrings.java\nPicoJseStdSmoke.java\n' > $(BUILDDIR)/disk_picojse_std/sources.lst
	@echo "--- picoJSE StdSmoke (disk package) ---"
	@cd $(BUILDDIR)/disk_picojse_std && $(DISK_PICOJVM) $(DISK_COMPILER) > PicoJseStdSmoke.pjvm \
		2> PicoJseStdSmoke_compile.log
	@$(PICOJVM) $(BUILDDIR)/disk_picojse_std/PicoJseStdSmoke.pjvm > $(BUILDDIR)/disk_picojse_std/PicoJseStdSmoke.out \
		2> $(BUILDDIR)/disk_picojse_std/PicoJseStdSmoke_run.log || true
	@if diff -q $(BUILDDIR)/disk_picojse_std/PicoJseStdSmoke.out $(EXPDIR)/PicoJseStd.txt > /dev/null 2>&1; then \
		echo "PASS: picoJSE StdSmoke (disk package)"; \
	else \
		echo "FAIL: picoJSE StdSmoke (disk package)"; \
		echo "  Expected:"; cat $(EXPDIR)/PicoJseStd.txt | head -8; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_std/PicoJseStdSmoke.out | head -8; \
		false; \
	fi

test-disk-picojse-string-concat: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm
	@mkdir -p $(BUILDDIR)/disk_picojse_string_concat
	@cp $(PICOJSE_DIR)/Native.java $(BUILDDIR)/disk_picojse_string_concat/
	@cp $(JAVA_LANG_DIR)/StringBuilder.java $(BUILDDIR)/disk_picojse_string_concat/
	@cp $(PICOJSE_DIR)/io/Console.java $(BUILDDIR)/disk_picojse_string_concat/
	@cp $(PICOJSE_DIR)/io/TextWriter.java $(BUILDDIR)/disk_picojse_string_concat/
	@cp $(PICOJSE_TESTDIR)/StringConcatSmoke.java $(BUILDDIR)/disk_picojse_string_concat/
	@printf 'Native.java\nStringBuilder.java\nConsole.java\nTextWriter.java\nStringConcatSmoke.java\n' > $(BUILDDIR)/disk_picojse_string_concat/sources.lst
	@echo "--- picoJSE StringConcatSmoke (disk package) ---"
	@cd $(BUILDDIR)/disk_picojse_string_concat && $(DISK_PICOJVM) $(DISK_COMPILER) > StringConcatSmoke.pjvm \
		2> StringConcatSmoke_compile.log
	@$(PICOJVM) $(BUILDDIR)/disk_picojse_string_concat/StringConcatSmoke.pjvm > $(BUILDDIR)/disk_picojse_string_concat/StringConcatSmoke.out \
		2> $(BUILDDIR)/disk_picojse_string_concat/StringConcatSmoke_run.log || true
	@if diff -q $(BUILDDIR)/disk_picojse_string_concat/StringConcatSmoke.out $(EXPDIR)/StringConcatSmoke.txt > /dev/null 2>&1; then \
		echo "PASS: picoJSE StringConcatSmoke (disk package)"; \
	else \
		echo "FAIL: picoJSE StringConcatSmoke (disk package)"; \
		echo "  Expected:"; cat $(EXPDIR)/StringConcatSmoke.txt | head -3; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_string_concat/StringConcatSmoke.out | head -3; \
		false; \
	fi

test-disk-picojse-archive: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm
	@mkdir -p $(BUILDDIR)/disk_picojse_archive
	@cp $(PICOJSE_DIR)/Native.java $(BUILDDIR)/disk_picojse_archive/
	@cp $(PICOJSE_DIR)/archive/Tar.java $(BUILDDIR)/disk_picojse_archive/
	@cp $(PICOJSE_DIR)/io/Files.java $(BUILDDIR)/disk_picojse_archive/
	@cp $(PICOJSE_DIR)/text/Format.java $(BUILDDIR)/disk_picojse_archive/
	@cp $(PICOJSE_DIR)/text/Strings.java $(BUILDDIR)/disk_picojse_archive/
	@cp $(PICOJSE_DIR)/util/Bytes.java $(BUILDDIR)/disk_picojse_archive/
	@cp $(PICOJSE_TESTDIR)/TarSmoke.java $(BUILDDIR)/disk_picojse_archive/
	@printf 'Native.java\nTar.java\nFiles.java\nFormat.java\nStrings.java\nBytes.java\nTarSmoke.java\n' > $(BUILDDIR)/disk_picojse_archive/sources.lst
	@echo "--- picoJSE TarSmoke (disk package) ---"
	@cd $(BUILDDIR)/disk_picojse_archive && $(DISK_PICOJVM) $(DISK_COMPILER) > TarSmoke.pjvm \
		2> TarSmoke_compile.log
	@$(PICOJVM) $(BUILDDIR)/disk_picojse_archive/TarSmoke.pjvm > $(BUILDDIR)/disk_picojse_archive/TarSmoke.out \
		2> $(BUILDDIR)/disk_picojse_archive/TarSmoke_run.log || true
	@if diff -q $(BUILDDIR)/disk_picojse_archive/TarSmoke.out $(EXPDIR)/TarSmoke.txt > /dev/null 2>&1; then \
		echo "PASS: picoJSE TarSmoke (disk package)"; \
	else \
		echo "FAIL: picoJSE TarSmoke (disk package)"; \
		echo "  Expected:"; cat $(EXPDIR)/TarSmoke.txt | head -8; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_archive/TarSmoke.out | head -8; \
		false; \
	fi

test-disk-picojse-zip: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm $(BUILDDIR)/picojvm-bigheap
	@mkdir -p $(BUILDDIR)/disk_picojse_zip
	@cp $(PICOJSE_DIR)/Native.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/archive/Zip.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/archive/ZipRead.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/archive/ZipWrite.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/archive/ZipDeflateBits.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/archive/ZipDeflate.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/archive/ZipHuffman.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/archive/ZipInflate.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/archive/ZipTables.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/io/Files.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/io/Binary.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/text/Format.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/text/Strings.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_DIR)/util/Bytes.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_TESTDIR)/ZipSmoke.java $(BUILDDIR)/disk_picojse_zip/
	@cp $(PICOJSE_TESTDIR)/ZipDeflateSmoke.java $(BUILDDIR)/disk_picojse_zip/
	@printf 'Native.java\nZip.java\nZipRead.java\nZipWrite.java\nZipDeflateBits.java\nZipDeflate.java\nZipHuffman.java\nZipInflate.java\nZipTables.java\nFiles.java\nBinary.java\nFormat.java\nStrings.java\nBytes.java\nZipSmoke.java\n' > $(BUILDDIR)/disk_picojse_zip/sources.lst
	@echo "--- picoJSE ZipSmoke (disk package) ---"
	@cd $(BUILDDIR)/disk_picojse_zip && $(DISK_PICOJVM_BIG) $(DISK_COMPILER) > ZipSmoke.pjvm \
		2> ZipSmoke_compile.log
	@$(PICOJVM) $(BUILDDIR)/disk_picojse_zip/ZipSmoke.pjvm > $(BUILDDIR)/disk_picojse_zip/ZipSmoke.out \
		2> $(BUILDDIR)/disk_picojse_zip/ZipSmoke_run.log || true
	@if diff -q $(BUILDDIR)/disk_picojse_zip/ZipSmoke.out $(EXPDIR)/ZipSmoke.txt > /dev/null 2>&1; then \
		echo "PASS: picoJSE ZipSmoke (disk package)"; \
	else \
		echo "FAIL: picoJSE ZipSmoke (disk package)"; \
		echo "  Expected:"; cat $(EXPDIR)/ZipSmoke.txt | head -8; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_zip/ZipSmoke.out | head -8; \
		false; \
	fi
	@printf 'Native.java\nZip.java\nZipRead.java\nZipWrite.java\nZipDeflateBits.java\nZipDeflate.java\nZipHuffman.java\nZipInflate.java\nZipTables.java\nFiles.java\nBinary.java\nFormat.java\nStrings.java\nBytes.java\nZipDeflateSmoke.java\n' > $(BUILDDIR)/disk_picojse_zip/sources.lst
	@echo "--- picoJSE ZipDeflateSmoke (disk package) ---"
	@cd $(BUILDDIR)/disk_picojse_zip && $(DISK_PICOJVM_BIG) $(DISK_COMPILER) > ZipDeflateSmoke.pjvm \
		2> ZipDeflateSmoke_compile.log
	@$(PICOJVM) $(BUILDDIR)/disk_picojse_zip/ZipDeflateSmoke.pjvm > $(BUILDDIR)/disk_picojse_zip/ZipDeflateSmoke.out \
		2> $(BUILDDIR)/disk_picojse_zip/ZipDeflateSmoke_run.log || true
	@if diff -q $(BUILDDIR)/disk_picojse_zip/ZipDeflateSmoke.out $(EXPDIR)/ZipDeflateSmoke.txt > /dev/null 2>&1; then \
		echo "PASS: picoJSE ZipDeflateSmoke (disk package)"; \
	else \
		echo "FAIL: picoJSE ZipDeflateSmoke (disk package)"; \
		echo "  Expected:"; cat $(EXPDIR)/ZipDeflateSmoke.txt | head -12; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_zip/ZipDeflateSmoke.out | head -12; \
		false; \
	fi

test-disk-picojse-tools-tar: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm
	@rm -rf $(BUILDDIR)/disk_picojse_tools_tar
	@mkdir -p $(BUILDDIR)/disk_picojse_tools_tar
	@cp $(PICOJSE_DIR)/Native.java $(BUILDDIR)/disk_picojse_tools_tar/
	@cp $(PICOJSE_DIR)/archive/Tar.java $(BUILDDIR)/disk_picojse_tools_tar/
	@cp $(PICOJSE_DIR)/io/Console.java $(BUILDDIR)/disk_picojse_tools_tar/
	@cp $(PICOJSE_DIR)/io/Files.java $(BUILDDIR)/disk_picojse_tools_tar/
	@cp $(PICOJSE_DIR)/io/TextWriter.java $(BUILDDIR)/disk_picojse_tools_tar/
	@cp $(PICOJSE_DIR)/util/Bytes.java $(BUILDDIR)/disk_picojse_tools_tar/
	@cp $(PICOJSE_TESTDIR)/PJTar.java $(BUILDDIR)/disk_picojse_tools_tar/
	@cp $(PICOJSE_TESTDIR)/PJUntar.java $(BUILDDIR)/disk_picojse_tools_tar/
	@printf 'Native.java\nTar.java\nConsole.java\nFiles.java\nTextWriter.java\nBytes.java\nPJTar.java\n' > $(BUILDDIR)/disk_picojse_tools_tar/sources.lst
	@echo "--- picoJSE PJTar (disk package) ---"
	@cd $(BUILDDIR)/disk_picojse_tools_tar && $(DISK_PICOJVM) $(DISK_COMPILER) > PJTar.pjvm \
		2> PJTar_compile.log
	@printf 'Native.java\nTar.java\nConsole.java\nFiles.java\nTextWriter.java\nBytes.java\nPJUntar.java\n' > $(BUILDDIR)/disk_picojse_tools_tar/sources.lst
	@echo "--- picoJSE PJUntar (disk package) ---"
	@cd $(BUILDDIR)/disk_picojse_tools_tar && $(DISK_PICOJVM) $(DISK_COMPILER) > PJUntar.pjvm \
		2> PJUntar_compile.log
	@printf 'ALPHA\n' > $(BUILDDIR)/disk_picojse_tools_tar/a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/disk_picojse_tools_tar/b.txt
	@cd $(BUILDDIR)/disk_picojse_tools_tar && $(DISK_PICOJVM) PJTar.pjvm -- out.tar a.txt b.txt > create.out 2> create_run.log
	@if diff -q $(BUILDDIR)/disk_picojse_tools_tar/create.out ../expected/PJArchiveCreate.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: picoJSE PJTar (disk package)"; \
		echo "  Expected:"; cat ../expected/PJArchiveCreate.txt; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_tools_tar/create.out; \
		false; \
	fi
	@rm -f $(BUILDDIR)/disk_picojse_tools_tar/a.txt $(BUILDDIR)/disk_picojse_tools_tar/b.txt
	@cd $(BUILDDIR)/disk_picojse_tools_tar && $(DISK_PICOJVM) PJUntar.pjvm -- out.tar > extract.out 2> extract_run.log
	@if diff -q $(BUILDDIR)/disk_picojse_tools_tar/extract.out ../expected/PJArchiveExtract.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: picoJSE PJUntar (disk package)"; \
		echo "  Expected:"; cat ../expected/PJArchiveExtract.txt; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_tools_tar/extract.out; \
		false; \
	fi
	@printf 'ALPHA\n' > $(BUILDDIR)/disk_picojse_tools_tar/exp_a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/disk_picojse_tools_tar/exp_b.txt
	@if diff -q $(BUILDDIR)/disk_picojse_tools_tar/a.txt $(BUILDDIR)/disk_picojse_tools_tar/exp_a.txt > /dev/null 2>&1 && \
	    diff -q $(BUILDDIR)/disk_picojse_tools_tar/b.txt $(BUILDDIR)/disk_picojse_tools_tar/exp_b.txt > /dev/null 2>&1; then \
		echo "PASS: picoJSE tar tools (disk package)"; \
	else \
		echo "FAIL: picoJSE tar tools contents (disk package)"; \
		false; \
	fi

test-disk-picojse-tools-zip: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm $(BUILDDIR)/picojvm-bigheap
	@rm -rf $(BUILDDIR)/disk_picojse_tools_zip
	@mkdir -p $(BUILDDIR)/disk_picojse_tools_zip
	@cp $(PICOJSE_DIR)/Native.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/archive/Zip.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/archive/ZipRead.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/archive/ZipWrite.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/archive/ZipDeflateBits.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/archive/ZipDeflate.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/archive/ZipHuffman.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/archive/ZipInflate.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/archive/ZipTables.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/io/Console.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/io/Files.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/io/Binary.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/io/TextWriter.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_DIR)/util/Bytes.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_TESTDIR)/PJZip.java $(BUILDDIR)/disk_picojse_tools_zip/
	@cp $(PICOJSE_TESTDIR)/PJUnzip.java $(BUILDDIR)/disk_picojse_tools_zip/
	@printf 'Native.java\nZip.java\nZipWrite.java\nZipDeflateBits.java\nZipDeflate.java\nZipHuffman.java\nZipTables.java\nConsole.java\nFiles.java\nBinary.java\nTextWriter.java\nBytes.java\nPJZip.java\n' > $(BUILDDIR)/disk_picojse_tools_zip/sources.lst
	@echo "--- picoJSE PJZip (disk package) ---"
	@cd $(BUILDDIR)/disk_picojse_tools_zip && $(DISK_PICOJVM_BIG) $(DISK_COMPILER) > PJZip.pjvm \
		2> PJZip_compile.log
	@printf 'Native.java\nZip.java\nZipRead.java\nZipHuffman.java\nZipInflate.java\nZipTables.java\nConsole.java\nFiles.java\nBinary.java\nTextWriter.java\nBytes.java\nPJUnzip.java\n' > $(BUILDDIR)/disk_picojse_tools_zip/sources.lst
	@echo "--- picoJSE PJUnzip (disk package) ---"
	@cd $(BUILDDIR)/disk_picojse_tools_zip && $(DISK_PICOJVM_BIG) $(DISK_COMPILER) > PJUnzip.pjvm \
		2> PJUnzip_compile.log
	@printf 'ALPHA\n' > $(BUILDDIR)/disk_picojse_tools_zip/a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/disk_picojse_tools_zip/b.txt
	@cd $(BUILDDIR)/disk_picojse_tools_zip && $(DISK_PICOJVM) PJZip.pjvm -- out.zip a.txt b.txt > create.out 2> create_run.log
	@if diff -q $(BUILDDIR)/disk_picojse_tools_zip/create.out ../expected/PJArchiveCreate.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: picoJSE PJZip (disk package)"; \
		echo "  Expected:"; cat ../expected/PJArchiveCreate.txt; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_tools_zip/create.out; \
		false; \
	fi
	@rm -f $(BUILDDIR)/disk_picojse_tools_zip/a.txt $(BUILDDIR)/disk_picojse_tools_zip/b.txt
	@cd $(BUILDDIR)/disk_picojse_tools_zip && $(DISK_PICOJVM) PJUnzip.pjvm -- out.zip > extract.out 2> extract_run.log
	@if diff -q $(BUILDDIR)/disk_picojse_tools_zip/extract.out ../expected/PJArchiveExtract.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: picoJSE PJUnzip (disk package)"; \
		echo "  Expected:"; cat ../expected/PJArchiveExtract.txt; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_tools_zip/extract.out; \
		false; \
	fi
	@printf 'ALPHA\n' > $(BUILDDIR)/disk_picojse_tools_zip/exp_a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/disk_picojse_tools_zip/exp_b.txt
	@if diff -q $(BUILDDIR)/disk_picojse_tools_zip/a.txt $(BUILDDIR)/disk_picojse_tools_zip/exp_a.txt > /dev/null 2>&1 && \
	    diff -q $(BUILDDIR)/disk_picojse_tools_zip/b.txt $(BUILDDIR)/disk_picojse_tools_zip/exp_b.txt > /dev/null 2>&1; then \
		echo "PASS: picoJSE zip tools (disk package)"; \
	else \
		echo "FAIL: picoJSE zip tools contents (disk package)"; \
		false; \
	fi

test-disk-picojse-tools-arc: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm $(BUILDDIR)/picojvm-bigheap
	@rm -rf $(BUILDDIR)/disk_picojse_tools_arc
	@mkdir -p $(BUILDDIR)/disk_picojse_tools_arc
	@cp $(PICOJSE_DIR)/Native.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/archive/Tar.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/archive/Zip.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/archive/ZipRead.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/archive/ZipWrite.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/archive/ZipDeflateBits.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/archive/ZipDeflate.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/archive/ZipHuffman.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/archive/ZipInflate.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/archive/ZipTables.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/io/Console.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/io/Files.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/io/Binary.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/io/TextWriter.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_DIR)/util/Bytes.java $(BUILDDIR)/disk_picojse_tools_arc/
	@cp $(PICOJSE_TESTDIR)/PJArc.java $(BUILDDIR)/disk_picojse_tools_arc/
	@printf 'Native.java\nTar.java\nZip.java\nZipRead.java\nZipWrite.java\nZipDeflateBits.java\nZipDeflate.java\nZipHuffman.java\nZipInflate.java\nZipTables.java\nConsole.java\nFiles.java\nBinary.java\nTextWriter.java\nBytes.java\nPJArc.java\n' > $(BUILDDIR)/disk_picojse_tools_arc/sources.lst
	@echo "--- picoJSE PJArc (disk package) ---"
	@cd $(BUILDDIR)/disk_picojse_tools_arc && $(DISK_PICOJVM_BIG) $(DISK_COMPILER) > PJArc.pjvm \
		2> PJArc_compile.log
	@printf 'ALPHA\n' > $(BUILDDIR)/disk_picojse_tools_arc/a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/disk_picojse_tools_arc/b.txt
	@cd $(BUILDDIR)/disk_picojse_tools_arc && $(DISK_PICOJVM) PJArc.pjvm -- tar out.tar a.txt b.txt > create-tar.out 2> create-tar.log
	@if diff -q $(BUILDDIR)/disk_picojse_tools_arc/create-tar.out ../expected/PJArchiveCreate.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: picoJSE PJArc tar create (disk package)"; \
		echo "  Expected:"; cat ../expected/PJArchiveCreate.txt; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_tools_arc/create-tar.out; \
		false; \
	fi
	@rm -f $(BUILDDIR)/disk_picojse_tools_arc/a.txt $(BUILDDIR)/disk_picojse_tools_arc/b.txt
	@cd $(BUILDDIR)/disk_picojse_tools_arc && $(DISK_PICOJVM) PJArc.pjvm -- untar out.tar > extract-tar.out 2> extract-tar.log
	@if diff -q $(BUILDDIR)/disk_picojse_tools_arc/extract-tar.out ../expected/PJArchiveExtract.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: picoJSE PJArc tar extract (disk package)"; \
		echo "  Expected:"; cat ../expected/PJArchiveExtract.txt; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_tools_arc/extract-tar.out; \
		false; \
	fi
	@printf 'ALPHA\n' > $(BUILDDIR)/disk_picojse_tools_arc/exp_a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/disk_picojse_tools_arc/exp_b.txt
	@if diff -q $(BUILDDIR)/disk_picojse_tools_arc/a.txt $(BUILDDIR)/disk_picojse_tools_arc/exp_a.txt > /dev/null 2>&1 && \
	    diff -q $(BUILDDIR)/disk_picojse_tools_arc/b.txt $(BUILDDIR)/disk_picojse_tools_arc/exp_b.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: picoJSE PJArc tar contents (disk package)"; \
		false; \
	fi
	@rm -f $(BUILDDIR)/disk_picojse_tools_arc/a.txt $(BUILDDIR)/disk_picojse_tools_arc/b.txt $(BUILDDIR)/disk_picojse_tools_arc/out.tar
	@printf 'ALPHA\n' > $(BUILDDIR)/disk_picojse_tools_arc/a.txt
	@printf 'BETA!\n' > $(BUILDDIR)/disk_picojse_tools_arc/b.txt
	@cd $(BUILDDIR)/disk_picojse_tools_arc && $(DISK_PICOJVM) PJArc.pjvm -- zip out.zip a.txt b.txt > create-zip.out 2> create-zip.log
	@if diff -q $(BUILDDIR)/disk_picojse_tools_arc/create-zip.out ../expected/PJArchiveCreate.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: picoJSE PJArc zip create (disk package)"; \
		echo "  Expected:"; cat ../expected/PJArchiveCreate.txt; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_tools_arc/create-zip.out; \
		false; \
	fi
	@rm -f $(BUILDDIR)/disk_picojse_tools_arc/a.txt $(BUILDDIR)/disk_picojse_tools_arc/b.txt
	@cd $(BUILDDIR)/disk_picojse_tools_arc && $(DISK_PICOJVM) PJArc.pjvm -- unzip out.zip > extract-zip.out 2> extract-zip.log
	@if diff -q $(BUILDDIR)/disk_picojse_tools_arc/extract-zip.out ../expected/PJArchiveExtract.txt > /dev/null 2>&1; then :; else \
		echo "FAIL: picoJSE PJArc zip extract (disk package)"; \
		echo "  Expected:"; cat ../expected/PJArchiveExtract.txt; \
		echo "  Got:"; cat $(BUILDDIR)/disk_picojse_tools_arc/extract-zip.out; \
		false; \
	fi
	@if diff -q $(BUILDDIR)/disk_picojse_tools_arc/a.txt $(BUILDDIR)/disk_picojse_tools_arc/exp_a.txt > /dev/null 2>&1 && \
	    diff -q $(BUILDDIR)/disk_picojse_tools_arc/b.txt $(BUILDDIR)/disk_picojse_tools_arc/exp_b.txt > /dev/null 2>&1; then \
		echo "PASS: picoJSE arc tool (disk package)"; \
	else \
		echo "FAIL: picoJSE PJArc zip contents (disk package)"; \
		false; \
	fi
	@if diff -q $(BUILDDIR)/disk_picojse_tools_arc/a.txt $(BUILDDIR)/disk_picojse_tools_arc/exp_a.txt > /dev/null 2>&1 && \
	    diff -q $(BUILDDIR)/disk_picojse_tools_arc/b.txt $(BUILDDIR)/disk_picojse_tools_arc/exp_b.txt > /dev/null 2>&1; then \
		echo "PASS: picoJSE arc deflate tool (disk package)"; \
	else \
		echo "FAIL: picoJSE PJArc deflate contents (disk package)"; \
		false; \
	fi

test-disk-picojse: test-disk-picojse-term test-disk-picojse-io test-disk-picojse-std test-disk-picojse-string-concat test-disk-picojse-archive test-disk-picojse-zip test-disk-picojse-tools-tar test-disk-picojse-tools-zip test-disk-picojse-tools-arc

# Disk selfhost: compile compiler sources via sources.lst, verify byte-identical to memory mode
test-disk-selfhost: $(PICOJVM) $(BUILDDIR)/picojc-disk.pjvm $(BUILDDIR)/gen1.pjvm
	@mkdir -p $(BUILDDIR)/disk_selfhost
	@for f in Token Lexer Native Compiler Catalog Resolver Emit Stmt Expr Linker; do \
		cp $(SRCDIR)/$$f.java $(BUILDDIR)/disk_selfhost/; \
	done
	@printf 'Token.java\nLexer.java\nNative.java\nCompiler.java\nCatalog.java\nResolver.java\nEmit.java\nStmt.java\nExpr.java\nLinker.java\n' > $(BUILDDIR)/disk_selfhost/sources.lst
	@echo "--- Disk multi-file selfhost ---"
	@cd $(BUILDDIR)/disk_selfhost && $(DISK_PICOJVM) $(DISK_COMPILER) > gen1_disk.pjvm \
		2> gen1_disk.log
	@if [ "$$(head -c 1 $(BUILDDIR)/disk_selfhost/gen1_disk.pjvm | od -An -tx1 | tr -d ' ')" != "85" ]; then \
		echo "FAIL: disk gen1 is not a .pjvm image (compiler error: $$(head -c 64 $(BUILDDIR)/disk_selfhost/gen1_disk.pjvm))"; \
		false; \
	fi
	@if cmp -s $(BUILDDIR)/disk_selfhost/gen1_disk.pjvm $(BUILDDIR)/gen1.pjvm; then \
		echo "PASS: disk multi-file gen1 == memory gen1 ($$(wc -c < $(BUILDDIR)/disk_selfhost/gen1_disk.pjvm | tr -d ' ') bytes)"; \
	else \
		echo "FAIL: disk multi-file gen1 != memory gen1"; \
		echo "  disk: $$(wc -c < $(BUILDDIR)/disk_selfhost/gen1_disk.pjvm | tr -d ' ') bytes"; \
		echo "  mem:  $$(wc -c < $(BUILDDIR)/gen1.pjvm | tr -d ' ') bytes"; \
		false; \
	fi

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
	@if [ "$$(head -c 1 $@ | od -An -tx1 | tr -d ' ')" != "85" ]; then \
		echo "FAIL: gen1 is not a .pjvm image (compiler error: $$(head -c 64 $@))"; \
		tail -5 $(BUILDDIR)/gen1_compile.log; \
		false; \
	fi
	@echo "gen1: $$(wc -c < $@ | tr -d ' ') bytes"

# gen2: gen1 compiles itself
$(BUILDDIR)/gen2.pjvm: $(BUILDDIR)/gen1.pjvm $(BUILDDIR)/compiler_src.java $(PICOJVM)
	$(PICOJVM) $(BUILDDIR)/gen1.pjvm \
		--preload 0xC000:$(BUILDDIR)/compiler_src.java \
		--save-raw 0xC000:/dev/null \
		> $@ 2> $(BUILDDIR)/gen2_compile.log
	@if [ "$$(head -c 1 $@ | od -An -tx1 | tr -d ' ')" != "85" ]; then \
		echo "FAIL: gen2 is not a .pjvm image (compiler error: $$(head -c 64 $@))"; \
		tail -5 $(BUILDDIR)/gen2_compile.log; \
		false; \
	fi
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

# Everything: unit tests + selfhost + disk modes
test-all: test test-negative test-selfhost test-disk test-disk-multi test-disk-package test-disk-picojse test-disk-selfhost
	@echo ""
	@echo "=== ALL TESTS PASSED ==="

clean:
	rm -rf $(BUILDDIR)

.PHONY: all test test-negative test-all build check-javac clean test-disk test-disk-multi test-disk-package test-disk-picojse test-disk-picojse-term test-disk-picojse-io test-disk-picojse-std test-disk-picojse-string-concat test-disk-picojse-archive test-disk-picojse-zip test-disk-picojse-tools-tar test-disk-picojse-tools-zip test-disk-picojse-tools-arc test-disk-selfhost
.PHONY: test-selfhost test-selfhost-fixpoint test-binmatch test-gen1
.SECONDARY: $(BUILDDIR)/%.pjvm $(BUILDDIR)/%.out
.SECONDARY: $(BUILDDIR)/gen1_%.pjvm $(BUILDDIR)/gen1_%.out
.SECONDARY: $(BUILDDIR)/lib_%.java $(BUILDDIR)/lib_%.pjvm $(BUILDDIR)/lib_%.out
