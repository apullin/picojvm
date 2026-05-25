"""Extraction of @Const array initializers from simple <clinit> bytecode."""

import struct

from .bytecode import (
    FIXED_OPCODE_LENGTHS,
    OP_AASTORE,
    OP_ACONST_NULL,
    OP_ANEWARRAY,
    OP_BASTORE,
    OP_BIPUSH,
    OP_CASTORE,
    OP_DUP,
    OP_ICONST_0,
    OP_ICONST_5,
    OP_ICONST_M1,
    OP_I2B,
    OP_I2C,
    OP_I2S,
    OP_IASTORE,
    OP_INVOKESTATIC,
    OP_LDC,
    OP_LDC_W,
    OP_NEW,
    OP_NEWARRAY,
    OP_NOP,
    OP_PUTSTATIC,
    OP_RETURN,
    OP_SASTORE,
    OP_SIPUSH,
    OP_INVOKESPECIAL,
)
from .classfile import ClassReader
from .constants import (
    PJVM_ELEM_BYTE,
    PJVM_ELEM_CHAR,
    PJVM_ELEM_INT,
    PJVM_ELEM_OBJECT_REF,
    PJVM_ELEM_SHORT,
    PJVM_ELEM_STRING_REF,
)
from .descriptors import argument_descriptors
from .errors import PackError
from .resolve import resolve_method_name

_ATYPE = {4: "Z", 5: "C", 6: "F", 7: "D", 8: "B", 9: "S", 10: "I", 11: "J"}
_ELEM_TYPE = {
    "B": PJVM_ELEM_BYTE,
    "C": PJVM_ELEM_CHAR,
    "S": PJVM_ELEM_SHORT,
    "I": PJVM_ELEM_INT,
    "Z": PJVM_ELEM_BYTE,
}


def _narrow_int(value, op):
    if op == OP_I2B:
        narrowed = value & 0xFF
        return narrowed - 0x100 if narrowed & 0x80 else narrowed
    if op == OP_I2S:
        narrowed = value & 0xFFFF
        return narrowed - 0x10000 if narrowed & 0x8000 else narrowed
    if op == OP_I2C:
        return value & 0xFFFF
    return value


def _apply_narrow_ops(value, ops):
    for op in ops:
        value = _narrow_int(value, op)
    return value


def _resolve_classref(cp, cp_idx):
    entry = cp[cp_idx]
    if entry and entry[0] == "Class":
        return cp[entry[1]][1]
    return None


def _resolve_fieldref(cp, cp_idx):
    entry = cp[cp_idx]
    if entry and entry[0] == "Fieldref":
        nat = cp[entry[2]]
        class_name = _resolve_classref(cp, entry[1])
        return class_name, cp[nat[1]][1], cp[nat[2]][1]
    return None, None, None


