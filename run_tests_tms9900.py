#!/usr/bin/env python3
"""
run_tests_tms9900.py -- picoJVM test runner for TMS9900.

For each test:
  1. Build the .pjvm image via the local Makefile
  2. Run on host picoJVM for expected output
  3. Build the TMS9900 simulator image
  4. Run on tms9900-trace
  5. Compare output
"""

import argparse
import json
import os
import subprocess
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TRACE = os.path.expanduser("~/personal/ti99/tms9900-trace/build/tms9900-trace")
OUTPUT_ADDR = 0xEF00
OUTPUT_LEN = 256
MAX_STEPS = 50_000_000

TESTS = [
    "HelloWorld",
    "Fib",
    "BubbleSort",
    "Counter",
    "StringTest",
    "StringApiSmoke",
    "StringShimTest",
    "RomStringTest",
    "NativeOpsTest",
    "StaticInitTest",
    "MultiArrayTest",
    "StringSwitchTest",
    "ConstTest",
    "Shapes",
    "Features",
    "InterfaceTest",
    "ExceptionTest",
    "EnumBasicTest",
    "EnumShimTest",
    "ThrowableShimTest",
]


def run(cmd, **kwargs):
    return subprocess.run(cmd, capture_output=True, text=False, **kwargs)


def ensure_host_vm(verbose):
    result = run(["make", "picojvm"], cwd=SCRIPT_DIR)
    if result.returncode != 0 and verbose:
        sys.stderr.write(result.stderr.decode("utf-8", "replace"))
    return result.returncode == 0


def ensure_pjvm(test_name, verbose):
    result = run(["make", f"tests/{test_name}.pjvm"], cwd=SCRIPT_DIR)
    if result.returncode != 0 and verbose:
        sys.stderr.write(result.stderr.decode("utf-8", "replace"))
    return result.returncode == 0


def get_host_output(test_name):
    result = run(["./picojvm", f"tests/{test_name}.pjvm"], cwd=SCRIPT_DIR)
    if result.returncode != 0:
        return None
    return result.stdout


def build_tms9900(test_name, opt_level, verbose):
    env = os.environ.copy()
    cmd = [
        "make",
        "tms9900-bin",
        f"PJVM_FILE=tests/{test_name}.pjvm",
        f"TMS_TARGET_OPT={opt_level}",
    ]
    if env.get("TMS_LTO") == "1":
        cmd.append("TMS_LTO=1")
    result = run(cmd, cwd=SCRIPT_DIR, env=env)
    if result.returncode != 0 and verbose:
        sys.stderr.write(result.stderr.decode("utf-8", "replace"))
    if result.returncode != 0:
        return None
    build_suffix = f"{test_name}-O{opt_level}"
    if env.get("TMS_LTO") == "1":
        build_suffix += "-lto"
    return os.path.join(SCRIPT_DIR, "build-tms9900", build_suffix, "picojvm.bin")


def run_tms9900(bin_path):
    cmd = [
        TRACE,
        "-e", "0x0000",
        "-l", "0x0000",
        "-n", str(MAX_STEPS),
        "-S",
        "-q",
        "-d", f"0x{OUTPUT_ADDR:04X}:{OUTPUT_LEN}",
        bin_path,
    ]
    result = run(cmd)
    if result.returncode != 0:
        return None, None

    steps = "?"
    try:
        summary = json.loads(result.stdout.decode("utf-8"))
        steps = summary.get("steps", "?")
    except Exception:
        pass

    stderr_text = result.stderr.decode("latin-1")
    output_bytes = bytearray()
    for line in stderr_text.strip().split("\n"):
        line = line.strip()
        if not line or ":" not in line:
            continue
        hex_part = line.split(":", 1)[1].strip()
        if "|" in hex_part:
            hex_part = hex_part.split("|", 1)[0].strip()
        for word_str in hex_part.split():
            if len(word_str) != 4:
                continue
            try:
                word = int(word_str, 16)
            except ValueError:
                continue
            output_bytes.append((word >> 8) & 0xFF)
            output_bytes.append(word & 0xFF)

    if len(output_bytes) < 2:
        return b"", steps
    out_len = (output_bytes[0] << 8) | output_bytes[1]
    return bytes(output_bytes[2:2 + out_len]), steps


def format_bytes(data, max_len=60):
    if data is None:
        return "<None>"
    result = []
    for b in data[:max_len]:
        if 32 <= b < 127:
            result.append(chr(b))
        else:
            result.append(f"\\x{b:02x}")
    if len(data) > max_len:
        result.append(f"... ({len(data)} bytes)")
    return "".join(result)


def main():
    parser = argparse.ArgumentParser(description="picoJVM TMS9900 test runner")
    parser.add_argument("--opt", default="2", help="Optimization level (default: 2)")
    parser.add_argument("--lto", action="store_true", help="Enable TMS9900 LTO build")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    parser.add_argument("tests", nargs="*", help="Specific tests to run (default: curated set)")
    args = parser.parse_args()

    tests = args.tests if args.tests else TESTS

    if not ensure_host_vm(args.verbose):
        print("Host picojvm build failed")
        return 1

    passed = 0
    failed = 0
    errors = 0

    if args.lto:
        os.environ["TMS_LTO"] = "1"
    else:
        os.environ.pop("TMS_LTO", None)

    config = f"O{args.opt}" + (" +LTO" if args.lto else "")
    print(f"picoJVM TMS9900 test runner ({config})")
    print(f"Running {len(tests)} tests...\n")

    for name in tests:
        print(f"  {name:20s} ", end="", flush=True)

        if not ensure_pjvm(name, args.verbose):
            print("ERROR (pjvm build)")
            errors += 1
            continue

        expected = get_host_output(name)
        if expected is None:
            print("ERROR (host run)")
            errors += 1
            continue

        bin_path = build_tms9900(name, args.opt, args.verbose)
        if bin_path is None:
            print("ERROR (tms9900 build)")
            errors += 1
            continue

        actual, steps = run_tms9900(bin_path)
        if actual is None:
            print("ERROR (emulator)")
            errors += 1
            continue

        if actual == expected:
            print(f"PASS  ({len(expected)}B output, {steps} steps)")
            passed += 1
        else:
            print("FAIL")
            print(f"    expected: {format_bytes(expected)}")
            print(f"    actual:   {format_bytes(actual)}")
            failed += 1

    print(f"\n{'=' * 50}")
    print(f"Results: {passed} passed, {failed} failed, {errors} errors / {len(tests)} total")
    return 0 if failed == 0 and errors == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
