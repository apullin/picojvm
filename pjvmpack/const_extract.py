"""Extraction of @Const array initializers from simple <clinit> bytecode."""

import struct

from .classfile import ClassReader
from .constants import (
    PJVM_ELEM_BYTE,
    PJVM_ELEM_CHAR,
    PJVM_ELEM_INT,
    PJVM_ELEM_SHORT,
    PJVM_ELEM_STRING_REF,
)

_ATYPE = {4: "Z", 5: "C", 6: "F", 7: "D", 8: "B", 9: "S", 10: "I", 11: "J"}
_ELEM_TYPE = {
    "B": PJVM_ELEM_BYTE,
    "C": PJVM_ELEM_CHAR,
    "S": PJVM_ELEM_SHORT,
    "I": PJVM_ELEM_INT,
    "Z": PJVM_ELEM_BYTE,
}


def _resolve_classref(cp, cp_idx):
    entry = cp[cp_idx]
    if entry and entry[0] == "Class":
        return cp[entry[1]][1]
    return None


def _resolve_fieldref(cp, cp_idx):
    entry = cp[cp_idx]
    if entry and entry[0] == "Fieldref":
        nat = cp[entry[2]]
        return cp[nat[1]][1], cp[nat[2]][1]
    return None, None


def extract_const_arrays(cls, cp, _static_field_base_slot=0, verbose=False):
    """Extract supported @Const array initializer patterns from <clinit>.

    Returns ``(field_name, elem_type_code, values, (start, end))`` tuples. If
    the initializer is not one of the simple javac array-store patterns, it is
    left untouched by returning no entry.
    """
    if not cls.const_fields:
        return []

    clinit_code = None
    for _m_access, m_name_idx, _m_desc_idx, code_data in cls.methods_raw:
        m_name = cp[m_name_idx][1]
        if m_name == "<clinit>" and code_data is not None:
            cr = ClassReader(code_data)
            cr.skip_u2(2)
            code_length = cr.u4()
            clinit_code = cr.read(code_length)
            break

    if clinit_code is None:
        return []

    results = []
    bc = clinit_code
    n = len(bc)
    i = 0
    pending = {}
    stack = []
    last_push_start = None

    while i < n:
        op = bc[i]

        if op == 0x00:
            i += 1
        elif op == 0x02:
            last_push_start = i
            stack.append(-1)
            i += 1
        elif op == 0x01:
            last_push_start = i
            stack.append(("null",))
            i += 1
        elif 0x03 <= op <= 0x08:
            last_push_start = i
            stack.append(op - 0x03)
            i += 1
        elif op == 0x10:
            last_push_start = i
            stack.append(struct.unpack_from(">b", bc, i + 1)[0])
            i += 2
        elif op == 0x11:
            last_push_start = i
            stack.append(struct.unpack_from(">h", bc, i + 1)[0])
            i += 3
        elif op == 0x12:
            last_push_start = i
            cp_idx = bc[i + 1]
            entry = cp[cp_idx]
            if entry and entry[0] == "Integer":
                stack.append(entry[1])
            elif entry and entry[0] == "String":
                stack.append(("string", cp[entry[1]][1]))
            else:
                stack.append(("opaque",))
            i += 2
        elif op == 0x13:
            last_push_start = i
            cp_idx = (bc[i + 1] << 8) | bc[i + 2]
            entry = cp[cp_idx]
            if entry and entry[0] == "Integer":
                stack.append(entry[1])
            elif entry and entry[0] == "String":
                stack.append(("string", cp[entry[1]][1]))
            else:
                stack.append(("opaque",))
            i += 3
        elif op == 0xBC:
            atype = bc[i + 1]
            size = stack.pop() if stack else 0
            type_char = _ATYPE.get(atype, "?")
            arr_id = len(results) + len(pending)
            seq_start = last_push_start if last_push_start is not None else i
            arr = {
                "type": type_char,
                "size": size,
                "values": [0] * size,
                "start": seq_start,
            }
            stack.append(("array", arr_id))
            pending[arr_id] = arr
            i += 2
        elif op == 0xBD:
            cp_idx = (bc[i + 1] << 8) | bc[i + 2]
            class_name = _resolve_classref(cp, cp_idx)
            size = stack.pop() if stack else 0
            arr_id = len(results) + len(pending)
            seq_start = last_push_start if last_push_start is not None else i
            if class_name == "java/lang/String" and isinstance(size, int):
                arr = {
                    "type": "Ljava/lang/String;",
                    "size": size,
                    "values": [None] * size,
                    "start": seq_start,
                }
                stack.append(("array", arr_id))
                pending[arr_id] = arr
            else:
                stack.append(("opaque",))
            i += 3
        elif op == 0x59:
            if stack:
                stack.append(stack[-1])
            i += 1
        elif op in (0x4F, 0x54, 0x55, 0x56):
            val = stack.pop() if stack else 0
            idx = stack.pop() if stack else 0
            aref = stack.pop() if stack else None
            if isinstance(aref, tuple) and aref[0] == "array":
                arr = pending.get(aref[1])
                if arr and isinstance(idx, int) and isinstance(val, int):
                    if 0 <= idx < arr["size"]:
                        mask = {
                            0x4F: 0xFFFFFFFF,
                            0x54: 0xFF,
                            0x55: 0xFFFF,
                            0x56: 0xFFFF,
                        }[op]
                        arr["values"][idx] = val & mask
            i += 1
        elif op == 0x53:
            val = stack.pop() if stack else None
            idx = stack.pop() if stack else 0
            aref = stack.pop() if stack else None
            if isinstance(aref, tuple) and aref[0] == "array":
                arr = pending.get(aref[1])
                if arr and arr["type"] == "Ljava/lang/String;" and isinstance(idx, int):
                    if 0 <= idx < arr["size"] and (
                            isinstance(val, tuple) and val[0] in ("string", "null")):
                        arr["values"][idx] = None if val[0] == "null" else val[1]
            i += 1
        elif op == 0xB3:
            cp_idx = (bc[i + 1] << 8) | bc[i + 2]
            field_name, field_desc = _resolve_fieldref(cp, cp_idx)
            val = stack.pop() if stack else None
            if (field_name and field_name in cls.const_fields and
                    isinstance(val, tuple) and val[0] == "array"):
                arr = pending.pop(val[1], None)
                if arr:
                    etype = _ELEM_TYPE.get(arr["type"])
                    if arr["type"] == "Ljava/lang/String;" and field_desc == "[Ljava/lang/String;":
                        etype = PJVM_ELEM_STRING_REF
                    if etype is not None:
                        end_off = i + 3
                        results.append(
                            (field_name, etype, arr["values"], (arr["start"], end_off))
                        )
                        if verbose:
                            print(
                                f"    @Const {field_name}: "
                                f"{arr['type']}[{arr['size']}] "
                                f"({end_off - arr['start']}B clinit code)"
                            )
            i += 3
        elif op == 0xB1:
            i += 1
        else:
            op_len = {
                0x15: 2, 0x19: 2, 0x36: 2, 0x3A: 2, 0x84: 3,
                0xA7: 3,
                0x99: 3, 0x9A: 3, 0x9B: 3, 0x9C: 3, 0x9D: 3, 0x9E: 3,
                0x9F: 3, 0xA0: 3, 0xA1: 3, 0xA2: 3, 0xA3: 3, 0xA4: 3,
                0xB2: 3, 0xB4: 3, 0xB5: 3,
                0xB6: 3, 0xB7: 3, 0xB8: 3,
                0xB9: 5,
                0xBB: 3,
                0xC0: 3, 0xC1: 3,
                0xC6: 3, 0xC7: 3,
            }.get(op, 1)
            stack.clear()
            i += op_len

    return results
