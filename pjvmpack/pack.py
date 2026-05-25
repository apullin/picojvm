"""Core .pjvm packing pipeline."""

import struct

from .bytecode import (
    OP_ACONST_NULL,
    OP_ALOAD,
    OP_ALOAD_0,
    OP_ALOAD_3,
    OP_ARETURN,
    OP_BIPUSH,
    OP_DUP,
    OP_ICONST_0,
    OP_ICONST_5,
    OP_ICONST_M1,
    OP_NOP,
    OP_I2B,
    OP_I2C,
    OP_I2S,
    OP_GETFIELD,
    OP_GETSTATIC,
    OP_ILOAD,
    OP_ILOAD_0,
    OP_ILOAD_3,
    OP_ANEWARRAY,
    OP_CHECKCAST,
    OP_INSTANCEOF,
    OP_INVOKEDYNAMIC,
    OP_INVOKEINTERFACE,
    OP_INVOKESTATIC,
    OP_INVOKESPECIAL,
    OP_INVOKEVIRTUAL,
    OP_LDC,
    OP_LDC2_W,
    OP_LDC_W,
    OP_MULTIANEWARRAY,
    OP_NEW,
    OP_PUTFIELD,
    OP_PUTSTATIC,
    OP_RETURN,
    OP_SIPUSH,
    add_ordered_cp_use,
    bytecode_cp_operands,
    rewrite_bytecode_cp_indices,
)
from .classfile import ClassReader, parse_class
from .const_extract import extract_const_arrays as _extract_const_arrays
from .constants import (
    ACC_FINAL,
    ACC_NATIVE,
    ACC_STATIC,
    ARRAY_NATIVE_IDS,
    ENUM_NATIVE_IDS,
    NATIVE_IDS,
    NATIVE_OBJECT_INIT,
    PJVM_CONST_NULL_REF,
    PJVM_CP_STR_FLAG,
    PJVM_CP_UNRESOLVED,
    PJVM_ELEM_OBJECT_REF,
    PJVM_ELEM_STRING_REF,
    PJVM_REF_ROM_STRING,
    PJVM_ET_ENTRY,
    PJVM_ET_ENTRY_V4,
    PJVM_HDR_SIZE_V3,
    PJVM_HDR_SIZE_V4,
    PJVM_MAGIC,
    PJVM_MAX_REAL_ID,
    PJVM_MAX_U8,
    PJVM_MAX_U16,
    PJVM_MAX_VTABLE_ENTRIES_V3,
    PJVM_MAX_VTABLE_ENTRIES_V4,
    PJVM_MT_ENTRY,
    PJVM_MT_ENTRY_V4,
    PJVM_NO_CLASS,
    PJVM_NO_CLINIT,
    PJVM_NO_VTABLE,
    PJVM_RF_CONST_DATA,
    PJVM_RF_PACKED_METHOD_TABLE,
    PJVM_RF_PIN_HINTS,
    PJVM_RF_REF_BITMAPS,
    PJVM_VERSION_V3,
    PJVM_VERSION_V4,
    STRING_NATIVE_IDS,
)
from .descriptors import argument_descriptors, count_args, is_ref_descriptor
from .emit import (
    require_real_id,
    require_real_id_v4,
    require_u8,
    require_u16,
)
from .formats.v3 import emit_id as emit_id_v3
from .formats.v4 import emit_id as emit_id_v4
from .formats.v4 import encode_method_table_packed as encode_v4_method_table_packed
from .errors import PackError
from .model import ClassInfo
from .resolve import resolve_class_name, resolve_method_name, topological_sort


# Standard exception hierarchy synthesized when bytecode references exceptions
# without shipping full classfiles for them.
EXCEPTION_HIERARCHY = {
    "java/lang/Throwable": "java/lang/Object",
    "java/lang/Exception": "java/lang/Throwable",
    "java/lang/RuntimeException": "java/lang/Exception",
    "java/lang/ArithmeticException": "java/lang/RuntimeException",
    "java/lang/NullPointerException": "java/lang/RuntimeException",
    "java/lang/ArrayIndexOutOfBoundsException": "java/lang/RuntimeException",
    "java/lang/IndexOutOfBoundsException": "java/lang/RuntimeException",
    "java/lang/ClassCastException": "java/lang/RuntimeException",
    "java/lang/IllegalArgumentException": "java/lang/RuntimeException",
    "java/lang/IllegalStateException": "java/lang/RuntimeException",
    "java/lang/StackOverflowError": "java/lang/Throwable",
}


OPCODE_NAMES = {
    OP_LDC: "ldc",
    OP_LDC_W: "ldc_w",
    OP_LDC2_W: "ldc2_w",
    OP_GETSTATIC: "getstatic",
    OP_PUTSTATIC: "putstatic",
    OP_GETFIELD: "getfield",
    OP_PUTFIELD: "putfield",
    OP_INVOKEVIRTUAL: "invokevirtual",
    OP_INVOKESPECIAL: "invokespecial",
    OP_INVOKESTATIC: "invokestatic",
    OP_INVOKEINTERFACE: "invokeinterface",
    OP_INVOKEDYNAMIC: "invokedynamic",
    OP_NEW: "new",
    OP_ANEWARRAY: "anewarray",
    OP_CHECKCAST: "checkcast",
    OP_INSTANCEOF: "instanceof",
    OP_MULTIANEWARRAY: "multianewarray",
}

IGNORED_CLASS_REF_OPS = {OP_ANEWARRAY, OP_MULTIANEWARRAY}


def _opcode_name(op):
    return OPCODE_NAMES.get(op, f"op 0x{op:02X}")


def _describe_cp_entry(cp, cp_idx):
    entry = cp[cp_idx]
    if entry is None:
        return "<empty constant-pool slot>"
    tag = entry[0]
    if tag in ("Methodref", "InterfaceMethodref"):
        ref_class, ref_method, ref_desc = resolve_method_name(cp, cp_idx)
        return f"{ref_class}.{ref_method}{ref_desc}"
    if tag == "Fieldref":
        ref_class = resolve_class_name(cp, entry[1])
        nat = cp[entry[2]]
        field_name = cp[nat[1]][1]
        field_desc = cp[nat[2]][1]
        return f"{ref_class}.{field_name}:{field_desc}"
    if tag == "Class":
        return cp[entry[1]][1]
    if tag == "String":
        return "String literal"
    if tag == "Integer":
        return "Integer literal"
    return tag


def _find_unresolved_cp_uses(class_name, cls, cp_resolve, uses):
    """Return bytecode CP references that still have no runtime resolution."""
    errors = []
    first_uses = uses["first"]
    for cp_idx in uses["all"]:
        if cp_resolve[cp_idx] != PJVM_CP_UNRESOLVED:
            continue
        method_name, method_desc, op, bytecode_off = first_uses[cp_idx]
        if op in IGNORED_CLASS_REF_OPS:
            # The runtime currently ignores the type CP operand for reference
            # array allocation. Keeping this permissive preserves old images
            # such as `new String[n]` without requiring java/lang/String.class.
            continue
        errors.append(
            f"{class_name}.{method_name}{method_desc}+{bytecode_off}: "
            f"{_opcode_name(op)} CP#{cp_idx} {_describe_cp_entry(cls.cp, cp_idx)}"
        )
    return errors


def _exception_classes_needed(classes, class_order):
    """Return synthetic exception class names referenced by parsed classes."""
    needed = set()
    for name in class_order:
        cls = classes[name]
        for _m_access, _m_name_idx, _m_desc_idx, code_data in cls.methods_raw:
            if code_data is None:
                continue
            cr = ClassReader(code_data)
            cr.skip_u2(2)
            code_length = cr.u4()
            cr.read(code_length)
            exc_count = cr.u2()
            for _ in range(exc_count):
                cr.skip_u2(3)
                catch_type = cr.u2()
                if catch_type != 0:
                    cname = resolve_class_name(cls.cp, catch_type)
                    if cname and cname not in classes:
                        needed.add(cname)

        for cp_idx in range(1, len(cls.cp)):
            entry = cls.cp[cp_idx]
            if entry and entry[0] == "Class":
                cname = cls.cp[entry[1]][1]
                if cname in EXCEPTION_HIERARCHY and cname not in classes:
                    needed.add(cname)
    return needed


def _with_exception_parents(classes, exc_classes_needed):
    """Include parent exception classes required by synthetic exceptions."""
    needed = set(exc_classes_needed)
    to_add = set(exc_classes_needed)
    while to_add:
        next_add = set()
        for ename in to_add:
            parent = EXCEPTION_HIERARCHY.get(ename)
            if parent and parent != "java/lang/Object" and parent not in classes and parent not in needed:
                needed.add(parent)
                next_add.add(parent)
        to_add = next_add
    return needed


def _sort_synthetic_exceptions(classes, exc_classes_needed):
    """Sort synthetic exception names so parents precede children."""
    sorted_exc = []
    added = set(classes.keys())
    remaining = set(exc_classes_needed)
    while remaining:
        progress = False
        for ename in list(remaining):
            parent = EXCEPTION_HIERARCHY.get(ename, "java/lang/Object")
            if parent == "java/lang/Object" or parent in added:
                sorted_exc.append(ename)
                added.add(ename)
                remaining.discard(ename)
                progress = True
        if not progress:
            sorted_exc.extend(remaining)
            break
    return sorted_exc


