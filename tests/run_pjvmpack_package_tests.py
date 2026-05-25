#!/usr/bin/env python3
"""Regression checks for the pjvmpack package entry points."""

import subprocess
import sys
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

CASES = [
    ("Fib", ["tests/Fib.class"], []),
    ("ConstTest", ["tests/ConstTest.class"], []),
    ("ConstStringArrayTest", ["tests/ConstStringArrayTest.class"], []),
    ("InterfaceTest", [
        "tests/HasArea.class",
        "tests/Describable.class",
        "tests/Measurable.class",
        "tests/Circle.class",
        "tests/Box.class",
        "tests/InterfaceTest.class",
    ], []),
    ("ExceptionTest", [
        "tests/MyException.class",
        "tests/ExceptionTest.class",
    ], []),
    ("FibV4Packed", ["tests/Fib.class"], ["--format", "v4", "--pack-method-table"]),
]


def run(cmd):
    return subprocess.run(cmd, cwd=ROOT, check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def pack_with_shim(classfiles, options, out):
    run([sys.executable, "pjvmpack.py", *classfiles, "-o", str(out), *options])


def pack_with_module(classfiles, options, out):
    run([sys.executable, "-m", "pjvmpack", *classfiles, "-o", str(out), *options])


def assert_unresolved_method_is_rejected(tmp_path):
    src = tmp_path / "UnresolvedMethodTest.java"
    src.write_text(textwrap.dedent("""
        public class UnresolvedMethodTest {
            public static void main(String[] args) {
                System.exit(0);
            }
        }
    """).strip() + "\n")
    run(["javac", "-source", "8", "-target", "8", "-sourcepath", str(tmp_path),
         "-d", str(tmp_path), str(src)])

    out = tmp_path / "UnresolvedMethodTest.pjvm"
    proc = subprocess.run(
        [sys.executable, "pjvmpack.py", str(tmp_path / "UnresolvedMethodTest.class"),
         "-o", str(out)],
        cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    combined = proc.stdout + proc.stderr
    if proc.returncode == 0:
        raise AssertionError("unsupported System.exit method packed successfully")
    if "Unresolved bytecode references" not in combined:
        raise AssertionError("missing unresolved-reference diagnostic")
    if "java/lang/System.exit(I)V" not in combined:
        raise AssertionError("diagnostic did not name unresolved System.exit")
    print("PASS: UnresolvedMethodRejected")


def main():
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        for name, classfiles, options in CASES:
            shim_out = tmp_path / f"{name}-shim.pjvm"
            module_out = tmp_path / f"{name}-module.pjvm"
            pack_with_shim(classfiles, options, shim_out)
            pack_with_module(classfiles, options, module_out)
            if shim_out.read_bytes() != module_out.read_bytes():
                raise AssertionError(f"{name}: shim and module outputs differ")
            print(f"PASS: {name}")
        assert_unresolved_method_is_rejected(tmp_path)


if __name__ == "__main__":
    main()