def extract_const_arrays(cls, cp, _static_field_base_slot=0, verbose=False,
                         factory_methods=None):
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
    next_new_id = 0
    factory_methods = factory_methods or {}

    while i < n:
        op = bc[i]

        if op == OP_NOP:
            i += 1
        elif op == OP_ICONST_M1:
            last_push_start = i
            stack.append(-1)
            i += 1
        elif op == OP_ACONST_NULL:
            last_push_start = i
            stack.append(("null",))
            i += 1
        elif OP_ICONST_0 <= op <= OP_ICONST_5:
            last_push_start = i
            stack.append(op - OP_ICONST_0)
            i += 1
        elif op == OP_BIPUSH:
            last_push_start = i
            stack.append(struct.unpack_from(">b", bc, i + 1)[0])
            i += 2
        elif op == OP_SIPUSH:
            last_push_start = i
            stack.append(struct.unpack_from(">h", bc, i + 1)[0])
            i += 3
        elif op == OP_LDC:
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
        elif op == OP_LDC_W:
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
        elif op == OP_NEWARRAY:
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
        elif op == OP_ANEWARRAY:
            cp_idx = (bc[i + 1] << 8) | bc[i + 2]
            class_name = _resolve_classref(cp, cp_idx)
            size = stack.pop() if stack else 0
            arr_id = len(results) + len(pending)
            seq_start = last_push_start if last_push_start is not None else i
            if class_name == "java/lang/String" and isinstance(size, int):
                arr = {
                    "type": "Ljava/lang/String;",
                    "class_name": class_name,
                    "kind": "string",
                    "size": size,
                    "values": [None] * size,
                    "start": seq_start,
                }
                stack.append(("array", arr_id))
                pending[arr_id] = arr
            elif class_name and isinstance(size, int):
                arr = {
                    "type": f"L{class_name};",
                    "class_name": class_name,
                    "kind": "object",
                    "size": size,
                    "values": [None] * size,
                    "start": seq_start,
                }
                stack.append(("array", arr_id))
                pending[arr_id] = arr
            else:
                stack.append(("opaque",))
            i += 3
        elif op == OP_DUP:
            if stack:
                stack.append(stack[-1])
            i += 1
        elif op == OP_NEW:
            cp_idx = (bc[i + 1] << 8) | bc[i + 2]
            class_name = _resolve_classref(cp, cp_idx)
            stack.append(("new", class_name, next_new_id))
            next_new_id += 1
            i += 3
        elif op == OP_INVOKESPECIAL:
            cp_idx = (bc[i + 1] << 8) | bc[i + 2]
            method_class, method_name, method_desc = resolve_method_name(cp, cp_idx)
            args = []
            try:
                arg_descs = argument_descriptors(method_desc)
            except PackError:
                arg_descs = None
            if method_name == "<init>" and arg_descs is not None:
                for _ in arg_descs:
                    args.append(stack.pop() if stack else ("opaque",))
                args.reverse()
                objref = stack.pop() if stack else None
                if (isinstance(objref, tuple) and objref[0] == "new" and
                        objref[1] == method_class):
                    obj = ("object", method_class, method_desc, tuple(args))
                    stack = [obj if item == objref else item for item in stack]
                else:
                    stack.clear()
            else:
                stack.clear()
            i += 3
        elif op in (OP_I2B, OP_I2C, OP_I2S):
            if stack and isinstance(stack[-1], int):
                stack[-1] = _narrow_int(stack[-1], op)
            elif stack:
                stack[-1] = ("opaque",)
            i += 1
        elif op == OP_INVOKESTATIC:
            cp_idx = (bc[i + 1] << 8) | bc[i + 2]
            method_key = resolve_method_name(cp, cp_idx)
            factory = factory_methods.get(method_key)
            if factory is None:
                stack.clear()
            else:
                call_args = []
                try:
                    arg_descs = argument_descriptors(method_key[2])
                except PackError:
                    arg_descs = None
                if arg_descs is None:
                    stack.clear()
                else:
                    for _ in arg_descs:
                        call_args.append(stack.pop() if stack else ("opaque",))
                    call_args.reverse()
                    obj_args = []
                    for source in factory["arg_sources"]:
                        if isinstance(source, tuple) and source[0] == "param":
                            value = call_args[source[1]]
                            if isinstance(value, int):
                                value = _apply_narrow_ops(value, source[2])
                            obj_args.append(value)
                        else:
                            obj_args.append(source)
                    stack.append((
                        "object", factory["class_name"], factory["constructor"],
                        tuple(obj_args)))
            i += 3
        elif op in (OP_IASTORE, OP_BASTORE, OP_CASTORE, OP_SASTORE):
            val = stack.pop() if stack else 0
            idx = stack.pop() if stack else 0
            aref = stack.pop() if stack else None
            if isinstance(aref, tuple) and aref[0] == "array":
                arr = pending.get(aref[1])
                if arr and isinstance(idx, int) and isinstance(val, int):
                    if 0 <= idx < arr["size"]:
                        mask = {
                            OP_IASTORE: 0xFFFFFFFF,
                            OP_BASTORE: 0xFF,
                            OP_CASTORE: 0xFFFF,
                            OP_SASTORE: 0xFFFF,
                        }[op]
                        arr["values"][idx] = val & mask
            i += 1
        elif op == OP_AASTORE:
            val = stack.pop() if stack else None
            idx = stack.pop() if stack else 0
            aref = stack.pop() if stack else None
            if isinstance(aref, tuple) and aref[0] == "array":
                arr = pending.get(aref[1])
                if arr and arr["kind"] == "string" and isinstance(idx, int):
                    if 0 <= idx < arr["size"] and (
                            isinstance(val, tuple) and val[0] in ("string", "null")):
                        arr["values"][idx] = None if val[0] == "null" else val[1]
                elif arr and arr["kind"] == "object" and isinstance(idx, int):
                    if 0 <= idx < arr["size"]:
                        if isinstance(val, tuple) and val[0] == "null":
                            arr["values"][idx] = None
                        elif (isinstance(val, tuple) and val[0] == "object" and
                              val[1] == arr["class_name"]):
                            arr["values"][idx] = {
                                "class_name": val[1],
                                "constructor": val[2],
                                "args": list(val[3]),
                            }
            i += 1
        elif op == OP_PUTSTATIC:
            cp_idx = (bc[i + 1] << 8) | bc[i + 2]
            _field_class, field_name, field_desc = _resolve_fieldref(cp, cp_idx)
            val = stack.pop() if stack else None
            if (field_name and field_name in cls.const_fields and
                    isinstance(val, tuple) and val[0] == "array"):
                arr = pending.pop(val[1], None)
                if arr:
                    etype = _ELEM_TYPE.get(arr["type"])
                    if arr.get("kind") == "string" and field_desc == "[Ljava/lang/String;":
                        etype = PJVM_ELEM_STRING_REF
                    elif arr.get("kind") == "object" and field_desc == f"[{arr['type']}":
                        etype = PJVM_ELEM_OBJECT_REF
                    if etype is not None:
                        end_off = i + 3
                        values = arr if etype == PJVM_ELEM_OBJECT_REF else arr["values"]
                        results.append(
                            (field_name, etype, values, (arr["start"], end_off))
                        )
                        if verbose:
                            print(
                                f"    @Const {field_name}: "
                                f"{arr['type']}[{arr['size']}] "
                                f"({end_off - arr['start']}B clinit code)"
                            )
            i += 3
        elif op == OP_RETURN:
            i += 1
        else:
            stack.clear()
            i += FIXED_OPCODE_LENGTHS[op]

    return results