def _program_needs_enum(classes):
    """Return True if javac enum boilerplate references java/lang/Enum."""
    if "java/lang/Enum" in classes:
        return False
    for cls in classes.values():
        if cls.parent_name == "java/lang/Enum":
            return True
        for cp_idx in range(1, len(cls.cp)):
            entry = cls.cp[cp_idx]
            if entry and entry[0] == "Class" and cls.cp[entry[1]][1] == "java/lang/Enum":
                return True
    return False


def _synthesize_enum_class(classes, verbose=False):
    """Add the tiny system java/lang/Enum parent used by javac enums."""
    if not _program_needs_enum(classes):
        return
    cp = [
        None,
        ("Utf8", "name"),
        ("Utf8", "Ljava/lang/String;"),
        ("Utf8", "ordinal"),
        ("Utf8", "I"),
    ]
    fields = [
        (ACC_FINAL, 1, 2, False),
        (ACC_FINAL, 3, 4, False),
    ]
    classes["java/lang/Enum"] = ClassInfo(
        "java/lang/Enum", "java/lang/Object", cp, fields, [])
    if verbose:
        print("  Synthesized system class java/lang/Enum")


def _synthesize_exception_classes(classes, class_order, verbose=False):
    """Append minimal synthetic exception classes to classes/class_order."""
    exc_classes_needed = _with_exception_parents(
        classes, _exception_classes_needed(classes, class_order))
    for ename in _sort_synthetic_exceptions(classes, exc_classes_needed):
        parent = EXCEPTION_HIERARCHY.get(ename, "java/lang/Object")
        cid = len(class_order)
        cls = ClassInfo(ename, parent, [], [], [])
        cls.class_id = cid
        if parent in classes:
            cls.parent_class_id = classes[parent].class_id
        else:
            cls.parent_class_id = PJVM_NO_CLASS
        classes[ename] = cls
        class_order.append(ename)
        if verbose:
            print(f"  Synthetic exception class #{cid}: {ename} "
                  f"(parent_id={cls.parent_class_id})")

    for name in class_order:
        cls = classes[name]
        if cls.parent_class_id == PJVM_NO_CLASS and cls.parent_name and cls.parent_name in classes:
            cls.parent_class_id = classes[cls.parent_name].class_id


def _v3_needs_wide(classes, class_order, method_table, main_index,
                   global_int_constants, global_string_constants,
                   global_exc_table, cp_bytes, total_vtable_entries):
    """Return True when selected program metadata cannot fit v3 ids/counts."""
    if len(method_table) > PJVM_MAX_U8 or main_index > PJVM_MAX_REAL_ID:
        return True
    if len(global_int_constants) > PJVM_MAX_U8:
        return True
    if len(class_order) > PJVM_MAX_U8:
        return True
    if len(global_string_constants) > PJVM_MAX_U8:
        return True
    if cp_bytes > PJVM_MAX_U16:
        return True
    if total_vtable_entries > PJVM_MAX_VTABLE_ENTRIES_V3:
        return True
    for cname in class_order:
        cls = classes[cname]
        if cls.parent_class_id != PJVM_NO_CLASS and cls.parent_class_id > PJVM_MAX_REAL_ID:
            return True
        if len(cls.all_instance_fields) > PJVM_MAX_U8:
            return True
        if len(cls.vtable) > PJVM_MAX_U8:
            return True
        if cls.clinit_mi != PJVM_NO_CLINIT and cls.clinit_mi > PJVM_MAX_REAL_ID:
            return True
        if any(vt_entry > PJVM_MAX_REAL_ID for vt_entry in cls.vtable):
            return True
    for mt in method_table:
        if mt["max_locals"] > PJVM_MAX_U8 or mt["max_stack"] > PJVM_MAX_U8:
            return True
        if mt["arg_count"] > PJVM_MAX_U8:
            return True
        if mt["cp_base"] > PJVM_MAX_U16:
            return True
        if mt["vtable_slot"] != PJVM_NO_VTABLE and mt["vtable_slot"] > PJVM_MAX_REAL_ID:
            return True
        if mt.get("vmid", PJVM_NO_VTABLE) != PJVM_NO_VTABLE and mt["vmid"] > PJVM_MAX_REAL_ID:
            return True
        if mt["exc_count"] > PJVM_MAX_U8 or mt["exc_offset_idx"] > PJVM_MAX_U8:
            return True
    return any(catch_cid != PJVM_NO_CLASS and catch_cid > PJVM_MAX_REAL_ID
               for (_start, _end, _handler, catch_cid) in global_exc_table)


def _select_format(pjvm_format, pack_method_table, needs_v4):
    """Resolve user format request into the actual emitted format."""
    if pjvm_format not in ("auto", "v3", "v4"):
        raise PackError(f"Unknown .pjvm format: {pjvm_format}")
    if pjvm_format == "auto":
        emit_v4 = needs_v4
    elif pjvm_format == "v4":
        emit_v4 = True
    else:
        if needs_v4:
            raise PackError(".pjvm v3 limits exceeded; use --format v4")
        emit_v4 = False
    if pack_method_table and not emit_v4:
        raise PackError("packed method tables require .pjvm v4")
    return emit_v4


def _validate_class_limits(classes, class_order, emit_v4):
    """Validate class-table values against selected format width."""
    for name in class_order:
        cls = classes[name]
        if emit_v4:
            if cls.parent_class_id != PJVM_NO_CLASS:
                require_real_id_v4(f"{name} parent class id", cls.parent_class_id)
            require_u16(f"{name} instance field count", len(cls.all_instance_fields))
            require_u16(f"{name} vtable size", len(cls.vtable))
            if cls.clinit_mi != PJVM_NO_CLINIT:
                require_real_id_v4(f"{name} <clinit> method index", cls.clinit_mi)
            for vt_entry in cls.vtable:
                require_real_id_v4(f"{name} vtable method index", vt_entry)
        else:
            if cls.parent_class_id != PJVM_NO_CLASS:
                require_real_id(f"{name} parent class id", cls.parent_class_id)
            require_u8(f"{name} instance field count", len(cls.all_instance_fields))
            require_u8(f"{name} vtable size", len(cls.vtable))
            if cls.clinit_mi != PJVM_NO_CLINIT:
                require_real_id(f"{name} <clinit> method index", cls.clinit_mi)
            for vt_entry in cls.vtable:
                require_real_id(f"{name} vtable method index", vt_entry)


def _validate_method_limits(method_table, emit_v4):
    """Validate method-table values against selected format width."""
    for i, mt in enumerate(method_table):
        if emit_v4:
            require_u16(f"method #{i} max_locals", mt["max_locals"])
            require_u16(f"method #{i} max_stack", mt["max_stack"])
            require_u16(f"method #{i} arg_count", mt["arg_count"])
        else:
            require_u8(f"method #{i} max_locals", mt["max_locals"])
            require_u8(f"method #{i} max_stack", mt["max_stack"])
            require_u8(f"method #{i} arg_count", mt["arg_count"])
        flags = 0
        if mt["is_native"]:
            flags = 1 | (mt["native_id"] << 1)
        if emit_v4:
            require_u16(f"method #{i} flags", flags)
            require_u16(f"method #{i} vtable slot",
                        0 if mt["vtable_slot"] == PJVM_NO_VTABLE else mt["vtable_slot"])
            require_u16(f"method #{i} virtual method id",
                        0 if mt.get("vmid", PJVM_NO_VTABLE) == PJVM_NO_VTABLE else mt["vmid"])
            require_u16(f"method #{i} exception count", mt["exc_count"])
            require_u16(f"method #{i} exception offset", mt["exc_offset_idx"])
        else:
            require_u8(f"method #{i} flags", flags)
            require_u16(f"method #{i} cp_base", mt["cp_base"])
            if mt["vtable_slot"] != PJVM_NO_VTABLE:
                require_real_id(f"method #{i} vtable slot", mt["vtable_slot"])
            if mt.get("vmid", PJVM_NO_VTABLE) != PJVM_NO_VTABLE:
                require_real_id(f"method #{i} virtual method id", mt["vmid"])
            require_u8(f"method #{i} exception count", mt["exc_count"])
            require_u8(f"method #{i} exception offset", mt["exc_offset_idx"])


def _validate_exception_limits(global_exc_table, emit_v4):
    """Validate exception catch class ids against selected format width."""
    for i, (_start, _end, _handler, catch_cid) in enumerate(global_exc_table):
        if catch_cid != PJVM_NO_CLASS:
            if emit_v4:
                require_real_id_v4(f"exception #{i} catch class id", catch_cid)
            else:
                require_real_id(f"exception #{i} catch class id", catch_cid)


