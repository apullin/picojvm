#!/usr/bin/env python3
"""Exercise picojc limits with generated sources instead of large fixtures."""

from pathlib import Path
import re
import subprocess
import sys


def compile_source(vm, compiler, source_path):
    return subprocess.run(
        [str(vm), str(compiler), "--preload", f"0xC000:{source_path}"],
        capture_output=True,
        check=False,
    )


def expect_error(vm, compiler, work, name, source, code):
    source_path = work / f"{name}.java"
    source_path.write_text(source, encoding="ascii")
    result = compile_source(vm, compiler, source_path)
    expected = re.compile(fr"E[0-9]+:{code}\n".encode("ascii"))
    if expected.fullmatch(result.stdout):
        print(f"PASS: {name} (E{code})")
        return True
    print(f"FAIL: {name}: expected E<line>:{code}")
    print(f"  stdout: {result.stdout[:120]!r}")
    print(f"  stderr: {result.stderr[-400:].decode('utf-8', 'replace')}")
    return False


def expect_output(vm, compiler, work, name, source, expected):
    source_path = work / f"{name}.java"
    image_path = work / f"{name}.pjvm"
    source_path.write_text(source, encoding="ascii")
    compiled = compile_source(vm, compiler, source_path)
    if not compiled.stdout.startswith(b"\x85L"):
        print(f"FAIL: {name}: compiler did not produce a v3 image")
        print(f"  stdout: {compiled.stdout[:120]!r}")
        return False
    image_path.write_bytes(compiled.stdout)
    result = subprocess.run(
        [str(vm), str(image_path)], capture_output=True, check=False
    )
    if result.returncode == 0 and result.stdout == expected:
        print(f"PASS: {name}")
        return True
    print(f"FAIL: {name}: expected {expected!r}, got {result.stdout!r}")
    print(f"  stderr: {result.stderr[-400:].decode('utf-8', 'replace')}")
    return False


def main():
    if len(sys.argv) != 4:
        print("usage: tests_capacity.py VM COMPILER WORKDIR", file=sys.stderr)
        return 2

    vm = Path(sys.argv[1]).resolve()
    compiler = Path(sys.argv[2]).resolve()
    work = Path(sys.argv[3]).resolve()
    work.mkdir(parents=True, exist_ok=True)

    const_arrays = "".join(
        f"    @Const static final int[] A{i} = {{ 0 }};\n" for i in range(17)
    )
    const_data = ", ".join("0" for _ in range(257))
    clinit_code = "".join("        Native.putchar(0);\n" for _ in range(300))
    clinit_fields = "".join(f"    static int f{i} = {i};\n" for i in range(129))
    method_code = "".join("        Native.putchar(0);\n" for _ in range(900))
    virtual_methods = "".join(f"    int m{i}() {{ return {i}; }}\n" for i in range(129))
    many_fields = "".join(f"    int f{i};\n" for i in range(130))
    parent_fields = "".join(f"    int p{i};\n" for i in range(200))
    child_fields = "".join(f"    int c{i};\n" for i in range(56))

    checks = [
        expect_error(
            vm,
            compiler,
            work,
            "const_array_count",
            f"public class ConstArrayCount {{\n{const_arrays}"
            "    public static void main(String[] args) {}\n}\n",
            279,
        ),
        expect_error(
            vm,
            compiler,
            work,
            "const_data_size",
            "public class ConstDataSize {\n"
            f"    @Const static final int[] DATA = {{ {const_data} }};\n"
            "    public static void main(String[] args) {}\n}\n",
            279,
        ),
        expect_error(
            vm,
            compiler,
            work,
            "clinit_code_size",
            "public class ClinitCodeSize {\n    static {\n"
            f"{clinit_code}    }}\n"
            "    public static void main(String[] args) {}\n}\n",
            279,
        ),
        expect_error(
            vm,
            compiler,
            work,
            "clinit_cp_count",
            f"public class ClinitCpCount {{\n{clinit_fields}"
            "    public static void main(String[] args) {}\n}\n",
            279,
        ),
        expect_error(
            vm,
            compiler,
            work,
            "method_code_size",
            "public class MethodCodeSize {\n"
            "    public static void main(String[] args) {\n"
            f"{method_code}    }}\n}}\n",
            256,
        ),
        expect_error(
            vm,
            compiler,
            work,
            "vtable_size",
            f"public class VtableSize {{\n{virtual_methods}"
            "    public static void main(String[] args) {}\n}\n",
            280,
        ),
        expect_error(
            vm,
            compiler,
            work,
            "inherited_field_count",
            f"class FieldParent {{\n{parent_fields}}}\n"
            f"class FieldChild extends FieldParent {{\n{child_fields}}}\n"
            "public class InheritedFieldCount {\n"
            "    public static void main(String[] args) {}\n}\n",
            280,
        ),
        expect_error(
            vm,
            compiler,
            work,
            "identifier_width",
            "class " + "A" * 256 + " {\n"
            "    public static void main(String[] args) {}\n}\n",
            251,
        ),
        expect_error(
            vm,
            compiler,
            work,
            "string_width",
            "public class StringWidth {\n"
            "    public static void main(String[] args) { Native.print(\""
            + "x" * 256
            + "\"); }\n}\n",
            259,
        ),
        expect_output(
            vm,
            compiler,
            work,
            "unsigned_field_count",
            f"class ManyFields {{\n{many_fields}}}\n"
            "public class UnsignedFieldCount {\n"
            "    public static void main(String[] args) {\n"
            "        ManyFields value = new ManyFields();\n"
            "        value.f129 = 65;\n"
            "        Native.putchar(value.f129);\n"
            "        Native.putchar(10);\n"
            "    }\n}\n",
            b"A\n",
        ),
    ]

    passed = sum(checks)
    print(f"Capacity tests: {passed} passed, {len(checks) - passed} failed")
    return 0 if passed == len(checks) else 1


if __name__ == "__main__":
    raise SystemExit(main())
