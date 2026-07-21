#!/usr/bin/env python3
"""Host-side v4 regression tests for metadata that exceeds v3 limits."""

from __future__ import annotations

import argparse
import shutil
import struct
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build" / "v4-tests"
SRC = BUILD / "src"
CLASSES = BUILD / "classes"


def run(cmd: list[str], **kwargs) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(cmd, check=True, **kwargs)


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def javac() -> None:
    sources = sorted(str(p) for p in SRC.glob("*.java"))
    sources.append(str(ROOT / "tests" / "Native.java"))
    run(["javac", "-source", "8", "-target", "8", "-d", str(CLASSES), *sources],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def pack(class_names: list[str], out: Path, pjvm_format: str = "auto",
         pack_method_table: bool = False) -> None:
    classfiles = [CLASSES / (name + ".class") for name in class_names]
    cmd = [sys.executable, str(ROOT / "pjvmpack.py"), *map(str, classfiles),
           "-o", str(out), "--format", pjvm_format]
    if pack_method_table:
        cmd.append("--pack-method-table")
    run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    data = out.read_bytes()
    if data[:2] != bytes((0x85, 0x4D)):
        raise AssertionError(f"{out.name}: expected v4 image, got header {data[:2].hex()}")


def vm_out(picojvm: Path, image: Path) -> bytes:
    r = run([str(picojvm), str(image)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return r.stdout


def expect_capacity_reject(picojvm: Path, image: Path) -> None:
    result = subprocess.run(
        [str(picojvm), str(image)], stdout=subprocess.PIPE,
        stderr=subprocess.PIPE)
    if result.returncode == 0 or b"Capacity exceeded" not in result.stderr:
        raise AssertionError(
            f"{image.name}: expected capacity rejection, got "
            f"rc={result.returncode}, stderr={result.stderr!r}")


def gen_many_methods() -> tuple[list[str], bytes]:
    body = ["public class V4ManyMethods {"]
    for i in range(300):
        val = 0x20 + (i % 64)
        body.append(f"  public static int m{i:03d}() {{ return {val}; }}")
    body.append("  public static void main(String[] args) { Native.putchar(m299()); }")
    body.append("}")
    write(SRC / "V4ManyMethods.java", "\n".join(body) + "\n")
    return ["V4ManyMethods", "Native"], bytes([0x20 + (299 % 64)])


def gen_many_classes() -> tuple[list[str], bytes]:
    lines = []
    class_names = []
    for i in range(261):
        name = f"V4C{i:03d}"
        class_names.append(name)
        lines.append(f"class {name} {{ int value() {{ return {i & 0x7F}; }} }}")
    lines.append("public class V4ManyClasses {")
    lines.append("  public static void main(String[] args) {")
    lines.append("    V4C260 x = new V4C260();")
    lines.append("    Native.putchar(x.value());")
    lines.append("  }")
    lines.append("}")
    write(SRC / "V4ManyClasses.java", "\n".join(lines) + "\n")
    return [*class_names, "V4ManyClasses", "Native"], bytes([260 & 0x7F])


def gen_many_statics() -> tuple[list[str], bytes]:
    lines = ["public class V4ManyStatics {"]
    for i in range(300):
        lines.append(f"  static int f{i:03d};")
    lines.append("  public static void main(String[] args) {")
    lines.append("    f299 = 81;")
    lines.append("    Native.putchar(f299);")
    lines.append("  }")
    lines.append("}")
    write(SRC / "V4ManyStatics.java", "\n".join(lines) + "\n")
    return ["V4ManyStatics", "Native"], b"Q"


def gen_many_virtuals() -> tuple[list[str], bytes]:
    lines = ["public class V4ManyVirtuals {"]
    for i in range(300):
        val = 0x30 + (i % 40)
        lines.append(f"  int m{i:03d}() {{ return {val}; }}")
    lines.append("  public static void main(String[] args) {")
    lines.append("    V4ManyVirtuals x = new V4ManyVirtuals();")
    lines.append("    Native.putchar(x.m299());")
    lines.append("  }")
    lines.append("}")
    write(SRC / "V4ManyVirtuals.java", "\n".join(lines) + "\n")
    return ["V4ManyVirtuals", "Native"], bytes([0x30 + (299 % 40)])


def gen_many_interface_vmids() -> tuple[list[str], bytes]:
    lines = ["interface V4IFace {"]
    for i in range(300):
        lines.append(f"  int m{i:03d}();")
    lines.append("}")
    lines.append("class V4Impl implements V4IFace {")
    for i in range(300):
        val = 0x40 + (i % 32)
        lines.append(f"  public int m{i:03d}() {{ return {val}; }}")
    lines.append("}")
    lines.append("public class V4ManyInterface {")
    lines.append("  public static void main(String[] args) {")
    lines.append("    V4IFace x = new V4Impl();")
    lines.append("    Native.putchar(x.m299());")
    lines.append("  }")
    lines.append("}")
    write(SRC / "V4ManyInterface.java", "\n".join(lines) + "\n")
    return ["V4IFace", "V4Impl", "V4ManyInterface", "Native"], bytes([0x40 + (299 % 32)])


def gen_interface_types() -> tuple[list[str], bytes]:
    write(SRC / "V4InterfaceTypes.java", """
interface V4RootMarker {}
interface V4ChildMarker extends V4RootMarker {}
class V4Marked implements V4ChildMarker {}
class V4MarkedChild extends V4Marked {}
class V4Unmarked {}
public class V4InterfaceTypes {
  public static void main(String[] args) {
    V4MarkedChild marked = new V4MarkedChild();
    Native.putchar(marked instanceof V4RootMarker ? 1 : 0);
    Native.putchar(new V4Unmarked() instanceof V4RootMarker ? 1 : 0);
    Object value = marked;
    V4RootMarker checked = (V4RootMarker) value;
    Native.putchar(checked != null ? 1 : 0);
  }
}
""".lstrip())
    return [
        "V4RootMarker", "V4ChildMarker", "V4Marked", "V4MarkedChild",
        "V4Unmarked", "V4InterfaceTypes", "Native",
    ], bytes((1, 0, 1))


def u2(v: int) -> bytes:
    return struct.pack("<H", v)


def u4(v: int) -> bytes:
    return struct.pack("<I", v)


def method_v4(max_locals: int, max_stack: int, arg_count: int, flags: int,
              code_offset: int, cp_base: int, vslot: int = 0xFFFF,
              vmid: int = 0xFFFF, exc_count: int = 0, exc_base: int = 0) -> bytes:
    return b"".join((
        u2(max_locals), u2(max_stack), u2(arg_count), u2(flags),
        u4(code_offset), u4(cp_base), u2(vslot), u2(vmid),
        u2(exc_count), u2(exc_base),
    ))


def write_capacity_image(out: Path, version: int, max_locals: int,
                         max_stack: int, packed: bool = False) -> None:
    bytecode = bytes((0xB1,))
    data = bytearray((0x85, version))
    if version == 0x4C:
        data += bytes((1, 0))
        data += u2(0) + bytes((0, 0, 0, 0))
        data += u4(len(bytecode)) + bytes((max_locals, max_stack))
        data += bytes((max_locals, max_stack, 0, 0))
        data += u4(0) + u2(0) + bytes((0xFF, 0xFF, 0, 0))
        data += u2(0)
    else:
        flags = 0x08 if packed else 0
        data += u2(1) + u2(0) + u2(0) + u2(0) + u2(0) + u2(0)
        data += u2(flags) + u4(len(bytecode))
        data += u2(max_locals) + u2(max_stack)
        if packed:
            # max_locals, max_stack, args, flags, vslot, vmid,
            # code_delta, cp_base+1, exc_count, exc_base+1
            fields = (max_locals, max_stack, 0, 0, 0, 0, 0, 1, 0, 1)
            blob = b"".join(_uleb(value) for value in fields)
            data += u4(len(blob)) + blob
        else:
            data += method_v4(max_locals, max_stack, 0, 0, 0, 0)
        data += u4(0)
    data += bytecode
    out.write_bytes(data)


def _uleb(value: int) -> bytes:
    encoded = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        encoded.append(byte | (0x80 if value else 0))
        if not value:
            return bytes(encoded)


def write_cpbase_image(out: Path) -> None:
    bytecode = bytes((0x10, ord("D"), 0xB8, 0x00, 0x00, 0xB1))
    cp_base = 65536
    cp = b"\xFF\xFF" * (cp_base // 2) + u2(0)
    data = bytearray()
    data += bytes((0x85, 0x4D))
    data += u2(2)       # n_methods
    data += u2(1)       # main_mi
    data += u2(0)       # n_static
    data += u2(0)       # n_int
    data += u2(1)       # n_classes
    data += u2(0)       # n_strings
    data += u2(0)       # region_flags
    data += u4(len(bytecode))
    data += u4(0)
    data += u2(0xFFFF) + u2(0) + u2(0) + u2(0xFFFF)  # class table
    data += method_v4(0, 0, 1, 1, 0, 0)              # native putchar
    data += method_v4(1, 1, 1, 0, 0, cp_base)        # main
    data += u4(len(cp)) + cp
    data += bytecode
    out.write_bytes(data)


def write_wide_locals_image(out: Path) -> None:
    # bipush 'F'; wide istore 260; wide iinc 260,1; wide iload 260;
    # invokestatic putchar; return
    bytecode = bytes((
        0x10, ord("F"),
        0xC4, 0x36, 0x01, 0x04,
        0xC4, 0x84, 0x01, 0x04, 0x00, 0x01,
        0xC4, 0x15, 0x01, 0x04,
        0xB8, 0x00, 0x00,
        0xB1,
    ))
    data = bytearray()
    data += bytes((0x85, 0x4D))
    data += u2(2)       # n_methods
    data += u2(1)       # main_mi
    data += u2(0)       # n_static
    data += u2(0)       # n_int
    data += u2(1)       # n_classes
    data += u2(0)       # n_strings
    data += u2(0)       # region_flags
    data += u4(len(bytecode))
    data += u4(0)
    data += u2(0xFFFF) + u2(0) + u2(0) + u2(0xFFFF)  # class table
    data += method_v4(0, 0, 1, 1, 0, 0)              # native putchar
    data += method_v4(261, 1, 0, 0, 0, 0)            # main
    data += u4(2) + u2(0)                            # CP: putchar
    data += bytecode
    out.write_bytes(data)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--picojvm", default=str(ROOT / "picojvm-large"))
    args = ap.parse_args()
    picojvm = Path(args.picojvm)

    shutil.rmtree(BUILD, ignore_errors=True)
    SRC.mkdir(parents=True)
    CLASSES.mkdir(parents=True)

    tests: list[tuple[str, list[str], bytes, str]] = []
    names, expected = gen_many_methods()
    tests.append(("many-methods", names, expected, "auto"))
    names, expected = gen_many_classes()
    tests.append(("many-classes", names, expected, "auto"))
    names, expected = gen_many_statics()
    tests.append(("many-statics", names, expected, "v4"))
    names, expected = gen_many_virtuals()
    tests.append(("many-virtuals", names, expected, "auto"))
    names, expected = gen_many_interface_vmids()
    tests.append(("many-interface-vmids", names, expected, "auto"))
    names, expected = gen_interface_types()
    tests.append(("interface-types", names, expected, "v4"))

    javac()

    for name, class_names, expected, pjvm_format in tests:
        image = BUILD / f"{name}.pjvm"
        pack(class_names, image, pjvm_format)
        got = vm_out(picojvm, image)
        if got != expected:
            raise AssertionError(f"{name}: expected {expected!r}, got {got!r}")
        print(f"PASS: v4 {name}")

        packed_image = BUILD / f"{name}-packed-mt.pjvm"
        pack(class_names, packed_image, pjvm_format, pack_method_table=True)
        got = vm_out(picojvm, packed_image)
        if got != expected:
            raise AssertionError(f"{name}-packed-mt: expected {expected!r}, got {got!r}")
        print(f"PASS: v4 {name} packed-mt")

    cpbase = BUILD / "wide-cpbase.pjvm"
    write_cpbase_image(cpbase)
    got = vm_out(picojvm, cpbase)
    if got != b"D":
        raise AssertionError(f"wide-cpbase: expected b'D', got {got!r}")
    print("PASS: v4 wide-cpbase")

    wide_locals = BUILD / "wide-locals.pjvm"
    write_wide_locals_image(wide_locals)
    got = vm_out(picojvm, wide_locals)
    if got != b"G":
        raise AssertionError(f"wide-locals: expected b'G', got {got!r}")
    print("PASS: v4 wide-locals")

    capacity_cases = (
        ("v3-max-stack", 0x4C, 0, 33, False),
        ("v4-max-stack", 0x4D, 0, 33, False),
        ("v4-packed-max-stack", 0x4D, 0, 33, True),
        ("v4-max-locals", 0x4D, 8193, 1, False),
        ("v4-packed-max-locals", 0x4D, 8193, 1, True),
    )
    for name, version, max_locals, max_stack, packed in capacity_cases:
        image = BUILD / f"{name}.pjvm"
        write_capacity_image(image, version, max_locals, max_stack, packed)
        expect_capacity_reject(picojvm, image)
        print(f"PASS: {name} rejected")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