def _validate_and_select_format(pjvm_format, pack_method_table, classes,
                                class_order, method_table, main_index,
                                global_int_constants, global_string_constants,
                                global_cp_resolve, global_exc_table,
                                total_static_fields):
    """Check format limits and return ``(emit_v4, cp_bytes)``."""
    cp_bytes = len(global_cp_resolve) * 2
    total_vtable_entries = sum(len(classes[name].vtable) for name in class_order)
    if len(global_int_constants) > 0x8000:
        raise PackError("Integer constant count exceeds 16-bit CP entry tagging limit")
    if len(global_string_constants) > 0x8000:
        raise PackError("String constant count exceeds 16-bit CP entry tagging limit")
    for i, val in enumerate(global_cp_resolve):
        require_u16(f"CP resolution entry #{i}", val)

    needs_v4 = _v3_needs_wide(
        classes, class_order, method_table, main_index,
        global_int_constants, global_string_constants, global_exc_table,
        cp_bytes, total_vtable_entries) or pack_method_table
    emit_v4 = _select_format(pjvm_format, pack_method_table, needs_v4)

    require_u16("static field count", total_static_fields)
    if emit_v4:
        if total_vtable_entries > PJVM_MAX_VTABLE_ENTRIES_V4:
            raise PackError("Total vtable entries exceed .pjvm v4 runtime base limit")
    elif total_vtable_entries > PJVM_MAX_VTABLE_ENTRIES_V3:
        raise PackError("Total vtable entries exceed .pjvm v3 runtime base limit")

    _validate_class_limits(classes, class_order, emit_v4)
    _validate_method_limits(method_table, emit_v4)
    _validate_exception_limits(global_exc_table, emit_v4)
    return emit_v4, cp_bytes


def _intern_const_string_refs(vals, global_string_constants, string_constant_dedup):
    """Convert extracted String[] values to const-data string indices."""
    string_indices = []
    for value in vals:
        if value is None:
            string_indices.append(PJVM_CONST_NULL_REF)
            continue
        if value in string_constant_dedup:
            string_index = string_constant_dedup[value]
        else:
            string_index = len(global_string_constants)
            global_string_constants.append(value.encode("utf-8"))
            string_constant_dedup[value] = string_index
        if string_index >= PJVM_CONST_NULL_REF:
            raise PackError("String constant index collides with @Const null sentinel")
        string_indices.append(string_index)
    return string_indices


def _code_bytes(code_data):
    """Return only bytecode bytes from a raw Code attribute body."""
    cr = ClassReader(code_data)
    cr.skip_u2(2)
    code_length = cr.u4()
    return cr.read(code_length)


def _resolve_fieldref_name(cp, cp_idx):
    entry = cp[cp_idx]
    if entry is None or entry[0] != "Fieldref":
        raise PackError(f"CP#{cp_idx} is not a Fieldref")
    class_name = resolve_class_name(cp, entry[1])
    nat = cp[entry[2]]
    return class_name, cp[nat[1]][1], cp[nat[2]][1]


def _read_local_load(bc, offset):
    """Read iload/aload forms used by trivial value constructors."""
    op = bc[offset]
    if OP_ILOAD_0 <= op <= OP_ILOAD_3:
        return "I", op - OP_ILOAD_0, offset + 1
    if OP_ALOAD_0 <= op <= OP_ALOAD_3:
        return "A", op - OP_ALOAD_0, offset + 1
    if op == OP_ILOAD:
        return "I", bc[offset + 1], offset + 2
    if op == OP_ALOAD:
        return "A", bc[offset + 1], offset + 2
    return None, None, offset


def _return_descriptor(descriptor):
    return descriptor[descriptor.index(")") + 1:]


def _arg_local_map(descriptor):
    """Map JVM local slots to single-slot argument indices/descriptors."""
    arg_for_local = {}
    local = 0
    for arg_idx, arg_desc in enumerate(argument_descriptors(descriptor)):
        if arg_desc in ("J", "D"):
            return None
        arg_for_local[local] = (arg_idx, arg_desc)
        local += 1
    return arg_for_local


def _is_int_like_descriptor(descriptor):
    return descriptor in ("I", "B", "S", "C", "Z")


def _is_string_descriptor(descriptor):
    return descriptor == "Ljava/lang/String;"


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


def _read_narrow_ops(bc, offset):
    ops = []
    while offset < len(bc) and bc[offset] in (OP_I2B, OP_I2C, OP_I2S):
        ops.append(bc[offset])
        offset += 1
    return tuple(ops), offset


def _narrow_ops_match(source_desc, target_desc, ops):
    """Check whether a load plus narrowing ops can feed ``target_desc``."""
    if not ops:
        return source_desc == target_desc
    if not _is_int_like_descriptor(source_desc):
        return False
    if ops[-1] == OP_I2B:
        return target_desc == "B"
    if ops[-1] == OP_I2S:
        return target_desc == "S"
    if ops[-1] == OP_I2C:
        return target_desc == "C"
    return False


def _read_const_factory_push(cp, bc, offset, arg_for_local):
    """Read one load/literal push in a trivial const-object factory."""
    kind, local_idx, next_offset = _read_local_load(bc, offset)
    if kind in ("I", "A"):
        narrow_ops, next_offset = _read_narrow_ops(bc, next_offset)
        arg_info = arg_for_local.get(local_idx)
        if arg_info is None:
            return None, offset
        arg_idx, arg_desc = arg_info
        if kind == "I" and not _is_int_like_descriptor(arg_desc):
            return None, offset
        if kind == "A" and not _is_string_descriptor(arg_desc):
            return None, offset
        if kind == "A" and narrow_ops:
            return None, offset
        return ("param", arg_idx, narrow_ops), next_offset

    op = bc[offset]
    if op == OP_ACONST_NULL:
        return ("null",), offset + 1
    if op == OP_ICONST_M1:
        value, next_offset = -1, offset + 1
        narrow_ops, next_offset = _read_narrow_ops(bc, next_offset)
        return _apply_narrow_ops(value, narrow_ops), next_offset
    if OP_ICONST_0 <= op <= OP_ICONST_5:
        value, next_offset = op - OP_ICONST_0, offset + 1
        narrow_ops, next_offset = _read_narrow_ops(bc, next_offset)
        return _apply_narrow_ops(value, narrow_ops), next_offset
    if op == OP_BIPUSH:
        value, next_offset = struct.unpack_from(">b", bc, offset + 1)[0], offset + 2
        narrow_ops, next_offset = _read_narrow_ops(bc, next_offset)
        return _apply_narrow_ops(value, narrow_ops), next_offset
    if op == OP_SIPUSH:
        value, next_offset = struct.unpack_from(">h", bc, offset + 1)[0], offset + 3
        narrow_ops, next_offset = _read_narrow_ops(bc, next_offset)
        return _apply_narrow_ops(value, narrow_ops), next_offset
    if op in (OP_LDC, OP_LDC_W):
        if op == OP_LDC:
            cp_idx = bc[offset + 1]
            next_offset = offset + 2
        else:
            cp_idx = (bc[offset + 1] << 8) | bc[offset + 2]
            next_offset = offset + 3
        entry = cp[cp_idx]
        if entry and entry[0] == "Integer":
            narrow_ops, next_offset = _read_narrow_ops(bc, next_offset)
            return _apply_narrow_ops(entry[1], narrow_ops), next_offset
        if entry and entry[0] == "String":
            return ("string", cp[entry[1]][1]), next_offset
    return None, offset


def _const_factory_source_matches(source, target_desc, factory_arg_descs):
    """Return whether a factory push source can feed a constructor argument."""
    if isinstance(source, int):
        return _is_int_like_descriptor(target_desc)
    if isinstance(source, tuple) and source[0] == "string":
        return _is_string_descriptor(target_desc)
    if isinstance(source, tuple) and source[0] == "null":
        return _is_string_descriptor(target_desc)
    if isinstance(source, tuple) and source[0] == "param":
        return _narrow_ops_match(factory_arg_descs[source[1]], target_desc, source[2])
    return False


def _const_object_factory_layout(cls, method_desc, code_data):
    """Recognize ``return new T(args...)`` static helper bytecode."""
    if code_data is None or not _return_descriptor(method_desc).startswith("L"):
        return None

    arg_for_local = _arg_local_map(method_desc)
    if arg_for_local is None:
        return None
    factory_arg_descs = argument_descriptors(method_desc)

    bc = _code_bytes(code_data)
    i = 0
    if i + 3 > len(bc) or bc[i] != OP_NEW:
        return None
    class_name = resolve_class_name(cls.cp, (bc[i + 1] << 8) | bc[i + 2])
    if _return_descriptor(method_desc) != f"L{class_name};":
        return None
    i += 3

    if i >= len(bc) or bc[i] != OP_DUP:
        return None
    i += 1

    arg_sources = []
    while i < len(bc) and bc[i] != OP_INVOKESPECIAL:
        source, next_i = _read_const_factory_push(cls.cp, bc, i, arg_for_local)
        if source is None or next_i == i:
            return None
        arg_sources.append(source)
        i = next_i

    if i + 3 > len(bc) or bc[i] != OP_INVOKESPECIAL:
        return None
    init_class, init_name, init_desc = resolve_method_name(
        cls.cp, (bc[i + 1] << 8) | bc[i + 2])
    if init_class != class_name or init_name != "<init>" or not init_desc.endswith(")V"):
        return None
    i += 3

    ctor_arg_descs = argument_descriptors(init_desc)
    if len(ctor_arg_descs) != len(arg_sources):
        return None
    if any(not _const_factory_source_matches(src, desc, factory_arg_descs)
           for src, desc in zip(arg_sources, ctor_arg_descs)):
        return None

    if i >= len(bc) or bc[i] != OP_ARETURN or i + 1 != len(bc):
        return None

    return {
        "class_name": class_name,
        "constructor": init_desc,
        "arg_sources": arg_sources,
    }


