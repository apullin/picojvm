#!/usr/bin/env python3
"""Regression checks for the pjvmpack package entry points."""

import subprocess
import sys
import tempfile
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


if __name__ == "__main__":
    main()
