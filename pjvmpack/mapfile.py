"""Emission of .pjvmmap sidecar files."""

import struct

from .constants import PJVM_NO_CLASS
from .emit import require_u8


def emit_map(class_order, method_table):
    """Emit a packed binary .pjvmmap file for exception trace decoding."""
    require_u8("map class count", len(class_order))
    require_u8("map method count", len(method_table))
    out = bytearray()
    out.append(len(class_order))
    out.append(len(method_table))

    for name in class_order:
        short = name.rsplit("/", 1)[-1]
        encoded = short.encode("utf-8")
        out.append(len(encoded))
        out.extend(encoded)

    for mt in method_table:
        cid = 0xFF if mt["class_id"] == PJVM_NO_CLASS else mt["class_id"]
        require_u8("map class id", cid)
        out.append(cid)
        encoded = mt["name"].encode("utf-8")
        out.append(len(encoded))
        out.extend(encoded)
        out.extend(struct.pack("<H", mt.get("code_offset", 0)))
        lines = mt.get("line_table", [])
        out.append(len(lines))
        for pc, line in lines:
            out.extend(struct.pack("<H", pc))
            out.extend(struct.pack("<H", line))

    return bytes(out)