def _collect_const_object_factories(classes):
    """Collect static helpers that can be folded into @Const object arrays."""
    factory_methods = {}
    for cls in classes.values():
        for m_access, m_name_idx, m_desc_idx, code_data in cls.methods_raw:
            if not (m_access & ACC_STATIC):
                continue
            method_name = cls.cp[m_name_idx][1]
            if method_name in ("<init>", "<clinit>"):
                continue
            method_desc = cls.cp[m_desc_idx][1]
            layout = _const_object_factory_layout(cls, method_desc, code_data)
            if layout is not None:
                factory_methods[(cls.name, method_name, method_desc)] = layout
    return factory_methods


def _const_object_constructor_layout(cls):
    """Validate and describe the supported immutable value-object subset."""
    if cls.parent_name not in (None, "java/lang/Object"):
        raise PackError(f"@Const object {cls.name}: superclass is not Object")

    own_fields = []
    for f_access, f_name_idx, f_desc_idx, _f_is_const in cls.fields_raw:
        if f_access & ACC_STATIC:
            continue
        field_name = cls.cp[f_name_idx][1]
        field_desc = cls.cp[f_desc_idx][1]
        if not (f_access & ACC_FINAL):
            raise PackError(f"@Const object {cls.name}.{field_name}: field is not final")
        if field_desc not in ("I", "B", "S", "C", "Z", "Ljava/lang/String;"):
            raise PackError(f"@Const object {cls.name}.{field_name}: unsupported field {field_desc}")
        own_fields.append((field_name, field_desc))

    ctors = []
    for _m_access, m_name_idx, m_desc_idx, code_data in cls.methods_raw:
        if cls.cp[m_name_idx][1] == "<init>":
            ctors.append((cls.cp[m_desc_idx][1], code_data))
    if len(ctors) != 1:
        raise PackError(f"@Const object {cls.name}: expected exactly one constructor")

    ctor_desc, code_data = ctors[0]
    if code_data is None:
        raise PackError(f"@Const object {cls.name}: constructor has no bytecode")
    arg_descs = argument_descriptors(ctor_desc)

    arg_for_local = {}
    local = 1
    for arg_idx, arg_desc in enumerate(arg_descs):
        arg_for_local[local] = arg_idx
        local += 2 if arg_desc in ("J", "D") else 1

    bc = _code_bytes(code_data)
    i = 0
    kind, local_idx, i = _read_local_load(bc, i)
    if kind != "A" or local_idx != 0 or i + 3 > len(bc) or bc[i] != OP_INVOKESPECIAL:
        raise PackError(f"@Const object {cls.name}: constructor is not trivial")
    init_class, init_name, init_desc = resolve_method_name(
        cls.cp, (bc[i + 1] << 8) | bc[i + 2])
    if (init_class, init_name, init_desc) != ("java/lang/Object", "<init>", "()V"):
        raise PackError(f"@Const object {cls.name}: constructor does not call Object.<init>")
    i += 3

    field_args = {}
    while i < len(bc):
        if bc[i] == OP_RETURN:
            i += 1
            break
        kind, local_idx, i = _read_local_load(bc, i)
        if kind != "A" or local_idx != 0:
            raise PackError(f"@Const object {cls.name}: constructor has non-field side effects")
        load_kind, value_local, i = _read_local_load(bc, i)
        narrow_ops, i = _read_narrow_ops(bc, i)
        if load_kind not in ("I", "A") or i + 3 > len(bc) or bc[i] != OP_PUTFIELD:
            raise PackError(f"@Const object {cls.name}: constructor has unsupported assignment")
        field_class, field_name, field_desc = _resolve_fieldref_name(
            cls.cp, (bc[i + 1] << 8) | bc[i + 2])
        i += 3
        if field_class != cls.name:
            raise PackError(f"@Const object {cls.name}: constructor assigns inherited field")
        if value_local not in arg_for_local:
            raise PackError(f"@Const object {cls.name}: constructor does not assign from an argument")
        arg_idx = arg_for_local[value_local]
        if load_kind == "A" and narrow_ops:
            raise PackError(f"@Const object {cls.name}.{field_name}: reference narrowing is invalid")
        if not _narrow_ops_match(arg_descs[arg_idx], field_desc, narrow_ops):
            raise PackError(f"@Const object {cls.name}.{field_name}: constructor arg type mismatch")
        if field_name in field_args:
            raise PackError(f"@Const object {cls.name}.{field_name}: assigned more than once")
        field_args[field_name] = (arg_idx, narrow_ops)

    if i != len(bc):
        raise PackError(f"@Const object {cls.name}: constructor has trailing bytecode")

    field_arg_indices = []
    for field_name, _field_desc in own_fields:
        if field_name not in field_args:
            raise PackError(f"@Const object {cls.name}.{field_name}: not assigned by constructor")
        field_arg_indices.append(field_args[field_name])

    return {
        "descriptor": ctor_desc,
        "fields": own_fields,
        "field_arg_indices": field_arg_indices,
    }


def _const_object_slot(field_desc, value, global_string_constants, string_constant_dedup):
    """Encode one final field value as a normal VM 32-bit slot."""
    if field_desc == "Ljava/lang/String;":
        if isinstance(value, tuple) and value[0] == "null":
            return 0, 0
        if not (isinstance(value, tuple) and value[0] == "string"):
            raise PackError("@Const object String field is not a literal string/null")
        string_idx = _intern_const_string_refs(
            [value[1]], global_string_constants, string_constant_dedup)[0]
        return string_idx, PJVM_REF_ROM_STRING

    if not isinstance(value, int):
        raise PackError(f"@Const object primitive field is not a literal int: {value!r}")
    if field_desc == "Z":
        value = 1 if value else 0
    elif field_desc == "B":
        value = _narrow_int(value, OP_I2B)
    elif field_desc == "S":
        value = _narrow_int(value, OP_I2S)
    elif field_desc == "C":
        value = _narrow_int(value, OP_I2C)
    raw = value & 0xFFFFFFFF
    return raw & 0xFFFF, (raw >> 16) & 0xFFFF


def _build_const_object_array(arr, classes, global_string_constants, string_constant_dedup):
    """Convert an extracted object array into packed fixed-width records."""
    class_name = arr["class_name"]
    if class_name not in classes:
        raise PackError(f"@Const object array uses unpacked class {class_name}")
    value_cls = classes[class_name]
    layout = _const_object_constructor_layout(value_cls)
    n_fields = len(layout["fields"])
    records = []

    for obj in arr["values"]:
        if obj is None:
            records.append(None)
            continue
        if obj["class_name"] != class_name:
            raise PackError(f"@Const object array {class_name}: subclass elements are unsupported")
        if obj["constructor"] != layout["descriptor"]:
            raise PackError(f"@Const object array {class_name}: constructor descriptor mismatch")

        slots = []
        for (field_name, field_desc), (arg_idx, narrow_ops) in zip(
                layout["fields"], layout["field_arg_indices"]):
            try:
                arg_value = obj["args"][arg_idx]
            except IndexError as exc:
                raise PackError(
                    f"@Const object array {class_name}.{field_name}: missing argument") from exc
            if isinstance(arg_value, int):
                arg_value = _apply_narrow_ops(arg_value, narrow_ops)
            slots.append(_const_object_slot(
                field_desc, arg_value, global_string_constants, string_constant_dedup))
        records.append(slots)

    return {
        "class_name": class_name,
        "class_id": value_cls.class_id,
        "n_fields": n_fields,
        "records": records,
    }


def _const_array_length(etype, vals):
    return len(vals["records"]) if etype == PJVM_ELEM_OBJECT_REF else len(vals)


def _extract_program_const_arrays(classes, class_order, static_field_base,
                                  global_string_constants, string_constant_dedup,
                                  verbose=False):
    """Extract all supported @Const arrays and return NOP ranges."""
    const_arrays = []
    const_nop_ranges = {}
    factory_methods = _collect_const_object_factories(classes)
    for name in class_order:
        cls = classes[name]
        if not cls.const_fields:
            continue
        extracted = _extract_const_arrays(cls, cls.cp, static_field_base.get(name, 0),
                                          verbose=verbose,
                                          factory_methods=factory_methods)
        for fname, etype, vals, (bstart, bend) in extracted:
            if etype == PJVM_ELEM_STRING_REF:
                vals = _intern_const_string_refs(
                    vals, global_string_constants, string_constant_dedup)
            elif etype == PJVM_ELEM_OBJECT_REF:
                vals = _build_const_object_array(
                    vals, classes, global_string_constants, string_constant_dedup)
            const_arrays.append((fname, etype, vals, name))
            const_nop_ranges.setdefault(name, []).append((bstart, bend))
    return const_arrays, const_nop_ranges


def _nop_const_initializers(classes, method_table, const_nop_ranges):
    """Replace extracted const-array initializer bytecode with NOPs."""
    for name, ranges in const_nop_ranges.items():
        cls = classes[name]
        for mt in method_table:
            if mt["class_id"] == cls.class_id and mt["name"] == "<clinit>":
                bc_arr = bytearray(mt["bytecode"])
                for start, end in ranges:
                    for offset in range(start, end):
                        bc_arr[offset] = 0x00
                mt["bytecode"] = bytes(bc_arr)
                break


def _nop_array_checkcasts(classes, method_table, class_by_id):
    """Erase array checkcasts; picoJVM arrays do not carry descriptor class ids."""
    for mt in method_table:
        cid = mt["class_id"]
        if mt["is_native"] or cid == PJVM_NO_CLASS:
            continue
        cname = class_by_id[cid]
        cls = classes[cname]
        bc_arr = bytearray(mt["bytecode"])
        changed = False
        for off, _width, op, cp_idx in bytecode_cp_operands(mt["bytecode"]):
            if op != OP_CHECKCAST or cp_idx <= 0 or cp_idx >= len(cls.cp):
                continue
            entry = cls.cp[cp_idx]
            if entry is None or entry[0] != "Class":
                continue
            if not cls.cp[entry[1]][1].startswith("["):
                continue
            op_off = off - 1
            bc_arr[op_off] = OP_NOP
            bc_arr[op_off + 1] = OP_NOP
            bc_arr[op_off + 2] = OP_NOP
            changed = True
        if changed:
            mt["bytecode"] = bytes(bc_arr)


def _build_bytecode_section(method_table):
    """Concatenate non-native method bytecode and assign code offsets."""
    bytecode_section = bytearray()
    for mt in method_table:
        if mt["is_native"]:
            mt["code_offset"] = 0
        else:
            mt["code_offset"] = len(bytecode_section)
            bytecode_section.extend(mt["bytecode"])
    return bytecode_section


def pack_pjvm(class_data_list, verbose=False, v2=False, pin_hints=None,
              compact_cp=True, pjvm_format="auto",
              pack_method_table=False):  # v2 ignored, v3/v4 only
    """Pack one or more .class files into a single .pjvm binary."""

    # Parse classfiles into a name-keyed graph.
    classes = {}
    for class_data in class_data_list:
        cp, this_class, super_class, fields, methods = parse_class(class_data)
        name = resolve_class_name(cp, this_class)
        parent_name = resolve_class_name(cp, super_class)
        classes[name] = ClassInfo(name, parent_name, cp, fields, methods)

    if verbose:
        print(f"Parsed {len(classes)} classes: {', '.join(classes.keys())}")

    _synthesize_enum_class(classes, verbose=verbose)

    # Parents must be laid out before children inherit fields/vtables.
    class_order = topological_sort(classes)
    for i, name in enumerate(class_order):
        classes[name].class_id = i
        parent_name = classes[name].parent_name
        if parent_name and parent_name in classes:
            classes[name].parent_class_id = classes[parent_name].class_id
        else:
            classes[name].parent_class_id = PJVM_NO_CLASS

    if verbose:
        for name in class_order:
            cls = classes[name]
            parent = f"extends {cls.parent_name}" if cls.parent_name else ""
            print(f"  Class #{cls.class_id}: {name} {parent}")

    # Synthesize minimal exception classes referenced by bytecode.
    _synthesize_exception_classes(classes, class_order, verbose=verbose)

    # Build instance/static field layouts.
    for name in class_order:
        cls = classes[name]
        parent = classes.get(cls.parent_name) if cls.parent_name else None

        if parent:
            cls.all_instance_fields = list(parent.all_instance_fields)
            cls.all_instance_field_descs = list(parent.all_instance_field_descs)
            cls.all_instance_field_is_ref = list(parent.all_instance_field_is_ref)
        else:
            cls.all_instance_fields = []
            cls.all_instance_field_descs = []
            cls.all_instance_field_is_ref = []

        for f_access, f_name_idx, f_desc_idx, f_is_const in cls.fields_raw:
            field_name = cls.cp[f_name_idx][1]
            field_desc = cls.cp[f_desc_idx][1]
            if f_access & ACC_STATIC:  # ACC_STATIC
                cls.static_fields.append(field_name)
                if f_is_const:
                    cls.const_fields.add(field_name)
            else:
                slot = len(cls.all_instance_fields)
                cls.own_instance_fields.append((field_name, slot))
                cls.all_instance_fields.append(field_name)
                cls.all_instance_field_descs.append(field_desc)
                cls.all_instance_field_is_ref.append(is_ref_descriptor(field_desc))

        if verbose and cls.all_instance_fields:
            print(f"    Instance fields: {cls.all_instance_fields}")

    # Build global method table and per-class vtables.
    vmid_map = {}
    next_vmid = [0]

    def get_vmid(mname, mdesc):
        key = (mname, mdesc)
        if key not in vmid_map:
            vmid_map[key] = next_vmid[0]
            next_vmid[0] += 1
        return vmid_map[key]

    method_table = []

    def append_native_method(method_name, descriptor, arg_count, native_id,
                             class_id=PJVM_NO_CLASS):
        native_idx = len(method_table)
        method_table.append({
            "name": method_name,
            "descriptor": descriptor,
            "max_locals": arg_count,
            "max_stack": 0,
            "arg_count": arg_count,
            "bytecode": b"",
            "is_native": True,
            "native_id": native_id,
            "class_id": class_id,
            "vtable_slot": PJVM_NO_VTABLE,
            "vmid": PJVM_NO_VTABLE,
            "cp_base": 0,
            "exc_table": [],
            "line_table": [],
        })
        return native_idx

    for name in class_order:
        cls = classes[name]
        parent = classes.get(cls.parent_name) if cls.parent_name else None

        # Inherit parent's vtable
        if parent:
            cls.vtable = list(parent.vtable)
            cls.method_vtable_slots = dict(parent.method_vtable_slots)
        else:
            cls.vtable = []
            cls.method_vtable_slots = {}

        for m_access, m_name_idx, m_desc_idx, code_data in cls.methods_raw:
            m_name = cls.cp[m_name_idx][1]
            m_desc = cls.cp[m_desc_idx][1]

            is_static = bool(m_access & ACC_STATIC)
            is_native = bool(m_access & ACC_NATIVE)

            if is_native:
                continue  # declared native (like Native.java) — skip

            if code_data is None:
                continue

            arg_count = count_args(m_desc)
            if not is_static:
                arg_count += 1  # 'this'

            # Parse Code attribute
            cr = ClassReader(code_data)
            max_stack = cr.u2()
            max_locals = cr.u2()
            code_length = cr.u4()
            bytecode = cr.read(code_length)
            exc_count = cr.u2()
            exc_table = []
            for _ in range(exc_count):
                e_start = cr.u2()
                e_end = cr.u2()
                e_handler = cr.u2()
                e_catch_type = cr.u2()  # CP index (0 = catch-all)
                exc_table.append((e_start, e_end, e_handler, e_catch_type))

            # Parse Code sub-attributes (LineNumberTable, etc.)
            line_number_table = []
            code_attr_count = cr.u2()
            for _ in range(code_attr_count):
                ca_name_idx = cr.u2()
                ca_len = cr.u4()
                ca_data = cr.read(ca_len)
                ca_name = cls.cp[ca_name_idx][1] if cls.cp[ca_name_idx] else ""
                if ca_name == "LineNumberTable":
                    lr = ClassReader(ca_data)
                    lnt_len = lr.u2()
                    for _ in range(lnt_len):
                        start_pc = lr.u2()
                        line_num = lr.u2()
                        line_number_table.append((start_pc, line_num))

            global_idx = len(method_table)
            if m_name == "<clinit>":
                cls.clinit_mi = global_idx

            # Determine vtable slot
            vtable_slot = PJVM_NO_VTABLE
            if not is_static and m_name != "<init>":
                key = (m_name, m_desc)
                if key in cls.method_vtable_slots:
                    # Override parent method
                    vtable_slot = cls.method_vtable_slots[key]
                    cls.vtable[vtable_slot] = global_idx
                else:
                    # New virtual method
                    vtable_slot = len(cls.vtable)
                    cls.vtable.append(global_idx)
                cls.method_vtable_slots[key] = vtable_slot

            cls.global_methods[(m_name, m_desc)] = global_idx

            method_table.append({
                "name": m_name,
                "descriptor": m_desc,
                "max_locals": max_locals,
                "max_stack": max_stack,
                "arg_count": arg_count,
                "bytecode": bytecode,
                "is_native": False,
                "native_id": 0,
                "class_id": cls.class_id,
                "vtable_slot": vtable_slot,
                "vmid": get_vmid(m_name, m_desc) if vtable_slot != PJVM_NO_VTABLE else PJVM_NO_VTABLE,
                "cp_base": 0,  # filled later
                "exc_table": exc_table,
                "line_table": line_number_table,
            })

            if verbose:
                vt_str = f"vt={vtable_slot}" if vtable_slot != PJVM_NO_VTABLE else "static"
                print(f"  Method #{global_idx}: {name}.{m_name}{m_desc} "
                      f"(locals={max_locals}, stack={max_stack}, args={arg_count}, "
                      f"code={len(bytecode)}B, {vt_str})")

    enum_cls = classes.get("java/lang/Enum")
    if enum_cls is not None:
        for (m_name, m_desc), native_id in ENUM_NATIVE_IDS.items():
            if (m_name, m_desc) in enum_cls.global_methods:
                continue
            a_count = count_args(m_desc)
            if m_name != "valueOf":
                a_count += 1
            native_idx = append_native_method(
                m_name, m_desc, a_count, native_id, class_id=enum_cls.class_id)
            enum_cls.global_methods[(m_name, m_desc)] = native_idx
            if verbose:
                print(f"  Native #{native_idx}: Enum.{m_name}{m_desc} "
                      f"(id={native_id})")

    # Locate the program entry point.
    main_index = None
    for i, mt in enumerate(method_table):
        if mt["name"] == "main" and mt["descriptor"] == "([Ljava/lang/String;)V":
            main_index = i
            break
    if main_index is None:
        raise PackError("No main method found")

    # Static field offsets per class (into global static_fields array)
    static_field_base = {}
    total_static_fields = 0
    for name in class_order:
        cls = classes[name]
        static_field_base[name] = total_static_fields
        total_static_fields += len(cls.static_fields)

    global_string_constants = []  # list of UTF-8 byte strings
    string_constant_dedup = {}   # utf8_text -> index

    # Extract and erase supported @Const initializers before CP compaction so
    # removed setup bytecode does not keep dead constant-pool entries alive.
    const_arrays, const_nop_ranges = _extract_program_const_arrays(
        classes, class_order, static_field_base, global_string_constants,
        string_constant_dedup, verbose=verbose)
    _nop_const_initializers(classes, method_table, const_nop_ranges)

    # Record exactly which original classfile CP indices are referenced from
    # executable bytecode.  Compact mode remaps only these entries into a dense
    # per-class .pjvm resolution slice.
    class_by_id = {}
    for name in class_order:
        class_by_id[classes[name].class_id] = name
    _nop_array_checkcasts(classes, method_table, class_by_id)
    class_cp_uses = {}
    for name in class_order:
        class_cp_uses[name] = {
            "all": [],
            "all_set": set(),
            "ldc": [],
            "ldc_set": set(),
            "first": {},
        }
    for mt in method_table:
        cid = mt["class_id"]
        if mt["is_native"] or cid == PJVM_NO_CLASS:
            continue
        cname = class_by_id[cid]
        uses = class_cp_uses[cname]
        for off, _width, op, cp_idx in bytecode_cp_operands(mt["bytecode"]):
            add_ordered_cp_use(uses, cp_idx, op == OP_LDC)
            uses["first"].setdefault(cp_idx, (mt["name"], mt["descriptor"], op, off - 1))

    # Add native/external methods referenced by bytecode.
    native_cache = {}  # (class, name, desc) -> global method index

    for name in class_order:
        cls = classes[name]
        cp_scan = class_cp_uses[name]["all"] if compact_cp else range(1, len(cls.cp))
        for cp_idx in cp_scan:
            if cp_idx <= 0 or cp_idx >= len(cls.cp):
                raise PackError(f"{name}: bytecode references invalid CP index {cp_idx}")
            entry = cls.cp[cp_idx]
            if entry is None or entry[0] != "Methodref":
                continue
            ref_class, ref_method, ref_desc = resolve_method_name(cls.cp, cp_idx)
            key = (ref_class, ref_method, ref_desc)

            if key in native_cache:
                continue

            if ref_class == "Native" or ref_class == "pj/Native":
                if ref_method not in NATIVE_IDS:
                    raise PackError(f"Unknown native: {ref_class}.{ref_method}")
                nm_idx = len(method_table)
                a_count = count_args(ref_desc)
                method_table.append({
                    "name": ref_method,
                    "descriptor": ref_desc,
                    "max_locals": a_count,
                    "max_stack": 0,
                    "arg_count": a_count,
                    "bytecode": b"",
                    "is_native": True,
                    "native_id": NATIVE_IDS[ref_method],
                    "class_id": PJVM_NO_CLASS,
                    "vtable_slot": PJVM_NO_VTABLE,
                    "vmid": PJVM_NO_VTABLE,
                    "cp_base": 0,
                    "exc_table": [],
                    "line_table": [],
                })
                native_cache[key] = nm_idx
                if verbose:
                    print(f"  Native #{nm_idx}: {ref_class}.{ref_method}{ref_desc} "
                          f"(id={NATIVE_IDS[ref_method]})")

            elif ref_method == "<init>" and (
                    ref_class == "java/lang/Object" or
                    ref_class in EXCEPTION_HIERARCHY):
                nm_idx = append_native_method(
                    "<init>", ref_desc, count_args(ref_desc) + 1,
                    NATIVE_OBJECT_INIT)
                native_cache[key] = nm_idx
                if verbose:
                    print(f"  Native #{nm_idx}: {ref_class}.<init>{ref_desc} "
                          "(no-op)")

            elif ref_class == "java/lang/String":
                str_key = (ref_method, ref_desc)
                if str_key in STRING_NATIVE_IDS:
                    nm_idx = len(method_table)
                    a_count = count_args(ref_desc) + 1  # +1 for 'this'
                    method_table.append({
                        "name": ref_method,
                        "descriptor": ref_desc,
                        "max_locals": a_count,
                        "max_stack": 0,
                        "arg_count": a_count,
                        "bytecode": b"",
                        "is_native": True,
                        "native_id": STRING_NATIVE_IDS[str_key],
                        "class_id": PJVM_NO_CLASS,
                        "vtable_slot": PJVM_NO_VTABLE,
                        "vmid": PJVM_NO_VTABLE,
                        "cp_base": 0,
                        "exc_table": [],
                        "line_table": [],
                    })
                    native_cache[key] = nm_idx
                    if verbose:
                        print(f"  Native #{nm_idx}: String.{ref_method}"
                              f"{ref_desc} (id={STRING_NATIVE_IDS[str_key]})")

            elif ref_class.startswith("["):
                arr_key = (ref_method, ref_desc)
                if arr_key in ARRAY_NATIVE_IDS:
                    native_id = ARRAY_NATIVE_IDS[arr_key]
                    nm_idx = append_native_method(
                        ref_method, ref_desc, 1, native_id)
                    native_cache[key] = nm_idx
                    if verbose:
                        print(f"  Native #{nm_idx}: array.{ref_method}"
                              f"{ref_desc} (id={native_id})")

    # Add interface method stubs used by invokeinterface dispatch.
    for name in class_order:
        cls = classes[name]
        cp_scan = class_cp_uses[name]["all"] if compact_cp else range(1, len(cls.cp))
        for cp_idx in cp_scan:
            if cp_idx <= 0 or cp_idx >= len(cls.cp):
                raise PackError(f"{name}: bytecode references invalid CP index {cp_idx}")
            entry = cls.cp[cp_idx]
            if entry is None or entry[0] != "InterfaceMethodref":
                continue
            ref_class, ref_method, ref_desc = resolve_method_name(cls.cp, cp_idx)
            key = (ref_class, ref_method, ref_desc)
            if key in native_cache:
                continue
            nm_idx = len(method_table)
            a_count = count_args(ref_desc) + 1  # +1 for 'this'
            method_table.append({
                "name": ref_method,
                "descriptor": ref_desc,
                "max_locals": a_count,
                "max_stack": 0,
                "arg_count": a_count,
                "bytecode": b"",
                "is_native": False,
                "native_id": 0,
                "class_id": PJVM_NO_CLASS,
                "vtable_slot": PJVM_NO_VTABLE,
                "vmid": get_vmid(ref_method, ref_desc),
                "cp_base": 0,
                "exc_table": [],
                "line_table": [],
            })
            native_cache[key] = nm_idx
            if verbose:
                print(f"  Interface stub #{nm_idx}: "
                      f"{ref_class}.{ref_method}{ref_desc} "
                      f"(vmid={method_table[-1]['vmid']})")

    # Build compact per-class CP resolution slices.
    global_cp_resolve = []  # list of uint16 values (v3: 16-bit CP entries)
    global_int_constants = []

    cp_bases = {}  # class_name -> cp_base offset
    cp_old_entries = 0
    cp_new_entries = 0
    unresolved_cp_errors = []

    for name in class_order:
        cls = classes[name]
        cp = cls.cp
        cp_old_entries += len(cp)
        if compact_cp:
            cp_indices = class_cp_uses[name]["all"]
            for cp_idx in cp_indices:
                if cp_idx <= 0 or cp_idx >= len(cp):
                    raise PackError(f"{name}: bytecode references invalid CP index {cp_idx}")
        else:
            cp_indices = range(1, len(cp))
        cp_base = len(global_cp_resolve) * 2  # byte offset (2 bytes per entry)
        cp_bases[name] = cp_base

        cp_resolve = [PJVM_CP_UNRESOLVED] * len(cp)

        # Resolve Methodrefs
        for cp_idx in cp_indices:
            entry = cp[cp_idx]
            if entry is None or entry[0] != "Methodref":
                continue
            ref_class, ref_method, ref_desc = resolve_method_name(cp, cp_idx)
            nkey = (ref_class, ref_method, ref_desc)

            # Check native cache first
            if nkey in native_cache:
                cp_resolve[cp_idx] = native_cache[nkey]
                continue

            # Search in our classes (walk up hierarchy)
            mkey = (ref_method, ref_desc)
            resolved = False
            walk = ref_class
            while walk:
                if walk in classes:
                    target = classes[walk]
                    if mkey in target.global_methods:
                        cp_resolve[cp_idx] = target.global_methods[mkey]
                        resolved = True
                        break
                    walk = target.parent_name
                else:
                    break

            if not resolved and verbose:
                print(f"  WARNING: unresolved Methodref "
                      f"{ref_class}.{ref_method}{ref_desc}")

        # Resolve InterfaceMethodrefs
        for cp_idx in cp_indices:
            entry = cp[cp_idx]
            if entry is None or entry[0] != "InterfaceMethodref":
                continue
            ref_class, ref_method, ref_desc = resolve_method_name(cp, cp_idx)
            nkey = (ref_class, ref_method, ref_desc)
            if nkey in native_cache:
                cp_resolve[cp_idx] = native_cache[nkey]

        # Resolve Fieldrefs
        for cp_idx in cp_indices:
            entry = cp[cp_idx]
            if entry is None or entry[0] != "Fieldref":
                continue
            ref_class_name = resolve_class_name(cp, entry[1])
            nat = cp[entry[2]]
            field_name = cp[nat[1]][1]

            target_cls = classes.get(ref_class_name)
            if not target_cls:
                # Walk up from referencing class
                walk = name
                while walk and walk in classes:
                    target_cls = classes[walk]
                    break
                if not target_cls:
                    continue

            # Check static fields
            found = False
            for slot, sf in enumerate(target_cls.static_fields):
                if sf == field_name:
                    cp_resolve[cp_idx] = static_field_base[ref_class_name] + slot
                    found = True
                    break

            if not found:
                # Check instance fields (search up hierarchy)
                walk = ref_class_name
                while walk and walk in classes:
                    tc = classes[walk]
                    for slot, iname in enumerate(tc.all_instance_fields):
                        if iname == field_name:
                            cp_resolve[cp_idx] = slot
                            found = True
                            break
                    if found:
                        break
                    walk = tc.parent_name

        # Resolve Integer constants
        for cp_idx in cp_indices:
            entry = cp[cp_idx]
            if entry is None or entry[0] != "Integer":
                continue
            ic_idx = len(global_int_constants)
            global_int_constants.append(entry[1])
            cp_resolve[cp_idx] = ic_idx

        # Resolve String constants → 0x80 | string_index
        for cp_idx in cp_indices:
            entry = cp[cp_idx]
            if entry is None or entry[0] != "String":
                continue
            utf8_text = cp[entry[1]][1]  # CONSTANT_String -> CONSTANT_Utf8
            if utf8_text in string_constant_dedup:
                sc_idx = string_constant_dedup[utf8_text]
            else:
                sc_idx = len(global_string_constants)
                global_string_constants.append(utf8_text.encode("utf-8"))
                string_constant_dedup[utf8_text] = sc_idx
            cp_resolve[cp_idx] = PJVM_CP_STR_FLAG | sc_idx

        # Resolve Class refs → class_id (for 'new' and 'anewarray')
        for cp_idx in cp_indices:
            entry = cp[cp_idx]
            if entry is None or entry[0] != "Class":
                continue
            ref_name = cp[entry[1]][1]
            if ref_name in classes:
                cp_resolve[cp_idx] = classes[ref_name].class_id

        unresolved_cp_errors.extend(
            _find_unresolved_cp_uses(name, cls, cp_resolve, class_cp_uses[name])
        )

        if compact_cp:
            uses = class_cp_uses[name]
            ldc_order = [idx for idx in uses["ldc"] if idx in uses["all_set"]]
            if len(ldc_order) > 256:
                raise PackError(f"{name}: too many distinct ldc CP references")
            ldc_set = set(ldc_order)
            cp_order = ldc_order + [idx for idx in uses["all"] if idx not in ldc_set]
            cp_index_map = {old_idx: new_idx for new_idx, old_idx in enumerate(cp_order)}
            global_cp_resolve.extend(cp_resolve[old_idx] for old_idx in cp_order)
            cp_new_entries += len(cp_order)

            for mt in method_table:
                if mt["is_native"] or mt["class_id"] != cls.class_id:
                    continue
                mt["bytecode"] = rewrite_bytecode_cp_indices(mt["bytecode"], cp_index_map)
        else:
            global_cp_resolve.extend(cp_resolve)
            cp_new_entries += len(cp_resolve)

    if unresolved_cp_errors:
        max_shown = 24
        shown = "\n".join(f"  {msg}" for msg in unresolved_cp_errors[:max_shown])
        remaining = len(unresolved_cp_errors) - max_shown
        if remaining > 0:
            shown += f"\n  ... {remaining} more"
        raise PackError(
            f"Unresolved bytecode references ({len(unresolved_cp_errors)}):\n{shown}"
        )

    # Set cp_base on all method entries
    for mt in method_table:
        cid = mt["class_id"]
        if cid != PJVM_NO_CLASS:
            for cname in class_order:
                if classes[cname].class_id == cid:
                    mt["cp_base"] = cp_bases[cname]
                    break

    # Build the global exception table.
    global_exc_table = []  # list of (start_pc, end_pc, handler_pc, catch_class_id)
    for mt in method_table:
        mt["exc_offset_idx"] = len(global_exc_table)
        mt["exc_count"] = len(mt["exc_table"])
        for (e_start, e_end, e_handler, e_catch_cp) in mt["exc_table"]:
            if e_catch_cp == 0:
                catch_cid = PJVM_NO_CLASS  # catch-all (finally)
            else:
                # Resolve CP index to class name, then to class_id
                cid = mt.get("class_id", PJVM_NO_CLASS)
                catch_cid = PJVM_NO_CLASS
                # Find the class whose CP owns this method
                if cid != PJVM_NO_CLASS:
                    for cname in class_order:
                        if classes[cname].class_id == cid:
                            cp = classes[cname].cp
                            catch_name = resolve_class_name(cp, e_catch_cp)
                            if catch_name and catch_name in classes:
                                catch_cid = classes[catch_name].class_id
                            elif verbose:
                                print(f"  WARNING: unresolved catch type "
                                      f"CP#{e_catch_cp} ({catch_name})")
                            break
            global_exc_table.append((e_start, e_end, e_handler, catch_cid))

    if verbose and global_exc_table:
        print(f"  Exception table: {len(global_exc_table)} entries")
        for i, (s, e, h, ct) in enumerate(global_exc_table):
            ct_str = "catch-all" if ct == PJVM_NO_CLASS else f"class#{ct}"
            print(f"    [{i}] start={s} end={e} handler={h} {ct_str}")

    # Concatenate final method bytecode streams.
    bytecode_section = _build_bytecode_section(method_table)

    # Validate selected format limits before emitting bytes.
    emit_v4, cp_bytes = _validate_and_select_format(
        pjvm_format, pack_method_table, classes, class_order, method_table,
        main_index, global_int_constants, global_string_constants,
        global_cp_resolve, global_exc_table, total_static_fields)

    out = bytearray()

    region_flags = 0
    if pin_hints:
        region_flags |= PJVM_RF_PIN_HINTS
    region_flags |= PJVM_RF_REF_BITMAPS
    if const_arrays:
        region_flags |= PJVM_RF_CONST_DATA
    if pack_method_table and emit_v4:
        region_flags |= PJVM_RF_PACKED_METHOD_TABLE

    if emit_v4:
        # v4 Header (24 bytes): 16-bit ids/counts and 32-bit CP section size.
        hdr_size = PJVM_HDR_SIZE_V4
        mt_entry_size = PJVM_MT_ENTRY_V4
        out.append(PJVM_MAGIC)
        out.append(PJVM_VERSION_V4)
        out.extend(struct.pack("<H", len(method_table)))
        out.extend(struct.pack("<H", main_index))
        out.extend(struct.pack("<H", total_static_fields))
        out.extend(struct.pack("<H", len(global_int_constants)))
        out.extend(struct.pack("<H", len(class_order)))
        out.extend(struct.pack("<H", len(global_string_constants)))
        out.extend(struct.pack("<H", region_flags))
        out.extend(struct.pack("<I", len(bytecode_section)))
        out.extend(struct.pack("<I", 0))  # reserved
    else:
        # v3 Header (16 bytes): 8-bit ids/counts, 16-bit static count.
        hdr_size = PJVM_HDR_SIZE_V3
        mt_entry_size = PJVM_MT_ENTRY
        out.append(PJVM_MAGIC)
        out.append(PJVM_VERSION_V3)
        out.append(len(method_table))
        out.append(main_index)
        out.extend(struct.pack("<H", total_static_fields))
        out.append(len(global_int_constants))
        out.append(len(class_order))
        out.append(len(global_string_constants))
        out.append(region_flags)
        out.extend(struct.pack("<I", len(bytecode_section)))
        out.extend(struct.pack("<H", 0))  # reserved

    # Class table.
    for name in class_order:
        cls = classes[name]
        if emit_v4:
            out.extend(struct.pack("<H", emit_id_v4(cls.parent_class_id)))
            out.extend(struct.pack("<H", len(cls.all_instance_fields)))
            out.extend(struct.pack("<H", len(cls.vtable)))
            out.extend(struct.pack("<H", emit_id_v4(cls.clinit_mi)))
            for vt_entry in cls.vtable:
                out.extend(struct.pack("<H", emit_id_v4(vt_entry)))
        else:
            out.append(emit_id_v3(cls.parent_class_id))
            out.append(len(cls.all_instance_fields))
            out.append(len(cls.vtable))
            out.append(emit_id_v3(cls.clinit_mi))
            for vt_entry in cls.vtable:
                out.append(emit_id_v3(vt_entry))
        n_bitmap = (len(cls.all_instance_field_is_ref) + 7) // 8
        for byte_idx in range(n_bitmap):
            bits = 0
            for bit in range(8):
                slot = byte_idx * 8 + bit
                if slot < len(cls.all_instance_field_is_ref) and cls.all_instance_field_is_ref[slot]:
                    bits |= 1 << bit
            out.append(bits)

    # Method table.
    if emit_v4 and pack_method_table:
        method_table_blob = encode_v4_method_table_packed(method_table)
        out.extend(struct.pack("<I", len(method_table_blob)))
        out.extend(method_table_blob)
        method_table_size = 4 + len(method_table_blob)
    else:
        method_table_size = len(method_table) * mt_entry_size
        for mt in method_table:
            flags = 0
            if mt["is_native"]:
                flags = 1 | (mt["native_id"] << 1)
            if emit_v4:
                out.extend(struct.pack("<H", mt["max_locals"]))
                out.extend(struct.pack("<H", mt["max_stack"]))
                out.extend(struct.pack("<H", mt["arg_count"]))
                out.extend(struct.pack("<H", flags))
                out.extend(struct.pack("<I", mt["code_offset"]))
                out.extend(struct.pack("<I", mt["cp_base"]))
                out.extend(struct.pack("<H", emit_id_v4(mt["vtable_slot"])))
                out.extend(struct.pack("<H", emit_id_v4(mt.get("vmid", PJVM_NO_VTABLE))))
                out.extend(struct.pack("<H", mt["exc_count"]))
                out.extend(struct.pack("<H", mt["exc_offset_idx"]))
            else:
                out.append(mt["max_locals"])
                out.append(mt["max_stack"])
                out.append(mt["arg_count"])
                out.append(flags)
                out.extend(struct.pack("<I", mt["code_offset"]))
                out.extend(struct.pack("<H", mt["cp_base"]))
                out.append(emit_id_v3(mt["vtable_slot"]))
                out.append(emit_id_v3(mt.get("vmid", PJVM_NO_VTABLE)))
                out.append(mt["exc_count"])
                out.append(mt["exc_offset_idx"])

    # CP resolution table (16-bit LE entries)
    if emit_v4:
        out.extend(struct.pack("<I", cp_bytes))
    else:
        out.extend(struct.pack("<H", cp_bytes))
    for val in global_cp_resolve:
        out.extend(struct.pack("<H", val))

    # Integer constants (4 bytes each, little-endian)
    for val in global_int_constants:
        out.extend(struct.pack("<i", val))

    # String constants (len_le16 + utf8_bytes each)
    for s in global_string_constants:
        out.extend(struct.pack("<H", len(s)))
        out.extend(s)

    # Bytecode section
    out.extend(bytecode_section)

    # Exception table section
    for (e_start, e_end, e_handler, e_catch_cid) in global_exc_table:
        if emit_v4:
            out.extend(struct.pack("<HHHH", e_start, e_end, e_handler,
                                   emit_id_v4(e_catch_cid)))
        else:
            out.extend(struct.pack("<H", e_start))
            out.extend(struct.pack("<H", e_end))
            out.extend(struct.pack("<H", e_handler))
            out.append(emit_id_v3(e_catch_cid))

    # Pin hints (one byte per method, after exception table)
    if pin_hints:
        pin_set = set(pin_hints)
        for i in range(len(method_table)):
            out.append(1 if i in pin_set else 0)
        if verbose:
            print(f"  Pin hints: methods {sorted(pin_set)}")

    # const_data section (after pin hints)
    cd_init_entries = []  # (static_field_slot, lo, hi)
    if const_arrays:
        cd_start = len(out)
        out.extend(struct.pack("<H", len(const_arrays)))

        for fname, etype, vals, cname in const_arrays:
            entry_offset = len(out)  # file offset of this entry's header
            n_elem = _const_array_length(etype, vals)
            out.extend(struct.pack("<H", n_elem))  # n_elements
            out.append(etype)                       # elem_type
            out.append(0)                           # reserved
            # Element data
            if etype == 0:  # byte
                for v in vals:
                    out.append(v & 0xFF)
            elif etype in (1, 2, PJVM_ELEM_STRING_REF):  # char/short/string-ref (2 bytes each)
                for v in vals:
                    out.extend(struct.pack("<H", v & 0xFFFF))
            elif etype == 3:  # int (4 bytes each)
                for v in vals:
                    out.extend(struct.pack("<I", v & 0xFFFFFFFF))
            elif etype == PJVM_ELEM_OBJECT_REF:
                out.extend(struct.pack("<HH", vals["class_id"], vals["n_fields"]))
                for record in vals["records"]:
                    class_id = PJVM_CONST_NULL_REF if record is None else vals["class_id"]
                    out.extend(struct.pack("<HH", class_id, vals["n_fields"]))
                    slots = record if record is not None else [(0, 0)] * vals["n_fields"]
                    for lo, hi in slots:
                        out.extend(struct.pack("<HH", lo, hi))

            # Compute ROM reference for this entry
            lo = entry_offset & 0xFFFF
            hi = ((entry_offset >> 16) + 1) & 0xFFFF

            # Find the global static field slot for this field
            cls = classes[cname]
            base = static_field_base[cname]
            for si, sf in enumerate(cls.static_fields):
                if sf == fname:
                    cd_init_entries.append((base + si, lo, hi))
                    break

        # Static field init table: n_init followed by (slot, lo, hi) entries
        out.extend(struct.pack("<H", len(cd_init_entries)))
        for (slot, lo, hi) in cd_init_entries:
            out.extend(struct.pack("<HHH", slot, lo, hi))

        if verbose:
            print(f"  const_data: {len(const_arrays)} arrays, "
                  f"{len(out) - cd_start} bytes")
            for i, (fname, etype, vals, cname) in enumerate(const_arrays):
                tnames = ['byte', 'char', 'short', 'int', 'string', 'object']
                print(f"    [{i}] {cname}.{fname}: "
                      f"{tnames[etype]}[{_const_array_length(etype, vals)}]")

    if verbose:
        fmt_name = "v4" if emit_v4 else "v3"
        print(f"\n.pjvm output ({fmt_name}): {len(out)} bytes")
        print(f"  Header: {hdr_size} bytes")
        class_fixed = 8 if emit_v4 else 4
        vt_entry_size = 2 if emit_v4 else 1
        cp_len_size = 4 if emit_v4 else 2
        et_entry_size = PJVM_ET_ENTRY_V4 if emit_v4 else PJVM_ET_ENTRY
        ct_size = sum(class_fixed + len(classes[n].vtable) * vt_entry_size +
                      ((len(classes[n].all_instance_field_is_ref) + 7) // 8)
                      for n in class_order)
        print(f"  Class table: {len(class_order)} classes, {ct_size} bytes")
        if emit_v4 and pack_method_table:
            fixed_size = len(method_table) * PJVM_MT_ENTRY_V4
            print(f"  Method table: packed {method_table_size} bytes "
                  f"({fixed_size - method_table_size} bytes saved)")
        else:
            print(f"  Method table: {len(method_table)} × {mt_entry_size} = "
                  f"{method_table_size} bytes")
        print(f"  CP resolution: {cp_bytes + cp_len_size} bytes "
              f"({len(global_cp_resolve)} entries × 2)")
        if compact_cp:
            saved = (cp_old_entries - cp_new_entries) * 2
            print(f"  CP compact: {cp_old_entries} -> {cp_new_entries} "
                  f"entries ({saved} bytes saved)")
        print(f"  Int constants: {len(global_int_constants)} × 4 = "
              f"{len(global_int_constants) * 4} bytes")
        sc_size = sum(2 + len(s) for s in global_string_constants)
        print(f"  String constants: {len(global_string_constants)}, "
              f"{sc_size} bytes")
        for i, s in enumerate(global_string_constants):
            print(f"    [{i}] \"{s.decode('utf-8')}\" ({len(s)} bytes)")
        print(f"  Bytecodes: {len(bytecode_section)} bytes")
        print(f"  Exception table: {len(global_exc_table)} × {et_entry_size} = "
              f"{len(global_exc_table) * et_entry_size} bytes")
        print(f"  Entry point: method #{main_index} "
              f"({method_table[main_index]['name']})")
        for name in class_order:
            cls = classes[name]
            if cls.vtable:
                vt_str = ", ".join(
                    f"{method_table[m]['name']}" for m in cls.vtable)
                print(f"  {name} vtable[{len(cls.vtable)}]: {vt_str}")

    return bytes(out), class_order, method_table
